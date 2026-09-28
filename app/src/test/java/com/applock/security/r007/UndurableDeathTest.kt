package com.applock.security.r007

import com.applock.security.LockoutManager
import com.applock.security.LockoutManager.FailureOutcome
import com.applock.security.LockoutSnapshot
import com.applock.security.LockoutState
import com.applock.security.LockoutState.Available
import com.applock.security.LockoutState.LockedOut
import com.applock.security.harness.FaultPlan
import com.applock.security.harness.GatedCaller
import com.applock.security.harness.LockoutHarness
import com.applock.security.harness.StorageOp
import com.applock.security.harness.WriteScript
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * P2 baseline, residual R-007/3 (test plan §7): death before admitted mutations become durable, R3.1 to R3.4. Cut (c)
 * of R3.1, a death inside the platform commit, is a device case: the JVM store commits in one step.
 */
class UndurableDeathTest : BaselineCase() {

    // ---- R3.1 cuts around one wrong PIN, from Z and from C4 ------------------------------------

    @Test
    fun `R3_1a - a death after verification and before admission loses the verified failure`() {
        listOf(Z, C4).forEach { initial ->
            val harness = harness(initial)
            harness.start()
            // The gate opens and the verifier runs; the process dies before the failure is submitted.
            assertEquals(Available, harness.poll())
            harness.restart()
            harness.check()
            assertEquals(initial, harness.store.durableState())
            assertEquals(initial.failureCount, harness.manager.failureCount())
            harness.record("R3.1a-${initial.failureCount}", "lost_verified_failures" to 1)
        }
    }

    @Test
    fun `R3_1b - a write that waits in the queue at death is lost, and the restart opens the gate at once`() {
        listOf(Z, C4).forEach { initial ->
            val harness = harness(initial)
            harness.start()
            harness.holdIo()
            deathBeforeCommit(harness, initial, "R3.1b")
            assertTrue("the queued write was dropped", harness.ledger.trace().any { "DROPPED 1 queued" in it })
        }
    }

    @Test
    fun `R3_1 - a write held before its commit at death is lost, and the restart opens the gate at once`() {
        listOf(Z, C4).forEach { initial ->
            val harness = harness(initial, FaultPlan().write(WriteScript.HoldBeforeCommit(), generation = 1, index = 0))
            harness.start()
            deathBeforeCommit(harness, initial, "R3.1-held")
        }
    }

    // One wrong PIN whose write cannot reach storage, then a death and a restart. The immediate state enforces from
    // memory; after the restart the old pair governs and the gate opens again.
    private fun deathBeforeCommit(harness: LockoutHarness, initial: LockoutSnapshot, case: String) {
        val caller = GatedCaller(harness)
        assertTrue(caller.wrongPin())
        harness.check()
        val immediate = caller.failures[0].immediate
        val expectedImmediate = if (initial == C4) LockedOut(T, degraded = true) else Available
        assertEquals(case, expectedImmediate, immediate)
        assertEquals(case, initial, harness.store.durableState())

        harness.restart()
        harness.check()
        assertEquals(case, initial, harness.store.durableState())
        assertEquals(case, initial.failureCount, harness.manager.failureCount())
        assertTrue("$case: the restart opens the gate", caller.wrongPin())
        harness.record("$case-${initial.failureCount}", "immediate" to immediate, "lost_verified_failures" to 1)
    }

    @Test
    fun `R3_1d - a death after the commit and before its return keeps the new pair`() {
        listOf(Z to LockoutSnapshot(1, 0L), C4 to L5).forEach { (initial, committed) ->
            val harness = harness(initial, FaultPlan().write(WriteScript.CommitThenHold, generation = 1, index = 0))
            harness.start()
            val failure = harness.fail()
            harness.check()
            assertFalse("unresolved at death", failure.resolved.isCompleted)
            assertEquals(committed, harness.store.durableState())

            harness.restart()
            harness.check()
            assertEquals(committed, harness.store.durableState())
            assertEquals(committed.failureCount, harness.manager.failureCount())
            val expected = if (initial == C4) LockedOut(T, degraded = false) else Available
            assertEquals(expected, harness.manager.currentState())
            harness.record("R3.1d-${initial.failureCount}", "durable" to committed, "lost_verified_failures" to 0)
        }
    }

