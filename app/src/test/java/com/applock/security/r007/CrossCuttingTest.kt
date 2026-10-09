package com.applock.security.r007

import com.applock.security.LockoutManager
import com.applock.security.LockoutManager.FailureOutcome
import com.applock.security.LockoutSnapshot
import com.applock.security.LockoutState.Available
import com.applock.security.LockoutState.LockedOut
import com.applock.security.LockoutStorage
import com.applock.security.harness.FaultPlan
import com.applock.security.harness.GatedCaller
import com.applock.security.harness.Phase
import com.applock.security.harness.ReadScript
import com.applock.security.harness.StorageOp
import com.applock.security.harness.VirtualClocks
import com.applock.security.harness.WriteScript
import com.applock.security.harness.stableThreadName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * P2 baseline, cross-cutting cases of test plan §9 that the baseline can run: X03 to X06, X08, X13, and X14. The
 * other X cases need a candidate, a caller, or a device.
 */
class CrossCuttingTest : BaselineCase() {

    private val managers = mutableListOf<LockoutManager>()

    @After
    fun stopManagers() {
        managers.forEach { it.shutdown() }
    }

    // ---- X03 fallback expiry and late completions ----------------------------------------------

    @Test
    fun `X03 - a threshold write that stalls past its window and fails arms a new fallback from its completion`() {
        val harness = harness(C4, FaultPlan().write(WriteScript.HoldBeforeCommit(WriteScript.ReturnFalse), 1, 0))
        harness.start()
        assertEquals(LockedOut(T, degraded = true), harness.fail().immediate)
        harness.advance(STALL_MS)
        assertEquals("the window ended during the stall", Available, harness.poll())

        harness.release(StorageOp.WRITE, 0)
        harness.check()
        assertEquals("a new 30 s from the completion", LockedOut(T, degraded = true), harness.manager.currentState())
        harness.record("X03-stall-fail", "enforced_ms_total" to T + T, "gap_ms" to STALL_MS - T)
    }

    @Test
    fun `X03 - a threshold write that stalls past its window and commits does not bring the lockout back`() {
        val harness = harness(C4, FaultPlan().write(WriteScript.HoldBeforeCommit(), generation = 1, index = 0))
        harness.start()
        harness.fail()
        harness.advance(STALL_MS)
        harness.release(StorageOp.WRITE, 0)
        harness.check()
        assertEquals(L5, harness.store.durableState())
        assertEquals(Available, harness.manager.currentState())
        harness.record("X03-stall-commit", "state" to Available)
    }

    // ---- X04 wall-clock changes ----------------------------------------------------------------

    @Test
    fun `X04 - in process a wall change cannot shorten a recorded lockout and a backward change lengthens it`() {
        val harness = harness(C4, clocks = realisticClocks())
        harness.start()
        harness.fail()
        harness.jumpWall(HOUR_MS)
        harness.check()
        assertEquals("the elapsed mirror holds", LockedOut(T, degraded = false), harness.manager.currentState())
        harness.jumpWall(-2 * HOUR_MS)
        harness.check()
        assertEquals("capped at 30 min", LockedOut(MAX, degraded = false), harness.manager.currentState())
        harness.record("X04-in-process", "backward_1h_state" to harness.manager.currentState())
    }

    @Test
    fun `X04 - after a restart a forward wall change ends the lockout and a backward change raises it to the cap`() {
        listOf(HOUR_MS to Available, -HOUR_MS to LockedOut(MAX, degraded = false)).forEach { (jumpMs, expected) ->
            val harness = harness(C4, clocks = realisticClocks())
            harness.start()
            harness.fail()
            harness.jumpWall(jumpMs)
            harness.restart()
            harness.check()
            assertEquals("jump $jumpMs", expected, harness.manager.currentState())
            assertEquals("jump $jumpMs", 5, harness.manager.failureCount())
            harness.record("X04-restart-jump$jumpMs", "state" to expected)
        }
    }

