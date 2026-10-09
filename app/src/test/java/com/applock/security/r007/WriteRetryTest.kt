package com.applock.security.r007

import com.applock.security.LockoutManager
import com.applock.security.LockoutManager.FailureOutcome
import com.applock.security.LockoutSnapshot
import com.applock.security.LockoutState.Available
import com.applock.security.LockoutState.LockedOut
import com.applock.security.LockoutStorage
import com.applock.security.RecoveryEvent
import com.applock.security.RecoveryEvent.Cancelled
import com.applock.security.RecoveryEvent.Ended
import com.applock.security.RecoveryEvent.Fired
import com.applock.security.RecoveryEvent.Ignored
import com.applock.security.RecoveryEvent.Scheduled
import com.applock.security.RecoveryEvent.Started
import com.applock.security.RecoveryKind
import com.applock.security.RecoverySetup
import com.applock.security.harness.FaultPlan
import com.applock.security.harness.LockoutHarness
import com.applock.security.harness.Phase
import com.applock.security.harness.ReadScript
import com.applock.security.harness.StorageOp
import com.applock.security.harness.VirtualClocks
import com.applock.security.harness.VirtualRecoveryTimer
import com.applock.security.harness.WriteScript
import com.applock.security.harness.stableThreadName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * P3 candidate B2, the retry of a failed lockout write (`2026-10-07_R007_F2_HARDENING_P3_B2_SPEC.md`). The harness
 * tests use the production delays on the virtual retry timer. The tests that cancel the manager job or block the
 * writer on a real thread use a manager without the harness.
 */
class WriteRetryTest : BaselineCase() {

    private val managers = mutableListOf<LockoutManager>()
    private val executors = mutableListOf<ExecutorService>()

    @After
    fun stopDirectRuns() {
        managers.forEach { it.shutdown() }
        executors.forEach { it.shutdownNow() }
    }

    // ---- R4.2 a failed clear and its retry -------------------------------------------------------

    // B2 spec: T19.
    @Test
    fun `R4_2 - a death before the retry commit keeps the stale pair and a death after it keeps Z`() {
        val earlyDeathHarness = harness(L5, FaultPlan().write(WriteScript.ReturnFalse, generation = 1, index = 0))
        earlyDeathHarness.start()
        earlyDeathHarness.succeed()
        earlyDeathHarness.advance(BEFORE_FIRST_RETRY_MS)
        earlyDeathHarness.restart()
        earlyDeathHarness.check()
        assertEquals("death before the first retry", L5, earlyDeathHarness.store.durableState())
        val earlyLock = LockedOut(T - BEFORE_FIRST_RETRY_MS, degraded = false)
        assertEquals("the stale lockout is back", earlyLock, earlyDeathHarness.manager.currentState())
        earlyDeathHarness.record("R4.2-death-before-retry", "durable" to L5, "retry_writes" to 0)

        val heldRetryHarness = harness(L5, firstWriteFailsThen(WriteScript.HoldBeforeCommit()))
        heldRetryHarness.start()
        heldRetryHarness.succeed()
        heldRetryHarness.advance(FIRST_DELAY_MS)
        heldRetryHarness.awaitHeld(StorageOp.WRITE, 1)
        heldRetryHarness.check()
        heldRetryHarness.restart()
        heldRetryHarness.check()
        assertEquals("death before the retry commit", L5, heldRetryHarness.store.durableState())
        val heldLock = LockedOut(T - FIRST_DELAY_MS, degraded = false)
        assertEquals("the stale lockout is back", heldLock, heldRetryHarness.manager.currentState())
        heldRetryHarness.record("R4.2-death-in-retry-write", "durable" to L5)

        val committedRetryHarness = harness(L5, firstWriteFailsThen(WriteScript.CommitThenHold))
        committedRetryHarness.start()
        committedRetryHarness.succeed()
        committedRetryHarness.advance(FIRST_DELAY_MS)
        committedRetryHarness.awaitHeld(StorageOp.WRITE, 1)
        committedRetryHarness.check()
        assertEquals("the retry committed before the death", Z, committedRetryHarness.store.durableState())
        (1..2).forEach { restartNumber ->
            committedRetryHarness.restart()
            committedRetryHarness.check()
            val label = "restart $restartNumber after the retry commit"
            assertEquals(label, Z, committedRetryHarness.store.durableState())
            assertEquals(label, Available, committedRetryHarness.manager.currentState())
            assertEquals(label, 0, committedRetryHarness.manager.failureCount())
            val newProcessWrites = committedRetryHarness.store.writeCount(committedRetryHarness.generation)
            assertEquals("$label: the new process writes nothing", 0, newProcessWrites)
        }
        committedRetryHarness.record("R4.2-death-after-retry-commit", "durable" to Z, "new_process_writes" to 0)
    }

