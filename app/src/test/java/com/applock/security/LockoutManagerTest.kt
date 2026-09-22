package com.applock.security

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lockout tests (FR-174, R-007). The manager keeps an in-memory authoritative snapshot, persists off the caller
 * thread, and enforces a degraded in-memory fallback when a durable write fails. These tests cover the ordinary
 * lockout math (on the async API), the degraded fallback, the completion-handling rules, the reset persistence
 * outcome, the cold-start re-seed, and the lifecycle.
 *
 * The concurrency-sensitive tests use [GatedStorage], whose writes can be held at a per-write gate on the manager's
 * real single-thread persistence dispatcher, so a second admission can publish while a first write is still parked.
 * The simple math tests use the auto-gated storage (writes commit at once) and await through [runBlocking].
 */
class LockoutManagerTest {

    /**
     * A [LockoutStorage] whose writes can be individually held. When [autoGate] is true a write commits at once.
     * When false, the Nth write parks at gate N until [release] is called for it, so a test can hold one write
     * in flight. [read] throws [readError] when set; [write] throws [writeError] when set (a throwing commit, which
     * the manager contains as a not-committed result). [queueResults] preloads per-write commit results (FIFO by
     * write-execution order); a write with no queued result commits successfully.
     */
    private class GatedStorage(seed: LockoutSnapshot = LockoutSnapshot(0, 0L)) : LockoutStorage {
        @Volatile
        var readError: Exception? = null

        @Volatile
        var writeError: Exception? = null

        @Volatile
        var autoGate: Boolean = true

        @Volatile
        private var stored: LockoutSnapshot = seed
        private val nextResults = LinkedBlockingQueue<Boolean>()
        private val index = AtomicInteger(0)
        private val gates = ConcurrentHashMap<Int, CountDownLatch>()

        // Optional latches to park a READ in flight (used for the re-seed shutdown test). The construction seed
        // read runs before a test sets these, so only a later re-seed read parks.
        @Volatile
        var readEntered: CountDownLatch? = null

        @Volatile
        var readProceed: CountDownLatch? = null

        /** Each write's payload, published as the write STARTS (before it parks). Drained by a test. */
        val started = LinkedBlockingQueue<LockoutSnapshot>()

        /** Each write's payload, in commit order (only successful commits). */
        val committed = CopyOnWriteArrayList<LockoutSnapshot>()

        override fun read(): LockoutSnapshot {
            readEntered?.countDown()
            readProceed?.await()
            readError?.let { throw it }
            return stored
        }

        override fun write(snapshot: LockoutSnapshot): Boolean {
            val i = index.getAndIncrement()
            started.put(snapshot)
            if (!autoGate) gates.computeIfAbsent(i) { CountDownLatch(1) }.await()
            writeError?.let { throw it }
            val ok = nextResults.poll() ?: true
            if (ok) {
                stored = snapshot
                committed.add(snapshot)
            }
            return ok
        }

        fun release(writeIndex: Int) = gates.computeIfAbsent(writeIndex) { CountDownLatch(1) }.countDown()

        fun queueResults(vararg results: Boolean) = nextResults.addAll(results.toList())

        fun awaitStart(): LockoutSnapshot =
            started.poll(2, TimeUnit.SECONDS) ?: error("a write never started")
    }

    private var wallNow = 1_000_000L
    private var monoNow = 5_000_000L
    private val managers = mutableListOf<LockoutManager>()

    @After
    fun tearDown() {
        managers.forEach { it.shutdown() }
    }

    private fun manager(storage: LockoutStorage): LockoutManager =
        LockoutManager(storage, clock = { wallNow }, elapsedRealtime = { monoNow }).also { managers += it }

    // ---- Await helpers on the new async API ----------------------------------------------------

    private fun LockoutManager.failOnce(): LockoutManager.FailureOutcome =
        runBlocking { withTimeout(TIMEOUT_MS) { recordFailure() } }

    private fun LockoutManager.failTimes(n: Int): LockoutState {
        var state: LockoutState = LockoutState.Available
        repeat(n) { state = failOnce().state }
        return state
    }