    @Test
    fun `X04 - a wall change does not move a degraded fallback`() {
        val harness = harness(Z, FaultPlan().write(WriteScript.ReturnFalse), realisticClocks())
        harness.start()
        harness.fail()
        harness.jumpWall(HOUR_MS)
        assertEquals(LockedOut(T, degraded = true), harness.manager.currentState())
        harness.jumpWall(-2 * HOUR_MS)
        harness.check()
        assertEquals(LockedOut(T, degraded = true), harness.manager.currentState())
        harness.record("X04-fallback", "state" to harness.manager.currentState())
    }

    // ---- X05 recovery retries with no budget ---------------------------------------------------

    // B1 spec: T9.
    @Test
    fun `X05 - under a persistent read fault polls start no read, and the retries back off to one every 60 s`() {
        val faults = FaultPlan().read(ReadScript.Throw)
        val harness = harness(L5, faults)
        harness.start()
        repeat(POLL_BURST) { harness.poll() }
        assertEquals("a burst at one instant starts no read", 1, harness.store.readCount(1))

        // Steps of 1 s end at every due time, so each retry fires at its due time.
        repeat(STEPS_10_MIN) { harness.advance(STEP_MS) }
        harness.check()
        val delays = harness.retryTrace(1)
            .filter { it.startsWith("RETRY_SCHEDULED") }
            .map { it.substringAfter("delay=").toLong() }
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L) + List(9) { 60_000L }, delays)
        val failedReadSeconds = harness.ledger.storageEvents(1)
            .filter { it.op == StorageOp.READ && it.index > 0 && it.phase == Phase.THREW }
            .map { (it.elapsedMs - E0) / STEP_MS }
        assertEquals(
            "14 retries in 10 min",
            listOf(1L, 3L, 7L, 15L, 31L, 63L, 123L, 183L, 243L, 303L, 363L, 423L, 483L, 543L),
            failedReadSeconds,
        )
        assertEquals(15, harness.store.readCount(1))
        assertEquals(0, harness.manager.failureCount())

        faults.clearReads()
        repeat(3) { harness.advance(STEP_MS) }
        harness.check()
        assertEquals("no exhaustion: the retry at 603 s recovers", 5, harness.manager.failureCount())
        assertEquals("the stored window ended long ago", Available, harness.manager.currentState())
        assertEquals(16, harness.store.readCount(1))
        harness.record(
            "X05",
            "reads_per_poll" to 0,
            "retries_10_min" to failedReadSeconds.size,
            "delays_ms" to delays,
            "budget" to "none",
        )
    }

    // ---- X06 manager shutdown and the recovery read --------------------------------------------

    // B1 spec: T7.
    @Test
    fun `X06 - a recovery retry queued before shutdown skips its read, and no retry follows`() {
        val harness = harness(L5, FaultPlan().read(ReadScript.Throw, generation = 1, index = 0))
        harness.start()
        harness.holdIo()
        harness.advance(FIRST_RETRY_MS)
        harness.shutdown()
        harness.resumeIo()
        assertEquals("the queued retry skips its read after the stop", 1, harness.store.readCount(1))
        assertEquals(0, harness.manager.failureCount())

        repeat(BURST) { assertEquals(Available, harness.manager.currentState()) }
        harness.advance(TEN_MIN_MS)
        assertEquals("no read starts after the stop", 1, harness.store.readCount(1))
        assertEquals(L5, harness.store.durableState())
        assertEquals(
            listOf(
                "RETRY_SCHEDULED seq=1 kind=READ attempt=1 delay=1000",
                "RETRY_FIRED seq=1",
                "RETRY_CANCELLED seq=1 reason=stopped",
                "RETRY_IGNORED seq=1 reason=stale",
            ),
            harness.retryTrace(1),
        )
        harness.record("X06-queued-reseed", "reads_after_stop" to 0)
    }

    // B1 spec: T13.
    @Test
    fun `X06 - a recovery read in flight at shutdown finishes unpublished, and no retry follows`() {
        val faults = FaultPlan()
            .read(ReadScript.Throw, generation = 1, index = 0)
            .read(ReadScript.HoldThenRead(), generation = 1, index = 1)
        val harness = harness(L5, faults)
        harness.start()
        harness.advance(FIRST_RETRY_MS)
        harness.awaitHeld(StorageOp.READ, 1)
        harness.shutdown()
        harness.release(StorageOp.READ, 1)
        assertEquals(2, harness.store.readCount(1))
        assertEquals(0, harness.manager.failureCount())
        assertEquals(Available, harness.manager.currentState())

        harness.advance(TEN_MIN_MS)
        assertEquals("no read starts after the stop", 2, harness.store.readCount(1))
        assertEquals("RETRY_ENDED seq=1 outcome=abandoned", harness.retryTrace(1).last())
        harness.record("X06-running-reseed", "published" to false, "reads_after_stop" to 0)
    }

    // B1 spec: T16.
    @Test
    fun `X06 - shutdown cancels a pending recovery retry, and no read follows`() {
        val harness = harness(L5, FaultPlan().read(ReadScript.Throw, generation = 1, index = 0))
        harness.start()
        harness.shutdown()
        harness.advance(TEN_MIN_MS)
        assertEquals("no read starts after the stop", 1, harness.store.readCount(1))
        assertEquals(0, harness.manager.failureCount())
        assertEquals(Available, harness.manager.currentState())
        assertEquals(L5, harness.store.durableState())
        assertEquals(
            listOf("RETRY_SCHEDULED seq=1 kind=READ attempt=1 delay=1000", "RETRY_CANCELLED seq=1 reason=stopped"),
            harness.retryTrace(1),
        )
        harness.record("X06-pending-retry", "reads_after_stop" to 0)
    }

    @Test
    fun `X06 - after shutdown a submission changes nothing and its callback never runs`() {
        val harness = harness(C4)
        harness.start()
        harness.shutdown()
        val callbacks = AtomicInteger()
        val failure = harness.manager.submitFailure { callbacks.incrementAndGet() }
        val reset = harness.manager.submitSuccess { callbacks.incrementAndGet() }
        assertEquals(FailureOutcome(Available, 4), runBlocking { failure.resolved.await() })
        assertEquals("a synthetic Available, not a grant", Available, reset.immediate)
        assertEquals(true, runBlocking { reset.resolved.await() })
        harness.awaitQuiescent()
        assertEquals(0, harness.store.writeCount(1))
        assertEquals(4, harness.manager.failureCount())
        assertEquals(0, callbacks.get())
        harness.record("X06-after-stop", "writes" to 0, "callbacks" to 0)
    }

    // ---- X08 cancellation ----------------------------------------------------------------------

    @Test
    fun `X08 - cancelling a waiter does not cancel the write`() {
        val harness = harness(Z, FaultPlan().write(WriteScript.HoldBeforeCommit(), generation = 1, index = 0))
        harness.start()
        val failure = harness.fail()
        runBlocking {
            // An undispatched start runs the waiter up to its suspension in await() before launch returns.
            val awaiting = AtomicBoolean()
            val waiter = launch(start = CoroutineStart.UNDISPATCHED) {
                awaiting.set(true)
                failure.resolved.await()
            }
            assertTrue("the waiter reached await()", awaiting.get())
            assertTrue("the waiter is suspended in await()", waiter.isActive && !failure.resolved.isCompleted)
            waiter.cancelAndJoin()
            assertTrue(waiter.isCancelled)
        }

        harness.release(StorageOp.WRITE, 0)
        harness.check()
        assertEquals(LockoutSnapshot(1, 0L), harness.store.durableState())
        assertEquals(FailureOutcome(Available, 1), runBlocking { failure.resolved.await() })
        harness.record("X08-waiter", "durable" to harness.store.durableState())
    }

    @Test
    fun `X08 - cancelling the resolved Deferred before its write starts drops the admitted write`() {
        val harness = harness(Z, FaultPlan().write(WriteScript.HoldBeforeCommit(), generation = 1, index = 0))
        harness.start()
        harness.fail()
        val callbacks = AtomicInteger()
        val secondFailure = harness.fail { callbacks.incrementAndGet() }
        secondFailure.resolved.cancel()

        harness.release(StorageOp.WRITE, 0)
        assertEquals("the second write never ran", 1, harness.store.writeCount(1))
        assertEquals(LockoutSnapshot(1, 0L), harness.store.durableState())
        assertEquals("memory keeps both failures", 2, harness.manager.failureCount())
        assertTrue(secondFailure.resolved.isCancelled)
        assertEquals(0, callbacks.get())

        harness.restart()
        harness.check()
        assertEquals("the restart loses the second failure", 1, harness.manager.failureCount())
        harness.record("X08-cancel-before-start", "lost_failures" to 1)
    }

    @Test
    fun `X08 - cancelling the resolved Deferred while its write runs still publishes and calls back`() {
        val harness = harness(Z, FaultPlan().write(WriteScript.HoldBeforeCommit(WriteScript.ReturnFalse), 1, 0))
        harness.start()
        val callbacks = AtomicInteger()
        val failure = harness.fail { callbacks.incrementAndGet() }
        failure.resolved.cancel()

        harness.release(StorageOp.WRITE, 0)
        assertEquals("the fallback was published", LockedOut(T, degraded = true), harness.manager.currentState())
        assertEquals(1, callbacks.get())
        assertTrue(failure.resolved.isCancelled)
        assertThrows(CancellationException::class.java) { runBlocking { failure.resolved.await() } }
        harness.record("X08-cancel-while-running", "callbacks" to 1, "await" to "CancellationException")
    }

    @Test
    fun `X08 - a failure write that throws a cancellation arms no fallback and skips its callback`() {
        val harness = harness(Z, FaultPlan().write(WriteScript.ThrowCancellation, generation = 1, index = 0))
        harness.start()
        val callbacks = AtomicInteger()
        val failure = harness.fail { callbacks.incrementAndGet() }
        assertEquals("no fallback for the failed write", Available, harness.manager.currentState())
        assertEquals(1, harness.manager.failureCount())
        assertEquals(0, callbacks.get())
        assertTrue(failure.resolved.isCancelled)
        assertEquals(Z, harness.store.durableState())

        // A direct submission: the model armed the fallback that the baseline skips, so harness admissions would fail.
        harness.manager.submitFailure()
        harness.awaitQuiescent()
        assertEquals("the writer still runs", LockoutSnapshot(2, 0L), harness.store.durableState())
        harness.record("X08-write-cancellation", "fallback" to "none", "callbacks" to 0)
    }

    @Test
    fun `X08 - a clear that throws a cancellation skips its callback, so the failed clear is not reported`() {
        val harness = harness(L5, FaultPlan().write(WriteScript.ThrowCancellation, generation = 1, index = 0))
        harness.start()
        val reported = AtomicReference<Boolean>()
        val reset = harness.succeed { committed -> reported.set(committed) }
        assertEquals(Available, harness.manager.currentState())
        assertEquals("no report", null, reported.get())
        assertTrue(reset.resolved.isCancelled)

        harness.restart()
        harness.check()
        assertEquals(LockedOut(T, degraded = false), harness.manager.currentState())
        harness.record("X08-clear-cancellation", "reported" to null)
    }

    // ---- X13 completion callbacks under the manager lock ---------------------------------------

    @Test
    fun `X13 - a blocking completion callback holds the manager lock, so admission waits but a state read does not`() {
        val manager = plainManager()
        val callback = BlockingCallback()
        manager.submitFailure(callback)
        try {
            assertTrue(callback.entered.await(WAIT_S, TimeUnit.SECONDS))
            assertEquals("the callback runs on the writer thread", "lockout-io", callback.thread.get())

            val admitted = AtomicBoolean()
            val admitter = thread {
                manager.submitFailure()
                admitted.set(true)
            }
            awaitBlocked(admitter)
            assertFalse("admission waits for the callback", admitted.get())
            val stateRead = CompletableFuture.supplyAsync { manager.currentState() }
            assertEquals("a state read does not wait", Available, stateRead.get(WAIT_S, TimeUnit.SECONDS))
            assertFalse("the callback still blocks after the read", callback.done.get())

            callback.release()
            admitter.join(TimeUnit.SECONDS.toMillis(WAIT_S))
            assertTrue(admitted.get())
            assertEquals(2, manager.failureCount())
        } finally {
            callback.release()
        }
    }

    @Test
    fun `X13 - shutdown waits for a blocking completion callback`() {
        val manager = plainManager()
        val callback = BlockingCallback()
        manager.submitFailure(callback)
        try {
            assertTrue(callback.entered.await(WAIT_S, TimeUnit.SECONDS))
            val stopped = AtomicBoolean()
            val stopper = thread {
                manager.shutdown()
                stopped.set(true)
            }
            awaitBlocked(stopper)
            assertFalse("the callback still blocks", callback.done.get())

            callback.release()
            stopper.join(TimeUnit.SECONDS.toMillis(WAIT_S))
            assertTrue("shutdown returned", stopped.get())
        } finally {
            callback.release()
        }
    }

    @Test
    fun `X13 - a throwing completion callback turns the resolved outcome into its exception`() {
        val manager = plainManager()
        val failure = manager.submitFailure { error("observer fault") }
        val thrown = assertThrows(IllegalStateException::class.java) { runBlocking { failure.resolved.await() } }
        assertEquals("observer fault", thrown.message)
        assertEquals("the admission stands", 1, manager.failureCount())

        runBlocking { manager.submitFailure().resolved.await() }
        assertEquals("the writer still runs", 2, manager.failureCount())
    }

    // ---- X14 stored values outside the normal range ---------------------------------------------

    @Test
    fun `X14 - a negative stored count gives extra guesses before the first lockout`() {
        val harness = harness(LockoutSnapshot(-3, 0L))
        harness.start()
        val caller = GatedCaller(harness)
        assertEquals("eight guesses instead of five", 8, caller.wrongPinsUntilBlocked())
        harness.check()
        harness.record("X14-negative-count", "entries_before_lock" to 8)
    }

    @Test
    fun `X14 - a stored count of Int MAX_VALUE wraps on the next failure and never locks`() {
        val harness = harness(LockoutSnapshot(Int.MAX_VALUE, 0L))
        harness.start()
        val caller = GatedCaller(harness)
        assertEquals("no lockout within the limit", OVERFLOW_TRIES, caller.wrongPinsUntilBlocked(OVERFLOW_TRIES))
        harness.check()
        assertEquals(Int.MIN_VALUE + OVERFLOW_TRIES - 1, harness.manager.failureCount())

        harness.restart()
        harness.check()
        assertEquals("the wrapped count is durable", Int.MIN_VALUE + OVERFLOW_TRIES - 1, harness.manager.failureCount())
        assertEquals(Available, harness.manager.currentState())
        harness.record("X14-count-overflow", "entries" to OVERFLOW_TRIES, "count" to harness.manager.failureCount())
    }

    @Test
    fun `X14 - a negative stored deadline reads as no lockout`() {
        val harness = harness(LockoutSnapshot(5, -1L))
        harness.start()
        harness.check()
        assertEquals(Available, harness.manager.currentState())
        assertEquals(1, GatedCaller(harness).wrongPinsUntilBlocked())
        assertEquals(LockedOut(60_000L, degraded = false), harness.manager.currentState())
        harness.record("X14-negative-deadline", "state" to Available)
    }

    @Test
    fun `X14 - a stored deadline past the cap shows the cap until less than the cap remains`() {
        val maxDeadlineHarness = harness(LockoutSnapshot(5, Long.MAX_VALUE))
        maxDeadlineHarness.start()
        maxDeadlineHarness.advance(HOUR_MS)
        assertEquals(LockedOut(MAX, degraded = false), maxDeadlineHarness.poll())
        maxDeadlineHarness.restart()
        maxDeadlineHarness.check()
        val afterRestart = maxDeadlineHarness.manager.currentState()
        assertEquals("still the cap after a restart", LockedOut(MAX, degraded = false), afterRestart)
        maxDeadlineHarness.record("X14-deadline-max", "denial" to "unbounded")

        val twoHourHarness = harness(LockoutSnapshot(5, W0 + 2 * HOUR_MS))
        twoHourHarness.start()
        twoHourHarness.advance(HOUR_MS + HOUR_MS / 2)
        assertEquals("still the cap after 90 min", LockedOut(MAX, degraded = false), twoHourHarness.poll())
        twoHourHarness.advance(TEN_MIN_MS)
        assertEquals("20 min left after 100 min", LockedOut(2 * TEN_MIN_MS, degraded = false), twoHourHarness.poll())
        twoHourHarness.advance(2 * TEN_MIN_MS)
        assertEquals(Available, twoHourHarness.poll())
        twoHourHarness.check()
        twoHourHarness.record("X14-deadline-2h", "denial_ms" to 2 * HOUR_MS)
    }

    // ---- Helpers -------------------------------------------------------------------------------

    // A wall clock at a realistic epoch time, so a one-hour backward change stays above zero.
    private fun realisticClocks() = VirtualClocks(wallMs = REALISTIC_WALL_MS)

    // A manager on its default single-thread writer, over storage that always succeeds.
    private fun plainManager(): LockoutManager =
        LockoutManager(HealthyStorage(), clock = { W0 }, elapsedRealtime = { E0 }).also { managers += it }

    private fun awaitBlocked(thread: Thread) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_S)
        while (thread.state != Thread.State.BLOCKED) {
            check(System.nanoTime() < deadline) { "${thread.name} never blocked; state ${thread.state}" }
            Thread.sleep(1L)
        }
    }

    /**
     * A completion callback that blocks the writer thread, with the manager lock held, until the test releases it. Its
     * own time limit is longer than every wait of the test. So when a wait of the test ends while [done] is false, the
     * callback still held the lock at that time.
     */
    private class BlockingCallback : (FailureOutcome) -> Unit {
        val entered = CountDownLatch(1)
        val done = AtomicBoolean()
        val thread = AtomicReference<String>()
        private val release = CountDownLatch(1)

        override fun invoke(outcome: FailureOutcome) {
            thread.set(stableThreadName())
            entered.countDown()
            release.await(HOLD_LIMIT_S, TimeUnit.SECONDS)
            done.set(true)
        }

        fun release() = release.countDown()
    }

    private class HealthyStorage : LockoutStorage {
        @Volatile
        private var stored = LockoutSnapshot(0, 0L)

        override fun read(): LockoutSnapshot = stored

        override fun write(snapshot: LockoutSnapshot): Boolean {
            stored = snapshot
            return true
        }
    }

    private companion object {
        const val POLL_BURST = 1_000
        const val FIRST_RETRY_MS = 1_000L
        const val STEP_MS = 1_000L
        const val STEPS_10_MIN = 600
        const val OVERFLOW_TRIES = 100
        const val TEN_MIN_MS = 600_000L
        const val WAIT_S = 5L
        const val HOLD_LIMIT_S = 60L
        const val REALISTIC_WALL_MS = 1_790_000_000_000L
    }
}
