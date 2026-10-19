package com.applock.security.r007

import com.applock.security.LockoutManager.FailureOutcome
import com.applock.security.LockoutSnapshot
import com.applock.security.LockoutState.Available
import com.applock.security.LockoutState.LockedOut
import com.applock.security.RecoveryEvent
import com.applock.security.RecoveryEvent.Cancelled
import com.applock.security.RecoveryEvent.Ended
import com.applock.security.RecoveryEvent.Fired
import com.applock.security.RecoveryEvent.Ignored
import com.applock.security.RecoveryEvent.Scheduled
import com.applock.security.RecoveryEvent.Started
import com.applock.security.RecoveryKind
import com.applock.security.harness.FaultPlan
import com.applock.security.harness.GatedCaller
import com.applock.security.harness.LedgerEvent
import com.applock.security.harness.LockoutHarness
import com.applock.security.harness.LockoutModel
import com.applock.security.harness.Phase
import com.applock.security.harness.StorageOp
import com.applock.security.harness.VirtualClocks
import com.applock.security.harness.WriteScript
import com.applock.security.r007.BaselineCase.Companion.C4
import com.applock.security.r007.BaselineCase.Companion.HOUR_MS
import com.applock.security.r007.BaselineCase.Companion.MAX
import com.applock.security.r007.BaselineCase.Companion.T
import com.applock.security.r007.BaselineCase.Companion.W0
import com.applock.security.r007.BaselineCase.Companion.Z
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Candidate B3 of phase P3 (`2026-10-07_R007_F2_HARDENING_P3_B3_SPEC.md`), for residual R-007/2: a write retry also
 * stores the remaining in-memory fallback as a wall deadline in the stored pair, so that a restart keeps it. Each case
 * checks and records `deadline_loss_window_ms`: the time from the first failed completion that leaves the fallback
 * outside storage to the first commit of a converted deadline. The X04 cases also record
 * `availability_regression`, the time that a backward wall change keeps access blocked.
 */
class DeadlineRetryTest : BaselineCase() {

    // ---- H06 the healthy path ------------------------------------------------------------------

    // B3 spec: D3.
    @Test
    fun `H06 - healthy writes and a stale threshold commit start no retry`() {
        val thresholdWrite = LockoutModel.THRESHOLD - 1
        healthyRun("H02", Z, heldWrite(thresholdWrite)) { harness ->
            assertEquals(LockoutModel.THRESHOLD, GatedCaller(harness).wrongPinsUntilBlocked())
            harness.release(StorageOp.WRITE, thresholdWrite)
            assertEquals("the threshold commit stores L5", L5, harness.store.durableState())
        }
        healthyRun("H03", L5, FaultPlan()) { harness ->
            val caller = GatedCaller(harness)
            harness.advance(T)
            LADDER_MS.forEach { windowMs ->
                assertTrue("a wrong PIN after the end of each lockout reaches verification", caller.wrongPin())
                harness.check()
                harness.advance(windowMs)
            }
        }
        listOf(0L, 10L, 100L, 1_000L).forEach { delayMs ->
            heldFirstRuns().forEach { run ->
                healthyRun("H06-${run.name}-$delayMs", run.initial, heldWrite(0)) { harness ->
                    if (run.reset) harness.succeed() else harness.fail()
                    harness.fail()
                    harness.advance(delayMs)
                    harness.release(StorageOp.WRITE, 0)
                    assertEquals("the last admitted pair is durable", run.finalPair, harness.store.durableState())
                }
            }
        }
    }

    // ---- R2.1 a failed below-threshold write, its retry, and death -----------------------------

    // B3 spec: D1, D4, D7, D14.
    @Test
    fun `R2_1 - after the retry commits a restart keeps the remainder of the fallback`() {
        listOf(WriteScript.ReturnFalse, WriteScript.Throw).forEach { script ->
            val harness = harness(Z, FaultPlan().write(script, generation = 1, index = 0))
            harness.start()
            val callbacks = AtomicInteger()
            val failure = harness.fail { callbacks.incrementAndGet() }
            harness.advance(FIRST_DELAY_MS)
            harness.check()
            assertEquals("$script: retry write 1 stores the fallback", FIRST_FALLBACK, harness.store.durableState())
            harness.advance(4_000L)
            harness.check()
            val recordedLock = LockedOut(25_000L, degraded = false)
            assertEquals("$script: the retry gives a recorded lockout", recordedLock, harness.manager.currentState())
            val degradedOutcome = FailureOutcome(LockedOut(T, degraded = true), 1)
            val outcome = runBlocking { failure.resolved.await() }
            assertEquals("$script: the failure outcome stays degraded", degradedOutcome, outcome)
            assertEquals("$script: one callback", 1, callbacks.get())
            assertEquals("$script: the first retry attempt commits", committedAttempt(1), harness.retryEvents())

            harness.restart()
            harness.check()
            assertEquals("$script: the restart keeps the stored deadline", recordedLock, harness.manager.currentState())
            assertEquals("$script: the restart keeps the failure count", 1, harness.manager.failureCount())
            assertFalse("$script: the gate stays blocked", GatedCaller(harness).wrongPin())
            val lossWindow = harness.checkedLossWindowMs(1_000L)
            harness.record("R2.1-retry-commit-$script", "deadline_loss_window_ms" to lossWindow)
        }
    }