    // B2 spec: T12, T10.
    @Test
    fun `R4_2 - a clear that fails three times is retried after 1, 2, and 4 s and stops at the first commit`() {
        val faults = FaultPlan()
        (0..2).forEach { index -> faults.write(WriteScript.ReturnFalse, generation = 1, index = index) }
        val harness = harness(L5, faults)
        harness.start()
        harness.succeed()
        listOf(1_000L, 2_000L).forEach { delayMs ->
            harness.advance(delayMs)
            harness.check()
            assertEquals("a failed retry keeps the stale pair", L5, harness.store.durableState())
        }

        harness.advance(4_000L)
        harness.check()
        assertEquals("the third retry commits at 7 s", Z, harness.store.durableState())
        assertEquals("the clear and three retry writes", 4, harness.store.writeCount(1))
        assertEquals("memory stays cleared", Available, harness.manager.currentState())
        assertEquals(0, harness.manager.failureCount())
        val expected = attemptEvents(1, delayMs = 1_000L, outcome = Ended.Outcome.RETRY) +
            attemptEvents(2, delayMs = 2_000L, outcome = Ended.Outcome.RETRY) +
            attemptEvents(3, delayMs = 4_000L, outcome = Ended.Outcome.SUCCESS)
        assertEquals(expected, harness.retryEvents())
        harness.record("R4.2-three-failed-attempts", "delays_ms" to listOf(1_000L, 2_000L, 4_000L), "writes" to 4)
    }

    // ---- R4.3 a reset retry and newer failures ---------------------------------------------------

    // B2 spec: T16.
    @Test
    fun `R4_3 - a waiting reset retry is cancelled by a new failure and never writes`() {
        val harness = harness(L5, FaultPlan().write(WriteScript.ReturnFalse, generation = 1, index = 0))
        harness.start()
        harness.succeed()
        harness.fail()
        harness.advance(TWO_MINUTES_MS)
        harness.check()
        assertEquals(LockoutSnapshot(1, 0L), harness.store.durableState())
        assertEquals("the clear and the failure write", 2, harness.store.writeCount(1))
        assertEquals(listOf(scheduled(1), cancelled(1)), harness.retryEvents())
        harness.record("R4.3-waiting-retry", "durable" to harness.store.durableState(), "retry_writes" to 0)
    }

    // B2 spec: T7, T16, T9.
    @Test
    fun `R4_3 - a reset retry that fired before a new failure is ignored on the writer`() {
        val harness = harness(L5, FaultPlan().write(WriteScript.ReturnFalse, generation = 1, index = 0))
        harness.start()
        harness.succeed()
        harness.holdIo()
        harness.advance(FIRST_DELAY_MS)
        harness.fail()
        harness.resumeIo()
        harness.check()
        assertEquals(LockoutSnapshot(1, 0L), harness.store.durableState())
        assertEquals("the clear and the failure write", 2, harness.store.writeCount(1))
        val expected = listOf(scheduled(1), Fired(1), cancelled(1), Ignored(1, Ignored.Reason.STALE))
        assertEquals(expected, harness.retryEvents())
        harness.record("R4.3-queued-retry", "durable" to harness.store.durableState(), "retry_writes" to 0)
    }

    // B2 spec: T17, T11.
    @Test
    fun `R4_3 - a reset retry in flight when a new failure is admitted commits first, and the failure write follows`() {
        val harness = harness(L5, firstWriteFailsThen(WriteScript.HoldBeforeCommit()))
        harness.start()
        harness.succeed()
        harness.advance(FIRST_DELAY_MS)
        harness.awaitHeld(StorageOp.WRITE, 1)
        harness.fail()
        harness.check()
        assertEquals("the failure write waits behind the retry", 2, harness.store.writeCount(1))

        harness.release(StorageOp.WRITE, 1)
        harness.check()
        val diskOrder = harness.durableCommits()
        assertEquals("the retry payload, then the failure", listOf(Z, LockoutSnapshot(1, 0L)), diskOrder)
        assertEquals(1, harness.manager.failureCount())
        assertEquals(Available, harness.manager.currentState())
        val expected = listOf(scheduled(1), Fired(1), Started(1), Ended(1, Ended.Outcome.ABANDONED))
        assertEquals("the retry publishes nothing", expected, harness.retryEvents())
        harness.record("R4.3-retry-in-flight", "disk_order" to diskOrder)

        val faults = firstWriteFailsThen(WriteScript.HoldBeforeCommit())
            .write(WriteScript.HoldBeforeCommit(), generation = 1, index = 2)
        val killHarness = harness(L5, faults)
        killHarness.start()
        killHarness.succeed()
        killHarness.advance(FIRST_DELAY_MS)
        killHarness.awaitHeld(StorageOp.WRITE, 1)
        killHarness.fail()
        killHarness.release(StorageOp.WRITE, 1)
        killHarness.awaitHeld(StorageOp.WRITE, 2)
        killHarness.check()
        killHarness.restart()
        killHarness.check()
        assertEquals("the committed prefix", Z, killHarness.store.durableState())
        assertEquals(0, killHarness.manager.failureCount())
        killHarness.record("R4.3-death-behind-retry", "durable" to Z)
    }

