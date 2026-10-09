package com.applock.security.harness

import com.applock.security.LockoutSnapshot
import com.applock.security.LockoutState
import com.applock.security.RecoveryEvent
import com.applock.security.RecoveryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** The outcome that the model expects for one admitted operation. */
sealed interface ExpectedOutcome {
    data class Failure(val state: LockoutState, val count: Int) : ExpectedOutcome

    data class Reset(val committed: Boolean) : ExpectedOutcome
}

/**
 * The reference model of the lockout contract (FR-174, R-007) that the harness uses as its oracle. It is written
 * from the F2 specification (the M7_PLAN F2 entry and the public LockoutManager contract) and from the write retry of
 * candidate B2 (`2026-10-07_R007_F2_HARDENING_P3_B2_SPEC.md`), not from the manager's code. From the harness actions
 * and the ledger, it predicts the public state of the live process, the position and payload of every write, the
 * value of every read, the outcome of every operation, the retry events, the process cache, and the durable state. An
 * assertion message names the rule it checks.
 *
 * - S1 Seed: a fresh process takes its count and wall deadline from its first read. A failed first read leaves the
 *   process empty, marks the seed as failed, and asks for a re-seed.
 * - S2 Failure: the count increases by one. At or above the threshold, the in-memory fallback extends to now plus
 *   the ladder duration (it never shortens), and the write carries the count and the wall time now plus that
 *   duration. Below the threshold, the write carries the count and the current recorded wall deadline.
 * - S3 Reset: the count and every deadline clear at once, and the authentication epoch advances. The write carries
 *   (0, 0).
 * - S4 Every admission cancels the request for a re-seed.
 * - S5 Writes run one at a time, in the order of the writer queue: admitted writes in admission order, and the task of
 *   a retry at the place where it joined the queue when it fired. The store numbers ordinary and retry writes
 *   together, in this order.
 * - S6 A threshold write that commits while it is still the latest admission records its admission-time deadlines
 *   (wall and elapsed).
 * - S7 A failure write that does not commit extends the fallback to its completion time plus its own window (the
 *   ladder duration at or above the threshold, the base duration below it), while its epoch is current. A reset that
 *   does not commit changes nothing in memory.
 * - S8 State: a remaining time is the deadline minus now, clamped to 0..max. The recorded remaining time is the
 *   longer of the wall and elapsed recorded windows. The process is locked while either the recorded window or the
 *   fallback remains, and the lock is degraded when the fallback outlasts the recorded window.
 * - S9 Outcome: a failure reports its own count, and a recorded lockout for a committed threshold write, a degraded
 *   lockout of its own window for an uncommitted threshold write, Available for a committed below-threshold write, or
 *   a degraded base lockout for an uncommitted below-threshold write. A reset reports whether it committed.
 * - S10 A poll starts one re-seed read when a re-seed is requested and none runs. A successful re-seed read applies
 *   only while the request stands: the count and the wall deadline come from storage, with no elapsed mirror and no
 *   fallback.
 * - S11 A failed write of the latest admission starts one retry chain for that admission, with attempt 1 and a delay
 *   of 1 s from its completion. The retry fires when the elapsed clock reaches its due time, and its task then joins
 *   the writer queue.
 * - S12 Every admission cancels the expected chain, and so does a shutdown. A retry task that reaches the writer
 *   afterwards writes nothing, and a retry write that runs at that time publishes nothing. After a shutdown, a write
 *   completion changes no memory and starts no chain.
 * - S13 Each retry write carries the payload of the admission that its chain retries. A commit ends the chain, and it
 *   records the deadlines of a threshold admission while that admission is the latest one. A failure expects the next
 *   attempt, with the delay doubled up to 60 s. A retry changes no count, fallback, outcome, or callback.
 */