    // B3 spec: D7, D14.
    @Test
    fun `R2_1 - a reboot after the retry commit enforces the stored deadline less the downtime`() {
        listOf(WriteScript.ReturnFalse, WriteScript.Throw).forEach { script ->
            val harness = harness(Z, FaultPlan().write(script, generation = 1, index = 0))
            harness.start()
            harness.fail()
            harness.advance(FIRST_DELAY_MS)
            harness.advance(4_000L)
            harness.reboot(REBOOT_DOWNTIME_MS)
            harness.check()
            assertEquals("$script: the reboot starts boot 2", 2, harness.clocks.bootCount())
            val lessDowntime = LockedOut(15_000L, degraded = false)
            assertEquals("$script: blocked until the stored deadline", lessDowntime, harness.manager.currentState())
            assertEquals("$script: the reboot keeps the failure count", 1, harness.manager.failureCount())
            assertFalse("$script: the gate stays blocked", GatedCaller(harness).wrongPin())
            val lossWindow = harness.checkedLossWindowMs(1_000L)
            harness.record("R2.1-reboot-$script", "deadline_loss_window_ms" to lossWindow)
        }
    }

    // B3 spec: D13.
    @Test
    fun `R2_1 - a death before the retry commit loses the fallback and a death after it keeps it`() {
        listOf(WriteScript.ReturnFalse, WriteScript.Throw).forEach { script ->
            deathAfterFailedWrite(script, retryScript = null, survives = false)
            deathAfterFailedWrite(script, WriteScript.HoldBeforeCommit(), survives = false)
            deathAfterFailedWrite(script, WriteScript.CommitThenHold, survives = true)
        }
    }

    // A wrong PIN through the gate whose write 0 follows [script] and fails. The process dies at 500 ms, or once
    // retry write 1 parks with [retryScript]. Then a restart and another wrong PIN.
    private fun deathAfterFailedWrite(script: WriteScript, retryScript: WriteScript?, survives: Boolean) {
        val case = "R2.1-death-$script-${retryScript ?: "at-500ms"}"
        val faults = FaultPlan().write(script, generation = 1, index = 0)
        if (retryScript != null) faults.write(retryScript, generation = 1, index = 1)
        val harness = harness(Z, faults)
        harness.start()
        val caller = GatedCaller(harness)
        assertTrue("$case: the first wrong PIN reaches verification", caller.wrongPin())
        if (retryScript != null) {
            harness.advance(FIRST_DELAY_MS)
            harness.awaitHeld(StorageOp.WRITE, 1)
        } else {
            harness.advance(BEFORE_FIRST_RETRY_MS)
        }
        harness.check()
        harness.restart()
        harness.check()
        val durable = if (survives) FIRST_FALLBACK else Z
        assertEquals("$case: only a retry commit stores the fallback", durable, harness.store.durableState())
        val afterRestart = if (survives) LockedOut(T - FIRST_DELAY_MS, degraded = false) else Available
        assertEquals("$case: the restart keeps only a stored fallback", afterRestart, harness.manager.currentState())
        assertEquals("$case: the gate opens only when no fallback is stored", !survives, caller.wrongPin())
        val entries = if (survives) 1 else 2
        assertEquals("$case: V + 1 only when no fallback is stored", entries, caller.verifierEntries)
        if (survives) assertEquals("$case: the new process writes nothing", 0, harness.store.writeCount(2))
        harness.record(
            case,
            "deadline_loss_window_ms" to harness.checkedLossWindowMs(if (survives) 1_000L else NOT_CLOSED),
            "verifier_entries" to caller.verifierEntries,
        )
    }

    // ---- R2.2 a committed count under a fallback -----------------------------------------------

    // B3 spec: D2, D4, D7.
    @Test
    fun `R2_2 - a committed count after the failed write starts a retry that stores the fallback`() {
        // Order A: F1 fails while F2 waits behind it, so F1 is not the latest admission and starts no chain.
        val orderA = committedCountUnderFallback("R2.2-order-A", FaultPlan().write(HELD_FALSE, 1, 0), heldFirst = true)
        assertEquals("order A: only the chain of F2 runs, and it commits", committedAttempt(1), orderA.retryEvents(1))
        // Order B: F1 fails at once, and F2 cancels the chain of F1.
        val orderB = committedCountUnderFallback("R2.2-order-B", firstWriteFails(), heldFirst = false)
        val orderBEvents = listOf(scheduled(1), cancelled(1)) + committedAttempt(2)
        assertEquals("order B: F2 cancels the chain of F1 and starts its own", orderBEvents, orderB.retryEvents(1))
    }

    // F1 fails and F2 commits (2, 0) at W0 and E0, so F2's commit starts a deadline chain. Then the clocks advance by
    // 1 s and 9 s, and the process restarts.
    private fun committedCountUnderFallback(case: String, faults: FaultPlan, heldFirst: Boolean): LockoutHarness {
        val harness = harness(Z, faults)
        harness.start()
        val firstFailure = harness.fail()
        val secondFailure = harness.fail()
        if (heldFirst) harness.release(StorageOp.WRITE, 0)
        harness.check()
        assertEquals("$case: F2 commits count 2", LockoutSnapshot(2, 0L), harness.store.durableState())
        harness.advance(FIRST_DELAY_MS)
        harness.check()
        val storedFallback = LockoutSnapshot(2, W0 + T)
        assertEquals("$case: the retry stores the fallback of F1", storedFallback, harness.store.durableState())
        harness.advance(9_000L)
        harness.check()
        val recordedLock = LockedOut(20_000L, degraded = false)
        assertEquals("$case: the fallback gives a recorded lockout", recordedLock, harness.manager.currentState())
        val firstOutcome = runBlocking { firstFailure.resolved.await() }
        assertEquals("$case: F1 has a degraded outcome", FailureOutcome(LockedOut(T, degraded = true), 1), firstOutcome)
        val secondOutcome = runBlocking { secondFailure.resolved.await() }
        assertEquals("$case: F2 has no lockout outcome", FailureOutcome(Available, 2), secondOutcome)

        harness.restart()
        harness.check()
        assertEquals("$case: the restart keeps the stored deadline", recordedLock, harness.manager.currentState())
        assertEquals("$case: the restart keeps the failure count", 2, harness.manager.failureCount())
        val lossWindow = harness.checkedLossWindowMs(1_000L, stored = storedFallback)
        harness.record(case, "deadline_loss_window_ms" to lossWindow)
        return harness
    }