    @Test
    fun `R3_1e - a death in the completion callback keeps the commit and loses the callback effects`() {
        listOf(Z to LockoutSnapshot(1, 0L), C4 to L5).forEach { (initial, committed) ->
            val harness = harness(initial)
            harness.start()
            val durableInCallback = AtomicReference<LockoutSnapshot>()
            val countInCallback = AtomicInteger(-1)
            // The effects stand for the audit and the capture that a legacy caller starts from its callback.
            val effects = AtomicInteger()
            val callback = harness.parkedCallback<FailureOutcome>(
                "audit",
                before = { outcome ->
                    durableInCallback.set(harness.store.durableState())
                    countInCallback.set(outcome.count)
                },
                after = { effects.incrementAndGet() },
            )
            val failure = harness.fail(callback)
            harness.check()
            assertEquals("the pair is durable when the callback starts", committed, durableInCallback.get())
            assertEquals(committed.failureCount, countInCallback.get())
            assertFalse("the callback has not returned", failure.resolved.isCompleted)

            harness.restart()
            harness.check()
            val callbackKilled = harness.ledger.trace().any { "CALLBACK_KILLED audit" in it }
            assertTrue("the kill ended the parked callback", callbackKilled)
            assertEquals("no callback effect", 0, effects.get())
            assertEquals(committed, harness.store.durableState())
            assertEquals(committed.failureCount, harness.manager.failureCount())
            val expected = if (initial == C4) LockedOut(T, degraded = false) else Available
            assertEquals(expected, harness.manager.currentState())
            harness.record("R3.1e-${initial.failureCount}", "durable_in_callback" to committed, "callback_effects" to 0)
        }
    }

    // ---- R3.2 queued mutations and a lost reset ------------------------------------------------

    @Test
    fun `R3_2 - a death at each queue boundary leaves exactly the committed prefix`() {
        val prefixes = listOf(LockoutSnapshot(2, 0L), LockoutSnapshot(3, 0L), C4, Z, LockoutSnapshot(1, 0L))
        prefixes.forEachIndexed { commits, expected ->
            val harness = queueDeath(LockoutSnapshot(2, 0L), commits)
            assertEquals("after $commits commits", expected, harness.store.durableState())
            assertEquals(Available, harness.manager.currentState())
            harness.record("R3.2-below-$commits", "durable" to expected)
        }
    }

    @Test
    fun `R3_2 - with a threshold write in the queue a death can bring a lockout back after an accepted reset`() {
        val prefixes = listOf(LockoutSnapshot(3, 0L), C4, L5, Z, LockoutSnapshot(1, 0L))
        prefixes.forEachIndexed { commits, expected ->
            val harness = queueDeath(LockoutSnapshot(3, 0L), commits)
            assertEquals("after $commits commits", expected, harness.store.durableState())
            val state = harness.manager.currentState()
            val expectedState = if (expected == L5) LockedOut(T, degraded = false) else Available
            assertEquals("after $commits commits", expectedState, state)
            harness.record("R3.2-threshold-$commits", "durable" to expected, "state_after_restart" to state)
        }
    }

    // Admits F, F, reset, F with every write of the first process held; releases [commits] of them in order; then
    // kills and restarts. Memory ends at count 1 before the death.
    private fun queueDeath(initial: LockoutSnapshot, commits: Int): LockoutHarness {
        val harness = harness(initial, FaultPlan().write(WriteScript.HoldBeforeCommit(), generation = 1))
        harness.start()
        harness.fail()
        harness.fail()
        harness.succeed()
        harness.fail()
        assertEquals(1, harness.manager.failureCount())
        repeat(commits) { index -> harness.release(StorageOp.WRITE, index) }
        harness.check()
        harness.restart()
        harness.check()
        assertEquals(harness.store.durableState().failureCount, harness.manager.failureCount())
        return harness
    }