class LockoutModel(private val clocks: VirtualClocks, initial: LockoutSnapshot) {
    @Suppress("LongParameterList") // each field is a distinct admission-time fact that the completion rules need
    private class Mutation(
        val writeIndex: Int,
        val reset: Boolean,
        val admission: Int,
        val epoch: Int,
        val count: Int,
        val threshold: Boolean,
        val windowMs: Long,
        val payload: LockoutSnapshot,
        val recordedWall: Long,
        val recordedElapsed: Long,
    )

    /** S11 to S13: one expected attempt of the retry chain of [target]. */
    private class Retry(val sequence: Long, val target: Mutation, val attempt: Int, val dueElapsed: Long) {
        // True once the retry fired and its task joined the writer queue.
        var fired = false

        // True once the writer started the retry, so that its write runs.
        var started = false
    }

    /** A task of the writer queue: an admitted write, or the task of a fired retry. */
    private sealed interface Task {
        class Write(val mutation: Mutation) : Task

        class RetryRun(val sequence: Long) : Task
    }

    /**
     * The write in flight: an admitted write, or the write of [retry] with the payload of its target. The write keeps
     * the [script] it started with (its BEGIN event), even if the fault plan changes while the write is parked.
     */
    private class InFlight(val index: Int, val script: WriteScript, val mutation: Mutation, val retry: Retry?)

    var durable: LockoutSnapshot = initial
        private set
    var cache: LockoutSnapshot = initial
        private set

    var generation = 0
        private set
    var count = 0
        private set
    var seedFailed = false
        private set
    var reseedRequested = false
        private set
    var readsExpected = 0
        private set
    var writesAdmitted = 0
        private set

    /** Admitted writes that have not finished yet. */
    val pendingWrites: Int get() = tasks.count { it is Task.Write } + if (admittedWriteInFlight()) 1 else 0

    /** S12: fired retries whose task has not reached the writer yet. */
    val queuedRetries: Int get() = tasks.count { it is Task.RetryRun }

    /**
     * S5: the store index of the next admitted write. Its admission cancels the chain (S12), so only the admitted
     * writes in the queue come before it.
     */
    val nextWriteIndex: Int get() = writesStarted + tasks.count { it is Task.Write }

    /** S5: the writes that the live process may start: its admitted writes and its started retries. */
    val writesExpected: Int get() = writesAdmitted + retryWritesStarted

    /** S11 to S13: the number of retry events of the live process, recorded or still expected. */
    val retryEventsExpected: Int get() = retryEventsSeen + expectedRetryEvents.size

    /** S11: the elapsed due time of the retry that waits on the timer, or null when none waits. */
    val retryDueElapsed: Long? get() = retry?.takeIf { !it.fired }?.dueElapsed

    private var recordedWall = NONE
    private var recordedElapsed = NONE
    private var fallbackElapsed = NONE
    private var reseedRunning = false
    private var latestAdmission = 0
    private var epoch = 0
    private var stopped = false
    private val outcomes = HashMap<Int, ExpectedOutcome>()

    // S5: the writer queue, the write in flight, and the number of writes that began in the live process.
    private val tasks = ArrayDeque<Task>()
    private var inFlight: InFlight? = null
    private var writesStarted = 0

    // S11 to S13: the retry that owns the chain, a started retry whose write has not begun yet, the next sequence
    // number of the scheduler, and the retry events that the manager must record next, in order.
    private var retry: Retry? = null
    private var startedRetry: Retry? = null
    private var nextSequence = 1L
    private val expectedRetryEvents = ArrayDeque<RecoveryEvent>()
    private var retryEventsSeen = 0
    private var retryWritesStarted = 0

    /** A new process: memory is empty until its first read, and the cache is loaded from the durable state. */
    fun startProcess(generation: Int) {
        this.generation = generation
        cache = durable
        count = 0
        recordedWall = NONE
        recordedElapsed = NONE
        fallbackElapsed = NONE
        seedFailed = false
        reseedRequested = false
        reseedRunning = false
        latestAdmission = 0
        epoch = 0
        stopped = false
        writesAdmitted = 0
        readsExpected = 1
        outcomes.clear()
        tasks.clear()
        inFlight = null
        writesStarted = 0
        retry = null
        startedRetry = null
        nextSequence = 1L
        expectedRetryEvents.clear()
        retryEventsSeen = 0
        retryWritesStarted = 0
    }

