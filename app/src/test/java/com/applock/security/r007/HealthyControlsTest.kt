package com.applock.security.r007

import com.applock.security.LockoutManager.FailureOutcome
import com.applock.security.LockoutSnapshot
import com.applock.security.LockoutState
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

/** P2 baseline, controls H01 to H06 (test plan §4): manager-level runs with healthy storage. */
class HealthyControlsTest : BaselineCase() {

    @Test
    fun `H01 - polls of a healthy zero store read once and write nothing`() {
        val harness = harness(Z)
        harness.start()
        repeat(POLLS) { assertEquals(Available, harness.poll()) }
        harness.check()
        assertEquals("the construction read only", 1, harness.store.readCount(1))
        assertEquals("no write without an admission", 0, harness.store.writeCount(1))
        assertFalse(harness.manager.seedReadFailed())
        harness.record(
            "H01",
            "polls" to POLLS,
            "reads" to harness.store.readCount(1),
            "writes" to harness.store.writeCount(1),
        )
    }

    @Test
    fun `H02 - the fifth wrong PIN locks at once and the gate blocks the sixth before verification`() {
        val harness = harness(Z, FaultPlan().write(WriteScript.HoldBeforeCommit(), generation = 1, index = 4))
        harness.start()
        val caller = GatedCaller(harness)
        (1..4).forEach { count ->
            assertTrue(caller.wrongPin())
            harness.check()
            assertEquals(LockoutSnapshot(count, 0L), harness.store.durableState())
        }

        assertTrue("the fifth wrong PIN reaches verification", caller.wrongPin())
        harness.check()
        assertEquals("the fallback until the commit", LockedOut(T, degraded = true), caller.failures[4].immediate)
        assertEquals(C4, harness.store.durableState())
        assertFalse("blocked while the threshold write is held", caller.wrongPin())

        harness.release(StorageOp.WRITE, 4)
        harness.check()
        assertEquals(L5, harness.store.durableState())
        assertEquals(LockedOut(T, degraded = false), harness.manager.currentState())
        assertFalse("blocked by the recorded lockout", caller.wrongPin())

        val outcomes = caller.failures.map { runBlocking { it.resolved.await() } }
        val expected = (1..4).map { FailureOutcome(Available, it) } + FailureOutcome(LockedOut(T, degraded = false), 5)
        assertEquals("each failure reports its own count", expected, outcomes)
        // The legacy LOCKOUT_TRIGGERED audit fires only for a recorded lockout outcome (ApplicationLockEngine).
        val recordedLockouts = outcomes.count { (it.state as? LockedOut)?.degraded == false }
        assertEquals(1, recordedLockouts)
        assertEquals(5, caller.verifierEntries)
        assertEquals(2, caller.blocked)
        assertEquals(5, harness.manager.failureCount())
        harness.record("H02", "verifier_entries" to 5, "blocked" to 2, "recorded_lockout_outcomes" to recordedLockouts)
    }

    @Test
    fun `H03 - a recorded lockout ends at its deadline and the ladder doubles to the cap`() {
        val harness = harness(L5)
        harness.start()
        val caller = GatedCaller(harness)
        harness.advance(T - 1)
        assertEquals(LockedOut(1L, degraded = false), harness.poll())
        harness.advance(1)
        assertEquals(Available, harness.poll())
        harness.advance(1)
        assertEquals(Available, harness.poll())
        assertEquals("the expiry keeps the count", 5, harness.manager.failureCount())

        val windows = listOf(60_000L, 120_000L, 240_000L, 480_000L, 960_000L, MAX, MAX)
        windows.forEach { window ->
            assertTrue(caller.wrongPin())
            harness.check()
            assertEquals(LockedOut(window, degraded = false), harness.manager.currentState())
            harness.advance(window - 1)
            assertFalse("blocked 1 ms before the deadline", caller.wrongPin())
            harness.advance(1)
            assertEquals(Available, harness.poll())
        }
        harness.check()
        assertEquals(5 + windows.size, harness.manager.failureCount())
        assertEquals("one write per failure, none per tick", windows.size, harness.store.writeCount(1))
        harness.record("H03", "windows_ms" to windows, "writes" to windows.size, "blocked" to caller.blocked)
    }