    // ---- R3.3 a stalled write, continued admission, and shutdown --------------------------------

    @Test
    fun `R3_3 - while the first write stalls, admissions go on and enforce from memory only`() {
        listOf(WriteScript.Normal, WriteScript.ReturnFalse).forEach { then ->
            val harness = harness(Z, FaultPlan().write(WriteScript.HoldBeforeCommit(then), generation = 1, index = 0))
            harness.start()
            val caller = GatedCaller(harness)
            assertEquals("$then: four counts and the threshold", 5, caller.wrongPinsUntilBlocked())
            harness.check()
            assertEquals("$then: only the first write started", 1, harness.store.writeCount(1))
            assertEquals(Z, harness.store.durableState())

            harness.advance(T + 1) // the threshold fallback ends while the storage still stalls
            assertEquals("$then: one more guess, then 60 s", 1, caller.wrongPinsUntilBlocked())
            harness.check()
            assertEquals(LockedOut(60_000L, degraded = true), harness.manager.currentState())

            harness.advance(STALL_MS - T - 1)
            harness.release(StorageOp.WRITE, 0)
            harness.check()
            assertEquals(6, harness.store.writeCount(1))
            assertEquals(LockoutSnapshot(6, W0 + T + 1 + 60_000L), harness.store.durableState())
            harness.record(
                "R3.3-stall-$then",
                "entries_while_stalled" to caller.verifierEntries,
                "queued_max" to 5,
                "state_after_drain" to harness.manager.currentState(),
            )
        }
    }

    @Test
    fun `R3_3 - a reset behind a stalled failure write clears memory, and the old failure cannot re-arm`() {
        val heldThenFalse = WriteScript.HoldBeforeCommit(WriteScript.ReturnFalse)
        val harness = harness(Z, FaultPlan().write(heldThenFalse, generation = 1, index = 0))
        harness.start()
        harness.fail()
        val reset = harness.succeed()
        harness.check()
        assertEquals(Available, reset.immediate)
        assertEquals(0, harness.manager.failureCount())

        harness.advance(STALL_MS)
        harness.release(StorageOp.WRITE, 0)
        harness.check()
        assertEquals("the old-streak failure arms no fallback", Available, harness.manager.currentState())
        assertEquals(Z, harness.store.durableState())
        harness.record("R3.3-reset-behind-stall", "state" to harness.manager.currentState())
    }

    @Test
    fun `R3_3 - after shutdown an accepted write still drains, and nothing is published or called back`() {
        val harness = harness(Z, FaultPlan().write(WriteScript.HoldBeforeCommit(), generation = 1, index = 0))
        harness.start()
        val callbacks = AtomicInteger()
        val failure = harness.fail { callbacks.incrementAndGet() }
        harness.shutdown()
        val afterStop = harness.manager.submitFailure { callbacks.incrementAndGet() }
        assertTrue("a synthetic result", afterStop.resolved.isCompleted)
        assertEquals(FailureOutcome(Available, 1), runBlocking { afterStop.resolved.await() })

        harness.release(StorageOp.WRITE, 0)
        assertEquals("the accepted write reached storage", LockoutSnapshot(1, 0L), harness.store.durableState())
        assertEquals("no write for the stopped submission", 1, harness.store.writeCount(1))
        assertEquals(0, callbacks.get())
        assertEquals(FailureOutcome(Available, 1), runBlocking { failure.resolved.await() })
        assertEquals(1, harness.manager.failureCount())
        harness.record("R3.3-shutdown", "callbacks" to callbacks.get(), "durable" to harness.store.durableState())
    }

