package com.applock.security.r007

import com.applock.security.LockoutSnapshot
import com.applock.security.LockoutState.Available
import com.applock.security.LockoutState.LockedOut
import com.applock.security.harness.FaultPlan
import com.applock.security.harness.GatedCaller
import com.applock.security.harness.LockoutHarness
import com.applock.security.harness.Phase
import com.applock.security.harness.ReadScript
import com.applock.security.harness.StorageOp
import com.applock.security.harness.WriteScript
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // B1 spec: T2, T5, T6, T8, T19.
    @Test
    fun `R1_1 - without a poll the recovery retry at 1 s loads the stored lockout`() {
        val harness = harness(L5, FaultPlan().read(ReadScript.Throw, generation = 1, index = 0))
        harness.start()
        harness.check()
        assertTrue(harness.manager.seedReadFailed())
        assertEquals(0, harness.manager.failureCount())

        // Only the construction read fails, so the storage is readable from then on.
        harness.advance(FIRST_RETRY_MS - 1)
        harness.check()
        assertEquals("no read before the first retry", 1, harness.store.readCount(1))
        assertEquals(Available, harness.manager.currentState())

        harness.advance(1)
        harness.check()
        assertEquals("the retry at 1 s reads", 2, harness.store.readCount(1))
        assertEquals(5, harness.manager.failureCount())
        assertEquals(LockedOut(T - FIRST_RETRY_MS, degraded = false), harness.manager.currentState())
        val readThreads = harness.ledger.storageEvents(1)
            .filter { it.op == StorageOp.READ && it.index == 1 }
            .map { it.thread }
            .toSet()
        assertEquals("the recovery read runs on the writer", setOf("lockout-io-g1"), readThreads)

        // At T - 1, a caller's gate blocks the wrong PIN.
        harness.advance(T - 1 - FIRST_RETRY_MS)
        val caller = GatedCaller(harness)
        assertFalse("no verification 1 ms before the stored deadline", caller.wrongPin())
        harness.check()
        assertEquals(0, caller.verifierEntries)
        assertEquals(L5, harness.store.durableState())

        harness.advance(TEN_MIN_MS)
        harness.check()
        assertEquals("no read after the success", 2, harness.store.readCount(1))
        assertEquals(
            listOf(
                "RETRY_SCHEDULED seq=1 kind=READ attempt=1 delay=1000",
                "RETRY_FIRED seq=1",
                "RETRY_STARTED seq=1",
                "RETRY_ENDED seq=1 outcome=success",
            ),
            harness.retryTrace(1),
        )
        harness.record("R1.1-no-poll", "unenforced_ms" to FIRST_RETRY_MS, "reads" to 2, "verifier_entries" to 0)
    }

    // B1 spec: T17.
    @Test
    fun `R1_1 - with 250 ms polls no poll reads, and the retry at 3 s loads the stored lockout`() {
        val faults = FaultPlan().read(ReadScript.Throw, generation = 1)
        val harness = harness(L5, faults)
        harness.start()
        val answers = (0 until 4).map { harness.poll().also { harness.advance(POLL_MS) } }
        assertEquals("the retry at 1 s fails", 2, harness.store.readCount(1))
        faults.clearReads() // the storage is readable from t = 1 s
        val answersToNextRetry = (0 until 8).map { harness.poll().also { harness.advance(POLL_MS) } }
        harness.check()

        assertEquals(List(4) { Available }, answers)
        assertEquals("the polls from 1 s answer before the next retry", List(8) { Available }, answersToNextRetry)
        assertEquals(LockedOut(T - 3_000L, degraded = false), harness.manager.currentState())
        assertEquals(5, harness.manager.failureCount())
        assertEquals("one construction read and two retry reads", 3, harness.store.readCount(1))
        assertTrue("the seed failure stays recorded", harness.manager.seedReadFailed())
        harness.record("R1.1-poll-250ms", "unenforced_ms" to 3_000L, "poll_reads" to 0, "retry_reads" to 2)
    }

    // B1 spec: T14.
    @Test
    fun `R1_1 - polls start no read, and no retry falls due while a recovery read is held`() {
        val faults = FaultPlan()
            .read(ReadScript.Throw, generation = 1)
            .read(ReadScript.HoldThenRead(then = ReadScript.Throw), generation = 1, index = 1)
        val harness = harness(L5, faults)
        harness.start()
        harness.advance(FIRST_RETRY_MS)
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
        harness.advance(TEN_MIN_MS)
        harness.check()
        assertEquals("no read starts while one is held", 2, harness.store.readCount(1))
        val firings = harness.retryTrace(1).count { it.startsWith("RETRY_FIRED") }
        assertEquals("no retry fires while the recovery read is held", 1, firings)

        // The released read fails, so the next retry falls due 2 s after the release.
        harness.release(StorageOp.READ, 1)
        repeat(BURST) { harness.poll() }
        harness.advance(SECOND_RETRY_DELAY_MS - 1)
        harness.check()
        assertEquals("the polls after the release start no read", 2, harness.store.readCount(1))
        harness.advance(1)
        harness.check()
        assertEquals("the next retry reads 2 s after the release", 3, harness.store.readCount(1))
        harness.record(
            "R1.1-burst",
            "polls_during_hold" to 2 * BURST,
            "reads_during_hold" to 0,
            "reads_after_polls" to 0,
            "next_retry_after_release_ms" to SECOND_RETRY_DELAY_MS,
        )
    }

    // ---- R1.2 persistent read failure ----------------------------------------------------------

    // B1 spec: T9.
    @Test
    fun `R1_2 - under a persistent read fault the state stays Available and the reads follow the backoff`() {
        listOf("L5" to L5, "L8" to L8, "Z" to Z).forEach { (name, initial) ->
            val harness = harness(initial, FaultPlan().read(ReadScript.Throw))
            harness.start()
            repeat(POLLS_10_S) {
                assertEquals(name, Available, harness.poll())
                harness.advance(POLL_MS)
            }
            harness.check()
            val failedReadTimes = harness.failedRetryReadTimes()
            assertEquals("$name: the retries fail at 1, 3, and 7 s", listOf(1_000L, 3_000L, 7_000L), failedReadTimes)
            assertEquals(name, 4, harness.store.readCount(1))
            assertEquals(name, 0, harness.manager.failureCount())
            assertEquals(name, 0, harness.store.writeCount(1))
            assertEquals(name, initial, harness.store.durableState())
            harness.record("R1.2-poll-$name", "reads" to harness.store.readCount(1), "retry_ms" to failedReadTimes)
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

    // B1 spec: T9.
    @Test
    fun `R1_2 - when a persistent read fault ends, the next due retry loads the stored lockout`() {
        val faults = FaultPlan().read(ReadScript.Throw)
        val harness = harness(L5, faults)
        harness.start()
        repeat(POLLS_10_S) {
            harness.poll()
            harness.advance(POLL_MS)
        }
        faults.clearReads()
        val answersToNextRetry = (0 until POLLS_5_S).map { harness.poll().also { harness.advance(POLL_MS) } }
        harness.check()
        assertEquals("the polls up to 15 s answer Available", List(POLLS_5_S) { Available }, answersToNextRetry)
        assertEquals(LockedOut(T - 15_000L, degraded = false), harness.manager.currentState())
        assertEquals(5, harness.store.readCount(1))
        harness.record("R1.2-late-recovery", "unenforced_ms" to 15_000L, "reads" to harness.store.readCount(1))
    }

    @Test
    fun `R1_2 - under a persistent read fault each restart gives five new verified guesses`() {
        val faultyHarness = harness(Z, FaultPlan().read(ReadScript.Throw))
        faultyHarness.start()
        val caller = GatedCaller(faultyHarness)
        val faultyEntries = caller.entriesAcrossRestarts(faultyHarness)
        faultyHarness.check()
        assertEquals(RestartEntries(5, List(CYCLES) { 5 }), faultyEntries)
        assertEquals(faultyEntries.total, caller.verifierEntries)

        val healthyHarness = harness(Z)
        healthyHarness.start()
        val healthyEntries = GatedCaller(healthyHarness).entriesAcrossRestarts(healthyHarness)
        healthyHarness.check()
        assertEquals("healthy control: a restart gives nothing", RestartEntries(5, List(CYCLES) { 0 }), healthyEntries)
        faultyHarness.record("R1.2-restart-cycles", "entries" to faultyEntries, "control" to healthyEntries)
    }

    // B1 spec: T18.
    @Test
    fun `R1_2 - a restart while a retry is pending starts a new chain at 1 s, and the dead process never fires`() {
        val harness = harness(L5, FaultPlan().read(ReadScript.Throw))
        harness.start()
        harness.advance(FIRST_RETRY_MS / 2)
        harness.restart()
        val restartElapsed = harness.clocks.elapsedMs()

        // The retry of the dead process would be due now.
        harness.advance(FIRST_RETRY_MS / 2)
        harness.check()
        assertEquals("no retry read before 1 s after the restart", 1, harness.store.readCount(2))
        harness.advance(FIRST_RETRY_MS / 2)
        harness.check()

        assertEquals(Available, harness.manager.currentState())
        assertEquals(0, harness.manager.failureCount())
        assertEquals(L5, harness.store.durableState())
        assertEquals(1, harness.store.readCount(1))
        assertEquals(
            "the dead process records no retry event after its kill",
            listOf("RETRY_SCHEDULED seq=1 kind=READ attempt=1 delay=1000"),
            harness.retryTrace(1),
        )
        assertEquals(
            listOf(
                "RETRY_SCHEDULED seq=1 kind=READ attempt=1 delay=1000",
                "RETRY_FIRED seq=1",
                "RETRY_STARTED seq=1",
                "RETRY_ENDED seq=1 outcome=retry",
                "RETRY_SCHEDULED seq=2 kind=READ attempt=2 delay=2000",
            ),
            harness.retryTrace(2),
        )
        val firedElapsed = harness.retryEvents(2).first { it.name == "RETRY_FIRED" }.elapsedMs
        assertEquals("the new chain fires 1 s after the restart", restartElapsed + FIRST_RETRY_MS, firedElapsed)
        harness.record("R1.2-restart-pending-retry", "fired_after_restart_ms" to firedElapsed - restartElapsed)
    }

    // ---- R1.3 old recovery read races a local mutation -----------------------------------------

    // B1 spec: T12.
    @Test
    fun `R1_3 - an old recovery value is not applied after a failure, and the failure write replaces the lockout`() {
        val faults = FaultPlan()
            .read(ReadScript.Throw, generation = 1, index = 0)
            .read(ReadScript.ReadThenHold, generation = 1, index = 1)
        val harness = harness(L5, faults)
        harness.start()
        harness.advance(FIRST_RETRY_MS)
        val failure = harness.fail()
        harness.check()
        assertEquals(Available, failure.immediate)
        assertEquals("the write waits behind the held read", 0, harness.store.writeCount(1))

        harness.release(StorageOp.READ, 1)
        harness.check()
        assertEquals(1, harness.manager.failureCount())
        assertEquals("the stored L5 is replaced", LockoutSnapshot(1, 0L), harness.store.durableState())
        assertEquals("RETRY_ENDED seq=1 outcome=abandoned", harness.retryTrace(1).last())
        harness.restart()
        harness.check()
        assertEquals(Available, harness.manager.currentState())
        harness.record("R1.3-failure", "durable_after" to harness.store.durableState())
    }

    // B1 spec: T12.
    @Test
    fun `R1_3 - when the racing failure write fails, the stored lockout returns after a restart`() {
        val faults = FaultPlan()
            .read(ReadScript.Throw, generation = 1, index = 0)
            .read(ReadScript.ReadThenHold, generation = 1, index = 1)
            .write(WriteScript.ReturnFalse, generation = 1, index = 0)
        val harness = harness(L5, faults)
        harness.start()
        harness.advance(FIRST_RETRY_MS)
        harness.fail()
        harness.release(StorageOp.READ, 1)
        harness.check()
        assertEquals(LockedOut(T, degraded = true), harness.manager.currentState())
        assertEquals(L5, harness.store.durableState())
        assertEquals("RETRY_ENDED seq=1 outcome=abandoned", harness.retryTrace(1).last())

        harness.restart()
        harness.check()
        assertEquals(LockedOut(T - FIRST_RETRY_MS, degraded = false), harness.manager.currentState())
        assertEquals(5, harness.manager.failureCount())
        harness.record("R1.3-failed-write", "durable_after" to harness.store.durableState())
    }

    // B1 spec: T12.
    @Test
    fun `R1_3 - an old recovery value is not applied after a reset`() {
        val faults = FaultPlan()
            .read(ReadScript.Throw, generation = 1, index = 0)
            .read(ReadScript.ReadThenHold, generation = 1, index = 1)
        val harness = harness(L5, faults)
        harness.start()
        harness.advance(FIRST_RETRY_MS)
        harness.succeed()
        harness.release(StorageOp.READ, 1)
        harness.check()
        assertEquals(0, harness.manager.failureCount())
        assertEquals(Z, harness.store.durableState())
        assertEquals("RETRY_ENDED seq=1 outcome=abandoned", harness.retryTrace(1).last())
        harness.record("R1.3-reset", "durable_after" to harness.store.durableState())
    }

    // B1 spec: T7.
    @Test
    fun `R1_3 - a recovery retry that waits in the queue skips its read after a failure`() {
        val harness = harness(L5, FaultPlan().read(ReadScript.Throw, generation = 1, index = 0))
        harness.start()
        harness.holdIo()
        harness.advance(FIRST_RETRY_MS)
        harness.fail()
        harness.check()
        assertEquals("the recovery read has not started", 1, harness.store.readCount(1))

        harness.resumeIo()
        harness.check()
        assertEquals("the queued retry skips its read", 1, harness.store.readCount(1))
        assertEquals(1, harness.manager.failureCount())
        assertEquals(LockoutSnapshot(1, 0L), harness.store.durableState())
        assertEquals(
            listOf(
                "RETRY_SCHEDULED seq=1 kind=READ attempt=1 delay=1000",
                "RETRY_FIRED seq=1",
                "RETRY_CANCELLED seq=1 reason=cancelled",
                "RETRY_IGNORED seq=1 reason=stale",
            ),
            harness.retryTrace(1),
        )
        harness.record("R1.3-queued-read", "durable_after" to harness.store.durableState(), "reads" to 1)
    }

    // B1 spec: T15.
    @Test
    fun `R1_3 - a failure or a reset admitted while a retry is pending cancels it, and no recovery read follows`() {
        listOf("failure" to 1, "reset" to 0).forEach { (admission, count) ->
            val harness = harness(L5, FaultPlan().read(ReadScript.Throw, generation = 1, index = 0))
            harness.start()
            if (admission == "failure") harness.fail() else harness.succeed()
            harness.check()
            harness.advance(TEN_MIN_MS)
            harness.check()
            assertEquals(admission, 1, harness.store.readCount(1))
            assertEquals(admission, count, harness.manager.failureCount())
            assertEquals(admission, LockoutSnapshot(count, 0L), harness.store.durableState())
            assertEquals(
                admission,
                listOf(
                    "RETRY_SCHEDULED seq=1 kind=READ attempt=1 delay=1000",
                    "RETRY_CANCELLED seq=1 reason=cancelled",
                ),
                harness.retryTrace(1),
            )
            val durableAfter = harness.store.durableState()
            harness.record("R1.3-pending-retry-$admission", "reads" to 1, "durable_after" to durableAfter)
        }
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

    // B1 spec: T12, T14.
    @Test
    fun `R1_4 - a held recovery read blocks every queued write while admissions enforce from memory`() {
        listOf(ReadScript.Normal, ReadScript.Throw).forEach { then ->
            val faults = FaultPlan()
                .read(ReadScript.Throw, generation = 1, index = 0)
                .read(ReadScript.HoldThenRead(then), generation = 1, index = 1)
            val harness = harness(L5, faults)
            harness.start()
            harness.advance(FIRST_RETRY_MS)
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
            assertEquals(LockoutSnapshot(5, W0 + FIRST_RETRY_MS + T), harness.store.durableState())
            assertEquals(Available, harness.manager.currentState())
            assertEquals(
                "$then: no retry follows the abandoned read",
                listOf(
                    "RETRY_SCHEDULED seq=1 kind=READ attempt=1 delay=1000",
                    "RETRY_FIRED seq=1",
                    "RETRY_STARTED seq=1",
                    "RETRY_ENDED seq=1 outcome=abandoned",
                ),
                harness.retryTrace(1),
            )
            harness.record("R1.4-held-reseed-$then", "write_delay_ms" to STALL_MS, "queued_writes" to 5)
        }
    }

    // B1 spec: T10.
    @Test
    fun `R1_4 - a recovery read that throws a cancellation is a failed read, and the next retry loads the lockout`() {
        val faults = FaultPlan()
            .read(ReadScript.Throw, generation = 1, index = 0)
            .read(ReadScript.ThrowCancellation, generation = 1, index = 1)
        val harness = harness(L5, faults)
        harness.start()
        harness.advance(FIRST_RETRY_MS)
        harness.advance(SECOND_RETRY_DELAY_MS - 1)
        harness.check()
        assertEquals("the stored lockout is not enforced before 3 s", Available, harness.manager.currentState())
        assertEquals(2, harness.store.readCount(1))

        harness.advance(1)
        harness.check()
        assertEquals(LockedOut(T - 3_000L, degraded = false), harness.manager.currentState())
        assertEquals(5, harness.manager.failureCount())
        assertEquals(3, harness.store.readCount(1))
        assertEquals(
            listOf(
                "RETRY_SCHEDULED seq=1 kind=READ attempt=1 delay=1000",
                "RETRY_FIRED seq=1",
                "RETRY_STARTED seq=1",
                "RETRY_ENDED seq=1 outcome=retry",
                "RETRY_SCHEDULED seq=2 kind=READ attempt=2 delay=2000",
                "RETRY_FIRED seq=2",
                "RETRY_STARTED seq=2",
                "RETRY_ENDED seq=2 outcome=success",
            ),
            harness.retryTrace(1),
        )
        harness.record("R1.4-cancelled-reseed", "reads" to 3, "unenforced_ms" to 3_000L)
    }

    // B1 spec: T14, I8.
    @Test
    fun `R1_4 - a recovery read released after the stored window ended loads the count but no lockout`() {
        val faults = FaultPlan()
            .read(ReadScript.Throw, generation = 1, index = 0)
            .read(ReadScript.HoldThenRead(), generation = 1, index = 1)
        val harness = harness(L5, faults)
        harness.start()
        harness.advance(FIRST_RETRY_MS)
        harness.awaitHeld(StorageOp.READ, 1)
        harness.advance(STALL_MS)
        harness.release(StorageOp.READ, 1)
        harness.check()
        assertEquals("the read loads the stored count", 5, harness.manager.failureCount())
        assertEquals("the stored window ended during the hold", Available, harness.manager.currentState())
        assertEquals("RETRY_ENDED seq=1 outcome=success", harness.retryTrace(1).last())

        val caller = GatedCaller(harness)
        assertTrue("the next wrong PIN reaches verification", caller.wrongPin())
        harness.check()
        assertEquals(1, caller.verifierEntries)
        val stateAfterFailure = harness.manager.currentState()
        assertEquals("a recorded 60 s lockout after the sixth failure", LockedOut(2 * T, false), stateAfterFailure)
        assertEquals(LockoutSnapshot(6, W0 + FIRST_RETRY_MS + STALL_MS + 2 * T), harness.store.durableState())
        harness.record("R1.4-recovery-after-window", "count_after_release" to 5, "verifier_entries" to 1)
    }

    // The elapsed times, counted from E0, of the failed recovery reads of the first process.
    private fun LockoutHarness.failedRetryReadTimes(): List<Long> =
        ledger.storageEvents(1)
            .filter { it.op == StorageOp.READ && it.index > 0 && it.phase == Phase.THREW }
            .map { it.elapsedMs - E0 }

    private companion object {
        const val POLLS_10_S = 40
        const val POLLS_5_S = 20
        val JOIN_MS = TimeUnit.SECONDS.toMillis(5)

        // The production delays of the first two retries of a chain.
        const val FIRST_RETRY_MS = 1_000L
        const val SECOND_RETRY_DELAY_MS = 2_000L
        const val TEN_MIN_MS = 600_000L
    }
}
