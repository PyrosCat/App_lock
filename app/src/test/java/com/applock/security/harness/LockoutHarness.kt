package com.applock.security.harness

import com.applock.security.LockoutManager
import com.applock.security.LockoutSnapshot
import com.applock.security.LockoutState
import com.applock.security.LockoutStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/**
 * Runs simulated processes of the real [LockoutManager] over one [SimulatedLockoutStore], and checks each step
 * against [LockoutModel].
 *
 * Each process is a generation with its own storage view, its own [ControlledIoExecutor], and its own manager. The
 * manager receives the virtual clocks and the harness executor through its constructor; its code is not changed.
 * Every step leaves the process quiescent: the executor is idle, or its worker is parked at a storage hold. The
 * harness then feeds the new ledger events of the live process to the model, in ledger order.
 *
 * [kill] simulates process death: the store fences the generation first, so the dead manager cannot change the store
 * afterwards; the executor drops its queued writes; then the parked operations wake and end. `shutdown()` is called
 * on the dead manager only to silence its callbacks.
 *
 * [check] compares the model with the durable state, the process cache, the public manager API, the resolved
 * operation outcomes, the read count, and the one-writer maximum. While a re-seed is requested, [check] does not call
 * `currentState()`, because that call starts a re-seed read; [poll] is the observation that does.
 */