    fun admitFailure(): LockoutState {
        reseedRequested = false // S4
        cancelRetry(RecoveryEvent.Cancelled.Reason.CANCELLED) // S12
        latestAdmission++
        count++
        val threshold = count >= THRESHOLD
        val windowMs = if (threshold) ladder(count) else BASE_MS
        val mutation = if (threshold) {
            val wallDeadline = clocks.wallMs() + windowMs
            val elapsedDeadline = clocks.elapsedMs() + windowMs
            fallbackElapsed = maxOf(fallbackElapsed, elapsedDeadline) // S2: extend, never shorten
            Mutation(
                nextWriteIndex, reset = false, latestAdmission, epoch, count, threshold = true, windowMs,
                LockoutSnapshot(count, wallDeadline), wallDeadline, elapsedDeadline,
            )
        } else {
            Mutation(
                nextWriteIndex, reset = false, latestAdmission, epoch, count, threshold = false, windowMs,
                LockoutSnapshot(count, recordedWall), recordedWall, recordedElapsed,
            )
        }
        admit(mutation)
        return state()
    }

    fun admitReset(): LockoutState {
        reseedRequested = false // S4
        cancelRetry(RecoveryEvent.Cancelled.Reason.CANCELLED) // S12
        latestAdmission++
        epoch++
        count = 0
        recordedWall = NONE
        recordedElapsed = NONE
        fallbackElapsed = NONE
        admit(
            Mutation(
                nextWriteIndex, reset = true, latestAdmission, epoch, count = 0, threshold = false, windowMs = 0L,
                LockoutSnapshot(0, NONE), NONE, NONE,
            )
        )
        return LockoutState.Available
    }

    /** S12: the manager stopped. The expected chain ends, and no later completion changes memory or starts a chain. */
    fun stop() {
        if (stopped) return
        stopped = true
        cancelRetry(RecoveryEvent.Cancelled.Reason.STOPPED)
    }

    /** The state that a poll returns. The poll also starts a re-seed read when S10 allows one. */
    fun poll(): LockoutState {
        val state = state()
        if (reseedRequested && !reseedRunning) {
            reseedRunning = true
            readsExpected++
        }
        return state
    }

    /** S8. */
    fun state(): LockoutState {
        val recorded = maxOf(
            remaining(recordedWall, clocks.wallMs()),
            remaining(recordedElapsed, clocks.elapsedMs()),
        )
        val fallback = remaining(fallbackElapsed, clocks.elapsedMs())
        return when {
            recorded == 0L && fallback == 0L -> LockoutState.Available
            recorded >= fallback -> LockoutState.LockedOut(recorded, degraded = false)
            else -> LockoutState.LockedOut(fallback, degraded = true)
        }
    }

    fun outcome(writeIndex: Int): ExpectedOutcome? = outcomes[writeIndex]

    /** Applies one storage event of the live process, in ledger order. */
    fun onStorageEvent(event: LedgerEvent.Storage) {
        require(event.generation == generation) { "event of g${event.generation} given to g$generation" }
        when (event.op) {
            StorageOp.READ -> onRead(event)
            StorageOp.WRITE -> onWrite(event)
        }
    }

    /**
     * Applies one retry event of the live process, in ledger order, at the elapsed time [elapsedMs] of the event. The
     * model predicts each scheduled, cancelled, and ended event. It checks each fired, started, and ignored event
     * against the writer queue and the chain.
     */
    fun onRetryEvent(event: RecoveryEvent, elapsedMs: Long) {
        retryEventsSeen++
        when (event) {
            is RecoveryEvent.Scheduled, is RecoveryEvent.Cancelled, is RecoveryEvent.Ended ->
                assertEquals("S11-S13: the next retry event", expectedRetryEvents.removeFirstOrNull(), event)
            is RecoveryEvent.Fired -> onFired(event, elapsedMs)
            is RecoveryEvent.Started -> onStarted(event)
            is RecoveryEvent.Ignored -> onIgnored(event)
        }
    }