    // B3 spec: D7, D14, D12.
    @Test
    fun `R2_2 - ordinary writes after a converted commit keep or replace the stored deadline, and a reset clears it`() {
        val harness = harness(Z, firstWriteFails())
        harness.start()
        harness.fail()
        harness.advance(FIRST_DELAY_MS)
        harness.check()
        assertEquals(FIRST_FALLBACK, harness.store.durableState())
        harness.fail() // manager-only: the gate is blocked
        harness.check()
        assertEquals("F2 keeps the stored deadline", LockoutSnapshot(2, W0 + T), harness.store.durableState())

        harness.advance(1_000L)
        harness.restart()
        harness.check()
        val keptLock = LockedOut(28_000L, degraded = false)
        assertEquals("the restart keeps the stored deadline", keptLock, harness.manager.currentState())
        assertEquals(2, harness.manager.failureCount())
        listOf(3, 4).forEach { count ->
            harness.fail()
            harness.check()
            val countPair = LockoutSnapshot(count, W0 + T)
            assertEquals("F$count keeps the stored deadline", countPair, harness.store.durableState())
        }
        harness.fail()
        harness.check()
        val thresholdPair = LockoutSnapshot(5, W0 + 32_000L)
        assertEquals("F5 replaces it with a later deadline", thresholdPair, harness.store.durableState())
        assertEquals(LockedOut(T, degraded = false), harness.manager.currentState())
        harness.succeed()
        harness.check()
        assertEquals("the reset clears it", Z, harness.store.durableState())
        harness.record("R2.2-ordinary-writes", "deadline_loss_window_ms" to harness.checkedLossWindowMs(1_000L))
    }

    // ---- R2.3 a fallback that outlasts a threshold commit ---------------------------------------

    // B3 spec: D2, D4, D7.
    @Test
    fun `R2_3 - a retry stores the remainder of a fallback that outlasts an expired threshold commit`() {
        val harness = harness(LockoutSnapshot(3, 0L), FaultPlan().write(HELD_FALSE, generation = 1, index = 0))
        harness.start()
        val fourthFailure = harness.fail()
        val fifthFailure = harness.fail()
        harness.advance(STALL_MS)
        harness.release(StorageOp.WRITE, 0)
        harness.check()
        assertEquals("F5 commits its expired threshold deadline", L5, harness.store.durableState())

        harness.advance(FIRST_DELAY_MS)
        harness.check()
        val storedFallback = LockoutSnapshot(5, W0 + STALL_MS + T)
        assertEquals("the retry stores the fallback of F4", storedFallback, harness.store.durableState())
        val recordedLock = LockedOut(T - FIRST_DELAY_MS, degraded = false)
        assertEquals(recordedLock, harness.manager.currentState())
        assertEquals(FailureOutcome(LockedOut(T, degraded = true), 4), runBlocking { fourthFailure.resolved.await() })
        assertEquals(FailureOutcome(LockedOut(T, degraded = false), 5), runBlocking { fifthFailure.resolved.await() })

        harness.restart()
        harness.check()
        assertEquals("the restart keeps the stored deadline", recordedLock, harness.manager.currentState())
        val lossWindow = harness.checkedLossWindowMs(1_000L, stored = storedFallback)
        harness.record("R2.3-late-fallback", "deadline_loss_window_ms" to lossWindow)
    }

    // ---- R2.4 failed retries -------------------------------------------------------------------

    // B3 spec: D10, D5.
    @Test
    fun `R2_4 - failed retries store the original deadline until it passes and none after it`() {
        val harness = harness(Z, FaultPlan().write(WriteScript.ReturnFalse))
        harness.start()
        harness.fail()
        RETRY_TIMES_MS.fold(0L) { previousMs, atMs ->
            harness.advance(atMs - previousMs)
            harness.check()
            val expected = if (atMs < T) LockedOut(T - atMs, degraded = true) else Available
            assertEquals("the original fallback at $atMs ms", expected, harness.manager.currentState())
            atMs
        }
        val payloads = listOf(FIRST_PAYLOAD) + List(4) { FIRST_FALLBACK } + FIRST_PAYLOAD
        assertEquals("the failed write and the retries at 1, 3, 7, 15, and 31 s", payloads, harness.writePayloads())
        assertEquals(Z, harness.store.durableState())

        harness.restart()
        harness.check()
        assertEquals(Available, harness.manager.currentState())
        assertTrue("the restart opens the gate", GatedCaller(harness).wrongPin())
        harness.record("R2.4-failed-retries", "deadline_loss_window_ms" to harness.checkedLossWindowMs(NOT_CLOSED))
    }

    // ---- X08 adapter cancellations ---------------------------------------------------------------

