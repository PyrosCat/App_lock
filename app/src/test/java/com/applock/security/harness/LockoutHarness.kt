package com.applock.security.harness

import com.applock.security.LockoutManager
import com.applock.security.LockoutSnapshot
import com.applock.security.LockoutState
import com.applock.security.LockoutStorage
import com.applock.security.RecoveryEvent
import com.applock.security.RecoverySetup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/**
 * Runs simulated processes of the real [LockoutManager] over one [SimulatedLockoutStore], and checks each step
 * against [LockoutModel].
 *
 * Each process is a generation with its own storage view, its own [ControlledIoExecutor], and its own manager. The
 * manager receives the virtual clocks and the harness executor through its constructor; its code is not changed.
 * Every step leaves the process quiescent: the executor is idle, it is held between tasks ([holdIo]), or its worker is
 * parked at a storage hold or in a [parkedCallback]. The harness then feeds the new ledger events of the live process
 * to the model, in ledger order.
 *
 * [kill] simulates process death: the store fences the generation first, so the dead manager cannot change the store
 * afterwards; the executor drops its queued writes; a parked callback wakes and ends; then the parked storage
 * operations wake and end. `shutdown()` is called on the dead manager only to silence its callbacks.
 *
 * [check] compares the model with the durable state, the process cache, the public manager API, the resolved
 * operation outcomes, the read and write counts, the retry events, and the one-writer maximum. While a re-seed is
 * requested, [check] does not call `currentState()`, because that call starts a re-seed read; [poll] is the
 * observation that does.
 *
 * Each process gets its own [VirtualRecoveryTimer] for the retry scheduler of the manager, and the retry events go to
 * the ledger while the process lives. The model reads them together with the storage events, in ledger order.
 * [advance] fires the retries that are due at the new time, also while a storage operation or a callback is held, and
 * then settles. A dead process fires no retry and records no retry event.
 */
