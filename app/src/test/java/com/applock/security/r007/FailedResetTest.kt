package com.applock.security.r007

import com.applock.security.LockoutSnapshot
import com.applock.security.LockoutState.Available
import com.applock.security.LockoutState.LockedOut
import com.applock.security.harness.FaultPlan
import com.applock.security.harness.GatedCaller
import com.applock.security.harness.LockoutHarness
import com.applock.security.harness.StorageOp
import com.applock.security.harness.WriteScript
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P2 baseline, residual R-007/4 (test plan §8): a failed reset brings stale enforcement back on restart, R4.1 to R4.4.
 * A reset over an active lockout is manager-only: a gated caller cannot accept a PIN while locked.
 */
class FailedResetTest : BaselineCase() {

    // ---- R4.1 a failed clear and a stale pair on restart ----------------------------------------

    @Test
    fun `R4_1 - a failed clear keeps the old lockout in storage and a restart enforces it again`() {
        listOf(WriteScript.ReturnFalse, WriteScript.Throw, WriteScript.HoldBeforeCommit()).forEach { script ->
            val harness = harness(L5, FaultPlan().write(script, generation = 1, index = 0))
            harness.start()
            assertEquals(LockedOut(T, degraded = false), harness.poll())
            val reset = harness.succeed()
            harness.check()
            assertEquals("$script: memory clears at once", Available, harness.manager.currentState())
            assertEquals(0, harness.manager.failureCount())
            if (script !is WriteScript.HoldBeforeCommit) {
                assertEquals("$script: the clear reports its failure", false, runBlocking { reset.resolved.await() })
            }
            repeat(POLLS_AFTER_RESET) {
                assertEquals("$script: no re-seed undoes the reset", Available, harness.poll())
            }
            assertEquals(1, harness.store.readCount(1))

            harness.advance(5_000L)
            harness.restart()
            harness.check()
            assertEquals("$script", L5, harness.store.durableState())
            val staleLock = LockedOut(T - 5_000L, degraded = false)
            assertEquals("$script: the old lockout is back", staleLock, harness.manager.currentState())
            harness.record("R4.1-$script", "stale_lock_ms" to T - 5_000L, "stale_count" to 5)
        }
    }

    @Test
    fun `R4_1 - after the stale deadline ends, the old count makes the next wrong PIN lock at once`() {
        val harness = harness(L5, FaultPlan().write(WriteScript.ReturnFalse, generation = 1, index = 0))
        harness.start()
        harness.succeed()
        harness.advance(STALL_MS)
        harness.restart()
        harness.check()
        assertEquals(Available, harness.manager.currentState())
        assertEquals(5, harness.manager.failureCount())

        val caller = GatedCaller(harness)
        assertEquals("one wrong PIN locks", 1, caller.wrongPinsUntilBlocked())
        harness.check()
        assertEquals(LockedOut(60_000L, degraded = false), harness.manager.currentState())
        harness.record("R4.1-expired", "entries_before_lock" to 1, "lock_ms" to 60_000L)
    }

    @Test
    fun `R4_1 - through the gate a failed clear of count 4 makes the first wrong PIN after a restart lock`() {
        val harness = harness(C4, FaultPlan().write(WriteScript.ReturnFalse, generation = 1, index = 0))
        harness.start()
        val caller = GatedCaller(harness)
        assertTrue(caller.correctPin())
        harness.check()
        assertEquals(0, harness.manager.failureCount())
        assertEquals(C4, harness.store.durableState())

        harness.restart()
        harness.check()
        assertEquals(4, harness.manager.failureCount())
        assertEquals("one wrong PIN locks", 1, caller.wrongPinsUntilBlocked())
        assertEquals(LockedOut(T, degraded = false), harness.manager.currentState())
        harness.record("R4.1-caller-C4", "entries_before_lock" to 1)
    }

    // ---- R4.2 a transient clear failure --------------------------------------------------------

