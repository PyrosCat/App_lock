package com.applock.security.r007

import com.applock.security.LockoutManager.FailureOutcome
import com.applock.security.LockoutSnapshot
import com.applock.security.LockoutState.Available
import com.applock.security.LockoutState.LockedOut
import com.applock.security.harness.FaultPlan
import com.applock.security.harness.GatedCaller
import com.applock.security.harness.StorageOp
import com.applock.security.harness.WriteScript
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P2 baseline, residual R-007/2 (test plan §6): in-memory degraded enforcement lost on restart, R2.1 to R2.4. */
class DegradedRestartTest : BaselineCase() {

    // ---- R2.1 below-threshold failure, then death ----------------------------------------------

    // B2 spec: T2, T10, T19. B3 spec: D1, D4, D7, D13, D14.
    @Test
    fun `R2_1 - after the retry commits a restart keeps the fallback remainder, and a death before it loses it`() {
        listOf(WriteScript.ReturnFalse, WriteScript.Throw).forEach { script ->
            listOf(0L, 5_000L, T - 1).forEach { deathMs -> restartAfterFailedWrite(script, deathMs, reboot = false) }
            restartAfterFailedWrite(script, 5_000L, reboot = true)
        }
    }

    private fun restartAfterFailedWrite(script: WriteScript, deathMs: Long, reboot: Boolean) {
        val label = "$script death=$deathMs reboot=$reboot"
        val harness = harness(Z, FaultPlan().write(script, generation = 1, index = 0))
        harness.start()
        val caller = GatedCaller(harness)
        assertTrue("$label: the first wrong PIN reaches verification", caller.wrongPin())
        harness.check()
        val outcome = runBlocking { caller.failures[0].resolved.await() }
        assertEquals("$label: the outcome is degraded", FailureOutcome(LockedOut(T, degraded = true), 1), outcome)

        harness.advance(deathMs)
        // Only write 0 fails, so the first retry, 1 s after the failed write, stores the count and the fallback.
        val retried = deathMs >= FIRST_RETRY_DELAY_MS
        val beforeDeath = LockedOut(T - deathMs, degraded = !retried)
        assertEquals("$label: degraded only before the retry commit", beforeDeath, harness.manager.currentState())
        assertFalse("$label: blocked in the same process", caller.wrongPin())

        if (reboot) harness.reboot(REBOOT_DOWNTIME_MS) else harness.restart()
        harness.check()
        val durable = if (retried) LockoutSnapshot(1, W0 + T) else Z
        assertEquals("$label: only a retry commit stores the fallback", durable, harness.store.durableState())
        assertEquals("$label: the restart loads the stored count", durable.failureCount, harness.manager.failureCount())
        val downtimeMs = if (reboot) REBOOT_DOWNTIME_MS else 0L
        val afterRestart = if (retried) LockedOut(T - deathMs - downtimeMs, degraded = false) else Available
        assertEquals("$label: the restart enforces a stored fallback", afterRestart, harness.manager.currentState())
        assertEquals("$label: the gate opens only when no fallback is stored", !retried, caller.wrongPin())
        harness.record(
            "R2.1-$script-death$deathMs-${if (reboot) "reboot" else "restart"}",
            "lost_enforcement_ms" to if (retried) 0L else T - deathMs,
            "verifier_entries" to caller.verifierEntries,
            "durable_count" to durable.failureCount,
        )
    }

    // ---- R2.2 a later healthy count commit starts a retry that stores the fallback ------------

    // B3 spec: D2, D4, D7, D14.
    @Test
    fun `R2_2 - a later committed count starts a retry that stores the earlier fallback`() {
        val harness = harness(Z, FaultPlan().write(HELD_FALSE, generation = 1, index = 0))
        harness.start()
        val firstFailure = harness.fail()
        val secondFailure = harness.fail()
        harness.release(StorageOp.WRITE, 0)
        harness.check()
        assertEquals(LockoutSnapshot(2, 0L), harness.store.durableState())
        assertEquals("global state is degraded from F1", LockedOut(T, degraded = true), harness.manager.currentState())
        assertEquals(FailureOutcome(LockedOut(T, degraded = true), 1), runBlocking { firstFailure.resolved.await() })
        val secondOutcome = runBlocking { secondFailure.resolved.await() }
        assertEquals("F2 has its own outcome", FailureOutcome(Available, 2), secondOutcome)

        harness.advance(10_000L)
        harness.check()
        val storedFallback = LockoutSnapshot(2, W0 + T)
        assertEquals("the retry at 10 s stores the fallback of F1", storedFallback, harness.store.durableState())
        harness.restart()
        harness.check()
        assertEquals(2, harness.manager.failureCount())
        assertEquals(LockedOut(T - 10_000L, degraded = false), harness.manager.currentState())
        harness.record("R2.2", "lost_enforcement_ms" to 0L, "durable" to harness.store.durableState())
    }