    // B3 spec: D1, D10, D7.
    @Test
    fun `X08 - adapter cancellations of a failure write and of its retry keep the original deadline`() {
        val faults = FaultPlan()
            .write(WriteScript.ThrowCancellation, generation = 1, index = 0)
            .write(WriteScript.ThrowCancellation, generation = 1, index = 1)
        val harness = harness(Z, faults)
        harness.start()
        val callbacks = AtomicInteger()
        harness.fail { callbacks.incrementAndGet() }
        harness.check()
        assertEquals("the fallback is armed", LockedOut(T, degraded = true), harness.manager.currentState())
        assertEquals(1, callbacks.get())
        harness.advance(FIRST_DELAY_MS)
        harness.check()
        assertEquals("retry 1 fails", Z, harness.store.durableState())

        harness.advance(2_000L)
        harness.check()
        assertEquals("retry 2 stores the original deadline", FIRST_FALLBACK, harness.store.durableState())
        assertEquals(LockedOut(T - 3_000L, degraded = false), harness.manager.currentState())
        val firstAttempt = listOf(scheduled(1), Fired(1), Started(1), Ended(1, Ended.Outcome.RETRY))
        val secondAttempt = listOf(scheduled(2, attempt = 2, delayMs = 2_000L), Fired(2), Started(2), succeeded(2))
        assertEquals(firstAttempt + secondAttempt, harness.retryEvents())
        assertEquals("one callback", 1, callbacks.get())
        harness.record("X08-cancellations", "deadline_loss_window_ms" to harness.checkedLossWindowMs(3_000L))
    }

    // ---- X03 the end of the fallback -------------------------------------------------------------

    // B3 spec: D6, D11.
    @Test
    fun `X03 - a deadline retry that runs at or after the end of the fallback writes nothing`() {
        listOf(T - 1, T, T + 1, TEN_MIN_MS).forEach { resumeMs ->
            val case = "X03-deadline-retry-resumed-at-$resumeMs"
            val harness = harness(Z, firstWriteFails())
            harness.start()
            harness.fail()
            harness.fail() // cancels the chain of F1, and its commit starts a deadline chain
            harness.holdIo()
            harness.advance(T - 1) // the retry fires at T - 1 ms and waits in the held queue
            harness.advance(resumeMs - (T - 1))
            harness.resumeIo()
            harness.check()
            val written = resumeMs < T
            val storedFallback = LockoutSnapshot(2, W0 + T)
            val durable = if (written) storedFallback else LockoutSnapshot(2, 0L)
            assertEquals("$case: the fallback is stored only before it ends", durable, harness.store.durableState())
            val state = if (written) LockedOut(1L, degraded = false) else Available
            assertEquals("$case: a lockout remains only when the retry wrote", state, harness.manager.currentState())
            val writes = if (written) 3 else 2
            assertEquals("$case: the retry writes only before the fallback ends", writes, harness.store.writeCount(1))
            val end = if (written) listOf(Started(2), succeeded(2)) else listOf(Ignored(2, Ignored.Reason.NOT_WANTED))
            val events = listOf(scheduled(1), cancelled(1), scheduled(2), Fired(2)) + end
            assertEquals("$case: the retry commits before the end, else it is ignored", events, harness.retryEvents())
            val expectedWindow = if (written) T - 1 else NOT_CLOSED
            val lossWindow = harness.checkedLossWindowMs(expectedWindow, stored = storedFallback)
            harness.record(case, "deadline_loss_window_ms" to lossWindow)
        }
    }

    // B3 spec: D4, D7, D11.
    @Test
    fun `X03 - a retry write that commits after the fallback ends brings no lockout back`() {
        val harness = harness(Z, firstWriteFailsThen(WriteScript.HoldBeforeCommit()))
        harness.start()
        harness.fail()
        harness.advance(FIRST_DELAY_MS)
        harness.awaitHeld(StorageOp.WRITE, 1)
        harness.advance(T + 1 - FIRST_DELAY_MS)
        harness.check()
        assertEquals("the fallback has ended", Available, harness.manager.currentState())

        harness.release(StorageOp.WRITE, 1)
        harness.check()
        assertEquals("the retry stores the passed deadline", FIRST_FALLBACK, harness.store.durableState())
        assertEquals("no lockout comes back", Available, harness.manager.currentState())
        harness.restart()
        harness.check()
        assertEquals(Available, harness.manager.currentState())
        assertEquals(1, harness.manager.failureCount())
        harness.record("X03-late-retry-commit", "deadline_loss_window_ms" to harness.checkedLossWindowMs(T + 1))
    }

    // B3 spec: D5, D8.
    @Test
    fun `X03 - a retry keeps a longer recorded deadline`() {
        // The stored form of a converted deadline after a backward wall change.
        val harness = harness(LockoutSnapshot(3, W0 + TEN_MIN_MS), firstWriteFails())
        harness.start()
        harness.fail() // manager-only: the gate is blocked
        harness.advance(FIRST_DELAY_MS)
        harness.check()
        val targetPayload = LockoutSnapshot(4, W0 + TEN_MIN_MS)
        assertEquals("no gap: the retry stores its target payload", targetPayload, harness.store.durableState())
        val recordedLock = LockedOut(TEN_MIN_MS - FIRST_DELAY_MS, degraded = false)
        assertEquals(recordedLock, harness.manager.currentState())
        harness.record("X03-longer-recorded-deadline", "deadline_loss_window_ms" to "none")
    }

    // ---- X02 freshness -------------------------------------------------------------------------