    private fun LockoutManager.succeed() = runBlocking { withTimeout(TIMEOUT_MS) { recordSuccess() } }

    private fun locked(state: LockoutState): LockoutState.LockedOut {
        assertTrue("expected LockedOut, was $state", state is LockoutState.LockedOut)
        return state as LockoutState.LockedOut
    }

    // ---- Ordinary lockout math (on the new async API) ------------------------------------------

    @Test
    fun `no lockout below threshold`() {
        val manager = manager(GatedStorage())
        val state = manager.failTimes(LockoutManager.FAILURE_THRESHOLD - 1)
        assertEquals(LockoutState.Available, state)
        assertEquals(LockoutState.Available, manager.currentState())
    }

    @Test
    fun `threshold failure triggers base lockout`() {
        val manager = manager(GatedStorage())
        val state = manager.failTimes(LockoutManager.FAILURE_THRESHOLD)
        assertEquals(LockoutManager.BASE_LOCKOUT_MS, locked(state).remainingMs)
        assertFalse("a threshold lockout that persisted is recorded, not degraded", locked(state).degraded)
    }

    @Test
    fun `failure count tracks consecutive failures and resets on success`() {
        val manager = manager(GatedStorage())
        assertEquals(0, manager.failureCount())
        manager.failTimes(3)
        assertEquals(3, manager.failureCount())
        manager.succeed()
        assertEquals(0, manager.failureCount())
    }

    @Test
    fun `failure count keeps climbing past the lockout threshold`() {
        val manager = manager(GatedStorage())
        manager.failTimes(LockoutManager.FAILURE_THRESHOLD)
        monoNow += LockoutManager.BASE_LOCKOUT_MS
        wallNow += LockoutManager.BASE_LOCKOUT_MS
        manager.failOnce()
        assertEquals(LockoutManager.FAILURE_THRESHOLD + 1, manager.failureCount())
    }

    @Test
    fun `lockout expires after its duration`() {
        val manager = manager(GatedStorage())
        manager.failTimes(LockoutManager.FAILURE_THRESHOLD)
        wallNow += LockoutManager.BASE_LOCKOUT_MS - 1
        monoNow += LockoutManager.BASE_LOCKOUT_MS - 1
        assertTrue(manager.currentState() is LockoutState.LockedOut)
        wallNow += 1
        monoNow += 1
        assertEquals(LockoutState.Available, manager.currentState())
    }

    @Test
    fun `delay doubles with each failure past the threshold`() {
        val manager = manager(GatedStorage())
        manager.failTimes(LockoutManager.FAILURE_THRESHOLD)
        wallNow += LockoutManager.BASE_LOCKOUT_MS
        monoNow += LockoutManager.BASE_LOCKOUT_MS

        val sixth = manager.failOnce().state
        assertEquals(2 * LockoutManager.BASE_LOCKOUT_MS, locked(sixth).remainingMs)

        wallNow += 2 * LockoutManager.BASE_LOCKOUT_MS
        monoNow += 2 * LockoutManager.BASE_LOCKOUT_MS
        val seventh = manager.failOnce().state
        assertEquals(4 * LockoutManager.BASE_LOCKOUT_MS, locked(seventh).remainingMs)
    }

    @Test
    fun `lockout duration is capped`() {
        val manager = manager(GatedStorage())
        val state = manager.failTimes(50)
        assertEquals(LockoutManager.MAX_LOCKOUT_MS, locked(state).remainingMs)
    }

    @Test
    fun `success resets counter and lockout`() {
        val manager = manager(GatedStorage())
        manager.failTimes(LockoutManager.FAILURE_THRESHOLD)
        manager.succeed()
        assertEquals(LockoutState.Available, manager.currentState())
        val state = manager.failTimes(LockoutManager.FAILURE_THRESHOLD - 1)
        assertEquals(LockoutState.Available, state)
    }

    @Test
    fun `counter survives process restart via storage`() {
        val storage = GatedStorage()
        val manager = manager(storage)
        manager.failTimes(LockoutManager.FAILURE_THRESHOLD - 1)
        // Simulate process death: a new manager over the same (persisted) storage.
        val revived = manager(storage)
        val state = revived.failOnce().state
        assertEquals(LockoutManager.BASE_LOCKOUT_MS, locked(state).remainingMs)
    }

