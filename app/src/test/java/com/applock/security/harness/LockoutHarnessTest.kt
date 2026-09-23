package com.applock.security.harness

import com.applock.security.LockoutManager
import com.applock.security.LockoutSnapshot
import com.applock.security.LockoutState
import com.applock.security.LockoutStorage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validation of the R-007 lockout harness (test plan phase P1). These tests show that the harness separates old,
 * new, and unknown durable state, that a killed process cannot change the store, that faults hit the scripted
 * operation, and that the model detects a wrong durable state and a stale write. They test the harness; the baseline
 * characterization of the manager is phase P2.
 */
class LockoutHarnessTest {

    private val harnesses = mutableListOf<LockoutHarness>()

    @After
    fun tearDown() {
        harnesses.forEach { it.close() }
    }

    private fun harness(
        initial: LockoutSnapshot = ZERO,
        faults: FaultPlan = FaultPlan(),
        decorator: (LockoutStorage) -> LockoutStorage = { it },
    ): LockoutHarness =
        LockoutHarness(initial, faults = faults, storageDecorator = decorator).also { harnesses += it }

    private fun LockoutHarness.storageEvents(generation: Int, op: StorageOp, index: Int): List<Phase> =
        ledger.storageEvents(generation).filter { it.op == op && it.index == index }.map { it.phase }

    // ---- Store fidelity ------------------------------------------------------------------------

    @Test
    fun `a write that fails on disk changes the process cache but not the durable state`() {
        val harness = harness(faults = FaultPlan().write(WriteScript.ReturnFalse, generation = 1, index = 0))
        harness.start()

        harness.fail()
        harness.check()
        assertEquals(LockoutSnapshot(1, 0L), harness.store.cacheOf(1))
        assertEquals(ZERO, harness.store.durableState())

        harness.restart()
        harness.check()
        assertEquals("a fresh process loads the durable state, not the old cache", 0, harness.manager.failureCount())
    }

    @Test
    fun `a throwing write changes neither the cache nor the durable state`() {
        val harness = harness(faults = FaultPlan().write(WriteScript.Throw, generation = 1, index = 0))
        harness.start()

        harness.fail()
        harness.check()
        assertEquals(ZERO, harness.store.cacheOf(1))
        assertEquals(ZERO, harness.store.durableState())
    }

    @Test
    fun `a write parked before its commit never reaches storage when the process dies`() {
        val harness = harness(faults = FaultPlan().write(WriteScript.HoldBeforeCommit(), generation = 1, index = 0))
        harness.start()
        harness.fail()
        harness.awaitHeld(StorageOp.WRITE, 0)
        harness.check()

        harness.restart()
        harness.check()
        assertEquals(ZERO, harness.store.durableState())
        assertTrue(Phase.KILLED_IN_FLIGHT in harness.storageEvents(1, StorageOp.WRITE, 0))
        assertFalse(Phase.DURABLE_COMMIT in harness.storageEvents(1, StorageOp.WRITE, 0))
    }

    @Test
    fun `a write killed after its durable commit survives the restart`() {
        val harness = harness(faults = FaultPlan().write(WriteScript.CommitThenHold, generation = 1, index = 0))
        harness.start()
        harness.fail()
        harness.awaitHeld(StorageOp.WRITE, 0)

        harness.restart()
        harness.check()
        assertEquals(LockoutSnapshot(1, 0L), harness.store.durableState())
        assertEquals(1, harness.manager.failureCount())
    }

    @Test
    fun `an injected acknowledgement ambiguity is durable although the write reported false`() {
        val harness = harness(faults = FaultPlan().write(WriteScript.CommitThenReportFalse, generation = 1, index = 0))
        harness.start()

        val outcome = harness.fail()
        harness.check()
        assertTrue(outcome.resolved.isCompleted)
        assertEquals(LockoutSnapshot(1, 0L), harness.store.durableState())

        harness.restart()
        harness.check()
        assertEquals(1, harness.manager.failureCount())
    }

    @Test
    fun `queued writes of a killed process never start`() {
        val harness = harness(faults = FaultPlan().write(WriteScript.HoldBeforeCommit(), generation = 1, index = 0))
        harness.start()
        repeat(3) { harness.fail() }
        harness.check()

        harness.restart()
        harness.check()
        assertEquals(ZERO, harness.store.durableState())
        assertTrue(harness.storageEvents(1, StorageOp.WRITE, 1).isEmpty())
        assertTrue(harness.storageEvents(1, StorageOp.WRITE, 2).isEmpty())
    }