    // B3 spec: D1, D4.
    @Test
    fun `X02 - a fallback extended without a new revision is stored with its extended deadline`() {
        val faults = FaultPlan()
            .write(HELD_FALSE, generation = 1, index = 0)
            .write(HELD_FALSE, generation = 1, index = 1)
        val harness = harness(Z, faults)
        harness.start()
        harness.fail()
        harness.fail()
        harness.release(StorageOp.WRITE, 0)
        harness.check()
        assertEquals("no chain while write 1 waits", emptyList<RecoveryEvent>(), harness.retryEvents())

        harness.advance(10_000L)
        harness.release(StorageOp.WRITE, 1)
        harness.check()
        assertEquals("the fallback now ends at 40 s", LockedOut(T, degraded = true), harness.manager.currentState())
        assertEquals("the failure of write 1 starts the chain", listOf(scheduled(1)), harness.retryEvents())
        harness.advance(FIRST_DELAY_MS)
        harness.check()
        val storedFallback = LockoutSnapshot(2, W0 + 40_000L)
        assertEquals("the retry stores the extended deadline", storedFallback, harness.store.durableState())
        assertEquals("the first attempt commits", committedAttempt(1), harness.retryEvents())

        harness.restart()
        harness.check()
        assertEquals(LockedOut(T - FIRST_DELAY_MS, degraded = false), harness.manager.currentState())
        // The window starts when write 0 fails. The failure of write 1 extends the fallback and starts no new window.
        val lossWindow = harness.checkedLossWindowMs(11_000L, stored = storedFallback)
        harness.record("X02-extended-fallback", "deadline_loss_window_ms" to lossWindow)
    }

    // B3 spec: D9, D2, D7.
    @Test
    fun `X02 - after an admission the manager ignores the retry commit and a new chain stores the deadline`() {
        val harness = harness(Z, firstWriteFailsThen(WriteScript.HoldBeforeCommit()))
        harness.start()
        harness.fail()
        harness.advance(FIRST_DELAY_MS)
        harness.awaitHeld(StorageOp.WRITE, 1)
        harness.fail() // manager-only: the gate is blocked
        harness.release(StorageOp.WRITE, 1)
        harness.check()
        val degradedLock = LockedOut(T - FIRST_DELAY_MS, degraded = true)
        assertEquals("the manager ignores the retry commit", degradedLock, harness.manager.currentState())

        harness.advance(FIRST_DELAY_MS)
        harness.check()
        val storedFallback = LockoutSnapshot(2, W0 + T)
        val diskOrder = listOf(FIRST_FALLBACK, LockoutSnapshot(2, 0L), storedFallback)
        assertEquals(diskOrder, harness.durableCommits())
        assertEquals(LockedOut(T - 2_000L, degraded = false), harness.manager.currentState())
        val abandoned = listOf(scheduled(1), Fired(1), Started(1), Ended(1, Ended.Outcome.ABANDONED))
        assertEquals(abandoned + committedAttempt(2), harness.retryEvents())
        // The admission during the retry write opens the window again.
        val lossWindows = listOf(
            harness.checkedLossWindowMs(1_000L),
            harness.checkedLossWindowMs(1_000L, startWrite = 2, stored = storedFallback),
        )
        harness.record("X02-admission-during-retry", "deadline_loss_window_ms" to lossWindows)
    }

    // ---- X04 wall-clock changes ----------------------------------------------------------------

    // B3 spec: D10, D4, D7.
    @Test
    fun `X04 - a wall change between retries moves the stored deadline and keeps the remainder`() {
        val faults = firstWriteFailsThen(WriteScript.ReturnFalse)
        val harness = harness(Z, faults, VirtualClocks(wallMs = REALISTIC_WALL_MS))
        harness.start()
        harness.fail()
        harness.advance(FIRST_DELAY_MS)
        harness.check()
        val firstRetry = LockoutSnapshot(1, REALISTIC_WALL_MS + T)
        assertEquals("retry 1 carries the deadline", firstRetry, harness.writePayloads()[1])

        harness.jumpWall(HOUR_MS)
        harness.advance(2_000L)
        harness.check()
        val movedFallback = LockoutSnapshot(1, REALISTIC_WALL_MS + HOUR_MS + T)
        assertEquals("retry 2 stores the moved deadline", movedFallback, harness.store.durableState())
        val recordedLock = LockedOut(T - 3_000L, degraded = false)
        assertEquals(recordedLock, harness.manager.currentState())
        harness.restart()
        harness.check()
        assertEquals("the restart keeps the remainder", recordedLock, harness.manager.currentState())
        harness.record(
            "X04-jump-between-retries",
            "deadline_loss_window_ms" to harness.checkedLossWindowMs(3_000L, stored = movedFallback),
            "availability_regression" to "none",
        )
    }