    private fun onRead(event: LedgerEvent.Storage) {
        when (event.phase) {
            Phase.BEGIN -> if (event.index > 0) {
                assertTrue("S10: re-seed read #${event.index} started although no poll asked for one", reseedRunning)
            }
            Phase.RETURNED -> {
                assertEquals("read #${event.index} returns the process cache", cache, event.value)
                finishRead(event.index, event.value)
            }
            Phase.THREW -> finishRead(event.index, null)
            Phase.HELD -> Unit
            else -> throw AssertionError("unexpected read phase ${event.phase} in the live process")
        }
    }

    private fun finishRead(index: Int, value: LockoutSnapshot?) {
        if (index == 0) {
            if (value != null) { // S1
                count = value.failureCount
                recordedWall = value.lockoutUntil
            } else {
                seedFailed = true
                reseedRequested = true
            }
            return
        }
        reseedRunning = false
        if (value != null && reseedRequested) { // S10
            count = value.failureCount
            recordedWall = value.lockoutUntil
            recordedElapsed = NONE
            fallbackElapsed = NONE
            reseedRequested = false
        }
    }

    private fun onWrite(event: LedgerEvent.Storage) {
        if (event.phase == Phase.BEGIN) {
            beginWrite(event)
            return
        }
        val write = inFlight?.takeIf { it.index == event.index }
            ?: throw AssertionError("S5: write #${event.index} ${event.phase} is not the write in flight")
        assertEquals("harness: write #${event.index} keeps the script it started with", write.script, event.script)
        val payload = write.mutation.payload
        when (event.phase) {
            Phase.CACHE_UPDATED -> {
                assertTrue("harness: ${write.script} must not change the cache", write.script.updatesCache())
                assertEquals("cache payload of write #${event.index}", payload, event.value)
                cache = payload
            }
            Phase.DURABLE_COMMIT -> {
                assertTrue("harness: ${write.script} must not commit", write.script.commits())
                assertEquals("durable payload of write #${event.index}", payload, event.value)
                durable = payload
            }
            Phase.RETURNED -> {
                assertEquals("harness: result of ${write.script}", write.script.returnValue(), event.result)
                finishWrite(write, requireNotNull(event.result), event.elapsedMs)
            }
            Phase.THREW -> {
                assertEquals("harness: ${write.script} must not throw", null, write.script.returnValue())
                finishWrite(write, false, event.elapsedMs)
            }
            Phase.HELD -> Unit
            else -> throw AssertionError("unexpected write phase ${event.phase} in the live process")
        }
    }

    // S5 and S13: a write begins at the next store index. It is the write of the retry that the writer has just
    // started, with the payload of its target, or else the admitted write at the head of the writer queue.
    private fun beginWrite(event: LedgerEvent.Storage) {
        assertEquals("S5: write #${event.index} begins after the write before it ended", null, inFlight?.index)
        assertEquals("S5: the store numbers ordinary and retry writes in queue order", writesStarted, event.index)
        writesStarted++
        val started = startedRetry
        startedRetry = null
        val mutation = started?.target ?: takeAdmittedWrite(event.index)
        assertEquals("S2/S3/S13: payload of write #${event.index}", mutation.payload, event.value)
        inFlight = InFlight(event.index, event.script as WriteScript, mutation, started)
    }

    private fun takeAdmittedWrite(index: Int): Mutation {
        val head = tasks.firstOrNull() as? Task.Write
            ?: throw AssertionError("S5: write #$index began, but no admitted write heads the writer queue")
        tasks.removeFirst()
        assertEquals("S5: writes run in admission order", head.mutation.writeIndex, index)
        return head.mutation
    }

