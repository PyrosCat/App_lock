package com.applock.security.harness

import com.applock.security.LockoutSnapshot
import com.applock.security.LockoutState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** The outcome that the model expects for one admitted operation. */
sealed interface ExpectedOutcome {
    data class Failure(val state: LockoutState, val count: Int) : ExpectedOutcome

    data class Reset(val committed: Boolean) : ExpectedOutcome
}

/**
 * The reference model of the lockout contract (FR-174, R-007) that the harness uses as its oracle. It is written
 * from the F2 specification (the M7_PLAN F2 entry and the public LockoutManager contract), not from the manager's
 * code. From the harness actions and the storage ledger, it predicts the public state of the live process, the
 * position and payload of every write, the value of every read, the outcome of every operation, the process cache,
 * and the durable state. An assertion message names the rule it checks.
 *
 * - S1 Seed: a fresh process takes its count and wall deadline from its first read. A failed first read leaves the
 *   process empty, marks the seed as failed, and asks for a recovery read (S10).
 * - S2 Failure: the count increases by one. At or above the threshold, the in-memory fallback extends to now plus
 *   the ladder duration (it never shortens), and the write carries the count and the wall time now plus that
 *   duration. Below the threshold, the write carries the count and the current recorded wall deadline.
 * - S3 Reset: the count and every deadline clear at once, and the authentication epoch advances. The write carries
 *   (0, 0).
 * - S4 Every admission cancels the request for a recovery read and its pending retry.
 * - S5 Writes run one at a time, in admission order.
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
 * - S10 Recovery read (candidate B1): a poll starts no read. After a failed first read, the first retry is due 1 s
 *   later on the elapsed clock. A retry fires when an advance reaches its due time. Its read runs only while the
 *   request stands and the manager runs. A successful read applies as in S1, with no elapsed mirror and no fallback,
 *   and ends the request. A failed read, including a cancellation that storage throws, schedules the next retry from
 *   the end of the read. The delays are 1, 2, 4, 8, 16, and 32 s, then 60 s. A read whose request ended changes
 *   nothing and schedules nothing. Stopping the manager also cancels the pending retry. The model computes each retry
 *   event from these rules and checks every retry event of the ledger against it.
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
    ) {
        // The script that the write took when it started (its BEGIN event). The later phases of the write follow
        // this script, even if the fault plan changes while the write is parked.
        var script: WriteScript? = null
    }

    /** Where a retry of S10 waits: on the timer, in the writer queue, or in its running read. */
    private enum class RetryPhase { PENDING, QUEUED, RUNNING }

    /** The retry that owns the scheduler state: its sequence number, its attempt, and its due elapsed time. */
    private class Retry(val sequence: Long, val attempt: Int, val dueElapsed: Long) {
        var phase = RetryPhase.PENDING
    }

    /** A predicted retry event as the ledger shows it, with its elapsed time when the model knows it. */
    private class ExpectedRetryEvent(val text: String, val elapsedMs: Long?)

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
    var readsExpected = 0
        private set
    var writesAdmitted = 0
        private set

    /** Admitted writes that have not finished yet, in admission order. */
    val pendingWrites: Int get() = queue.size

    private var recordedWall = NONE
    private var recordedElapsed = NONE
    private var fallbackElapsed = NONE
    private var latestAdmission = 0
    private var epoch = 0
    private val queue = ArrayDeque<Mutation>()
    private val outcomes = HashMap<Int, ExpectedOutcome>()

    // S10 state of the live process.
    private var recoveryRequested = false
    private var managerRunning = true
    private var retry: Retry? = null
    private var nextRetrySequence = 1L

    // The sequence number of a recovery read that lost its retry while it ran. Its end is recorded as abandoned.
    private var abandonedRead: Long? = null
    private val expectedRetryEvents = ArrayDeque<ExpectedRetryEvent>()

    /** A new process: memory is empty until its first read, and the cache is loaded from the durable state. */
    fun startProcess(generation: Int) {
        this.generation = generation
        cache = durable
        count = 0
        recordedWall = NONE
        recordedElapsed = NONE
        fallbackElapsed = NONE
        seedFailed = false
        latestAdmission = 0
        epoch = 0
        writesAdmitted = 0
        readsExpected = 1
        queue.clear()
        outcomes.clear()
        recoveryRequested = false
        managerRunning = true
        retry = null
        nextRetrySequence = 1L
        abandonedRead = null
        expectedRetryEvents.clear()
    }

    fun admitFailure(): LockoutState {
        endRecovery(CANCELLED_BY_ADMISSION) // S4
        latestAdmission++
        count++
        val threshold = count >= THRESHOLD
        val windowMs = if (threshold) ladder(count) else BASE_MS
        val mutation = if (threshold) {
            val wallDeadline = clocks.wallMs() + windowMs
            val elapsedDeadline = clocks.elapsedMs() + windowMs
            fallbackElapsed = maxOf(fallbackElapsed, elapsedDeadline) // S2: extend, never shorten
            Mutation(
                writesAdmitted++, reset = false, latestAdmission, epoch, count, threshold = true, windowMs,
                LockoutSnapshot(count, wallDeadline), wallDeadline, elapsedDeadline,
            )
        } else {
            Mutation(
                writesAdmitted++, reset = false, latestAdmission, epoch, count, threshold = false, windowMs,
                LockoutSnapshot(count, recordedWall), recordedWall, recordedElapsed,
            )
        }
        queue.addLast(mutation)
        return state()
    }

    fun admitReset(): LockoutState {
        endRecovery(CANCELLED_BY_ADMISSION) // S4
        latestAdmission++
        epoch++
        count = 0
        recordedWall = NONE
        recordedElapsed = NONE
        fallbackElapsed = NONE
        queue.addLast(
            Mutation(
                writesAdmitted++, reset = true, latestAdmission, epoch, count = 0, threshold = false, windowMs = 0L,
                LockoutSnapshot(0, NONE), NONE, NONE,
            )
        )
        return LockoutState.Available
    }

    /** The state that a poll returns. A poll starts no read (S10). */
    fun poll(): LockoutState = state()

    /** S10: after the manager stops, no retry runs. The model has no other rule for a stopped manager. */
    fun stopManager() {
        managerRunning = false
        endRecovery(CANCELLED_BY_STOP)
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
     * Checks one retry event of the live process against S10, in ledger order. The model predicts the scheduled,
     * cancelled, ignored, and ended events, and each must match the next prediction. A firing must come at or after
     * the pending retry's due time, and a start must follow a firing while the request stands and the manager runs.
     */
    fun onRetryEvent(event: LedgerEvent.Action) {
        require(event.generation == generation) { "event of g${event.generation} given to g$generation" }
        val text = "${event.name} ${event.detail}"
        val expected = expectedRetryEvents.removeFirstOrNull()
        if (expected != null) {
            assertEquals("S10: the next retry event", expected.text, text)
            expected.elapsedMs?.let { assertEquals("S10: the elapsed time of $text", it, event.elapsedMs) }
            return
        }
        val current = retry ?: throw AssertionError("S10: no retry event without a retry: $text")
        assertEquals("S10: the event of the current retry", "seq=${current.sequence}", event.detail)
        when (event.name) {
            "RETRY_FIRED" -> {
                assertEquals("S10: a pending retry fires", RetryPhase.PENDING, current.phase)
                assertTrue("S10: a retry fires at or after its due time", event.elapsedMs >= current.dueElapsed)
                current.phase = RetryPhase.QUEUED
            }
            "RETRY_STARTED" -> {
                assertEquals("S10: a fired retry starts", RetryPhase.QUEUED, current.phase)
                assertTrue("S10: a retry starts while the request stands", recoveryRequested && managerRunning)
                current.phase = RetryPhase.RUNNING
                readsExpected++
            }
            else -> throw AssertionError("S10: no other retry event at this point: $text")
        }
    }

    /**
     * S10: an advance fires every due retry, so a pending retry is not due yet. When the executor is [idle], every
     * expected retry event has occurred, and no fired retry or recovery read waits.
     */
    fun checkRetries(idle: Boolean) {
        val current = retry
        if (current != null && current.phase == RetryPhase.PENDING) {
            assertTrue("S10: a due retry has fired", clocks.elapsedMs() < current.dueElapsed)
        }
        if (idle) {
            val missing = expectedRetryEvents.map { it.text }
            assertEquals("S10: every expected retry event occurred", emptyList<String>(), missing)
            assertTrue("S10: no fired retry waits", current == null || current.phase == RetryPhase.PENDING)
            assertEquals("S10: no recovery read is unfinished", null, abandonedRead)
        }
    }

    private fun onRead(event: LedgerEvent.Storage) {
        when (event.phase) {
            Phase.BEGIN -> if (event.index > 0) {
                assertEquals("S10: read #${event.index} belongs to a started retry", RetryPhase.RUNNING, retry?.phase)
            }
            Phase.RETURNED -> {
                assertEquals("read #${event.index} returns the process cache", cache, event.value)
                finishRead(event, event.value)
            }
            Phase.THREW -> finishRead(event, null)
            Phase.HELD -> Unit
            else -> throw AssertionError("unexpected read phase ${event.phase} in the live process")
        }
    }

    private fun finishRead(event: LedgerEvent.Storage, value: LockoutSnapshot?) {
        if (event.index == 0) {
            if (value != null) { // S1
                count = value.failureCount
                recordedWall = value.lockoutUntil
            } else {
                seedFailed = true
                recoveryRequested = true
                scheduleRetry(attempt = 1, fromElapsed = event.elapsedMs) // S10
            }
            return
        }
        val owner = retry?.takeIf { it.phase == RetryPhase.RUNNING }
        if (owner == null) { // S10: the request ended during the read
            val sequence = abandonedRead ?: throw AssertionError("S10: read #${event.index} belongs to a recovery read")
            abandonedRead = null
            expectRetryEvent("RETRY_ENDED seq=$sequence outcome=abandoned", event.elapsedMs)
            return
        }
        retry = null
        if (value != null) { // S10: the read applies as S1 does
            count = value.failureCount
            recordedWall = value.lockoutUntil
            recordedElapsed = NONE
            fallbackElapsed = NONE
            recoveryRequested = false
            expectRetryEvent("RETRY_ENDED seq=${owner.sequence} outcome=success", event.elapsedMs)
        } else {
            expectRetryEvent("RETRY_ENDED seq=${owner.sequence} outcome=retry", event.elapsedMs)
            scheduleRetry(owner.attempt + 1, event.elapsedMs)
        }
    }

    // S10: plans the next retry of the chain from [fromElapsed], the end of the failed read.
    private fun scheduleRetry(attempt: Int, fromElapsed: Long) {
        val delayMs = retryDelay(attempt)
        val sequence = nextRetrySequence++
        retry = Retry(sequence, attempt, fromElapsed + delayMs)
        expectRetryEvent("RETRY_SCHEDULED seq=$sequence kind=READ attempt=$attempt delay=$delayMs", fromElapsed)
    }

    // S4 and the S10 stop: the request ends, and the retry is cancelled for [reason]. A queued retry still reaches
    // the writer, which ignores it. A running read finishes, and its end is recorded as abandoned.
    private fun endRecovery(reason: String) {
        recoveryRequested = false
        val current = retry ?: return
        retry = null
        val cancelled = "RETRY_CANCELLED seq=${current.sequence} reason=$reason"
        when (current.phase) {
            RetryPhase.PENDING -> expectRetryEvent(cancelled, clocks.elapsedMs())
            RetryPhase.QUEUED -> {
                expectRetryEvent(cancelled, clocks.elapsedMs())
                expectRetryEvent("RETRY_IGNORED seq=${current.sequence} reason=stale", elapsedMs = null)
            }
            RetryPhase.RUNNING -> abandonedRead = current.sequence
        }
    }

    private fun expectRetryEvent(text: String, elapsedMs: Long?) {
        expectedRetryEvents.addLast(ExpectedRetryEvent(text, elapsedMs))
    }

    private fun onWrite(event: LedgerEvent.Storage) {
        val head = queue.firstOrNull()
            ?: throw AssertionError("S5: write #${event.index} ${event.phase} with no admitted mutation")
        assertEquals("S5: writes run in admission order", head.writeIndex, event.index)
        if (event.phase == Phase.BEGIN) {
            assertEquals("S2/S3: payload of write #${event.index}", head.payload, event.value)
            head.script = event.script as WriteScript
            return
        }
        val script = checkNotNull(head.script) { "write #${event.index} ${event.phase} before its BEGIN" }
        assertEquals("harness: write #${event.index} keeps the script it started with", script, event.script)
        when (event.phase) {
            Phase.CACHE_UPDATED -> {
                assertTrue("harness: $script must not change the cache", script.updatesCache())
                assertEquals("cache payload of write #${event.index}", head.payload, event.value)
                cache = head.payload
            }
            Phase.DURABLE_COMMIT -> {
                assertTrue("harness: $script must not commit", script.commits())
                assertEquals("durable payload of write #${event.index}", head.payload, event.value)
                durable = head.payload
            }
            Phase.RETURNED -> {
                assertEquals("harness: result of $script", script.returnValue(), event.result)
                finishWrite(requireNotNull(event.result), event.elapsedMs)
            }
            Phase.THREW -> {
                assertEquals("harness: $script must not throw", null, script.returnValue())
                finishWrite(false, event.elapsedMs)
            }
            Phase.HELD -> Unit
            else -> throw AssertionError("unexpected write phase ${event.phase} in the live process")
        }
    }

    private fun finishWrite(committed: Boolean, completedElapsed: Long) {
        val mutation = queue.removeFirst()
        if (mutation.reset) {
            outcomes[mutation.writeIndex] = ExpectedOutcome.Reset(committed) // S7: memory unchanged either way
            return
        }
        if (committed) {
            if (mutation.threshold && mutation.admission == latestAdmission) { // S6
                recordedWall = mutation.recordedWall
                recordedElapsed = mutation.recordedElapsed
            }
        } else if (mutation.epoch == epoch) { // S7
            fallbackElapsed = maxOf(fallbackElapsed, completedElapsed + mutation.windowMs)
        }
        val state = when { // S9
            mutation.threshold -> LockoutState.LockedOut(mutation.windowMs, degraded = !committed)
            committed -> LockoutState.Available
            else -> LockoutState.LockedOut(BASE_MS, degraded = true)
        }
        outcomes[mutation.writeIndex] = ExpectedOutcome.Failure(state, mutation.count)
    }

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

        // S10: the delays of the retry policy, and the reasons of a cancelled retry as the ledger shows them.
        private const val FIRST_RETRY_MS = 1_000L
        private const val RETRY_CAP_MS = 60_000L
        private const val CANCELLED_BY_ADMISSION = "cancelled"
        private const val CANCELLED_BY_STOP = "stopped"

        // 1 s doubles past 60 s after 6 steps; more steps cannot change the capped value.
        private const val RETRY_STEPS_TO_CAP = 6

        /** S10: the delay before retry [attempt] of a chain: 1 s, doubled for each later attempt, capped at 60 s. */
        private fun retryDelay(attempt: Int): Long {
            var delayMs = FIRST_RETRY_MS
            repeat((attempt - 1).coerceIn(0, RETRY_STEPS_TO_CAP)) { delayMs = minOf(delayMs * 2, RETRY_CAP_MS) }
            return delayMs
        }
    }
}