class LockoutHarness(
    initial: LockoutSnapshot = LockoutSnapshot(0, 0L),
    val clocks: VirtualClocks = VirtualClocks(),
    val faults: FaultPlan = FaultPlan(),
    private val storageDecorator: (LockoutStorage) -> LockoutStorage = { it },
) : AutoCloseable {

    /** An admitted operation of the live process and its result: a FailureOutcome or a reset commit Boolean. */
    private class LiveOp(val writeIndex: Int, val resolved: Deferred<Any>)

    val ledger = Ledger()
    val store = SimulatedLockoutStore(initial, clocks, faults, ledger)
    val model = LockoutModel(clocks, initial)

    var generation = 0
        private set
    private var managerOrNull: LockoutManager? = null
    val manager: LockoutManager
        get() = checkNotNull(managerOrNull) { "no live manager in g$generation" }

    private var executor: ControlledIoExecutor? = null
    private var starter: FutureTask<LockoutManager>? = null
    private var syncedSeq = 0
    private val liveOps = ArrayList<LiveOp>()

    /** The write index that the next admission of the live process receives. */
    val nextWriteIndex: Int get() = model.writesAdmitted

    // ---- Process lifecycle ---------------------------------------------------------------------

    /** Starts a new process. Its construction read runs on the test thread, as the constructor reads synchronously. */
    fun start(): LockoutManager {
        val io = beginProcess()
        managerOrNull = construct(io)
        settle()
        return manager
    }

    /**
     * Starts a new process whose construction read is scripted to hold. The constructor runs on a starter thread and
     * parks; no manager exists until [releaseSeed].
     */
    fun startWithHeldSeed() {
        val io = beginProcess()
        val task = FutureTask { construct(io) }
        starter = task
        Thread(task, "starter-g$generation").apply { isDaemon = true }.start()
        store.awaitHeld(generation, StorageOp.READ, 0)
        sync()
    }

    fun releaseSeed(): LockoutManager {
        val task = checkNotNull(starter) { "no held start" }
        ledger.action(generation, "RELEASE", "READ#0", clocks)
        store.release(generation, StorageOp.READ, 0)
        managerOrNull = task.get(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        starter = null
        settle()
        return manager
    }

    fun kill() {
        val io = executor ?: return
        ledger.action(generation, "KILL", "", clocks)
        store.fence(generation)
        val dropped = io.stop()
        ledger.action(generation, "DROPPED", "$dropped queued task(s)", clocks)
        managerOrNull?.shutdown() // silences the dead manager's callbacks; the fence above is the crash
        store.wakeKilled(generation)
        check(io.awaitTermination(TIMEOUT_MS)) { "the persistence worker of g$generation did not end" }
        starter?.let { task ->
            runCatching { task.get(TIMEOUT_MS, TimeUnit.MILLISECONDS) }
            starter = null
        }
        managerOrNull = null
        executor = null
        liveOps.clear()
    }

    fun restart(): LockoutManager {
        kill()
        return start()
    }

    /** Kills the process, starts a new boot after [downtimeMs], and starts a new process. */
    fun reboot(downtimeMs: Long): LockoutManager {
        kill()
        clocks.reboot(downtimeMs)
        ledger.action(generation, "REBOOT", "downtime=$downtimeMs boot=${clocks.bootCount()}", clocks)
        return start()
    }

    override fun close() = kill()

    private fun beginProcess(): ControlledIoExecutor {
        check(executor == null) { "kill g$generation before starting another process" }
        generation++
        model.startProcess(generation)
        liveOps.clear()
        ledger.action(generation, "START", "boot=${clocks.bootCount()}", clocks)
        return ControlledIoExecutor("lockout-io-g$generation").also { executor = it }
    }

    private fun construct(io: ControlledIoExecutor): LockoutManager {
        val dispatcher = io.asCoroutineDispatcher()
        return LockoutManager(
            storage = storageDecorator(store.open(generation)),
            clock = clocks::wallMs,
            elapsedRealtime = clocks::elapsedMs,
            ioDispatcher = dispatcher,
            scope = CoroutineScope(SupervisorJob() + dispatcher),
        )
    }

    // ---- Actions ---------------------------------------------------------------------------------

    /** Admits one failure at manager level and checks the immediate state against the model. */
    fun fail(): LockoutManager.Pending<LockoutManager.FailureOutcome> {
        val writeIndex = model.writesAdmitted
        val pending = paused {
            val pending = manager.submitFailure()
            val expected = model.admitFailure()
            ledger.action(generation, "ADMIT_FAILURE", "write#$writeIndex", clocks)
            assertEquals("S2/S8: immediate state after failure write#$writeIndex", expected, pending.immediate)
            pending
        }
        liveOps += LiveOp(writeIndex, pending.resolved)
        settle()
        return pending
    }

    /** Admits one reset at manager level (an accepted success). */
    fun succeed(): LockoutManager.Pending<Boolean> {
        val writeIndex = model.writesAdmitted
        val pending = paused {
            val pending = manager.submitSuccess()
            val expected = model.admitReset()
            ledger.action(generation, "ADMIT_RESET", "write#$writeIndex", clocks)
            assertEquals("S3: immediate state after reset write#$writeIndex", expected, pending.immediate)
            pending
        }
        liveOps += LiveOp(writeIndex, pending.resolved)
        settle()
        return pending
    }

    /** Reads `currentState()` as a caller poll does. At baseline this can start a re-seed read (S10). */
    fun poll(): LockoutState {
        val state = paused {
            val expected = model.poll()
            val actual = manager.currentState()
            ledger.action(generation, "POLL", "$actual", clocks)
            assertEquals("S8/S10: polled state", expected, actual)
            actual
        }
        settle()
        return state
    }

    fun advance(ms: Long) {
        clocks.advance(ms)
        ledger.action(generation, "ADVANCE", "$ms", clocks)
    }

    fun jumpWall(ms: Long) {
        clocks.jumpWall(ms)
        ledger.action(generation, "JUMP_WALL", "$ms", clocks)
    }

    fun awaitHeld(op: StorageOp, index: Int) = store.awaitHeld(generation, op, index)

    /**
     * Drops the queued persistence work of the live process without killing it. Only the negative control uses it,
     * to show that [check] detects an admitted write that never runs. The ledger records it.
     */
    fun dropQueuedWork(): Int {
        val dropped = checkNotNull(executor).dropQueued()
        ledger.action(generation, "DROP_QUEUED", "$dropped task(s)", clocks)
        return dropped
    }

    fun release(op: StorageOp, index: Int) {
        ledger.action(generation, "RELEASE", "$op#$index", clocks)
        store.release(generation, op, index)
        settle()
    }

    // ---- Checks ----------------------------------------------------------------------------------

    /**
     * Compares the model with every observation that has no side effect on the process. While a write is parked,
     * later work can wait in the queue, so the counts may lag behind the admissions. When the executor is idle,
     * nothing can still run: every requested read and every admitted write must have run, and every admitted
     * operation must have resolved.
     */
    fun check() {
        val live = manager
        val idle = checkNotNull(executor).isIdle()
        assertEquals("durable state", model.durable, store.durableState())
        assertEquals("process cache", model.cache, store.cacheOf(generation))
        assertEquals("failure count", model.count, live.failureCount())
        assertEquals("seed read failed", model.seedFailed, live.seedReadFailed())
        val reads = store.readCount(generation)
        val writes = store.writeCount(generation)
        assertTrue("S10: no read without a request", reads <= model.readsExpected)
        assertTrue("S5: no write without an admission", writes <= model.writesAdmitted)
        if (idle) {
            assertEquals("S10: every requested read ran", model.readsExpected, reads)
            assertEquals("S5: every admitted write ran", model.writesAdmitted, writes)
            assertEquals("S5: every admitted write finished", 0, model.pendingWrites)
        }
        assertTrue("S5: one ordered writer", store.maxConcurrentWrites() <= 1)
        assertTrue("escaped task failures: ${executor?.escaped}", executor?.escaped.isNullOrEmpty())
        if (!model.reseedRequested) {
            assertEquals("S8: lockout state", model.state(), live.currentState())
        }
        checkOutcomes(idle)
    }

    private fun checkOutcomes(idle: Boolean) {
        liveOps.forEach { op ->
            val expected = model.outcome(op.writeIndex)
            if (!op.resolved.isCompleted) {
                assertFalse("S9: write#${op.writeIndex} is unresolved with nothing left to run", idle)
                return@forEach
            }
            val actual = when (val result = runBlocking { op.resolved.await() }) {
                is LockoutManager.FailureOutcome -> ExpectedOutcome.Failure(result.state, result.count)
                is Boolean -> ExpectedOutcome.Reset(result)
                else -> error("unexpected outcome type $result")
            }
            assertEquals("S9: outcome of write#${op.writeIndex}", expected, actual)
        }
    }

    // ---- Quiescence and model feed ---------------------------------------------------------------

    // Runs [block] with the executor paused, so the work it enqueues starts only after the block has logged it.
    private fun <T> paused(block: () -> T): T {
        val io = checkNotNull(executor)
        io.pause()
        try {
            return block()
        } finally {
            io.resume()
        }
    }

    private fun settle() {
        awaitQuiescent()
        sync()
    }

    /** Waits until the executor is idle or its worker is parked at a storage hold. */
    fun awaitQuiescent() {
        val io = executor ?: return
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MS)
        while (!io.isIdle() && !store.isParked(io.threadName)) {
            check(System.nanoTime() < deadline) { "the persistence executor of g$generation did not settle" }
            io.awaitChange(1L)
        }
    }

    private fun sync() {
        val events = ledger.snapshot()
        events.drop(syncedSeq)
            .filterIsInstance<LedgerEvent.Storage>()
            .filter { it.generation == generation }
            .forEach(model::onStorageEvent)
        syncedSeq = events.size
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