    // B2 spec: T2, T16.
    @Test
    fun `R4_3 - a second success and a later failure each supersede the reset retry before them`() {
        val faults = FaultPlan()
            .write(WriteScript.ReturnFalse, generation = 1, index = 0)
            .write(WriteScript.ReturnFalse, generation = 1, index = 1)
        val harness = harness(L5, faults)
        harness.start()
        harness.succeed()
        harness.succeed()
        harness.fail()
        harness.advance(TWO_MINUTES_MS)
        harness.check()
        assertEquals(LockoutSnapshot(1, 0L), harness.store.durableState())
        assertEquals("two clears and the failure write", 3, harness.store.writeCount(1))
        assertEquals(listOf(scheduled(1), cancelled(1), scheduled(2), cancelled(2)), harness.retryEvents())
        harness.record("R4.3-second-success", "durable" to harness.store.durableState(), "chains" to 2)
    }

    // ---- R4.4 a retry write that never returns ---------------------------------------------------

    // B2 spec: T15, T17.
    @Test
    fun `R4_4 - a retry write that never returns blocks later writes and no second writer starts`() {
        val harness = harness(L5, firstWriteFailsThen(WriteScript.HoldBeforeCommit()))
        harness.start()
        harness.succeed()
        harness.advance(FIRST_DELAY_MS)
        harness.awaitHeld(StorageOp.WRITE, 1)
        harness.fail()
        harness.fail()
        harness.advance(TWO_MINUTES_MS)
        harness.check()
        assertEquals("the clear and the held retry write", 2, harness.store.writeCount(1))
        assertEquals(1, harness.store.maxConcurrentWrites())
        assertEquals("no new firing", listOf(scheduled(1), Fired(1), Started(1)), harness.retryEvents())
        assertEquals(2, harness.manager.failureCount())

        harness.restart()
        harness.check()
        assertEquals(L5, harness.store.durableState())
        harness.record("R4.4-retry-never-returns", "writes_started" to 2, "max_concurrent_writes" to 1)
    }

    // ---- R2.4 failed retries and the fallback deadline -------------------------------------------

    // B2 spec: T12.
    @Test
    fun `R2_4 - failed retries keep the count and end the fallback at its original deadline`() {
        val harness = harness(Z, FaultPlan().write(WriteScript.ReturnFalse))
        harness.start()
        val callbacks = AtomicInteger()
        val failure = harness.fail { callbacks.incrementAndGet() }
        listOf(1_000L, 2_000L, 4_000L, 8_000L).forEach { delayMs ->
            harness.advance(delayMs) // the retries at 1, 3, 7, and 15 s
            harness.check()
        }
        harness.advance(T - 1 - FOURTH_RETRY_MS)
        harness.check()
        val lastMillisecond = LockedOut(1L, degraded = true)
        assertEquals("1 ms before the original deadline", lastMillisecond, harness.manager.currentState())
        harness.advance(1L)
        harness.check()
        assertEquals("no extension", Available, harness.manager.currentState())
        harness.advance(FIFTH_RETRY_MS - T) // the retry at 31 s
        harness.check()
        assertEquals(1, harness.manager.failureCount())
        assertEquals(Z, harness.store.durableState())
        assertEquals(FailureOutcome(LockedOut(T, degraded = true), 1), runBlocking { failure.resolved.await() })
        assertEquals("one callback, and none for a retry", 1, callbacks.get())
        assertEquals("the failed write and five retry writes", 6, harness.store.writeCount(1))
        harness.record("R2.4-failed-retries", "fallback_end_ms" to T, "writes" to 6)
    }

    // ---- X01 a failure retry and newer admissions ------------------------------------------------