class LockoutHarness(
    initial: LockoutSnapshot = LockoutSnapshot(0, 0L),
    val clocks: VirtualClocks = VirtualClocks(),
    val faults: FaultPlan = FaultPlan(),
    private val storageDecorator: (LockoutStorage) -> LockoutStorage = { it },
) : AutoCloseable {

    /** An admitted operation of the live process and its result: a FailureOutcome or a reset commit Boolean. */
    private class LiveOp(val writeIndex: Int, val resolved: Deferred<Any>) {
        // Set by [cancelResult]: the test cancelled the result, as a caller can.
        var cancelledByTest = false
    }

    /** The hold of one completion callback from [parkedCallback]. */
    private class CallbackHold {
        val parked = CountDownLatch(1)
        val release = CountDownLatch(1)

        @Volatile
        var thread: String? = null

        // Set before the countdown, as in the store holds, so a released callback stops counting as parked at once.
        @Volatile
        var released = false

        @Volatile
        var killed = false

        fun open() {
            released = true
            release.countDown()
        }

        fun isParked(): Boolean = parked.count == 0L && !released
    }

    private val callbackHolds = ConcurrentHashMap<Pair<Int, String>, CallbackHold>()

    val ledger = Ledger()
    val store = SimulatedLockoutStore(initial, clocks, faults, ledger)
    val model = LockoutModel(clocks, initial)

    var generation = 0
        private set
    private var managerOrNull: LockoutManager? = null
    val manager: LockoutManager
        get() = checkNotNull(managerOrNull) { "no live manager in g$generation" }

    private var executor: ControlledIoExecutor? = null
    private var recoveryTimer: VirtualRecoveryTimer? = null

    // The generation whose retry events reach the ledger, or 0 while no process lives. A kill clears it first, so the
    // shutdown of a dead manager records nothing.
    @Volatile
    private var recordingGeneration = 0

    private var starter: FutureTask<LockoutManager>? = null
    private var syncedSeq = 0
    private val liveOps = ArrayList<LiveOp>()

    /** The store write index that the next admission of the live process receives. Retry writes take indexes too. */
    val nextWriteIndex: Int get() = model.nextWriteIndex

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
        recordingGeneration = 0
        recoveryTimer?.stop()
        store.fence(generation)
        val dropped = io.stop()
        ledger.action(generation, "DROPPED", "$dropped queued task(s)", clocks)
        // A parked callback holds the manager lock, so it must end before shutdown() can take that lock.
        callbackHolds.filterKeys { it.first == generation }.values.forEach { hold ->
            hold.killed = true
            hold.open()
        }
        managerOrNull?.shutdown() // silences the dead manager's callbacks; the fence above is the crash
        store.wakeKilled(generation)
        check(io.awaitTermination(TIMEOUT_MS)) { "the persistence worker of g$generation did not end" }
        starter?.let { task ->
            runCatching { task.get(TIMEOUT_MS, TimeUnit.MILLISECONDS) }
            starter = null
        }
        managerOrNull = null
        executor = null
        recoveryTimer = null
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
        recoveryTimer = VirtualRecoveryTimer(clocks)
        recordingGeneration = generation
        return ControlledIoExecutor("lockout-io-g$generation").also { executor = it }
    }

    private fun construct(io: ControlledIoExecutor): LockoutManager {
        val dispatcher = io.asCoroutineDispatcher()
        val owner = generation
        val recovery = RecoverySetup(
            timer = checkNotNull(recoveryTimer) { "no recovery timer for g$owner" },
            listener = { event ->
                if (recordingGeneration == owner) {
                    ledger.action(owner, event.ledgerName(), event.ledgerDetail(), clocks, recovery = event)
                }
            },
        )
        return LockoutManager(
            storage = storageDecorator(store.open(generation)),
            clock = clocks::wallMs,
            elapsedRealtime = clocks::elapsedMs,
            ioDispatcher = dispatcher,
            scope = CoroutineScope(SupervisorJob() + dispatcher),
            recovery = recovery,
        )
    }

    // ---- Actions ---------------------------------------------------------------------------------

    /**
     * Admits one failure at manager level and checks the immediate state against the model. [onResolved] goes to the
     * manager as the completion callback of a synchronous caller.
     */
    fun fail(
        onResolved: ((LockoutManager.FailureOutcome) -> Unit)? = null,
    ): LockoutManager.Pending<LockoutManager.FailureOutcome> {
        val writeIndex = model.nextWriteIndex
        val pending = paused {
            val pending = manager.submitFailure(onResolved)
            val expected = model.admitFailure()
            ledger.action(generation, "ADMIT_FAILURE", "write#$writeIndex", clocks)
            assertEquals("S2/S8: immediate state after failure write#$writeIndex", expected, pending.immediate)
            pending
        }
        liveOps += LiveOp(writeIndex, pending.resolved)
        settle()
        return pending
    }

    /** Admits one reset at manager level (an accepted success). [onResolved] goes to the manager as with [fail]. */
    fun succeed(onResolved: ((Boolean) -> Unit)? = null): LockoutManager.Pending<Boolean> {
        val writeIndex = model.nextWriteIndex
        val pending = paused {
            val pending = manager.submitSuccess(onResolved)
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

    /**
     * Moves both clocks by [ms]. In a live process, it then fires every retry that is due at the new time and settles.
     * The firing never waits for a storage hold or a parked callback, so a retry can join the queue behind a held
     * write.
     */
    fun advance(ms: Long) {
        if (executor == null) {
            clocks.advance(ms)
            ledger.action(generation, "ADVANCE", "$ms", clocks)
            return
        }
        paused {
            clocks.advance(ms)
            ledger.action(generation, "ADVANCE", "$ms", clocks)
            recoveryTimer?.fireDue()
        }
        settle()
    }

    fun jumpWall(ms: Long) {
        clocks.jumpWall(ms)
        ledger.action(generation, "JUMP_WALL", "$ms", clocks)
    }

    fun awaitHeld(op: StorageOp, index: Int) = store.awaitHeld(generation, op, index)

    /**
     * Holds the persistence executor of the live process between tasks, so the work that later actions enqueue waits
     * in the queue until [resumeIo]. A kill during the hold drops that work before it starts.
     */
    fun holdIo() {
        checkNotNull(executor).hold()
        ledger.action(generation, "HOLD_IO", "", clocks)
        awaitQuiescent()
    }

    fun resumeIo() {
        ledger.action(generation, "RESUME_IO", "", clocks)
        checkNotNull(executor).unhold()
        settle()
    }

    /**
     * Returns a completion callback for [fail] or [succeed] that runs [before], then parks at a hold named [label]
     * until [releaseCallback]. After a release it runs [after]. A kill wakes the hold instead: the callback then throws
     * [ProcessKilledException] and [after] never runs, as the callback of a dead process never finishes. The manager
     * runs the callback on its writer thread with its lock held, and the harness counts a parked callback as quiescent.
     * A hold with no release or kill within [CALLBACK_LIMIT_MS] ends with an error. So a test that blocks on the
     * manager lock fails instead of hanging.
     */
    fun <T> parkedCallback(label: String, before: (T) -> Unit = {}, after: (T) -> Unit = {}): (T) -> Unit {
        val owner = generation
        val hold = CallbackHold()
        check(callbackHolds.putIfAbsent(owner to label, hold) == null) { "callback $label already exists in g$owner" }
        return { value ->
            before(value)
            hold.thread = stableThreadName()
            ledger.action(owner, "CALLBACK_HELD", label, clocks)
            hold.parked.countDown()
            if (!hold.release.await(CALLBACK_LIMIT_MS, TimeUnit.MILLISECONDS)) {
                ledger.action(owner, "CALLBACK_TIMEOUT", label, clocks)
                error("g$owner callback $label: no release or kill within $CALLBACK_LIMIT_MS ms")
            }
            if (hold.killed) {
                ledger.action(owner, "CALLBACK_KILLED", label, clocks)
                throw ProcessKilledException("g$owner callback $label: the process died in the callback")
            }
            after(value)
        }
    }

    fun releaseCallback(label: String) {
        ledger.action(generation, "RELEASE", "callback $label", clocks)
        checkNotNull(callbackHolds[generation to label]) { "no callback $label in g$generation" }.open()
        settle()
    }

    /**
     * Calls `shutdown()` on the live manager, as its owner does. The model then expects the end of the retry chain and
     * no further completion effect (S12). The model has no rule for a submission after the stop, so a test checks
     * such a submission directly.
     */
    fun shutdown() {
        ledger.action(generation, "SHUTDOWN", "", clocks)
        manager.shutdown()
        model.stop()
    }

    /**
     * Cancels the result of an admission of the live process, as a caller can. The manager still runs the write, so
     * [check] treats this result as cancelled and does not await it. The ledger records the cancellation.
     */
    fun cancelResult(pending: LockoutManager.Pending<*>) {
        val op = liveOps.single { it.resolved === pending.resolved }
        ledger.action(generation, "CANCEL_RESULT", "write#${op.writeIndex}", clocks)
        op.cancelledByTest = true
        pending.resolved.cancel()
    }

    /** The retry events of [generation], in ledger order. */
    fun retryEvents(generation: Int = this.generation): List<RecoveryEvent> =
        ledger.snapshot()
            .filterIsInstance<LedgerEvent.Action>()
            .filter { it.generation == generation }
            .mapNotNull { it.recovery }

    /** The retries of the live process that wait on its timer. */
    fun pendingRetryCount(): Int = recoveryTimer?.pendingCount() ?: 0

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
     * nothing can still run: every requested read, every admitted write, and every fired retry must have run, and every
     * admitted operation must have resolved. A retry that is due must have fired.
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
        assertTrue("S5: no write without an admission or a started retry", writes <= model.writesExpected)
        if (idle) {
            assertEquals("S10: every requested read ran", model.readsExpected, reads)
            assertEquals("S5: every admitted write and every started retry wrote", model.writesExpected, writes)
            assertEquals("S5: every admitted write finished", 0, model.pendingWrites)
            assertEquals("S12: every fired retry reached the writer", 0, model.queuedRetries)
        }
        assertTrue("S5: one ordered writer", store.maxConcurrentWrites() <= 1)
        assertEquals("S11-S13: the live process has the expected retry events", model.retryEventsExpected, retryCount())
        model.retryDueElapsed?.let { dueMs ->
            assertTrue("S11: a retry fires when it falls due", dueMs > clocks.elapsedMs())
        }
        assertTrue("escaped task failures: ${executor?.escaped}", executor?.escaped.isNullOrEmpty())
        if (!model.reseedRequested) {
            assertEquals("S8: lockout state", model.state(), live.currentState())
        }
        checkOutcomes(idle)
    }

    private fun checkOutcomes(idle: Boolean) {
        liveOps.forEach { op ->
            if (op.cancelledByTest) {
                assertTrue("S9: the result of write#${op.writeIndex} stays cancelled", op.resolved.isCancelled)
                return@forEach
            }
            assertFalse("S9: only the test cancels the result of write#${op.writeIndex}", op.resolved.isCancelled)
            if (!op.resolved.isCompleted) {
                assertFalse("S9: write#${op.writeIndex} is unresolved with nothing left to run", idle)
                return@forEach
            }
            val actual = when (val result = runBlocking { op.resolved.await() }) {
                is LockoutManager.FailureOutcome -> ExpectedOutcome.Failure(result.state, result.count)
                is Boolean -> ExpectedOutcome.Reset(result)
                else -> error("unexpected outcome type $result")
            }
            assertEquals("S9: outcome of write#${op.writeIndex}", model.outcome(op.writeIndex), actual)
        }
    }

    // The number of retry events that the ledger holds for the live process.
    private fun retryCount(): Int = retryEvents().size

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

    /**
     * Waits until the executor is idle, is held between tasks, or has its worker parked at a storage hold or in a
     * parked callback.
     */
    fun awaitQuiescent() {
        val io = executor ?: return
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MS)
        while (!io.isIdle() && !io.isHeldBetweenTasks() && !isParked(io.threadName)) {
            check(System.nanoTime() < deadline) { "the persistence executor of g$generation did not settle" }
            io.awaitChange(1L)
        }
    }

    private fun isParked(thread: String): Boolean =
        store.isParked(thread) || callbackHolds.values.any { it.thread == thread && it.isParked() }

    // Feeds the new storage and retry events of the live process to the model, in ledger order.
    private fun sync() {
        val events = ledger.snapshot()
        events.drop(syncedSeq)
            .filter { it.generation == generation }
            .forEach { event ->
                when (event) {
                    is LedgerEvent.Storage -> model.onStorageEvent(event)
                    is LedgerEvent.Action -> event.recovery?.let { model.onRetryEvent(it, event.elapsedMs) }
                }
            }
        syncedSeq = events.size
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L

        // Far longer than a test needs between a park and its release or kill.
        const val CALLBACK_LIMIT_MS = 30_000L
    }
}

