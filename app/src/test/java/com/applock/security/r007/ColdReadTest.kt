package com.applock.security.r007

import com.applock.security.LockoutSnapshot
import com.applock.security.LockoutState.Available
import com.applock.security.LockoutState.LockedOut
import com.applock.security.harness.FaultPlan
import com.applock.security.harness.GatedCaller
import com.applock.security.harness.ReadScript
import com.applock.security.harness.StorageOp
import com.applock.security.harness.WriteScript
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** P2 baseline, residual R-007/1 (test plan §5): a cold-start read failure, cases R1.1 to R1.4. */
class ColdReadTest : BaselineCase() {

    // ---- R1.1 transient read failure -----------------------------------------------------------

    @Test
    fun `R1_1 - without a poll a failed seed read is never retried and the stored lockout is not enforced`() {
        val harness = harness(L5, FaultPlan().read(ReadScript.Throw, generation = 1, index = 0))
        harness.start()
        harness.check()
        assertTrue(harness.manager.seedReadFailed())
        assertEquals(0, harness.manager.failureCount())

        // Only the construction read fails, so the storage is readable from then on.
        harness.advance(T - 1)
        harness.check()
        assertEquals("no read without a poll", 1, harness.store.readCount(1))

        // At T - 1 the first state read of a caller still answers Available; its re-seed read then loads L5.
        val caller = GatedCaller(harness)
        assertTrue("verification 1 ms before the stored deadline", caller.wrongPin())
        harness.check()
        assertEquals(2, harness.store.readCount(1))
        assertEquals("the failure counts on top of the loaded count", 6, harness.manager.failureCount())
        assertEquals(LockoutSnapshot(6, W0 + T - 1 + 60_000L), harness.store.durableState())
        harness.record("R1.1-no-poll", "unenforced_ms" to T - 1, "reads_before_poll" to 1, "verifier_entries" to 1)
    }

    @Test
    fun `R1_1 - with 250 ms polls the first successful re-seed read loads the stored lockout`() {
        val faults = FaultPlan().read(ReadScript.Throw, generation = 1)
        val harness = harness(L5, faults)
        harness.start()
        val answers = (0 until 4).map { harness.poll().also { harness.advance(POLL_MS) } }
        faults.clearReads() // the storage is readable from t = 1 s
        val recovering = harness.poll()
        harness.check()

        assertEquals(List(4) { Available }, answers)
        assertEquals("the poll answers before its read", Available, recovering)
        assertEquals(LockedOut(T - 1_000L, degraded = false), harness.manager.currentState())
        assertEquals(5, harness.manager.failureCount())
        assertEquals("one construction read and five re-seed reads", 6, harness.store.readCount(1))
        assertTrue("the seed failure stays recorded", harness.manager.seedReadFailed())
        harness.record("R1.1-poll-250ms", "unenforced_ms" to 1_000L, "reseed_reads" to 5)
    }

    @Test
    fun `R1_1 - each poll starts one read, and polls during a read in flight start none`() {
        val faults = FaultPlan()
            .read(ReadScript.Throw, generation = 1)
            .read(ReadScript.HoldThenRead(then = ReadScript.Throw), generation = 1, index = 1)
        val harness = harness(L5, faults)
        harness.start()
        harness.poll()
        harness.awaitHeld(StorageOp.READ, 1)

        repeat(BURST) { harness.poll() }
        val startGate = CountDownLatch(1)
        val pollerPool = Executors.newFixedThreadPool(BURST)
        try {
            val reads = List(BURST) {
                pollerPool.submit(
                    Callable {
                        startGate.await()
                        harness.manager.currentState()
                    },
                )
            }
            startGate.countDown()
            // get() rethrows a failure of the read. Each read must answer as the unknown-state seed does.
            reads.forEach { read ->
                assertEquals("a concurrent state read", Available, read.get(JOIN_MS, TimeUnit.MILLISECONDS))
            }
        } finally {
            pollerPool.shutdownNow()
        }
        harness.check()
        assertEquals("no read starts while one is in flight", 2, harness.store.readCount(1))

        harness.release(StorageOp.READ, 1)
        repeat(BURST) { harness.poll() }
        harness.check()
        assertEquals("one read for each poll when none is in flight", 2 + BURST, harness.store.readCount(1))
        harness.record("R1.1-burst", "polls_in_flight" to 2 * BURST, "reads_in_flight" to 0, "reads_after" to BURST)
    }

