package com.applock.security

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

/** The kind of a retry chain: a retried storage read or a retried storage write. */
enum class RecoveryKind { READ, WRITE }

/** The decision of the `complete` step of a retry. */
enum class RecoveryStep {
    /** The retry reached its goal, and the chain ends. */
    SUCCESS,

    /** The attempt failed, and the scheduler plans the next attempt. */
    RETRY,

    /** The chain is no longer wanted, and it ends without a further attempt. */
    ABANDON,
}

/**
 * The storage operation of one retry attempt. The scheduler calls [perform] on the writer without the manager lock.
 * It calls [complete] under the manager lock, and only while the retry still owns the scheduler state.
 */
interface RecoveryOperation {
    /** Does the storage I/O. Returns false when the attempt fails, also for a storage fault. */
    fun perform(): Boolean

    /** Applies the result, with no I/O, and decides the next step. It must not throw. */
    fun complete(succeeded: Boolean): RecoveryStep
}

/** Supplies the operation of each attempt of one retry chain. */
fun interface RecoveryAction {
    /**
     * Called on the writer under the manager lock, with no I/O, and must not throw. Returns the operation of this
     * attempt, or null when the retry is no longer wanted.
     */
    fun prepare(): RecoveryOperation?
}

/** A lifecycle event of one retry. The harness keeps these events as test evidence; production ignores them. */
sealed interface RecoveryEvent {
    val sequence: Long

    data class Scheduled(
        override val sequence: Long,
        val kind: RecoveryKind,
        val attempt: Int,
        val delayMs: Long,
    ) : RecoveryEvent

    data class Fired(override val sequence: Long) : RecoveryEvent

    data class Cancelled(override val sequence: Long, val reason: Reason) : RecoveryEvent {
        enum class Reason { REPLACED, CANCELLED, STOPPED }
    }

    data class Ignored(override val sequence: Long, val reason: Reason) : RecoveryEvent {
        enum class Reason { STALE, NOT_WANTED }
    }

    data class Started(override val sequence: Long) : RecoveryEvent

    data class Ended(override val sequence: Long, val outcome: Outcome) : RecoveryEvent {
        enum class Outcome { SUCCESS, RETRY, ABANDONED, THREW }
    }
}

/** Receives the [RecoveryEvent]s of a scheduler, under the manager lock or on the timer thread. */
fun interface RecoveryListener {
    fun onEvent(event: RecoveryEvent)

    companion object {
        /** The production listener. It ignores every event, so recovery creates no audit event. */
        val NONE = RecoveryListener { }
    }
}

/** Maps the attempt number of a chain to its delay: [firstMs] for attempt 1, doubling up to [capMs]. */
class RecoveryBackoff(private val firstMs: Long = FIRST_MS, private val capMs: Long = CAP_MS) {
    init {
        require(firstMs > 0 && capMs >= firstMs) { "a backoff needs 0 < firstMs <= capMs" }
    }

    fun delayFor(attempt: Int): Long {
        require(attempt >= 1) { "attempts start at 1" }
        var delay = firstMs
        repeat(attempt - 1) {
            // A delay above half the cap would pass the cap, or overflow, when it doubles.
            if (delay > capMs / 2) return capMs
            delay *= 2
        }
        return delay
    }

    companion object {
        const val FIRST_MS = 1_000L
        const val CAP_MS = 60_000L

        /** The production delays: 1 s, doubling up to 60 s, with no attempt limit. */
        val PRODUCTION = RecoveryBackoff()
    }
}

/** The recovery dependencies of one manager. The defaults are the production timer, delays, and listener. */
class RecoverySetup(
    val timer: RecoveryTimer = ScheduledRecoveryTimer(),
    val backoff: RecoveryBackoff = RecoveryBackoff.PRODUCTION,
    val listener: RecoveryListener = RecoveryListener.NONE,
)