    @Test
    fun `active lockout survives process restart`() {
        val storage = GatedStorage()
        val manager = manager(storage)
        manager.failTimes(LockoutManager.FAILURE_THRESHOLD)
        val revived = manager(storage)
        assertTrue(revived.currentState() is LockoutState.LockedOut)
    }

    @Test
    fun `clock moving backwards cannot extend lockout past the cap`() {
        val manager = manager(GatedStorage())
        manager.failTimes(LockoutManager.FAILURE_THRESHOLD)
        wallNow -= 24 * 60 * 60_000L // user set the wall clock back a day
        val state = locked(manager.currentState())
        assertTrue(state.remainingMs <= LockoutManager.MAX_LOCKOUT_MS)
    }

    @Test
    fun `extreme failure counts do not overflow the duration`() {
        assertEquals(LockoutManager.MAX_LOCKOUT_MS, LockoutManager.lockoutDurationFor(1000))
        assertEquals(LockoutManager.MAX_LOCKOUT_MS, LockoutManager.lockoutDurationFor(Int.MAX_VALUE))
        assertTrue(LockoutManager.lockoutDurationFor(Int.MIN_VALUE) > 0)
    }

    @Test
    fun `currentState is idempotent during an active lockout`() {
        val manager = manager(GatedStorage())
        manager.failTimes(LockoutManager.FAILURE_THRESHOLD)
        wallNow += 10_000
        monoNow += 10_000
        val first = manager.currentState()
        val second = manager.currentState()
        assertEquals(first, second)
        assertEquals(LockoutManager.BASE_LOCKOUT_MS - 10_000, locked(second).remainingMs)
    }

    @Test
    fun `failure during an active lockout restarts a longer window`() {
        val manager = manager(GatedStorage())
        manager.failTimes(LockoutManager.FAILURE_THRESHOLD)
        wallNow += 5_000
        monoNow += 5_000
        val state = manager.failOnce().state
        assertEquals(2 * LockoutManager.BASE_LOCKOUT_MS, locked(state).remainingMs)
    }

    @Test
    fun `recordFailure returns the state and count of the same failure`() {
        val manager = manager(GatedStorage())
        repeat(LockoutManager.FAILURE_THRESHOLD + 2) { i ->
            val outcome = manager.failOnce()
            val expectedCount = i + 1
            assertEquals(expectedCount, outcome.count)
            assertEquals(
                expectedCount >= LockoutManager.FAILURE_THRESHOLD,
                outcome.state is LockoutState.LockedOut,
            )
        }
    }

    // ---- R-007: degraded-storage fallback ------------------------------------------------------

    @Test
    fun `a below-threshold failure whose write fails arms the degraded fallback`() {
        val storage = GatedStorage()
        storage.queueResults(false) // the first (and only) write fails to commit
        val manager = manager(storage)

        val outcome = manager.failOnce()

        // Failure 1 is below the 5-failure threshold, but its write failed, so the next attempt is blocked.
        val state = locked(outcome.state)
        assertTrue("a lost failure count cannot be trusted, so it enforces in memory", state.degraded)
        assertEquals(1, outcome.count) // the actual count, never a fabricated threshold
        assertTrue(manager.currentState() is LockoutState.LockedOut)
    }

    @Test
    fun `the degraded fallback keeps blocking through successful reads until it expires`() {
        val storage = GatedStorage()
        storage.queueResults(false)
        val manager = manager(storage)
        manager.failOnce() // arms the fallback for BASE_LOCKOUT_MS from monoNow

        storage.readError = null // reads succeed; the fallback is in memory, not read from storage
        monoNow += LockoutManager.BASE_LOCKOUT_MS - 1
        assertTrue("still within the fallback window", manager.currentState() is LockoutState.LockedOut)
        monoNow += 1
        assertEquals(LockoutState.Available, manager.currentState())
    }