    // B3 spec: D7, D14.
    @Test
    fun `X04 - after a retry commit a backward wall change blocks until the stored deadline, also after a restart`() {
        val forwardHarness = committedRetryAtRealisticWall()
        forwardHarness.jumpWall(HOUR_MS)
        forwardHarness.check()
        val mirrorLock = LockedOut(T - FIRST_DELAY_MS, degraded = false)
        assertEquals("the elapsed mirror holds", mirrorLock, forwardHarness.manager.currentState())
        forwardHarness.restart()
        forwardHarness.check()
        assertEquals("a restart ends the lockout", Available, forwardHarness.manager.currentState())
        forwardHarness.record(
            "X04-forward-1h",
            "deadline_loss_window_ms" to forwardHarness.checkedLossWindowMs(1_000L, stored = REALISTIC_FALLBACK),
            "availability_regression" to "none",
        )

        listOf(false, true).forEach { restart ->
            val case = "X04-backward-1h-${if (restart) "restart" else "in-process"}"
            val harness = committedRetryAtRealisticWall()
            harness.jumpWall(-HOUR_MS)
            if (restart) harness.restart()
            harness.check()
            assertEquals("$case: capped at 30 min at the jump", LockedOut(MAX, degraded = false), harness.poll())
            harness.advance(CAPPED_UNTIL_MS - 1)
            assertEquals("$case: still at the cap", LockedOut(MAX, degraded = false), harness.poll())
            harness.advance(2L)
            assertEquals("$case: below the cap", LockedOut(MAX - 1, degraded = false), harness.poll())
            harness.advance(BACKWARD_BLOCKED_MS - 2 - CAPPED_UNTIL_MS)
            assertEquals("$case: counting down", LockedOut(1L, degraded = false), harness.poll())
            harness.advance(1L)
            assertEquals("$case: open again", Available, harness.poll())
            harness.check()
            harness.record(
                case,
                "deadline_loss_window_ms" to harness.checkedLossWindowMs(1_000L, stored = REALISTIC_FALLBACK),
                "availability_regression" to "blocked $BACKWARD_BLOCKED_MS ms against ${T - FIRST_DELAY_MS} ms",
            )
        }
    }

    // A failed first write at a realistic wall time, and its retry, which stores the fallback deadline at 1 s.
    private fun committedRetryAtRealisticWall(): LockoutHarness {
        val harness = harness(Z, firstWriteFails(), VirtualClocks(wallMs = REALISTIC_WALL_MS))
        harness.start()
        harness.fail()
        harness.advance(FIRST_DELAY_MS)
        harness.check()
        assertEquals(REALISTIC_FALLBACK, harness.store.durableState())
        return harness
    }

    // ---- R4.3 a reset and a retry ----------------------------------------------------------------

    // B3 spec: D12, D9.
    @Test
    fun `R4_3 - a reset drops a queued retry and its clear follows a running retry`() {
        val queuedHarness = harness(Z, firstWriteFails())
        queuedHarness.start()
        queuedHarness.fail()
        queuedHarness.holdIo()
        queuedHarness.advance(FIRST_DELAY_MS)
        queuedHarness.succeed() // manager-only: the gate is blocked
        queuedHarness.resumeIo()
        queuedHarness.check()
        val queuedEvents = listOf(scheduled(1), Fired(1), cancelled(1), Ignored(1, Ignored.Reason.STALE))
        assertEquals("queued: the reset cancels the fired retry", queuedEvents, queuedHarness.retryEvents())
        assertEquals("queued: only the clear is stored", listOf(Z), queuedHarness.durableCommits())
        assertResetHolds(queuedHarness, "R4.3-queued-retry", NOT_CLOSED)

        val runningHarness = harness(Z, firstWriteFailsThen(WriteScript.HoldBeforeCommit()))
        runningHarness.start()
        runningHarness.fail()
        runningHarness.advance(FIRST_DELAY_MS)
        runningHarness.awaitHeld(StorageOp.WRITE, 1)
        runningHarness.succeed()
        runningHarness.release(StorageOp.WRITE, 1)
        runningHarness.check()
        val diskOrder = listOf(FIRST_FALLBACK, Z)
        assertEquals("running: the retry pair, then the clear", diskOrder, runningHarness.durableCommits())
        val runningEvents = listOf(scheduled(1), Fired(1), Started(1), Ended(1, Ended.Outcome.ABANDONED))
        assertEquals("running: the manager ignores the retry commit", runningEvents, runningHarness.retryEvents())
        assertResetHolds(runningHarness, "R4.3-running-retry", 1_000L)
    }

    // The reset holds: Available with count 0 in the live process, and Available after a restart. The loss window of
    // the case is [lossWindow].
    private fun assertResetHolds(harness: LockoutHarness, case: String, lossWindow: Any) {
        assertEquals("$case: the reset opens the gate", Available, harness.manager.currentState())
        assertEquals("$case: the reset clears the count", 0, harness.manager.failureCount())
        harness.restart()
        harness.check()
        assertEquals("$case: still open after the restart", Available, harness.manager.currentState())
        harness.record(case, "deadline_loss_window_ms" to harness.checkedLossWindowMs(lossWindow))
    }

    // B3 spec: D9, D12.
    @Test
    fun `R4_3 - a failed clear behind a running retry leaves the stored deadline until the reset retry commits`() {
        listOf(true, false).forEach { restart ->
            val case = "R4.3-failed-clear-${if (restart) "restart" else "reset-retry"}"
            val faults = firstWriteFailsThen(WriteScript.HoldBeforeCommit())
                .write(WriteScript.ReturnFalse, generation = 1, index = 2)
            val harness = harness(Z, faults)
            harness.start()
            harness.fail()
            harness.advance(FIRST_DELAY_MS)
            harness.awaitHeld(StorageOp.WRITE, 1)
            harness.succeed() // manager-only: the gate is blocked
            harness.release(StorageOp.WRITE, 1)
            harness.check()
            assertEquals("$case: the reset opens the gate", Available, harness.manager.currentState())
            assertEquals("$case: the failed clear leaves the deadline", FIRST_FALLBACK, harness.store.durableState())
            if (restart) {
                harness.advance(500L)
                harness.restart()
                harness.check()
                val staleLock = LockedOut(T - 1_500L, degraded = false)
                assertEquals("$case: the stored deadline is back", staleLock, harness.manager.currentState())
            } else {
                harness.advance(FIRST_DELAY_MS)
                harness.check()
                assertEquals("$case: the reset retry stores Z", Z, harness.store.durableState())
                assertEquals("$case: the gate stays open", Available, harness.manager.currentState())
            }
            harness.record(case, "deadline_loss_window_ms" to harness.checkedLossWindowMs(1_000L))
        }
    }