    // ---- R1.2 persistent read failure ----------------------------------------------------------

    @Test
    fun `R1_2 - under a persistent read fault the state stays Available and each 250 ms poll reads`() {
        listOf("L5" to L5, "L8" to L8, "Z" to Z).forEach { (name, initial) ->
            val harness = harness(initial, FaultPlan().read(ReadScript.Throw))
            harness.start()
            repeat(POLLS_10_S) {
                assertEquals(name, Available, harness.poll())
                harness.advance(POLL_MS)
            }
            harness.check()
            assertEquals(name, 1 + POLLS_10_S, harness.store.readCount(1))
            assertEquals(name, 0, harness.manager.failureCount())
            assertEquals(name, 0, harness.store.writeCount(1))
            harness.record("R1.2-poll-$name", "reads_per_s" to 4, "reads" to harness.store.readCount(1))
        }
    }

    @Test
    fun `R1_2 - a wrong PIN over an unreadable lockout overwrites the stored lockout`() {
        listOf("L5" to L5, "L8" to L8).forEach { (name, initial) ->
            val faults = FaultPlan().read(ReadScript.Throw)
            val harness = harness(initial, faults)
            harness.start()
            val caller = GatedCaller(harness)
            assertTrue("$name: verification over an unreadable lockout", caller.wrongPin())
            harness.check()
            assertEquals(name, LockoutSnapshot(1, 0L), harness.store.durableState())

            harness.restart()
            harness.check()
            assertEquals("$name: still unreadable", 0, harness.manager.failureCount())
            faults.clearReads()
            harness.restart()
            harness.check()
            assertEquals("$name: the stored lockout is lost", Available, harness.manager.currentState())
            assertEquals(name, 1, harness.manager.failureCount())
            harness.record("R1.2-overwrite-$name", "durable_after" to harness.store.durableState())
        }
    }

    @Test
    fun `R1_2 - a correct PIN over an unreadable lockout clears the stored lockout`() {
        val harness = harness(L5, FaultPlan().read(ReadScript.Throw))
        harness.start()
        assertTrue(GatedCaller(harness).correctPin())
        harness.check()
        assertEquals(Z, harness.store.durableState())
        harness.record("R1.2-correct-pin", "durable_after" to harness.store.durableState())
    }

    @Test
    fun `R1_2 - when a persistent read fault ends, the next poll loads the stored lockout`() {
        val faults = FaultPlan().read(ReadScript.Throw)
        val harness = harness(L5, faults)
        harness.start()
        repeat(POLLS_10_S) {
            harness.poll()
            harness.advance(POLL_MS)
        }
        faults.clearReads()
        assertEquals("the poll answers before its read", Available, harness.poll())
        harness.check()
        assertEquals(LockedOut(T - 10_000L, degraded = false), harness.manager.currentState())
        assertEquals(2 + POLLS_10_S, harness.store.readCount(1))
        harness.record("R1.2-late-recovery", "unenforced_ms" to 10_000L, "reads" to harness.store.readCount(1))
    }

    @Test
    fun `R1_2 - under a persistent read fault each restart gives five new verified guesses`() {
        val faultyHarness = harness(Z, FaultPlan().read(ReadScript.Throw))
        faultyHarness.start()
        val caller = GatedCaller(faultyHarness)
        val perCycle = caller.entriesPerRestartCycle(faultyHarness)
        faultyHarness.check()
        assertEquals(List(CYCLES) { 5 }, perCycle)
        assertEquals(5 * CYCLES, caller.verifierEntries)

        val healthyHarness = harness(Z)
        healthyHarness.start()
        val healthyPerCycle = GatedCaller(healthyHarness).entriesPerRestartCycle(healthyHarness)
        healthyHarness.check()
        assertEquals("healthy control: a restart gives nothing", listOf(5) + List(CYCLES - 1) { 0 }, healthyPerCycle)
        faultyHarness.record("R1.2-restart-cycles", "entries_per_cycle" to perCycle, "control" to healthyPerCycle)
    }