    @Test
    fun `a threshold failure whose write fails is degraded, not recorded`() {
        val storage = GatedStorage()
        storage.queueResults(true, true, true, true, false) // the fifth (threshold) write fails
        val manager = manager(storage)

        val state = locked(manager.failTimes(LockoutManager.FAILURE_THRESHOLD))
        assertTrue("the threshold lockout could not persist, so it is degraded", state.degraded)
        assertEquals(LockoutManager.BASE_LOCKOUT_MS, state.remainingMs)
    }

    @Test
    fun `a recorded lockout is not degraded`() {
        val manager = manager(GatedStorage())
        val state = locked(manager.failTimes(LockoutManager.FAILURE_THRESHOLD))
        assertFalse(state.degraded)
    }

    @Test
    fun `the adapter write result of false is a storage failure`() {
        val storage = GatedStorage()
        storage.queueResults(false)
        val manager = manager(storage)
        // commit()==false must be treated exactly like a throw: the fallback arms.
        assertTrue(locked(manager.failOnce().state).degraded)
    }

    // ---- Reset persistence outcome (a failed durable clear is surfaced, not swallowed) ---------

    @Test
    fun `a committed reset resolves committed`() {
        val manager = manager(GatedStorage())
        manager.failTimes(LockoutManager.FAILURE_THRESHOLD)
        val committed = runBlocking { withTimeout(TIMEOUT_MS) { manager.recordSuccess() } }
        assertTrue("a reset whose write commits resolves committed", committed)
        assertEquals(LockoutState.Available, manager.currentState())
    }

    @Test
    fun `a reset whose write returns false resolves not-committed but still clears in memory`() {
        val storage = GatedStorage()
        storage.queueResults(true, true, true, true, true, false) // the 5 failures commit; the reset write fails
        val manager = manager(storage)
        manager.failTimes(LockoutManager.FAILURE_THRESHOLD)
        assertTrue(manager.currentState() is LockoutState.LockedOut)

        val committed = runBlocking { withTimeout(TIMEOUT_MS) { manager.recordSuccess() } }

        // commit()==false must resolve not-committed so a caller can report the failed clear, but the in-memory
        // reset is authoritative: the authenticated user is never re-locked.
        assertFalse("commit()==false must resolve not-committed", committed)
        assertEquals(
            "the in-memory reset still cleared the lockout (no re-lock)",
            LockoutState.Available,
            manager.currentState(),
        )
    }

    @Test
    fun `a reset whose write throws is contained as not-committed and does not re-lock`() {
        val storage = GatedStorage()
        val manager = manager(storage)
        manager.failTimes(LockoutManager.FAILURE_THRESHOLD) // recorded lockout, the failure writes committed
        assertTrue(manager.currentState() is LockoutState.LockedOut)
        storage.writeError = RuntimeException("commit boom") // the reset write throws

        val committed = runBlocking { withTimeout(TIMEOUT_MS) { manager.recordSuccess() } }

        // A throwing commit is contained exactly like commit()==false: not-committed, but the in-memory clear holds.
        assertFalse("a throwing reset write is contained as not-committed", committed)
        assertEquals("the in-memory reset still cleared the lockout", LockoutState.Available, manager.currentState())
    }

    // ---- currentState stays responsive and picks the longer remaining --------------------------

    @Test
    fun `currentState stays responsive and enforcement holds while a write is stalled`() {
        val storage = GatedStorage().apply { autoGate = false }
        val manager = manager(storage)

        repeat(LockoutManager.FAILURE_THRESHOLD) { manager.submitFailure() } // admissions only
        storage.awaitStart() // the first write is now parked, holding the dispatcher

        // currentState reads the published snapshot without the lock, so it returns at once even while the
        // durable write is stalled, and it already enforces the threshold lockout from the admissions.
        assertTrue(manager.currentState() is LockoutState.LockedOut)
        assertEquals(LockoutManager.FAILURE_THRESHOLD, manager.failureCount())

        repeat(LockoutManager.FAILURE_THRESHOLD) { storage.release(it) } // let the parked writes drain
    }