    private fun finishWrite(write: InFlight, committed: Boolean, completedElapsed: Long) {
        inFlight = null
        val started = write.retry
        if (started == null) {
            finishAdmittedWrite(write.mutation, committed, completedElapsed)
        } else {
            finishRetryWrite(started, committed, completedElapsed)
        }
    }

    private fun finishAdmittedWrite(mutation: Mutation, committed: Boolean, completedElapsed: Long) {
        if (!stopped) applyCompletion(mutation, committed, completedElapsed) // S12: no effect after a shutdown
        if (mutation.reset) {
            outcomes[mutation.writeIndex] = ExpectedOutcome.Reset(committed)
            return
        }
        val state = when { // S9
            mutation.threshold -> LockoutState.LockedOut(mutation.windowMs, degraded = !committed)
            committed -> LockoutState.Available
            else -> LockoutState.LockedOut(BASE_MS, degraded = true)
        }
        outcomes[mutation.writeIndex] = ExpectedOutcome.Failure(state, mutation.count)
    }

    private fun applyCompletion(mutation: Mutation, committed: Boolean, completedElapsed: Long) {
        val latest = mutation.admission == latestAdmission
        if (committed) {
            if (mutation.threshold && latest) { // S6
                recordedWall = mutation.recordedWall
                recordedElapsed = mutation.recordedElapsed
            }
            return
        }
        if (!mutation.reset && mutation.epoch == epoch) { // S7: a failed reset changes nothing in memory
            fallbackElapsed = maxOf(fallbackElapsed, completedElapsed + mutation.windowMs)
        }
        if (latest) scheduleRetry(mutation, attempt = 1, completedElapsed) // S11
    }

    // S12 and S13: the end of a retry write. Only a retry that still owns its chain publishes or asks for the next
    // attempt.
    private fun finishRetryWrite(started: Retry, committed: Boolean, completedElapsed: Long) {
        if (retry !== started) {
            expectedRetryEvents.addLast(RecoveryEvent.Ended(started.sequence, ENDED_ABANDONED))
            return
        }
        retry = null
        val target = started.target
        if (committed) {
            if (target.threshold && target.admission == latestAdmission) {
                recordedWall = target.recordedWall
                recordedElapsed = target.recordedElapsed
            }
            expectedRetryEvents.addLast(RecoveryEvent.Ended(started.sequence, ENDED_SUCCESS))
        } else {
            expectedRetryEvents.addLast(RecoveryEvent.Ended(started.sequence, ENDED_RETRY))
            scheduleRetry(target, started.attempt + 1, completedElapsed)
        }
    }

    // S11 and S13: the scheduler plans an attempt of the chain of [target], due its delay after [nowElapsed].
    private fun scheduleRetry(target: Mutation, attempt: Int, nowElapsed: Long) {
        val delayMs = retryDelay(attempt)
        val planned = Retry(nextSequence++, target, attempt, nowElapsed + delayMs)
        expectedRetryEvents.addLast(RecoveryEvent.Scheduled(planned.sequence, RecoveryKind.WRITE, attempt, delayMs))
        retry = planned
    }

    // S12: an admission or a shutdown ends the expected chain. The scheduler records the cancellation of a retry that
    // waits on the timer or in the writer queue. A started retry loses its chain without an event.
    private fun cancelRetry(reason: RecoveryEvent.Cancelled.Reason) {
        val owner = retry ?: return
        if (!owner.started) expectedRetryEvents.addLast(RecoveryEvent.Cancelled(owner.sequence, reason))
        retry = null
    }

    // S11: only the retry that waits on the timer fires, and not before its due time. Its task joins the writer queue.
    private fun onFired(event: RecoveryEvent.Fired, elapsedMs: Long) {
        assertNoExpectedEvent(event)
        val waiting = retry?.takeIf { it.sequence == event.sequence && !it.fired }
            ?: throw AssertionError("S11: retry ${event.sequence} fired, but it does not wait on the timer")
        assertTrue("S11: retry ${event.sequence} fires at its due time or later", elapsedMs >= waiting.dueElapsed)
        waiting.fired = true
        tasks.addLast(Task.RetryRun(event.sequence))
    }