    @Test
    fun `H04 - a correct PIN clears memory at once and the durable clear survives a restart`() {
        val harness = harness(C4, FaultPlan().write(WriteScript.HoldBeforeCommit(), generation = 1, index = 0))
        harness.start()
        val caller = GatedCaller(harness)
        assertTrue(caller.correctPin())
        harness.check()
        assertEquals("the reset applies before its commit", 0, harness.manager.failureCount())
        assertEquals(C4, harness.store.durableState())

        harness.release(StorageOp.WRITE, 0)
        harness.check()
        assertEquals(Z, harness.store.durableState())
        harness.restart()
        harness.check()
        assertEquals(0, harness.manager.failureCount())
        assertEquals(Available, harness.manager.currentState())
        assertTrue("no failure outcome", caller.failures.isEmpty())
        harness.record("H04", "durable_after_restart" to harness.store.durableState())
    }

    @Test
    fun `H05 - a restart and a reboot reload the last committed pair`() {
        val rows = listOf(
            RestartRow("count1-restart", LockoutSnapshot(1, 0L), 0L, null, Available),
            RestartRow("count4-restart", C4, 0L, null, Available),
            RestartRow("L5-active-restart", L5, 10_000L, null, LockedOut(20_000L, degraded = false)),
            RestartRow("L5-expired-restart", L5, STALL_MS, null, Available),
            RestartRow("L8-active-reboot", L8, 10_000L, 60_000L, LockedOut(170_000L, degraded = false)),
            RestartRow("L5-expired-reboot", L5, 10_000L, 60_000L, Available),
        )
        rows.forEach { row ->
            val harness = harness(row.initial)
            harness.start()
            harness.advance(row.afterMs)
            if (row.rebootDowntimeMs == null) harness.restart() else harness.reboot(row.rebootDowntimeMs)
            harness.check()
            assertEquals(row.name, row.expected, harness.manager.currentState())
            assertEquals(row.name, row.initial.failureCount, harness.manager.failureCount())
            assertEquals(row.name, 2, harness.generation)
            harness.record(
                "H05-${row.name}",
                "boot" to harness.clocks.bootCount(),
                "state" to harness.manager.currentState(),
            )
        }
    }

    @Test
    fun `H06 - slow healthy writes keep the admission order and the last admitted pair is durable`() {
        listOf(0L, 10L, 100L, 1_000L).forEach { delayMs ->
            slowWrite(SlowKind.BELOW, delayMs, LockoutSnapshot(2, 0L))
            slowWrite(SlowKind.THRESHOLD, delayMs, LockoutSnapshot(6, W0 + 60_000L))
            slowWrite(SlowKind.RESET, delayMs, LockoutSnapshot(1, 0L))
        }
    }

    // One H06 run: the first operation's write is held for [delayMs] while the state is read and a failure is admitted
    // behind it. Then the storage drains in admission order.
    private fun slowWrite(kind: SlowKind, delayMs: Long, finalPair: LockoutSnapshot) {
        val label = "$kind delay=$delayMs"
        val initial = if (kind == SlowKind.BELOW) Z else C4
        val harness = harness(initial, FaultPlan().write(WriteScript.HoldBeforeCommit(), generation = 1, index = 0))
        harness.start()
        if (kind == SlowKind.RESET) harness.succeed() else harness.fail()
        val heldState = harness.manager.currentState()
        repeat(POLLS_WHILE_HELD) { assertEquals(label, heldState, harness.poll()) }
        harness.fail()
        harness.check()
        assertEquals("$label: nothing is durable while the write is held", initial, harness.store.durableState())
        assertEquals("$label: the second write waits", 1, harness.store.writeCount(1))

        harness.advance(delayMs)
        harness.release(StorageOp.WRITE, 0)
        harness.check()
        assertEquals("$label: the last admitted pair is durable", finalPair, harness.store.durableState())
        harness.record("H06-$kind-$delayMs", "held_state" to heldState, "durable" to harness.store.durableState())
    }

    private enum class SlowKind { BELOW, THRESHOLD, RESET }

    private class RestartRow(
        val name: String,
        val initial: LockoutSnapshot,
        val afterMs: Long,
        val rebootDowntimeMs: Long?,
        val expected: LockoutState,
    )

    private companion object {
        const val POLLS = 1_000
        const val POLLS_WHILE_HELD = 10
    }
}