    @Test
    fun `currentState returns the longer remaining so a forward wall-clock jump cannot shorten enforcement`() {
        val manager = manager(GatedStorage())
        manager.failTimes(LockoutManager.FAILURE_THRESHOLD) // wall and mono deadlines both at +BASE

        // The wall clock jumps forward past the wall deadline, but the monotonic deadline is untouched.
        wallNow += 2 * LockoutManager.BASE_LOCKOUT_MS
        val state = locked(manager.currentState())
        assertEquals(LockoutManager.BASE_LOCKOUT_MS, state.remainingMs) // the monotonic deadline still holds
    }

    @Test
    fun `a recorded lockout holds until the monotonic clock also passes the deadline`() {
        // The manager trusts the injected monotonic clock, so a recorded lockout expires only when both the wall
        // and monotonic deadlines pass. This is why production must inject a boot-time clock
        // (SystemClock.elapsedRealtime): if the monotonic clock froze during deep sleep (System.nanoTime) while
        // the wall clock advanced, the lockout would outlive its wall deadline after wake.
        val manager = manager(GatedStorage())
        manager.failTimes(LockoutManager.FAILURE_THRESHOLD) // wall and mono deadlines both at +BASE

        // Only the wall clock advances past the deadline (a frozen monotonic clock, as during deep sleep).
        wallNow += LockoutManager.BASE_LOCKOUT_MS + 5_000
        assertTrue("a frozen monotonic clock keeps the lockout alive", manager.currentState() is LockoutState.LockedOut)

        // A sleep-aware monotonic clock advances too, so the lockout expires.
        monoNow += LockoutManager.BASE_LOCKOUT_MS + 5_000
        assertEquals(LockoutState.Available, manager.currentState())
    }

    // ---- Cold-start re-seed --------------------------------------------------------------------

    @Test
    fun `a failed seed read degrades to Available then an off-main re-seed picks up the persisted lockout`() {
        val persisted = LockoutSnapshot(LockoutManager.FAILURE_THRESHOLD, wallNow + LockoutManager.BASE_LOCKOUT_MS)
        val storage = GatedStorage(seed = persisted)
        storage.readError = RuntimeException("decrypt boom") // the construction seed read fails
        val manager = manager(storage)

        assertEquals(LockoutState.Available, manager.currentState()) // seed failed; re-seed read also still fails

        storage.readError = null // storage recovers
        runBlocking {
            withTimeout(TIMEOUT_MS) {
                manager.currentState() // triggers the lazy re-seed
                while (manager.currentState() is LockoutState.Available) delay(10)
            }
        }
        val state = locked(manager.currentState())
        assertFalse("a re-seeded persisted lockout is recorded, not degraded", state.degraded)
    }

    @Test
    fun `re-seed is disabled after any local mutation so a failed reset is not resurrected`() {
        val persisted = LockoutSnapshot(LockoutManager.FAILURE_THRESHOLD, wallNow + LockoutManager.BASE_LOCKOUT_MS)
        val storage = GatedStorage(seed = persisted)
        storage.readError = RuntimeException("decrypt boom")
        val manager = manager(storage)
        assertEquals(LockoutState.Available, manager.currentState())

        storage.readError = null
        storage.queueResults(false) // the reset's own write fails, so storage keeps the old lockout
        manager.succeed() // a local mutation: the snapshot is now authoritative, re-seed is disabled

        // A re-seed WOULD read the still-persisted lockout, but the local mutation disabled it, so the reset is
        // not resurrected. Give any (disabled) re-seed a chance to run, then confirm it stayed Available.
        runBlocking {
            withTimeout(TIMEOUT_MS) {
                repeat(5) {
                    manager.currentState()
                    delay(10)
                }
            }
        }
        assertEquals(LockoutState.Available, manager.currentState())
    }