    // B2 spec: T16, T9, T3.
    @Test
    fun `X01 - a failure retry cannot persist behind a newer reset or over a newer failure`() {
        val waitingHarness = harness(Z, FaultPlan().write(WriteScript.ReturnFalse, generation = 1, index = 0))
        waitingHarness.start()
        waitingHarness.fail()
        waitingHarness.fail()
        waitingHarness.succeed()
        waitingHarness.advance(TWO_MINUTES_MS)
        assertNoRetryWrite(waitingHarness, "X01-waiting-retry", listOf(scheduled(1), cancelled(1)))

        val queuedHarness = harness(Z, FaultPlan().write(WriteScript.ReturnFalse, generation = 1, index = 0))
        queuedHarness.start()
        queuedHarness.fail()
        queuedHarness.holdIo()
        queuedHarness.advance(FIRST_DELAY_MS)
        queuedHarness.fail()
        queuedHarness.succeed()
        queuedHarness.resumeIo()
        val queuedEvents = listOf(scheduled(1), Fired(1), cancelled(1), Ignored(1, Ignored.Reason.STALE))
        assertNoRetryWrite(queuedHarness, "X01-queued-retry", queuedEvents)

        // F2 fails while the reset waits behind it, so F2 is not the latest admission and starts no chain.
        val faults = FaultPlan()
            .write(WriteScript.ReturnFalse, generation = 1, index = 0)
            .write(WriteScript.HoldBeforeCommit(WriteScript.ReturnFalse), generation = 1, index = 1)
        val staleFailureHarness = harness(Z, faults)
        staleFailureHarness.start()
        staleFailureHarness.fail()
        staleFailureHarness.fail()
        staleFailureHarness.succeed()
        staleFailureHarness.release(StorageOp.WRITE, 1)
        assertNoRetryWrite(staleFailureHarness, "X01-stale-failure", listOf(scheduled(1), cancelled(1)))
    }

    // Checks one X01 run: Z is stored, no retry wrote, and the run has exactly [events] as its retry events.
    private fun assertNoRetryWrite(harness: LockoutHarness, case: String, events: List<RecoveryEvent>) {
        harness.check()
        assertEquals(case, Z, harness.store.durableState())
        assertEquals(case, events, harness.retryEvents())
        assertTrue("$case: no retry write", harness.retryEvents().none { it is Started })
        harness.record(case, "durable" to Z, "retry_writes" to 0)
    }

    // ---- X03 a threshold write that failed after its window --------------------------------------

    // B2 spec: T2, T10.
    @Test
    fun `X03 - a retry of a threshold write that failed late persists the expired deadline and revives nothing`() {
        val harness = harness(C4, FaultPlan().write(WriteScript.HoldBeforeCommit(WriteScript.ReturnFalse), 1, 0))
        harness.start()
        harness.fail()
        harness.advance(STALL_MS)
        harness.release(StorageOp.WRITE, 0)
        harness.check()
        assertEquals("a new 30 s from the completion", LockedOut(T, degraded = true), harness.manager.currentState())

        harness.advance(FIRST_DELAY_MS)
        harness.check()
        assertEquals("the admission-time deadline, already passed", L5, harness.store.durableState())
        val fallbackLock = LockedOut(T - FIRST_DELAY_MS, degraded = true)
        assertEquals("the fallback still governs, with no new window", fallbackLock, harness.manager.currentState())
        val expected = listOf(scheduled(1), Fired(1), Started(1), Ended(1, Ended.Outcome.SUCCESS))
        assertEquals(expected, harness.retryEvents())

        harness.restart()
        harness.check()
        assertEquals(5, harness.manager.failureCount())
        assertEquals("the expired deadline locks nothing", Available, harness.manager.currentState())
        harness.record("X03-retry-after-stall", "durable" to L5, "state_after_restart" to Available)
    }

    // ---- X05 one chain under a persistent fault --------------------------------------------------