    // ---- R2.3 a delayed threshold commit older than the fallback --------------------------------

    @Test
    fun `R2_3 - a threshold commit whose deadline elapsed leaves a degraded lockout that a restart drops`() {
        val initial = LockoutSnapshot(3, 0L)
        val harness = harness(initial, FaultPlan().write(HELD_FALSE, generation = 1, index = 0))
        harness.start()
        val fourthFailure = harness.fail()
        val fifthFailure = harness.fail()
        assertEquals(LockedOut(T, degraded = true), fifthFailure.immediate)

        harness.advance(STALL_MS)
        harness.release(StorageOp.WRITE, 0)
        harness.check()
        assertEquals(LockoutSnapshot(5, W0 + T), harness.store.durableState())
        assertEquals("fallback from F4's completion", LockedOut(T, degraded = true), harness.manager.currentState())
        assertEquals(FailureOutcome(LockedOut(T, degraded = true), 4), runBlocking { fourthFailure.resolved.await() })
        assertEquals(FailureOutcome(LockedOut(T, degraded = false), 5), runBlocking { fifthFailure.resolved.await() })

        harness.restart()
        harness.check()
        assertEquals(5, harness.manager.failureCount())
        assertEquals("the stored deadline has passed", Available, harness.manager.currentState())
        harness.record("R2.3", "lost_enforcement_ms" to T, "durable" to harness.store.durableState())
    }

    // ---- R2.4 persistent faults, expiry, and repeated interruption -----------------------------

    @Test
    fun `R2_4 - under persistent write faults the fallback ends at its deadline and any death clears it`() {
        listOf(-1L, 0L, 1L).forEach { offsetMs ->
            val harness = harness(Z, FaultPlan().write(WriteScript.ReturnFalse))
            harness.start()
            harness.fail()
            harness.advance(T + offsetMs)
            harness.check()
            val expected = if (offsetMs < 0) LockedOut(-offsetMs, degraded = true) else Available
            assertEquals("f$offsetMs", expected, harness.manager.currentState())

            harness.restart()
            harness.check()
            assertEquals("f$offsetMs", Available, harness.manager.currentState())
            assertEquals("f$offsetMs", 0, harness.manager.failureCount())
            assertEquals("f$offsetMs", Z, harness.store.durableState())
            harness.record("R2.4-expiry-f$offsetMs", "state_before_death" to expected)
        }
    }

    @Test
    fun `R2_4 - under persistent write faults each restart gives one verified guess and the count never climbs`() {
        listOf(WriteScript.ReturnFalse, WriteScript.Throw).forEach { script ->
            val harness = harness(Z, FaultPlan().write(script))
            harness.start()
            val caller = GatedCaller(harness)
            val entries = caller.entriesAcrossRestarts(harness)
            harness.check()
            assertEquals("$script", RestartEntries(1, List(CYCLES) { 1 }), entries)
            assertEquals("$script", entries.total, caller.verifierEntries)
            assertEquals("$script", Z, harness.store.durableState())
            assertEquals("$script", 1, harness.manager.failureCount())
            harness.record("R2.4-restart-cycles-$script", "entries" to entries, "total" to entries.total)
        }
    }

    private companion object {
        const val REBOOT_DOWNTIME_MS = 1_000L

        /** The delay of the first retry of a failed write. */
        const val FIRST_RETRY_DELAY_MS = 1_000L

        /** A write held before its commit that then returns false. */
        val HELD_FALSE = WriteScript.HoldBeforeCommit(WriteScript.ReturnFalse)
    }
}
