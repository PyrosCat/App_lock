package com.applock.security.r007

import com.applock.security.LockoutSnapshot
import com.applock.security.harness.FaultPlan
import com.applock.security.harness.GatedCaller
import com.applock.security.harness.LedgerEvent
import com.applock.security.harness.LockoutHarness
import com.applock.security.harness.LockoutModel
import com.applock.security.harness.StorageOp
import com.applock.security.harness.WriteScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Candidate B3 of phase P3 (`2026-10-07_R007_F2_HARDENING_P3_B3_SPEC.md`), for residual R-007/2: a write retry also
 * stores the remaining in-memory fallback as a wall deadline in the stored pair, so that a restart keeps it. Each case
 * records `deadline_loss_window_ms`: the time from the failed completion, or from the count commit that leaves a
 * fallback outside storage, to the first commit of a converted deadline.
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
            assertEquals("the threshold commit records the lockout", L5, harness.store.durableState())
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

    // The H06 runs of the baseline: a first operation whose write is held while a failure is admitted behind it. In
    // the stale threshold run, F5 commits after F6 was admitted.
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

    // A fault plan that holds write [index] of the first process before its commit.
    private fun heldWrite(index: Int): FaultPlan =
        FaultPlan().write(WriteScript.HoldBeforeCommit(), generation = 1, index = index)

    // The harness actions whose names start with [prefix], as "name detail".
    private fun LockoutHarness.actionsStartingWith(prefix: String): List<String> =
        ledger.snapshot()
            .filterIsInstance<LedgerEvent.Action>()
            .filter { it.name.startsWith(prefix) }
            .map { "${it.name} ${it.detail}" }

    private companion object {
        const val ADMIT_PREFIX = "ADMIT_"
        const val RETRY_PREFIX = "RETRY_"

        /** The lockout windows from the sixth to the twelfth failure: doubling from 60 s up to the 30 min cap. */
        val LADDER_MS = (6..12).map { count -> LockoutModel.ladder(count) }
    }
}