    // ---- R1.3 old recovery read races a local mutation -----------------------------------------

    @Test
    fun `R1_3 - an old re-seed value is not applied after a failure, and the failure write replaces the lockout`() {
        val faults = FaultPlan()
            .read(ReadScript.Throw, generation = 1, index = 0)
            .read(ReadScript.ReadThenHold, generation = 1, index = 1)
        val harness = harness(L5, faults)
        harness.start()
        harness.poll()
        val failure = harness.fail()
        harness.check()
        assertEquals(Available, failure.immediate)
        assertEquals("the write waits behind the held read", 0, harness.store.writeCount(1))

        harness.release(StorageOp.READ, 1)
        harness.check()
        assertEquals(1, harness.manager.failureCount())
        assertEquals("the stored L5 is replaced", LockoutSnapshot(1, 0L), harness.store.durableState())
        harness.restart()
        harness.check()
        assertEquals(Available, harness.manager.currentState())
        harness.record("R1.3-failure", "durable_after" to harness.store.durableState())
    }

    @Test
    fun `R1_3 - when the racing failure write fails, the stored lockout returns after a restart`() {
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
        assertEquals(LockedOut(T, degraded = true), harness.manager.currentState())
        assertEquals(L5, harness.store.durableState())

        harness.restart()
        harness.check()
        assertEquals(LockedOut(T, degraded = false), harness.manager.currentState())
        assertEquals(5, harness.manager.failureCount())
        harness.record("R1.3-failed-write", "durable_after" to harness.store.durableState())
    }

    @Test
    fun `R1_3 - an old re-seed value is not applied after a reset`() {
        val faults = FaultPlan()
            .read(ReadScript.Throw, generation = 1, index = 0)
            .read(ReadScript.ReadThenHold, generation = 1, index = 1)
        val harness = harness(L5, faults)
        harness.start()
        harness.poll()
        harness.succeed()
        harness.release(StorageOp.READ, 1)
        harness.check()
        assertEquals(0, harness.manager.failureCount())
        assertEquals(Z, harness.store.durableState())
        harness.record("R1.3-reset", "durable_after" to harness.store.durableState())
    }

    @Test
    fun `R1_3 - a re-seed read that waits in the queue is not applied after a failure`() {
        val harness = harness(L5, FaultPlan().read(ReadScript.Throw, generation = 1, index = 0))
        harness.start()
        harness.holdIo()
        harness.poll()
        harness.fail()
        harness.check()
        assertEquals("the re-seed read has not started", 1, harness.store.readCount(1))

        harness.resumeIo()
        harness.check()
        assertEquals(2, harness.store.readCount(1))
        assertEquals(1, harness.manager.failureCount())
        assertEquals(LockoutSnapshot(1, 0L), harness.store.durableState())
        harness.record("R1.3-queued-read", "durable_after" to harness.store.durableState())
    }

    @Test
    fun `R1_3 - after a local mutation a poll starts no re-seed read`() {
        val harness = harness(L5, FaultPlan().read(ReadScript.Throw, generation = 1, index = 0))
        harness.start()
        harness.fail()
        repeat(BURST) { harness.poll() }
        harness.check()
        assertEquals(1, harness.store.readCount(1))
        assertEquals(LockoutSnapshot(1, 0L), harness.store.durableState())
        harness.record("R1.3-mutation-first", "reads" to 1)
    }

    // ---- R1.4 a read that does not return ------------------------------------------------------