    @Test
    fun `a dead process cannot change the store`() {
        var firstView: LockoutStorage? = null
        val harness = harness(decorator = { view -> view.also { if (firstView == null) firstView = it } })
        harness.start()
        harness.fail()
        harness.restart()

        assertThrows(ProcessKilledException::class.java) { checkNotNull(firstView).write(LockoutSnapshot(9, 9L)) }
        assertTrue(Phase.REJECTED_DEAD in harness.storageEvents(1, StorageOp.WRITE, 1))
        assertEquals(LockoutSnapshot(1, 0L), harness.store.durableState())
        harness.check()
    }

    @Test
    fun `a persistent fault stays in force across a restart`() {
        val harness = harness(initial = L5, faults = FaultPlan().read(ReadScript.Throw))
        harness.start()
        harness.check()
        assertTrue(harness.manager.seedReadFailed())

        harness.restart()
        harness.check()
        assertTrue("the plan is not reset by a restart", harness.manager.seedReadFailed())
        assertEquals(L5, harness.store.durableState())
    }

    @Test
    fun `writes queue behind a parked write and one writer runs at a time`() {
        val harness = harness(faults = FaultPlan().write(WriteScript.HoldBeforeCommit(), generation = 1, index = 0))
        harness.start()
        repeat(LockoutModel.THRESHOLD) { harness.fail() }
        harness.check()
        assertEquals(1, harness.store.writeCount(1))

        harness.release(StorageOp.WRITE, 0)
        harness.check()
        assertEquals(LockoutModel.THRESHOLD, harness.store.writeCount(1))
        assertEquals(1, harness.store.maxConcurrentWrites())
    }

    @Test
    fun `a re-seed read parked with its value is not applied after a local admission`() {
        val faults = FaultPlan()
            .read(ReadScript.Throw, generation = 1, index = 0)
            .read(ReadScript.ReadThenHold, generation = 1, index = 1)
        val harness = harness(initial = L5, faults = faults)
        harness.start()
        harness.poll()
        harness.awaitHeld(StorageOp.READ, 1)

        harness.fail() // queues its write behind the parked read
        harness.release(StorageOp.READ, 1)
        harness.check()
        assertEquals(1, harness.manager.failureCount())
    }

    @Test
    fun `a fault rule changed while a write is parked does not change that write`() {
        val faults = FaultPlan().write(WriteScript.HoldBeforeCommit(), generation = 1, index = 0)
        val harness = harness(faults = faults)
        harness.start()
        harness.fail()

        faults.write(WriteScript.Throw, generation = 1, index = 0)
        harness.release(StorageOp.WRITE, 0)
        harness.check()
        assertEquals("the write keeps the script it started with", LockoutSnapshot(1, 0L), harness.store.durableState())
    }

    @Test
    fun `a held construction read leaves no manager until it returns`() {
        val faults = FaultPlan().read(ReadScript.HoldThenRead(), generation = 1, index = 0)
        val harness = harness(initial = L5, faults = faults)
        harness.startWithHeldSeed()
        assertThrows(IllegalStateException::class.java) { harness.manager }

        harness.releaseSeed()
        harness.check()
        assertEquals(LockoutModel.THRESHOLD, harness.manager.failureCount())
    }

    // ---- Controls ------------------------------------------------------------------------------

    @Test
    fun `positive control - a healthy threshold commit survives a restart`() {
        val harness = harness()
        harness.start()
        repeat(LockoutModel.THRESHOLD) { harness.fail() }
        harness.check()

        harness.restart()
        harness.check()
        assertEquals(LockoutState.LockedOut(LockoutModel.BASE_MS, degraded = false), harness.manager.currentState())
    }

    @Test
    fun `positive control - a reboot keeps the durable wall deadline`() {
        val harness = harness()
        harness.start()
        repeat(LockoutModel.THRESHOLD) { harness.fail() }

        harness.reboot(downtimeMs = 10_000L)
        harness.check()
        assertEquals(LockoutState.LockedOut(20_000L, degraded = false), harness.manager.currentState())
    }