    // S12: the writer starts a retry only while the retry still owns its chain.
    private fun onStarted(event: RecoveryEvent.Started) {
        assertNoExpectedEvent(event)
        takeRetryTask(event.sequence)
        val owner = retry?.takeIf { it.sequence == event.sequence }
            ?: throw AssertionError("S12: retry ${event.sequence} started after its chain ended")
        owner.started = true
        startedRetry = owner
        retryWritesStarted++
    }

    // S12: the writer ignores the task of a retry whose chain ended, and writes nothing for it.
    private fun onIgnored(event: RecoveryEvent.Ignored) {
        assertNoExpectedEvent(event)
        takeRetryTask(event.sequence)
        assertTrue("S12: retry ${event.sequence} is ignored after its chain ended", retry?.sequence != event.sequence)
        assertEquals("S12: reason for ignoring retry ${event.sequence}", IGNORED_STALE, event.reason)
    }

    // S5: the writer reaches the task of retry [sequence] at the head of the writer queue.
    private fun takeRetryTask(sequence: Long) {
        val head = tasks.firstOrNull()
        assertTrue("S5: retry $sequence heads the writer queue", head is Task.RetryRun && head.sequence == sequence)
        tasks.removeFirst()
    }

    // The manager records each predicted event right after its cause, so an event that the model only checks comes
    // after every predicted event.
    private fun assertNoExpectedEvent(event: RecoveryEvent) =
        assertTrue("S11-S13: $event came before $expectedRetryEvents", expectedRetryEvents.isEmpty())

    private fun admit(mutation: Mutation) {
        writesAdmitted++
        tasks.addLast(Task.Write(mutation))
    }

    private fun admittedWriteInFlight(): Boolean = inFlight.let { it != null && it.retry == null }

    private fun remaining(deadline: Long, now: Long): Long =
        if (deadline == NONE) 0L else (deadline - now).coerceIn(0L, MAX_MS)

    companion object {
        /** Contract values from the specification: threshold 5, base 30 s, doubling, cap 30 min. */
        const val THRESHOLD = 5
        const val BASE_MS = 30_000L
        const val MAX_MS = 30 * 60_000L
        private const val NONE = 0L

        /** The lockout duration for a failure count at or above the threshold: base, doubled per step, capped. */
        fun ladder(count: Int): Long {
            var duration = BASE_MS
            repeat((count - THRESHOLD).coerceIn(0, LADDER_STEPS_TO_CAP)) { duration = minOf(duration * 2, MAX_MS) }
            return duration
        }

        // 30 s doubles past 30 min after 6 steps; more steps cannot change the capped value.
        private const val LADDER_STEPS_TO_CAP = 8

        // S11 and S13: the first retry waits 1 s, and each later attempt doubles the delay up to 60 s. The delay
        // passes 60 s after 6 doublings, so more doublings cannot change the capped value.
        private const val RETRY_FIRST_MS = 1_000L
        private const val RETRY_CAP_MS = 60_000L
        private const val RETRY_STEPS_TO_CAP = 6

        private val IGNORED_STALE = RecoveryEvent.Ignored.Reason.STALE
        private val ENDED_SUCCESS = RecoveryEvent.Ended.Outcome.SUCCESS
        private val ENDED_RETRY = RecoveryEvent.Ended.Outcome.RETRY
        private val ENDED_ABANDONED = RecoveryEvent.Ended.Outcome.ABANDONED

        private fun retryDelay(attempt: Int): Long {
            var delay = RETRY_FIRST_MS
            repeat((attempt - 1).coerceIn(0, RETRY_STEPS_TO_CAP)) { delay = minOf(delay * 2, RETRY_CAP_MS) }
            return delay
        }
    }
}