    @Test
    fun `an in-flight re-seed does not publish after shutdown`() {
        // shutdown() does not cancel the scope, so a re-seed read in flight when shutdown returns must still not
        // publish to the stopped manager.
        val persisted = LockoutSnapshot(LockoutManager.FAILURE_THRESHOLD, wallNow + LockoutManager.BASE_LOCKOUT_MS)
        val storage = GatedStorage(seed = persisted)
        storage.readError = RuntimeException("decrypt boom") // the construction seed read fails
        val manager = manager(storage)
        assertEquals(0, manager.failureCount()) // seed degraded to empty (no re-seed triggered yet)

        // Storage recovers, but the next re-seed read is gated so it parks in flight.
        val entered = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        storage.readEntered = entered
        storage.readProceed = proceed
        storage.readError = null

        manager.currentState() // triggers the lazy re-seed, which parks in the gated read
        assertTrue("re-seed read never started", entered.await(2, TimeUnit.SECONDS))

        manager.shutdown() // stopped; the scope is not cancelled
        proceed.countDown() // the re-seed read completes; its publication must be skipped

        // The re-seed must not publish to a stopped manager: the state stays Available across the window.
        runBlocking {
            withTimeout(TIMEOUT_MS) {
                repeat(20) {
                    assertEquals("re-seed published after shutdown", LockoutState.Available, manager.currentState())
                    assertEquals(0, manager.failureCount())
                    delay(25)
                }
            }
        }
    }

    // ---- Completion handling, split by outcome (ordering / interleaving) ------------------------

    @Test
    fun `admission publishes before the write, in admission order, and disk order matches`() {
        val storage = GatedStorage().apply { autoGate = false }
        val manager = manager(storage)

        manager.submitFailure() // rev1: count 1
        storage.awaitStart()
        manager.submitFailure() // rev2: count 2 (published at admission, its write queued behind rev1)

        assertEquals(2, manager.failureCount()) // the snapshot advanced to rev2 before rev1's write ran

        storage.release(0)
        storage.release(1)
        runBlocking { withTimeout(TIMEOUT_MS) { while (storage.committed.size < 2) delay(10) } }
        assertEquals(listOf(1, 2), storage.committed.map { it.failureCount }) // disk FIFO equals admission order
    }

    @Test
    fun `an older success completion is dropped by the revision check`() {
        // Seed at threshold minus one so two more failures both cross the threshold (degraded=true pending).
        val storage = GatedStorage(seed = LockoutSnapshot(LockoutManager.FAILURE_THRESHOLD - 1, 0L))
            .apply { autoGate = false }
        storage.queueResults(true, true) // both writes commit
        val manager = manager(storage)

        manager.submitFailure() // count 5: threshold, degraded=true pending, write index 0
        storage.awaitStart()
        val revB = manager.submitFailure() // count 6: threshold, degraded=true pending, write index 1 queued

        storage.release(0)
        storage.awaitStart() // revB's write starting proves revA's completion already ran on the dispatcher

        // revA's success returned while revB is current, so it was dropped: it did not mark the lockout recorded.
        assertTrue(locked(manager.currentState()).degraded)

        storage.release(1)
        val outcome = runBlocking { withTimeout(TIMEOUT_MS) { revB.resolved.await() } }
        assertFalse("revB's own success marks the lockout recorded", locked(outcome.state).degraded)
        assertEquals(6, manager.failureCount())
    }

    @Test
    fun `a stale failed write still arms the fallback while its streak is current`() {
        // Sequence (a): fail1(rev1), fail2(rev2), then rev1's write fails. rev1 is stale but its streak is
        // current, so the degraded fallback still activates.
        val storage = GatedStorage().apply { autoGate = false }
        storage.queueResults(false) // rev1's write (index 0) fails
        val manager = manager(storage)

        manager.submitFailure() // rev1: count 1, write index 0
        storage.awaitStart()
        manager.submitFailure() // rev2: count 2, write index 1 queued

        storage.release(0)
        storage.awaitStart() // rev2's write starting proves rev1's completion ran
        assertTrue("a stale failure in the current streak still blocks", locked(manager.currentState()).degraded)

        storage.release(1)
    }

    @Test
    fun `a reset advances the streak so an older failed write cannot reinstate a lockout`() {
        // Sequence (b): fail1(rev1), then a successful reset (streak advances), then rev1's write fails. The
        // fallback is not reinstated.
        val storage = GatedStorage().apply { autoGate = false }
        storage.queueResults(false, true) // rev1's failure write fails; the reset write commits
        val manager = manager(storage)

        manager.submitFailure() // rev1: count 1, write index 0
        storage.awaitStart()
        manager.submitSuccess() // reset: streak advances, write index 1 queued

        storage.release(0)
        storage.awaitStart() // the reset write starting proves rev1's completion ran
        assertEquals("the reset fenced off the stale failure", LockoutState.Available, manager.currentState())

        storage.release(1)
    }