    @Test
    fun `negative control - the model detects a restart from an unexpected durable state`() {
        val harness = harness()
        harness.start()
        repeat(4) { harness.fail() }
        harness.check()

        harness.store.tamperDurable(harness.generation, ZERO)
        assertThrows(AssertionError::class.java) { harness.restart() }
    }

    @Test
    fun `negative control - the check detects an admitted write that never runs`() {
        val harness = harness(faults = FaultPlan().write(WriteScript.HoldBeforeCommit(), generation = 1, index = 0))
        harness.start()
        harness.fail()
        harness.fail() // queued behind the parked write
        assertEquals(1, harness.dropQueuedWork())

        harness.release(StorageOp.WRITE, 0)
        assertThrows(AssertionError::class.java) { harness.check() }
    }

    @Test
    fun `mutation control - the model detects a stale write payload`() {
        val harness = harness(decorator = ::StaleReplayStorage)
        harness.start()
        harness.fail()
        harness.check()

        assertThrows(AssertionError::class.java) { harness.fail() }
    }

    @Test
    fun `the model constants match the manager constants`() {
        assertEquals(LockoutModel.THRESHOLD, LockoutManager.FAILURE_THRESHOLD)
        assertEquals(LockoutModel.BASE_MS, LockoutManager.BASE_LOCKOUT_MS)
        assertEquals(LockoutModel.MAX_MS, LockoutManager.MAX_LOCKOUT_MS)
        (LockoutModel.THRESHOLD..LADDER_CHECK_LIMIT).forEach { count ->
            assertEquals("ladder at $count", LockoutModel.ladder(count), LockoutManager.lockoutDurationFor(count))
        }
    }

    // ---- Seeded schedules ----------------------------------------------------------------------

    @Test
    fun `a failure inside a seeded action reports its seed and step`() {
        val error = assertThrows(AssertionError::class.java) {
            ScheduleDriver(REPORT_SEED, storageDecorator = ::StaleReplayStorage).run()
        }
        // The stale payload is detected while the admission step runs, before its check.
        val context = Regex("^seed=$REPORT_SEED step=\\d+ action=(fail|succeed): ")
        assertTrue(error.message.orEmpty(), context.containsMatchIn(error.message.orEmpty()))
    }

    @Test
    fun `one seed gives the same trace twice`() {
        assertEquals(ScheduleDriver(SAME_SEED).run(), ScheduleDriver(SAME_SEED).run())
    }

    @Test
    fun `seeded schedules agree with the model and reach the fault cases`() {
        val traces = (1L..SEEDED_SCHEDULES).flatMap { seed -> ScheduleDriver(seed).run() }

        // The schedules must reach every case that the harness models, or agreement with the model proves little.
        val required = listOf(
            "READ#0 THREW", "READ#1 RETURNED", "READ#1 THREW", "WRITE#", "DURABLE_COMMIT", "RETURNED ReturnFalse",
            "THREW Throw", "HELD HoldBeforeCommit", "HELD CommitThenHold", "KILLED_IN_FLIGHT", "CommitThenReportFalse",
            "ADMIT_RESET", "g2 START", "REBOOT", "JUMP_WALL", "LockedOut(remainingMs=", "degraded=true",
        )
        val missing = required.filterNot { token -> traces.any { token in it } }
        assertTrue("seeded schedules never reached: $missing", missing.isEmpty())
    }

    /** Passes each write to storage with the payload of the previous write: a stale replay the model must catch. */
    private class StaleReplayStorage(private val inner: LockoutStorage) : LockoutStorage {
        private var previous: LockoutSnapshot? = null

        override fun read(): LockoutSnapshot = inner.read()

        override fun write(snapshot: LockoutSnapshot): Boolean {
            val replay = previous ?: snapshot
            previous = snapshot
            return inner.write(replay)
        }
    }

    private companion object {
        val ZERO = LockoutSnapshot(0, 0L)

        // The test plan fixture L5: count 5 and a wall deadline of W0 + 30 s.
        val L5 = LockoutSnapshot(LockoutModel.THRESHOLD, VirtualClocks.W0 + LockoutModel.BASE_MS)

        const val LADDER_CHECK_LIMIT = 40
        const val SAME_SEED = 42L
        const val REPORT_SEED = 7L
        const val SEEDED_SCHEDULES = 100L
    }
}