    // ---- R3.4 a commit whose acknowledgement is lost -------------------------------------------

    @Test
    fun `R3_4 - a commit whose return is lost at death is read once by each later process`() {
        val cases = listOf(
            AckCase("failure", Z, LockoutSnapshot(1, 0L), Available) { it.fail() },
            AckCase("threshold", C4, L5, LockedOut(T, degraded = false)) { it.fail() },
            AckCase("reset", L5, Z, Available) { it.succeed() }, // manager-only: a gate blocks a PIN while locked
        )
        cases.forEach { case ->
            val faults = FaultPlan().write(WriteScript.CommitThenHold, generation = 1, index = 0)
            val harness = harness(case.initial, faults)
            harness.start()
            case.admit(harness)
            harness.check()
            repeat(2) {
                harness.restart()
                harness.check()
                assertEquals(case.name, case.committed, harness.store.durableState())
                assertEquals(case.name, case.committed.failureCount, harness.manager.failureCount())
                assertEquals(case.name, case.expected, harness.manager.currentState())
                val newProcessWrites = harness.store.writeCount(harness.generation)
                assertEquals("${case.name}: a new process writes nothing", 0, newProcessWrites)
            }
            harness.record("R3.4-lost-return-${case.name}", "durable" to case.committed)
        }
    }

    @Test
    fun `R3_4 - a false acknowledgement after a real commit arms a fallback over a stored count`() {
        val harness = harness(Z, FaultPlan().write(WriteScript.CommitThenReportFalse, generation = 1, index = 0))
        harness.start()
        val failure = harness.fail()
        harness.check()
        assertEquals(FailureOutcome(LockedOut(T, degraded = true), 1), runBlocking { failure.resolved.await() })
        assertEquals(LockoutSnapshot(1, 0L), harness.store.durableState())
        assertEquals("30 s for a stored count 1", LockedOut(T, degraded = true), harness.manager.currentState())

        harness.restart()
        harness.check()
        assertEquals(1, harness.manager.failureCount())
        assertEquals(Available, harness.manager.currentState())
        harness.record("R3.4-false-ack-below", "in_process_over_enforcement_ms" to T)
    }

    @Test
    fun `R3_4 - a false acknowledgement of a threshold write gives a degraded outcome for a durable lockout`() {
        val harness = harness(C4, FaultPlan().write(WriteScript.CommitThenReportFalse, generation = 1, index = 0))
        harness.start()
        val failure = harness.fail()
        harness.check()
        val outcome = runBlocking { failure.resolved.await() }
        val degradedOutcome = FailureOutcome(LockedOut(T, degraded = true), 5)
        assertEquals("no recorded outcome, so no lockout audit", degradedOutcome, outcome)
        assertEquals(L5, harness.store.durableState())

        harness.restart()
        harness.check()
        assertEquals("recorded after the restart", LockedOut(T, degraded = false), harness.manager.currentState())
        harness.record("R3.4-false-ack-threshold", "outcome" to outcome.state)
    }

    @Test
    fun `R3_4 - a false acknowledgement of a reset reports a failed clear that is durable`() {
        val harness = harness(L5, FaultPlan().write(WriteScript.CommitThenReportFalse, generation = 1, index = 0))
        harness.start()
        val reset = harness.succeed() // manager-only
        harness.check()
        assertEquals(false, runBlocking { reset.resolved.await() })
        assertEquals(Z, harness.store.durableState())

        harness.restart()
        harness.check()
        assertEquals(Available, harness.manager.currentState())
        assertEquals(0, harness.manager.failureCount())
        harness.record("R3.4-false-ack-reset", "reported_committed" to false, "durable" to Z)
    }

    private class AckCase(
        val name: String,
        val initial: LockoutSnapshot,
        val committed: LockoutSnapshot,
        val expected: LockoutState,
        val admit: (LockoutHarness) -> LockoutManager.Pending<*>,
    )
}