    @Test
    fun `a write that stalled past the fallback window installs a fresh degraded lockout`() {
        // The fallback deadline must start at completion time, not admission time. A write that stalls past the
        // window and then fails must still block the next attempt.
        val storage = GatedStorage().apply { autoGate = false }
        storage.queueResults(false) // the write fails
        val manager = manager(storage)

        val pending = manager.submitFailure() // count 1, write index 0, parked
        storage.awaitStart()
        monoNow += LockoutManager.BASE_LOCKOUT_MS + 5_000 // the write stalls past the fallback window
        storage.release(0)
        runBlocking { withTimeout(TIMEOUT_MS) { pending.resolved.await() } }

        // A fresh fallback, measured from the completion, still blocks; it does not arrive already-expired.
        assertTrue("a fresh fallback blocks the next attempt", manager.currentState() is LockoutState.LockedOut)
        monoNow += LockoutManager.BASE_LOCKOUT_MS - 1
        assertTrue(manager.currentState() is LockoutState.LockedOut)
        monoNow += 1
        assertEquals(LockoutState.Available, manager.currentState())
    }

    @Test
    fun `overlapping attempts each report their own count`() {
        // Two overlapping attempts must return their own counts, not the latest global snapshot's count.
        val storage = GatedStorage(seed = LockoutSnapshot(1, 0L)).apply { autoGate = false }
        val manager = manager(storage)

        val firstAttempt = manager.submitFailure() // count 2, write index 0, parked
        storage.awaitStart()
        val secondAttempt = manager.submitFailure() // count 3, write index 1 queued

        storage.release(0)
        storage.awaitStart()
        storage.release(1)
        val firstOutcome = runBlocking { withTimeout(TIMEOUT_MS) { firstAttempt.resolved.await() } }
        val secondOutcome = runBlocking { withTimeout(TIMEOUT_MS) { secondAttempt.resolved.await() } }

        assertEquals(2, firstOutcome.count)
        assertEquals(3, secondOutcome.count)
    }

    @Test
    fun `a later reset does not change an earlier failure's reported count`() {
        // A reset admitted while a failure write is in flight must not rewrite that failure's reported count.
        val storage = GatedStorage().apply { autoGate = false }
        val manager = manager(storage)

        val failureAttempt = manager.submitFailure() // count 1, write index 0, parked
        storage.awaitStart()
        manager.submitSuccess() // reset admitted: the global count is now 0, write index 1 queued

        storage.release(0)
        val failureOutcome = runBlocking { withTimeout(TIMEOUT_MS) { failureAttempt.resolved.await() } }
        assertEquals(1, failureOutcome.count) // the failure still reports its own count, not the reset's 0

        storage.awaitStart()
        storage.release(1) // drain the reset write
    }

    @Test
    fun `a below-threshold success does not clear an unrelated degraded fallback`() {
        // A failed below-threshold write arms a degraded fallback; an overlapping below-threshold success persists
        // only a count, so it must not mark the in-memory fallback as recorded.
        val storage = GatedStorage().apply { autoGate = false }
        storage.queueResults(false, true) // write 0 fails (arms fallback), write 1 succeeds (count only)
        val manager = manager(storage)

        val firstAttempt = manager.submitFailure() // count 1, write index 0, parked (will fail)
        storage.awaitStart()
        val secondAttempt = manager.submitFailure() // count 2, write index 1 queued (will succeed, below threshold)

        storage.release(0)
        storage.awaitStart() // write 1 starting proves write 0's completion armed the fallback
        storage.release(1)
        val firstOutcome = runBlocking { withTimeout(TIMEOUT_MS) { firstAttempt.resolved.await() } }
        val secondOutcome = runBlocking { withTimeout(TIMEOUT_MS) { secondAttempt.resolved.await() } }

        assertTrue("the fallback stays degraded, not recorded", locked(manager.currentState()).degraded)
        assertTrue("the failed write is a degraded lockout", locked(firstOutcome.state).degraded)
        assertEquals("a below-threshold success reports no lockout", LockoutState.Available, secondOutcome.state)
    }