    // ---- X06, X12, R3.4 lifecycle, accounting, and acknowledgement -------------------------------

    // B3 spec: D13.
    @Test
    fun `X06 - after shutdown a running retry still commits and the manager ignores the commit`() {
        val harness = harness(Z, firstWriteFailsThen(WriteScript.HoldBeforeCommit()))
        harness.start()
        val callbacks = AtomicInteger()
        harness.fail { callbacks.incrementAndGet() }
        harness.advance(FIRST_DELAY_MS)
        harness.awaitHeld(StorageOp.WRITE, 1)
        harness.shutdown()
        harness.release(StorageOp.WRITE, 1)
        harness.check()
        assertEquals(FIRST_FALLBACK, harness.store.durableState())
        val degradedLock = LockedOut(T - FIRST_DELAY_MS, degraded = true)
        assertEquals("the manager ignores the commit after the stop", degradedLock, harness.manager.currentState())
        assertEquals("no callback for the retry", 1, callbacks.get())
        val events = listOf(scheduled(1), Fired(1), Started(1), Ended(1, Ended.Outcome.ABANDONED))
        assertEquals("only ENDED abandoned after the stop", events, harness.retryEvents())

        harness.restart()
        harness.check()
        assertEquals(LockedOut(T - FIRST_DELAY_MS, degraded = false), harness.manager.currentState())
        harness.record("X06-retry-after-stop", "deadline_loss_window_ms" to harness.checkedLossWindowMs(1_000L))
    }

    // B3 spec: D7.
    @Test
    fun `X12 - a retry commit changes only the derived degraded flag`() {
        val harness = harness(Z, firstWriteFails())
        harness.start()
        val callbacks = AtomicInteger()
        val failure = harness.fail { callbacks.incrementAndGet() }
        harness.check()
        val degradedOutcome = FailureOutcome(LockedOut(T, degraded = true), 1)
        assertEquals(degradedOutcome, runBlocking { failure.resolved.await() })

        harness.advance(FIRST_DELAY_MS)
        harness.check()
        val recordedLock = LockedOut(T - FIRST_DELAY_MS, degraded = false)
        assertEquals("the remaining fallback, with no extension", recordedLock, harness.manager.currentState())
        assertEquals("the outcome stays as delivered", degradedOutcome, runBlocking { failure.resolved.await() })
        assertEquals("one callback, and none for the retry", 1, callbacks.get())
        assertEquals(1, harness.manager.failureCount())
        harness.record("X12-flag-only", "deadline_loss_window_ms" to harness.checkedLossWindowMs(1_000L))
    }

    // B3 spec: D10, D7.
    @Test
    fun `R3_4 - a retry after a false acknowledgement stores the same deadline again`() {
        val harness = harness(Z, firstWriteFailsThen(WriteScript.CommitThenReportFalse))
        harness.start()
        harness.fail()
        harness.advance(FIRST_DELAY_MS)
        harness.check()
        assertEquals("write 1 stores the deadline", FIRST_FALLBACK, harness.store.durableState())
        val degradedLock = LockedOut(T - FIRST_DELAY_MS, degraded = true)
        assertEquals("after the false result the lockout stays degraded", degradedLock, harness.manager.currentState())

        harness.advance(2_000L)
        harness.check()
        assertEquals("write 2 stores the same pair", listOf(FIRST_FALLBACK, FIRST_FALLBACK), harness.durableCommits())
        val recordedLock = LockedOut(T - 3_000L, degraded = false)
        assertEquals(
            "write 2 gives a recorded lockout with the original expiry",
            recordedLock,
            harness.manager.currentState(),
        )
        harness.record("R3.4-false-ack-retry", "deadline_loss_window_ms" to harness.checkedLossWindowMs(1_000L))
    }

    // ---- Helpers -------------------------------------------------------------------------------

    // Runs one healthy sequence in a new harness, then checks the model, that the ledger has no retry event, and that
    // each admission wrote once.
    private fun healthyRun(
        case: String,
        initial: LockoutSnapshot,
        faults: FaultPlan,
        sequence: (LockoutHarness) -> Unit,
    ) {
        val harness = harness(initial, faults)
        harness.start()
        sequence(harness)
        harness.check()
        val retryEvents = harness.actionsStartingWith(RETRY_PREFIX)
        val admissions = harness.actionsStartingWith(ADMIT_PREFIX).size
        val writes = harness.store.writeCount(1)
        assertEquals("$case: no retry event", emptyList<String>(), retryEvents)
        assertEquals("$case: one write for each admission", admissions, writes)
        harness.record(
            case,
            "retry_events" to retryEvents.size,
            "writes" to writes,
            "deadline_loss_window_ms" to "none",
        )
    }
}

// The H06 runs of the baseline: a first operation whose write is held while a failure is admitted behind it. In the
// stale threshold run, F5 commits after F6 was admitted.
private fun heldFirstRuns(): List<HeldFirstRun> = listOf(
    HeldFirstRun("below", Z, reset = false, LockoutSnapshot(2, 0L)),
    HeldFirstRun("stale-threshold", C4, reset = false, LockoutSnapshot(6, W0 + 60_000L)),
    HeldFirstRun("reset", C4, reset = true, LockoutSnapshot(1, 0L)),
)

private class HeldFirstRun(
    val name: String,
    val initial: LockoutSnapshot,
    val reset: Boolean,
    val finalPair: LockoutSnapshot,
)

