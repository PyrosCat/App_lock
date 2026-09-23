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
 *   process empty, marks the seed as failed, and asks for a re-seed.
 * - S2 Failure: the count increases by one. At or above the threshold, the in-memory fallback extends to now plus
 *   the ladder duration (it never shortens), and the write carries the count and the wall time now plus that
 *   duration. Below the threshold, the write carries the count and the current recorded wall deadline.
 * - S3 Reset: the count and every deadline clear at once, and the authentication epoch advances. The write carries
 *   (0, 0).
 * - S4 Every admission cancels the request for a re-seed.
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
 * - S10 A poll starts one re-seed read when a re-seed is requested and none runs. A successful re-seed read applies
 *   only while the request stands: the count and the wall deadline come from storage, with no elapsed mirror and no
 *   fallback.
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

    /** Admitted writes that have not finished yet, in admission order. */
    val pendingWrites: Int get() = queue.size

    private var recordedWall = NONE
    private var recordedElapsed = NONE
    private var fallbackElapsed = NONE
    private var reseedRunning = false
    private var latestAdmission = 0
    private var epoch = 0
    private val queue = ArrayDeque<Mutation>()
    private val outcomes = HashMap<Int, ExpectedOutcome>()

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
        writesAdmitted = 0
        readsExpected = 1
        queue.clear()
        outcomes.clear()
    }

    fun admitFailure(): LockoutState {
        reseedRequested = false // S4
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
        reseedRequested = false // S4
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
    }
}