    @Test
    fun `R1_4 - a held construction read leaves no manager until it returns`() {
        listOf(ReadScript.Normal, ReadScript.Throw).forEach { then ->
            val harness = harness(L5, FaultPlan().read(ReadScript.HoldThenRead(then), generation = 1, index = 0))
            harness.startWithHeldSeed()
            assertThrows(IllegalStateException::class.java) { harness.manager }
            harness.advance(STALL_MS)
            harness.releaseSeed()
            harness.check()
            if (then == ReadScript.Normal) {
                assertEquals("the L5 window ended during the stall", Available, harness.manager.currentState())
                assertEquals(5, harness.manager.failureCount())
            } else {
                assertTrue(harness.manager.seedReadFailed())
                assertEquals(0, harness.manager.failureCount())
            }
            harness.record("R1.4-held-seed-$then", "stall_ms" to STALL_MS, "count" to harness.manager.failureCount())
        }
    }

    @Test
    fun `R1_4 - a construction read that throws a cancellation makes the construction throw`() {
        val faults = FaultPlan().read(ReadScript.HoldThenRead(ReadScript.ThrowCancellation), generation = 1, index = 0)
        val harness = harness(L5, faults)
        harness.startWithHeldSeed()
        harness.advance(STALL_MS)
        val thrown = assertThrows(ExecutionException::class.java) { harness.releaseSeed() }
        assertTrue("${thrown.cause}", thrown.cause is CancellationException)
        harness.record("R1.4-held-seed-cancellation", "constructor" to thrown.cause?.javaClass?.simpleName)
    }

    @Test
    fun `R1_4 - a held re-seed read blocks every queued write while admissions enforce from memory`() {
        listOf(ReadScript.Normal, ReadScript.Throw).forEach { then ->
            val faults = FaultPlan()
                .read(ReadScript.Throw, generation = 1, index = 0)
                .read(ReadScript.HoldThenRead(then), generation = 1, index = 1)
            val harness = harness(L5, faults)
            harness.start()
            harness.poll()
            val caller = GatedCaller(harness)
            assertEquals(5, caller.wrongPinsUntilBlocked())
            harness.check()
            assertEquals("$then: no write starts behind the held read", 0, harness.store.writeCount(1))
            assertEquals(L5, harness.store.durableState())
            assertEquals(LockedOut(T, degraded = true), harness.manager.currentState())

            harness.advance(STALL_MS)
            assertEquals("$then: the fallback ended while the writes waited", Available, harness.poll())
            harness.release(StorageOp.READ, 1)
            harness.check()
            assertEquals(5, harness.store.writeCount(1))
            assertEquals(LockoutSnapshot(5, W0 + T), harness.store.durableState())
            assertEquals(Available, harness.manager.currentState())
            harness.record("R1.4-held-reseed-$then", "write_delay_ms" to STALL_MS, "queued_writes" to 5)
        }
    }

    @Test
    fun `R1_4 - a re-seed read that throws a cancellation stops all re-seeds for the life of the process`() {
        val faults = FaultPlan()
            .read(ReadScript.Throw, generation = 1, index = 0)
            .read(ReadScript.ThrowCancellation, generation = 1, index = 1)
        val harness = harness(L5, faults)
        harness.start()
        assertEquals(Available, harness.poll())
        // Direct state reads: the model expects a new re-seed read for each poll, and the baseline starts none.
        repeat(BURST) { assertEquals(Available, harness.manager.currentState()) }
        harness.awaitQuiescent()
        assertEquals("no re-seed read after the cancellation", 2, harness.store.readCount(1))

        harness.advance(T - 1)
        assertEquals("the stored lockout is never enforced", Available, harness.manager.currentState())
        assertEquals(0, harness.manager.failureCount())
        assertEquals(2, harness.store.readCount(1))

        harness.restart()
        harness.check()
        assertEquals("a new process reads it", LockedOut(1L, degraded = false), harness.manager.currentState())
        harness.record("R1.4-cancelled-reseed", "reads_after_cancel" to 0, "unenforced_ms" to T - 1)
    }

    private companion object {
        const val POLLS_10_S = 40
        val JOIN_MS = TimeUnit.SECONDS.toMillis(5)
    }
}