private const val ADMIT_PREFIX = "ADMIT_"
private const val RETRY_PREFIX = "RETRY_"

/** The lockout windows from the sixth to the twelfth failure: doubling from 60 s up to the 30 min cap. */
private val LADDER_MS = (6..12).map { count -> LockoutModel.ladder(count) }

/** The delay of the first retry of a chain. */
private const val FIRST_DELAY_MS = 1_000L

/** A death before the first retry. */
private const val BEFORE_FIRST_RETRY_MS = 500L

private const val REBOOT_DOWNTIME_MS = 10_000L
private const val TEN_MIN_MS = 600_000L

/** A realistic epoch time, so a one-hour backward change stays above zero. */
private const val REALISTIC_WALL_MS = 1_790_000_000_000L

/** After a one-hour backward change at 1 s, access stays blocked for 1 h 29 s. */
private const val BACKWARD_BLOCKED_MS = HOUR_MS + T - FIRST_DELAY_MS

/** The reported remaining time stays at the 30 min cap for the first 30 min 29 s of that block. */
private const val CAPPED_UNTIL_MS = BACKWARD_BLOCKED_MS - MAX

/** The retries of a chain whose attempts all fail: 1, 3, 7, 15, and 31 s after the failed write. */
private val RETRY_TIMES_MS = listOf(1_000L, 3_000L, 7_000L, 15_000L, 31_000L)

/** The payload of a first failure below the threshold: count 1 and no deadline. */
private val FIRST_PAYLOAD = LockoutSnapshot(1, 0L)

/** What a retry stores after a failed first write at W0: count 1 and the fallback deadline. */
private val FIRST_FALLBACK = LockoutSnapshot(1, W0 + T)

/** The same pair at the realistic wall time. */
private val REALISTIC_FALLBACK = LockoutSnapshot(1, REALISTIC_WALL_MS + T)

/** The value of a loss window that no commit closes. */
private const val NOT_CLOSED = "not closed"

/** A write held before its commit that then returns false. */
private val HELD_FALSE = WriteScript.HoldBeforeCommit(WriteScript.ReturnFalse)

// A fault plan that holds write [index] of the first process before its commit.
private fun heldWrite(index: Int): FaultPlan =
    FaultPlan().write(WriteScript.HoldBeforeCommit(), generation = 1, index = index)

// Write 0 of the first process fails, and the writes after it commit.
private fun firstWriteFails(): FaultPlan = FaultPlan().write(WriteScript.ReturnFalse, generation = 1, index = 0)

// Write 0 fails, and the first retry write, write 1, follows [retryScript].
private fun firstWriteFailsThen(retryScript: WriteScript): FaultPlan =
    firstWriteFails().write(retryScript, generation = 1, index = 1)

private fun scheduled(sequence: Long, attempt: Int = 1, delayMs: Long = FIRST_DELAY_MS) =
    Scheduled(sequence, RecoveryKind.WRITE, attempt, delayMs)

private fun cancelled(sequence: Long) = Cancelled(sequence, Cancelled.Reason.CANCELLED)

private fun succeeded(sequence: Long) = Ended(sequence, Ended.Outcome.SUCCESS)

// The retry events of a first attempt that is scheduled, fires, starts, and commits.
private fun committedAttempt(sequence: Long): List<RecoveryEvent> =
    listOf(scheduled(sequence), Fired(sequence), Started(sequence), succeeded(sequence))

// The harness actions whose names start with [prefix], as "name detail".
private fun LockoutHarness.actionsStartingWith(prefix: String): List<String> =
    ledger.snapshot()
        .filterIsInstance<LedgerEvent.Action>()
        .filter { it.name.startsWith(prefix) }
        .map { "${it.name} ${it.detail}" }

// The write events of process 1 in [phase], in ledger order.
private fun LockoutHarness.writeEvents(phase: Phase): List<LedgerEvent.Storage> =
    ledger.storageEvents(1).filter { it.op == StorageOp.WRITE && it.phase == phase }

// The payloads that reached durable storage in process 1, in commit order.
private fun LockoutHarness.durableCommits(): List<LockoutSnapshot?> = writeEvents(Phase.DURABLE_COMMIT).map { it.value }

// The payloads of the writes of process 1, in the order in which they began.
private fun LockoutHarness.writePayloads(): List<LockoutSnapshot?> = writeEvents(Phase.BEGIN).map { it.value }

/**
 * The loss window in process 1, checked against [expected]: from the end of write [startWrite] to the first commit of
 * [stored] after it. Write [startWrite] is the first failed write that leaves the fallback outside storage, or the
 * count commit that replaces a stored deadline after an admission during a retry write. A later failure or a new chain
 * does not restart the window. Without such a commit the window stays open, and the value is [NOT_CLOSED].
 */
private fun LockoutHarness.checkedLossWindowMs(
    expected: Any,
    startWrite: Int = 0,
    stored: LockoutSnapshot = FIRST_FALLBACK,
): Any {
    val writes = ledger.storageEvents(1).filter { it.op == StorageOp.WRITE }
    val start = writes.first { it.index == startWrite && (it.phase == Phase.RETURNED || it.phase == Phase.THREW) }
    val end = writes.firstOrNull { it.seq > start.seq && it.phase == Phase.DURABLE_COMMIT && it.value == stored }
    val window: Any = if (end == null) NOT_CLOSED else end.elapsedMs - start.elapsedMs
    assertEquals("the loss window from write #$startWrite to $stored", expected, window)
    return window
}