/**
 * Holds at most one pending retry for one lockout manager (R-007 hardening, phase P3). The contract is in
 * `2026-10-07_R007_F2_HARDENING_P3_SCHEDULER_SPEC.md`. The caller decides when a chain starts and what each attempt
 * does.
 *
 * Every scheduled retry gets the next sequence number, and only the newest one owns the scheduler state. [start],
 * [cancel], and [stop] make the previous number stale. A stale retry never changes the state. A queued one is ignored
 * before `prepare`. A running one finishes its I/O, because blocking I/O cannot be interrupted safely, but never
 * reaches `complete`.
 *
 * The wait runs on the [RecoveryTimer]. A firing takes no lock and reads no scheduler state: it only appends the retry
 * to the writer queue through [launchOnWriter], behind the writes already there. On the writer, `prepare` and
 * `complete` run under [lock] and `perform` runs without it, so no thread holds the lock during a delay or during
 * storage I/O.
 *
 * [start], [scheduleNext], [cancel], [stop], and [pendingKind] must be called under [lock].
 */
class RecoveryScheduler(
    private val lock: Any,
    private val setup: RecoverySetup,
    // Runs a block on the manager's single writer, behind the work already queued there.
    private val launchOnWriter: (suspend () -> Unit) -> Unit,
) {
    // The mutable fields are guarded by [lock].
    private class Chain(val sequence: Long, val kind: RecoveryKind, val attempt: Int, val action: RecoveryAction) {
        var timerHandle: RecoveryTimer.Handle? = null

        // True while the operation of this chain performs its I/O.
        var running = false
    }

    // Guarded by [lock]. [current] is the chain that owns the state: pending, queued, or running.
    private var nextSequence = 1L
    private var current: Chain? = null
    private var stopped = false

    /** Starts a new chain at attempt 1 in place of the current one. Returns its sequence number, or null if stopped. */
    fun start(kind: RecoveryKind, action: RecoveryAction): Long? {
        requireLock()
        if (stopped) return null
        current?.let { release(it, RecoveryEvent.Cancelled.Reason.REPLACED) }
        return schedule(Chain(nextSequence++, kind, attempt = 1, action))
    }

    /** Ends the current chain. A running retry finishes its I/O without publishing its result. */
    fun cancel() {
        requireLock()
        current?.let { release(it, RecoveryEvent.Cancelled.Reason.CANCELLED) }
        current = null
    }

    /** Ends the current chain, refuses every later chain, and stops the timer. */
    fun stop() {
        requireLock()
        if (stopped) return
        stopped = true
        current?.let { release(it, RecoveryEvent.Cancelled.Reason.STOPPED) }
        current = null
        setup.timer.stop()
    }

    /** The kind of the pending, queued, or running chain, or null when no chain exists. */
    fun pendingKind(): RecoveryKind? {
        requireLock()
        return current?.kind
    }

    /**
     * Plans the next attempt of the chain with [sequence], if that chain still owns the state. Returns whether it
     * planned one.
     */
    fun scheduleNext(sequence: Long): Boolean {
        requireLock()
        val chain = current
        if (stopped || chain == null || chain.sequence != sequence) return false
        schedule(Chain(nextSequence++, chain.kind, chain.attempt + 1, chain.action))
        return true
    }

    private fun schedule(chain: Chain): Long {
        current = chain
        val delayMs = setup.backoff.delayFor(chain.attempt)
        val sequence = chain.sequence
        // The event comes first, so a short real delay cannot log the firing before the plan.
        emit(RecoveryEvent.Scheduled(sequence, chain.kind, chain.attempt, delayMs))
        chain.timerHandle = setup.timer.schedule(delayMs) { fire(sequence) }
        return sequence
    }

    // Cancels the timer task of a pending or queued chain and records the cancellation. A running chain keeps its I/O
    // and records nothing here, because its run ends as abandoned. The caller changes [current].
    private fun release(chain: Chain, reason: RecoveryEvent.Cancelled.Reason) {
        if (chain.running) return
        chain.timerHandle?.cancel()
        emit(RecoveryEvent.Cancelled(chain.sequence, reason))
    }

    // Runs on the timer thread. It takes no lock and reads no scheduler state: it only adds the retry to the writer
    // queue.
    private fun fire(sequence: Long) {
        emit(RecoveryEvent.Fired(sequence))
        launchOnWriter { runOnWriter(sequence) }
    }

    @Suppress("TooGenericExceptionCaught") // every throwable ends the run, and each one is rethrown
    private suspend fun runOnWriter(sequence: Long) {
        val context = currentCoroutineContext()
        val (chain, operation) = synchronized(lock) { begin(sequence) } ?: return
        val succeeded = try {
            perform(operation)
        } catch (e: Throwable) {
            // The manager job itself was cancelled, or the operation threw an error: the run ends, and no retry
            // follows.
            synchronized(lock) { finish(chain, RecoveryEvent.Ended.Outcome.THREW) }
            throw e
        }
        // Blocking I/O can return normally after the manager job was cancelled, and the job can also be cancelled
        // while the writer waits for the lock. The job is therefore checked under the lock, right before `complete`.
        val cancelled = synchronized(lock) {
            if (context.isActive) {
                complete(chain, operation, succeeded)
                false
            } else {
                finish(chain, RecoveryEvent.Ended.Outcome.THREW)
                true
            }
        }
        // Rethrows the cancellation of the manager job, as the other cancelled runs do.
        if (cancelled) context.ensureActive()
    }

    // Under [lock]: ignores a stale retry. Otherwise it asks the action for the operation of this attempt.
    private fun begin(sequence: Long): Pair<Chain, RecoveryOperation>? {
        val chain = current
        if (stopped || chain == null || chain.sequence != sequence) {
            emit(RecoveryEvent.Ignored(sequence, RecoveryEvent.Ignored.Reason.STALE))
            return null
        }
        val operation = chain.action.prepare()
        if (operation == null) {
            current = null
            emit(RecoveryEvent.Ignored(sequence, RecoveryEvent.Ignored.Reason.NOT_WANTED))
            return null
        }
        chain.running = true
        emit(RecoveryEvent.Started(sequence))
        return chain to operation
    }

    // Without the lock. An adapter cancellation while the manager job runs is an unsuccessful storage operation; a
    // cancellation of the job itself propagates. A storage exception that the operation did not catch is a failed
    // attempt. An Error propagates.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun perform(operation: RecoveryOperation): Boolean =
        try {
            operation.perform()
        } catch (e: CancellationException) {
            if (currentCoroutineContext().isActive) false else throw e
        } catch (e: Exception) {
            false
        }

    // Under [lock]: a run that lost ownership ends without `complete`; an owning run applies its result.
    private fun complete(chain: Chain, operation: RecoveryOperation, succeeded: Boolean) {
        if (current !== chain) return finish(chain, RecoveryEvent.Ended.Outcome.ABANDONED)
        chain.running = false
        when (operation.complete(succeeded)) {
            RecoveryStep.SUCCESS -> finish(chain, RecoveryEvent.Ended.Outcome.SUCCESS)
            RecoveryStep.ABANDON -> finish(chain, RecoveryEvent.Ended.Outcome.ABANDONED)
            RecoveryStep.RETRY -> {
                emit(RecoveryEvent.Ended(chain.sequence, RecoveryEvent.Ended.Outcome.RETRY))
                scheduleNext(chain.sequence)
            }
        }
    }

    // Under [lock]: ends a run. An owning run gives up the state and records [outcome]. A run without ownership
    // records ABANDONED.
    private fun finish(chain: Chain, outcome: RecoveryEvent.Ended.Outcome) {
        chain.running = false
        if (current === chain) {
            current = null
            emit(RecoveryEvent.Ended(chain.sequence, outcome))
        } else {
            emit(RecoveryEvent.Ended(chain.sequence, RecoveryEvent.Ended.Outcome.ABANDONED))
        }
    }

    private fun emit(event: RecoveryEvent) = setup.listener.onEvent(event)

    private fun requireLock() = check(Thread.holdsLock(lock)) { "call the recovery scheduler under the manager lock" }
}