    @Test
    fun `R4_2 - a failed clear is never retried, so the stale pair stays until the next committed write`() {
        val harness = harness(L5, FaultPlan().write(WriteScript.ReturnFalse, generation = 1, index = 0))
        harness.start()
        harness.succeed()
        harness.advance(1_000L) // the storage is healthy from here: only write 0 fails
        harness.advance(4_000L)
        harness.check()
        assertEquals("no retry", 1, harness.store.writeCount(1))
        assertEquals(L5, harness.store.durableState())

        harness.restart()
        harness.check()
        assertEquals(LockedOut(T - 5_000L, degraded = false), harness.manager.currentState())
        harness.advance(T - 5_000L)
        assertTrue(GatedCaller(harness).correctPin())
        harness.check()
        assertEquals("the next committed write clears it", Z, harness.store.durableState())
        harness.record("R4.2", "retries" to 0, "cleared_by" to "the next accepted success")
    }

    // ---- R4.3 the baseline order of a failed clear and later failures --------------------------

    @Test
    fun `R4_3 - after a failed clear the next failure write of the new streak replaces the stale pair`() {
        val harness = harness(L5, FaultPlan().write(WriteScript.ReturnFalse, generation = 1, index = 0))
        harness.start()
        harness.succeed()
        harness.fail()
        harness.check()
        assertEquals(LockoutSnapshot(1, 0L), harness.store.durableState())

        harness.restart()
        harness.check()
        assertEquals(1, harness.manager.failureCount())
        assertEquals(Available, harness.manager.currentState())
        harness.record("R4.3-next-failure", "durable" to harness.store.durableState())
    }

    @Test
    fun `R4_3 - a failure queued behind a held failing clear commits after it`() {
        val heldThenFalse = WriteScript.HoldBeforeCommit(WriteScript.ReturnFalse)
        val harness = harness(L5, FaultPlan().write(heldThenFalse, generation = 1, index = 0))
        harness.start()
        harness.succeed()
        harness.fail()
        harness.check()
        assertEquals(L5, harness.store.durableState())

        harness.release(StorageOp.WRITE, 0)
        harness.check()
        assertEquals(LockoutSnapshot(1, 0L), harness.store.durableState())
        assertEquals(Available, harness.manager.currentState())
        harness.record("R4.3-queued-failure", "durable" to harness.store.durableState())
    }

    // ---- R4.4 a persistent clear failure and repeated restarts ---------------------------------

    @Test
    fun `R4_4 - under persistent write faults the stored lockout and count return on each restart`() {
        listOf(WriteScript.ReturnFalse, WriteScript.Throw).forEach { script ->
            val harness = harness(L5, FaultPlan().write(script))
            harness.start()
            val caller = GatedCaller(harness)
            val firstWaitMs = harness.waitThenCorrectPin(caller, "$script first process")
            val restartWaitsMs = (1..CYCLES).map { cycle ->
                harness.restart()
                assertEquals("$script cycle $cycle: the stale count returns", 5, harness.manager.failureCount())
                harness.waitThenCorrectPin(caller, "$script cycle $cycle")
            }
            harness.check()
            assertEquals("$script: the first process waits for the stale window", T, firstWaitMs)
            assertEquals("$script: the expired stale window denies nothing", List(CYCLES) { 0L }, restartWaitsMs)
            assertEquals(L5, harness.store.durableState())

            harness.restart()
            assertEquals("$script: the stale count returns", 5, harness.manager.failureCount())
            assertEquals("$script: one wrong PIN locks", 1, caller.wrongPinsUntilBlocked())
            harness.check()
            assertEquals(LockedOut(60_000L, degraded = true), harness.manager.currentState())
            harness.record(
                "R4.4-$script",
                "first_wait_ms" to firstWaitMs,
                "restart_waits_ms" to restartWaitsMs,
                "restarts_with_stale_count" to CYCLES + 1,
            )
        }
    }

    // ---- Helpers -------------------------------------------------------------------------------

    // Waits until the lockout ends, then enters the correct PIN, whose clear fails. Returns the wait.
    private fun LockoutHarness.waitThenCorrectPin(caller: GatedCaller, label: String): Long {
        val waitMs = (poll() as? LockedOut)?.remainingMs ?: 0L
        advance(waitMs)
        assertTrue(label, caller.correctPin())
        assertEquals(label, 0, manager.failureCount())
        return waitMs
    }

    private companion object {
        const val POLLS_AFTER_RESET = 10
    }
}