private const val RETRY_PREFIX = "RETRY_"

// The ledger action name of a retry event, for example RETRY_SCHEDULED.
private fun RecoveryEvent.ledgerName(): String = RETRY_PREFIX + when (this) {
    is RecoveryEvent.Scheduled -> "SCHEDULED"
    is RecoveryEvent.Fired -> "FIRED"
    is RecoveryEvent.Cancelled -> "CANCELLED"
    is RecoveryEvent.Ignored -> "IGNORED"
    is RecoveryEvent.Started -> "STARTED"
    is RecoveryEvent.Ended -> "ENDED"
}

// The ledger detail of a retry event, for example "seq=3 kind=WRITE attempt=2 delay=2000".
private fun RecoveryEvent.ledgerDetail(): String = when (this) {
    is RecoveryEvent.Scheduled -> "seq=$sequence kind=$kind attempt=$attempt delay=$delayMs"
    is RecoveryEvent.Fired -> "seq=$sequence"
    is RecoveryEvent.Cancelled -> "seq=$sequence reason=${reason.label()}"
    is RecoveryEvent.Ignored -> "seq=$sequence reason=${reason.label()}"
    is RecoveryEvent.Started -> "seq=$sequence"
    is RecoveryEvent.Ended -> "seq=$sequence outcome=${outcome.label()}"
}

private fun Enum<*>.label(): String = name.lowercase().replace('_', '-')