    // B2 spec: T12, T20.
    @Test
    fun `X05 - under a persistent write fault one chain runs with delays up to 60 s, and polls add no retry`() {
        val harness = harness(Z, FaultPlan().write(WriteScript.ReturnFalse))
        harness.start()
        harness.fail()
        val delays = listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L, 60_000L)
        delays.forEach { delayMs ->
            repeat(POLL_BURST) { harness.poll() }
            assertEquals("one retry waits", 1, harness.pendingRetryCount())
            harness.advance(delayMs)
            harness.check()
        }
        assertEquals("one retry waits after attempt 9", 1, harness.pendingRetryCount())
        val plannedDelays = harness.retryEvents().filterIsInstance<Scheduled>().map { it.delayMs }
        assertEquals("the delays of attempts 1 to 10", delays + 60_000L, plannedDelays)
        assertEquals("the construction read only", 1, harness.store.readCount(1))
        assertEquals("the failed write and nine retry writes", 10, harness.store.writeCount(1))
        harness.record("X05-one-chain", "delays_ms" to delays, "polls" to POLL_BURST * delays.size, "writes" to 10)
    }

    // ---- X06 shutdown ----------------------------------------------------------------------------

    // B2 spec: T18.
    @Test
    fun `X06 - shutdown cancels a waiting retry, ignores a queued one, and lets a running one finish unpublished`() {
        val waitingHarness = harness(C4, FaultPlan().write(WriteScript.ReturnFalse, generation = 1, index = 0))
        waitingHarness.start()
        waitingHarness.fail()
        waitingHarness.shutdown()
        waitingHarness.advance(TWO_MINUTES_MS)
        waitingHarness.check()
        assertEquals(listOf(scheduled(1), stopped(1)), waitingHarness.retryEvents())
        assertEquals("no retry write", 1, waitingHarness.store.writeCount(1))
        waitingHarness.record("X06-waiting-retry", "retry_writes" to 0)

        val queuedHarness = harness(C4, FaultPlan().write(WriteScript.ReturnFalse, generation = 1, index = 0))
        queuedHarness.start()
        queuedHarness.fail()
        queuedHarness.holdIo()
        queuedHarness.advance(FIRST_DELAY_MS)
        queuedHarness.shutdown()
        queuedHarness.resumeIo()
        queuedHarness.check()
        val queuedEvents = listOf(scheduled(1), Fired(1), stopped(1), Ignored(1, Ignored.Reason.STALE))
        assertEquals(queuedEvents, queuedHarness.retryEvents())
        assertEquals("no retry write", 1, queuedHarness.store.writeCount(1))
        queuedHarness.record("X06-queued-retry", "retry_writes" to 0)

        listOf(WriteScript.Normal, WriteScript.ReturnFalse).forEach { then ->
            val runningHarness = harness(C4, firstWriteFailsThen(WriteScript.HoldBeforeCommit(then)))
            runningHarness.start()
            val callbacks = AtomicInteger()
            runningHarness.fail { callbacks.incrementAndGet() }
            runningHarness.advance(FIRST_DELAY_MS)
            runningHarness.awaitHeld(StorageOp.WRITE, 1)
            runningHarness.shutdown()
            runningHarness.release(StorageOp.WRITE, 1)
            runningHarness.check()
            val stored = if (then == WriteScript.Normal) L5 else C4
            assertEquals("$then", stored, runningHarness.store.durableState())
            val unpublished = LockedOut(T - FIRST_DELAY_MS, degraded = true)
            assertEquals("$then: no promotion", unpublished, runningHarness.manager.currentState())
            assertEquals("$then: one callback", 1, callbacks.get())
            val runningEvents = listOf(scheduled(1), Fired(1), Started(1), Ended(1, Ended.Outcome.ABANDONED))
            assertEquals("$then", runningEvents, runningHarness.retryEvents())

            runningHarness.advance(TWO_MINUTES_MS)
            runningHarness.check()
            assertEquals("$then: no rescheduling", runningEvents, runningHarness.retryEvents())
            assertEquals("$then", 2, runningHarness.store.writeCount(1))
            runningHarness.record("X06-running-retry-$then", "durable" to stored, "published" to false)
        }
    }

    // ---- X08 cancellation ------------------------------------------------------------------------

    // B2 spec: T12, T10.
    @Test
    fun `X08 - a retry write that throws a cancellation while the job is active is retried after the next delay`() {
        val harness = harness(L5, firstWriteFailsThen(WriteScript.ThrowCancellation))
        harness.start()
        harness.succeed()
        harness.advance(FIRST_DELAY_MS)
        harness.check()
        assertEquals("the first retry failed", L5, harness.store.durableState())

        harness.advance(2_000L)
        harness.check()
        assertEquals("the second retry commits at 3 s", Z, harness.store.durableState())
        assertEquals("memory stays cleared", Available, harness.manager.currentState())
        assertEquals(0, harness.manager.failureCount())
        val expected = attemptEvents(1, delayMs = 1_000L, outcome = Ended.Outcome.RETRY) +
            attemptEvents(2, delayMs = 2_000L, outcome = Ended.Outcome.SUCCESS)
        assertEquals(expected, harness.retryEvents())
        harness.record("X08-retry-cancellation", "attempts" to 2, "durable" to Z)
    }

    // B2 spec: T4, T14.
    @Test
    fun `X08 - a cancelled manager job ends its write without a completion and cancels the caller result`() {
        // The job is cancelled while its write waits in the queue, so the write never starts.
        val queuedRun = DirectRun(Z, writeResults = emptyList(), gatedWrite = null)
        val writerRelease = queuedRun.occupyWriter()
        val queuedCallbacks = AtomicInteger()
        val queued = queuedRun.manager.submitFailure { queuedCallbacks.incrementAndGet() }
        val queuedWaiter = waiterOf(queued.resolved)
        queuedRun.scope.cancel()
        writerRelease.countDown()
        assertCancelledWithoutCompletion("queued write", queuedWaiter, queued.resolved, queuedCallbacks)
        assertEquals("the queued write never starts", 0, queuedRun.storage.writes)
        assertEquals("no fallback without a completion", Available, queuedRun.manager.currentState())
        assertEquals("no chain", emptyList<RecoveryEvent>(), queuedRun.events.toList())

        // The job is cancelled while its write blocks, and the write then fails.
        val blockedRun = DirectRun(Z, writeResults = listOf(false), gatedWrite = 0)
        val blockedCallbacks = AtomicInteger()
        val blocked = blockedRun.manager.submitFailure { blockedCallbacks.incrementAndGet() }
        val blockedWaiter = waiterOf(blocked.resolved)
        assertTrue("the write blocks", blockedRun.storage.gateReached.await(WAIT_S, TimeUnit.SECONDS))
        blockedRun.scope.cancel()
        blockedRun.storage.open()
        assertCancelledWithoutCompletion("blocked write", blockedWaiter, blocked.resolved, blockedCallbacks)
        assertEquals("the blocked write ran", 1, blockedRun.storage.writes)
        assertEquals("no fallback without a completion", Available, blockedRun.manager.currentState())
        assertEquals("no chain", emptyList<RecoveryEvent>(), blockedRun.events.toList())

        // The job is cancelled while the retry write of a threshold failure blocks, and the retry write then commits.
        val retryRun = DirectRun(C4, writeResults = listOf(false), gatedWrite = 1)
        awaitCompletion(retryRun.manager.submitFailure().resolved)
        retryRun.advance(FIRST_DELAY_MS)
        assertTrue("the retry write blocks", retryRun.storage.gateReached.await(WAIT_S, TimeUnit.SECONDS))
        retryRun.scope.cancel()
        retryRun.storage.open()
        awaitEvent(retryRun.events) { it is Ended }
        val expected = listOf(scheduled(1), Fired(1), Started(1), Ended(1, Ended.Outcome.THREW))
        assertEquals("the retry ends without a publication", expected, retryRun.events.toList())
        assertEquals("nothing is scheduled", 0, retryRun.timer.pendingCount())
        assertEquals("the retry write reached storage", L5, retryRun.storage.durable)
        val unpublished = LockedOut(T - FIRST_DELAY_MS, degraded = true)
        assertEquals("the lockout stays degraded", unpublished, retryRun.manager.currentState())
    }

    // ---- X12 accounting --------------------------------------------------------------------------

    // B2 spec: T10.
    @Test
    fun `X12 - a committed retry of a failed threshold write records the lockout with no second outcome or callback`() {
        val harness = harness(C4, FaultPlan().write(WriteScript.ReturnFalse, generation = 1, index = 0))
        harness.start()
        val callbacks = AtomicInteger()
        val fifthFailure = harness.fail { callbacks.incrementAndGet() }
        harness.check()
        val degradedOutcome = FailureOutcome(LockedOut(T, degraded = true), 5)
        assertEquals(degradedOutcome, runBlocking { fifthFailure.resolved.await() })

        harness.advance(FIRST_DELAY_MS)
        harness.check()
        val recordedLock = LockedOut(T - FIRST_DELAY_MS, degraded = false)
        assertEquals("recorded after the retry", recordedLock, harness.manager.currentState())
        assertEquals(L5, harness.store.durableState())
        assertEquals("the outcome stays as delivered", degradedOutcome, runBlocking { fifthFailure.resolved.await() })
        assertEquals("one callback, and none for the retry", 1, callbacks.get())
        assertEquals(5, harness.manager.failureCount())

        harness.advance(4_000L)
        harness.restart()
        harness.check()
        val restartLock = LockedOut(T - 5_000L, degraded = false)
        assertEquals("recorded after a restart at 5 s", restartLock, harness.manager.currentState())
        harness.record(
            "X12-retry-commit",
            "outcome" to degradedOutcome.state,
            "state_after_retry" to recordedLock,
            "callbacks" to callbacks.get(),
        )
    }

    // ---- R3.4 a false acknowledgement ------------------------------------------------------------

    // B2 spec: T2, T10.
    @Test
    fun `R3_4 - a retry after a false acknowledgement writes the same pair again and changes nothing in memory`() {
        val harness = harness(Z, FaultPlan().write(WriteScript.CommitThenReportFalse, generation = 1, index = 0))
        harness.start()
        val failure = harness.fail()
        harness.check()
        val committed = LockoutSnapshot(1, 0L)
        assertEquals(committed, harness.store.durableState())

        harness.advance(FIRST_DELAY_MS)
        harness.check()
        assertEquals("the same pair twice", listOf(committed, committed), harness.durableCommits())
        assertEquals(1, harness.manager.failureCount())
        val fallbackLock = LockedOut(T - FIRST_DELAY_MS, degraded = true)
        assertEquals("the fallback stays", fallbackLock, harness.manager.currentState())
        assertEquals(FailureOutcome(LockedOut(T, degraded = true), 1), runBlocking { failure.resolved.await() })
        harness.record("R3.4-false-ack-retry", "durable" to committed, "state" to fallbackLock)
    }

    // ---- R1.3 a retry over an unreadable stored lockout ------------------------------------------

    // B2 spec: T2, T10.
    @Test
    fun `R1_3 - a retry of the racing failure write erases the unreadable stored lockout that the baseline keeps`() {
        val faults = FaultPlan()
            .read(ReadScript.Throw, generation = 1, index = 0)
            .read(ReadScript.ReadThenHold, generation = 1, index = 1)
            .write(WriteScript.ReturnFalse, generation = 1, index = 0)
        val harness = harness(L5, faults)
        harness.start()
        harness.poll()
        harness.fail()
        harness.release(StorageOp.READ, 1)
        harness.check()
        assertEquals("the failed write keeps the stored lockout", L5, harness.store.durableState())

        harness.advance(FIRST_DELAY_MS)
        harness.check()
        val retryPayload = LockoutSnapshot(1, 0L)
        assertEquals("the retry overwrites the unread lockout", retryPayload, harness.store.durableState())

        harness.restart()
        harness.check()
        assertEquals("the stored lockout is lost", Available, harness.manager.currentState())
        assertEquals(1, harness.manager.failureCount())
        harness.record("R1.3-retry-erases-lockout", "erased" to L5, "durable_after" to retryPayload)
    }

    // ---- I3 no manager lock during retry I/O -----------------------------------------------------

    // B2 spec: T8, T17.
    @Test
    fun `I3 - a blocked retry write holds no manager lock, so admission and state reads go on`() {
        val run = DirectRun(Z, writeResults = listOf(false), gatedWrite = 1)
        awaitCompletion(run.manager.submitFailure().resolved)
        run.advance(FIRST_DELAY_MS)
        try {
            assertTrue("the retry write blocks", run.storage.gateReached.await(WAIT_S, TimeUnit.SECONDS))
            assertEquals("the retry runs on the writer", "lockout-io", run.storage.gatedThread)
            val fallbackLock = LockedOut(T - FIRST_DELAY_MS, degraded = true)
            val admission = CompletableFuture.supplyAsync { run.manager.submitFailure().immediate }
            assertEquals("an admission does not wait", fallbackLock, admission.get(WAIT_S, TimeUnit.SECONDS))
            val stateRead = CompletableFuture.supplyAsync { run.manager.currentState() }
            assertEquals("a state read does not wait", fallbackLock, stateRead.get(WAIT_S, TimeUnit.SECONDS))
            assertEquals(2, run.manager.failureCount())
            assertEquals("the second write waits behind the retry", 2, run.storage.writes)
        } finally {
            run.storage.open()
        }
        awaitEvent(run.events) { it is Ended }
        assertEquals("the admission ended the chain", Ended(1, Ended.Outcome.ABANDONED), run.events.last())
    }

    // ---- Helpers ---------------------------------------------------------------------------------

    // Write 0 fails, and the first retry write, write 1, follows [retryScript].
    private fun firstWriteFailsThen(retryScript: WriteScript): FaultPlan =
        FaultPlan()
            .write(WriteScript.ReturnFalse, generation = 1, index = 0)
            .write(retryScript, generation = 1, index = 1)

    private fun scheduled(sequence: Long, attempt: Int = 1, delayMs: Long = FIRST_DELAY_MS) =
        Scheduled(sequence, RecoveryKind.WRITE, attempt, delayMs)

    // The retry events of one attempt that is scheduled, fires, starts, and ends with [outcome]. The chain starts at
    // sequence number 1 and every attempt runs, so the attempt has the sequence number [attempt].
    private fun attemptEvents(attempt: Int, delayMs: Long, outcome: Ended.Outcome): List<RecoveryEvent> {
        val sequence = attempt.toLong()
        return listOf(
            scheduled(sequence, attempt, delayMs),
            Fired(sequence),
            Started(sequence),
            Ended(sequence, outcome),
        )
    }

    private fun cancelled(sequence: Long) = Cancelled(sequence, Cancelled.Reason.CANCELLED)

    private fun stopped(sequence: Long) = Cancelled(sequence, Cancelled.Reason.STOPPED)

    // The payloads that reached durable storage in process 1, in commit order.
    private fun LockoutHarness.durableCommits(): List<LockoutSnapshot?> =
        ledger.storageEvents(1)
            .filter { it.op == StorageOp.WRITE && it.phase == Phase.DURABLE_COMMIT }
            .map { it.value }

    // A waiter on another thread that awaits [resolved] and keeps the exception that the await threw.
    private fun waiterOf(resolved: Deferred<*>): CompletableFuture<Throwable?> {
        val thrown = CompletableFuture<Throwable?>()
        thread(name = "waiter") { thrown.complete(runCatching { runBlocking { resolved.await() } }.exceptionOrNull()) }
        return thrown
    }

    private fun assertCancelledWithoutCompletion(
        case: String,
        waiter: CompletableFuture<Throwable?>,
        resolved: Deferred<*>,
        callbacks: AtomicInteger,
    ) {
        val thrown = waiter.get(WAIT_S, TimeUnit.SECONDS)
        assertTrue("$case: the waiter ends with a cancellation, not $thrown", thrown is CancellationException)
        assertTrue("$case: the caller result is cancelled", resolved.isCancelled)
        assertEquals("$case: no callback", 0, callbacks.get())
    }

    private fun awaitCompletion(resolved: Deferred<*>) {
        val completed = CountDownLatch(1)
        resolved.invokeOnCompletion { completed.countDown() }
        assertTrue("the caller result completes", completed.await(WAIT_S, TimeUnit.SECONDS))
    }

    private fun awaitEvent(events: List<RecoveryEvent>, predicate: (RecoveryEvent) -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_S)
        while (events.none(predicate)) {
            check(System.nanoTime() < deadline) { "no matching retry event in $events" }
            Thread.sleep(1L)
        }
    }

    /**
     * A manager without the harness, for the tests that cancel the manager job or block the writer on a real thread.
     * Its writer is one thread named `lockout-io`, the test can cancel its scope, and its retry timer is virtual.
     */
    private inner class DirectRun(seed: LockoutSnapshot, writeResults: List<Boolean>, gatedWrite: Int?) {
        val clocks = VirtualClocks()
        val timer = VirtualRecoveryTimer(clocks)
        val events = CopyOnWriteArrayList<RecoveryEvent>()
        val storage = GateStorage(seed, writeResults, gatedWrite)
        private val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "lockout-io").apply { isDaemon = true }
        }.also { executors += it }
        private val dispatcher = executor.asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val manager = LockoutManager(
            storage,
            clock = clocks::wallMs,
            elapsedRealtime = clocks::elapsedMs,
            ioDispatcher = dispatcher,
            scope = scope,
            recovery = RecoverySetup(timer = timer, listener = { event -> events += event }),
        ).also { managers += it }

        /** Occupies the writer until the returned latch opens, so that later work waits in its queue. */
        fun occupyWriter(): CountDownLatch {
            val busy = CountDownLatch(1)
            val release = CountDownLatch(1)
            executor.execute {
                busy.countDown()
                release.await(HOLD_LIMIT_S, TimeUnit.SECONDS)
            }
            assertTrue("the writer is occupied", busy.await(WAIT_S, TimeUnit.SECONDS))
            return release
        }

        /** Moves the virtual clocks by [ms] and fires the retries that are then due. */
        fun advance(ms: Long) {
            clocks.advance(ms)
            timer.fireDue()
        }
    }

    /**
     * Storage for the tests without the harness. Write N returns `writeResults[N]`, or true when the list has no such
     * entry, and a committed write is stored. The write with the index [gatedWrite] records its thread and then blocks
     * until [open].
     */
    private class GateStorage(
        seed: LockoutSnapshot,
        private val writeResults: List<Boolean>,
        private val gatedWrite: Int?,
    ) : LockoutStorage {
        val gateReached = CountDownLatch(1)

        @Volatile
        var gatedThread: String? = null
            private set

        @Volatile
        var durable: LockoutSnapshot = seed
            private set
        private val gate = CountDownLatch(1)
        private val writeCount = AtomicInteger()

        val writes: Int get() = writeCount.get()

        override fun read(): LockoutSnapshot = durable

        override fun write(snapshot: LockoutSnapshot): Boolean {
            val index = writeCount.getAndIncrement()
            if (index == gatedWrite) {
                gatedThread = stableThreadName()
                gateReached.countDown()
                gate.await(HOLD_LIMIT_S, TimeUnit.SECONDS)
            }
            val committed = writeResults.getOrElse(index) { true }
            if (committed) durable = snapshot
            return committed
        }

        fun open() = gate.countDown()
    }

    private companion object {
        /** The delay of the first retry of a chain. */
        const val FIRST_DELAY_MS = 1_000L

        /** A death before the first retry. */
        const val BEFORE_FIRST_RETRY_MS = 500L

        /** When the fourth and fifth retries run after the failed write, if every attempt fails. */
        const val FOURTH_RETRY_MS = 15_000L
        const val FIFTH_RETRY_MS = 31_000L

        const val TWO_MINUTES_MS = 120_000L
        const val POLL_BURST = 1_000
        const val WAIT_S = 5L

        // Longer than every wait of a test, so a hold that a test forgets to open still ends.
        const val HOLD_LIMIT_S = 60L
    }
}