    @Test
    fun `a delayed threshold write whose deadline elapsed is not mislabeled as durable`() {
        // Failure 4 stalls and fails, arming a fresh fallback. Overlapping failure 5 then commits a threshold
        // deadline that already elapsed during the stall. The active lockout is the fresh fallback, so the state
        // must read degraded, and a restarted manager (reading only the elapsed persisted deadline) is Available.
        val storage = GatedStorage(seed = LockoutSnapshot(LockoutManager.FAILURE_THRESHOLD - 2, 0L))
            .apply { autoGate = false }
        storage.queueResults(false, true) // failure 4's write fails; failure 5's write commits
        val manager = manager(storage)

        val failure4 = manager.submitFailure() // count 4 (below threshold), write index 0, parked (will fail)
        storage.awaitStart()
        val failure5 = manager.submitFailure() // count 5 (threshold), write index 1 queued (will commit)

        // Both writes stall well past the 30s lockout window.
        wallNow += 40_000
        monoNow += 40_000

        storage.release(0) // failure 4 fails -> arms a fresh fallback measured from completion time
        storage.awaitStart() // failure 5's write starting proves failure 4's completion ran
        storage.release(1) // failure 5 commits its already-elapsed threshold deadline
        val outcome4 = runBlocking { withTimeout(TIMEOUT_MS) { failure4.resolved.await() } }
        val outcome5 = runBlocking { withTimeout(TIMEOUT_MS) { failure5.resolved.await() } }
        assertEquals(4, outcome4.count)
        assertEquals(5, outcome5.count)

        // The persisted deadline elapsed during the stall, so the active lockout is the in-memory fallback.
        assertTrue("an elapsed persisted deadline must not look durable", locked(manager.currentState()).degraded)

        // A restarted manager reads only the persisted (elapsed) deadline, so it reports Available.
        val revived = manager(storage)
        assertEquals(LockoutState.Available, revived.currentState())
    }

    // ---- Lifecycle -----------------------------------------------------------------------------

    @Test
    fun `an in-flight write during shutdown publishes nothing`() {
        val storage = GatedStorage().apply { autoGate = false }
        storage.queueResults(false) // the in-flight write would arm the fallback, if its completion ran
        val manager = manager(storage)

        val pending = manager.submitFailure() // count 1, write index 0, parked at the gate
        storage.awaitStart()

        manager.shutdown() // marks stopped; the in-flight write drains but its completion is gated
        storage.release(0) // the parked write runs to completion, but its completion is dropped
        runBlocking { withTimeout(TIMEOUT_MS) { pending.resolved.await() } }

        // The failed write would normally arm the degraded fallback. After shutdown it publishes nothing.
        assertEquals(LockoutState.Available, manager.currentState())
    }

    @Test
    fun `a completion callback does not fire after shutdown`() {
        val storage = GatedStorage().apply { autoGate = false }
        val manager = manager(storage)
        val fired = AtomicBoolean(false)

        val pending = manager.submitFailure { fired.set(true) } // parked at write index 0
        storage.awaitStart()

        manager.shutdown()
        storage.release(0) // the write drains, but the completion callback is gated by stopped
        runBlocking { withTimeout(TIMEOUT_MS) { pending.resolved.await() } }

        assertFalse("the completion callback must not fire after shutdown", fired.get())
    }

    @Test
    fun `after shutdown a submission mutates nothing and resolves`() {
        val manager = manager(GatedStorage())
        manager.shutdown()

        val pending = manager.submitFailure()
        assertEquals(LockoutState.Available, pending.immediate)
        val outcome = runBlocking { withTimeout(TIMEOUT_MS) { pending.resolved.await() } }
        assertEquals(0, outcome.count) // no mutation
        assertEquals(LockoutState.Available, manager.currentState())
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
