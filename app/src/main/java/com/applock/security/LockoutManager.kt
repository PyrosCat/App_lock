package com.applock.security

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * An atomic snapshot of the persisted lockout counters. Both fields describe one durable observation, so a
 * reader never sees a half-updated pair and a writer commits both fields as one unit (FR-174).
 */
data class LockoutSnapshot(val failureCount: Int, val lockoutUntil: Long)

/**
 * Persisted failure counters. Backed by storage that survives process death so lockout cannot be bypassed by
 * force-stopping the app (FR-174). The interface is an atomic-snapshot read and write: [read] returns both
 * fields as one observation, and [write] commits both fields as one durable operation and returns whether the
 * commit succeeded. A write that returns false, or a read that throws, is a storage fault the manager degrades
 * around (R-007).
 */
interface LockoutStorage {
    /** Reads both counters as one observation. It may throw on a decryption or IO fault. */
    fun read(): LockoutSnapshot

    /** Commits both counters as one durable operation. Returns true only when the commit reached storage. */
    fun write(snapshot: LockoutSnapshot): Boolean
}

sealed interface LockoutState {
    data object Available : LockoutState

    /**
     * Authentication is blocked for [remainingMs]. [degraded] is true when the lockout is held in memory only,
     * because its durable write failed or has not resolved yet (R-007). A degraded lockout still blocks, but it is
     * never audited as a recorded `LOCKOUT_TRIGGERED` and it does not survive process death. The default false is
     * the ordinary recorded lockout.
     */
    data class LockedOut(val remainingMs: Long, val degraded: Boolean = false) : LockoutState
}

/**
 * Brute-force protection (FR-009/FR-010/FR-174). Pure Kotlin: the clocks, the storage, and the persistence
 * dispatcher are injected, so this runs on the JVM in the unit tests.
 *
 * Failures below [FAILURE_THRESHOLD] are only counted. From the threshold on, every failure starts a lockout
 * window whose duration doubles per failure ([BASE_LOCKOUT_MS] at the threshold, capped at [MAX_LOCKOUT_MS]). A
 * successful authentication resets everything.
 *
 * ## Storage-failure handling (R-007)
 * The old store swallowed write failures ([android.content.SharedPreferences] `apply()`), so a degraded store
 * could silently drop the counter and never enforce a lockout. This manager makes storage failure a first-class
 * outcome. It keeps an in-memory authoritative snapshot, published lock-free, and enforces from it immediately,
 * while a durable write is awaited off the caller thread. If the write fails, an in-memory degraded fallback still
 * blocks the next attempt (fail-secure), even below the threshold.
 *
 * ## Concurrency model
 * One ordering boundary is a plain [lock] (a monitor, not a coroutine `Mutex`), so the synchronous legacy callers
 * and the non-suspend [shutdown] can enter it. It is not held during storage writes: inside that short section
 * (CPU and enqueue only, never across IO) each mutation assigns the next [Snapshot.revision], updates and publishes
 * the snapshot, and enqueues its durable write to a single-thread dispatcher. So snapshot order equals admission
 * order equals the dispatcher FIFO order. Each write is stamped with its revision and its authentication
 * [Snapshot.streak].
 *
 * ## Completion, split by outcome
 * A successful write publishes its recorded durability only while its revision is still current, so a returning
 * older success cannot overwrite a newer change. A failed failure-write arms or extends the degraded monotonic
 * deadline while its streak still equals the current streak, even if its revision is now stale, so an older failure
 * in the same streak still blocks the next attempt. A successful reset advances the streak, which fences off an
 * older failed write so it cannot reinstate a lockout after the reset.
 */
@Suppress("TooManyFunctions") // one cohesive lockout boundary: the read, the two mutations, and the lifecycle
class LockoutManager(
    private val storage: LockoutStorage,
    private val clock: () -> Long = System::currentTimeMillis,
    // A monotonic clock for the enforcement deadline, immune to a wall-clock jump. It never persists (R-007
    // residual: a degrade does not survive process death). The default is JVM-pure for tests. Android callers must
    // inject a boot-time clock (SystemClock.elapsedRealtime): System.nanoTime pauses during deep sleep, which (with
    // the longer-remaining rule in stateOf) would keep a lockout alive past its wall deadline after wake. AppModule
    // injects the sleep-aware clock for production.
    private val elapsedRealtime: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
    // The durable IO runs here, one task at a time, in admission order. Injected so a test can drive it.
    private val ioDispatcher: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { r -> Thread(r, "lockout-io").apply { isDaemon = true } }
            .asCoroutineDispatcher(),
    // The scope that owns the write and re-seed coroutines. [shutdown] sets `stopped` but does NOT cancel it, so an
    // in-flight write drains to completion (its completion effect is dropped by the stopped check). Defaulted for
    // production.
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher),
) {

    /**
     * The in-memory authoritative view. [revision] increases on every mutation; [streak] increases on every reset.
     *
     * The recorded (persisted) lockout and the in-memory degraded fallback are tracked separately, so a delayed
     * threshold write whose persisted deadline has already elapsed cannot mislabel the fresher fallback as durable.
     * [recordedWallDeadline] is the persisted deadline in [clock] epoch millis; [recordedMonoDeadline] is its
     * [elapsedRealtime] mirror, set only when a threshold write commits, so a wall-clock jump cannot break a recorded
     * lockout. [fallbackMonoDeadline] is the in-memory degraded fallback in [elapsedRealtime] millis (from a failed
     * write, a below-threshold write failure, or a not-yet-committed threshold failure). The standing lockout is
     * recorded when the recorded remaining is at least the fallback remaining, else degraded; [degraded] on the
     * returned state is therefore derived, never stored.
     */
    private data class Snapshot(
        val revision: Long,
        val streak: Long,
        val failureCount: Int,
        val recordedWallDeadline: Long,
        val recordedMonoDeadline: Long,
        val fallbackMonoDeadline: Long,
    )

    private enum class Kind { FAILURE, RESET }

    /** One admitted mutation: what to persist, plus the identity and per-operation facts that govern completion. */
    @Suppress("LongParameterList") // each field records a distinct fact the completion needs; a bag would obscure them
    private class PendingWrite(
        val revision: Long,
        val streak: Long,
        val kind: Kind,
        val persist: LockoutSnapshot,
        // This operation's own failure count, captured at admission, so overlapping attempts each report their own
        // count and a later reset cannot rewrite an earlier failure's reported count.
        val count: Int,
        // Whether this operation intended a threshold lockout (a durable deadline), not a below-threshold count.
        // Only a committed threshold write is a recorded lockout.
        val wasThreshold: Boolean,
        // The degraded fallback window (a duration, not an absolute deadline). On a failed write the deadline is
        // computed fresh from the completion time, so a write that stalled past the window cannot install an
        // already-expired fallback.
        val fallbackDurationMs: Long,
        // The recorded deadlines this threshold write intends to make durable, captured at admission. On a committed
        // threshold write they promote the snapshot's recorded lockout. Both cover the admission window, so a write
        // that stalled past the window promotes an already-elapsed deadline (which the derived degraded flag then
        // treats as not covering the active lockout).
        val recordedWallDeadline: Long,
        val recordedMonoDeadline: Long,
    )

    /** The [state] of a recorded failure and the [count] that produced it, read as one observation. */
    data class FailureOutcome(val state: LockoutState, val count: Int)

    /** An admission result: the [immediate] snapshot state for enforcement, and the [resolved] durable outcome. */
    data class Pending<T>(val immediate: LockoutState, val resolved: Deferred<T>)

    private val lock = Any()

    // Assigned under [lock]. The first mutation is revision 1; the construction seed is revision 0.
    private var nextRevision = 1L

    // Set true by [shutdown] under [lock]. A completion that observes it publishes nothing and starts nothing.
    private var stopped = false

    // True while a cold-start re-seed is still wanted: the construction seed read failed and no local mutation
    // has made the snapshot authoritative. Read lock-free (a hint) and re-checked under [lock] before applying.
    @Volatile
    private var wantReseed = false
    private val reseedInFlight = AtomicBoolean(false)

    // The historical fact that the construction seed read failed. It is never auto-cleared, so a consumer (the
    // runtime seed) can report the degraded cold start once. A re-seed clears the enforcement gap, not this fact.
    @Volatile
    private var seedFailed = false

    private val snapshot: AtomicReference<Snapshot> = AtomicReference(seedSnapshot())

    /** Seeds the snapshot from storage at construction. A read failure degrades to Available and arms a re-seed. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // seed read failure: degrade, never crash construction
    private fun seedSnapshot(): Snapshot =
        try {
            val persisted = storage.read()
            // A persisted lockout is a wall deadline. Its monotonic mirror cannot be reconstructed across a restart,
            // so a recovered lockout is recorded and enforces on the wall deadline alone.
            Snapshot(
                revision = 0L,
                streak = 0L,
                failureCount = persisted.failureCount,
                recordedWallDeadline = persisted.lockoutUntil,
                recordedMonoDeadline = NOT_LOCKED,
                fallbackMonoDeadline = NOT_LOCKED,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            wantReseed = true
            seedFailed = true
            EMPTY_SNAPSHOT
        }

    /** Whether the construction seed read failed (a cold-start degrade). Lock-free; for the consumer's diagnostics. */
    fun seedReadFailed(): Boolean = seedFailed

    // ---- Reads (lock-free) ---------------------------------------------------------------------

    /**
     * The current lockout state, read from the published snapshot without the lock, so it stays responsive even
     * while a durable write is stalled. It does not re-read storage, so an in-memory reset is never resurrected. It
     * returns the longer remaining of the recorded and fallback windows, as remaining durations, so a backwards
     * wall-clock jump cannot shorten enforcement, and it marks the state degraded only when the recorded
     * (persisted-backed) window does not cover the active lockout.
     */
    fun currentState(): LockoutState {
        if (wantReseed) triggerReseed()
        return stateOf(snapshot.get())
    }

    /** The consecutive failure count since the last success (drives FR-081 capture). Lock-free. */
    fun failureCount(): Int = snapshot.get().failureCount

    private fun stateOf(snap: Snapshot): LockoutState {
        // The recorded window is backed by a persisted deadline (plus its monotonic mirror); the fallback is
        // in-memory only. The state is degraded when the recorded window does not cover the active lockout, so a
        // delayed threshold write whose persisted deadline has already elapsed is not mislabeled as durable.
        val recordedRemaining = maxOf(
            remainingOf(snap.recordedWallDeadline, clock()),
            remainingOf(snap.recordedMonoDeadline, elapsedRealtime()),
        )
        val fallbackRemaining = remainingOf(snap.fallbackMonoDeadline, elapsedRealtime())
        return when {
            recordedRemaining <= 0L && fallbackRemaining <= 0L -> LockoutState.Available
            recordedRemaining >= fallbackRemaining -> LockoutState.LockedOut(recordedRemaining, degraded = false)
            else -> LockoutState.LockedOut(fallbackRemaining, degraded = true)
        }
    }

    // Guard against a device clock moving backwards: never report more time than the longest possible window.
    private fun remainingOf(deadline: Long, now: Long): Long =
        if (deadline == NOT_LOCKED) 0L else (deadline - now).coerceAtMost(MAX_LOCKOUT_MS).coerceAtLeast(0L)

    // ---- Mutations -----------------------------------------------------------------------------

    /**
     * Records a failed attempt. Returns the [Pending.immediate] state for immediate enforcement, and a
     * [Pending.resolved] outcome that completes when the durable write resolves. [onResolved], when given, is
     * invoked once with the resolved outcome off the caller thread, for the synchronous legacy callers that
     * cannot await.
     */
    fun submitFailure(onResolved: ((FailureOutcome) -> Unit)? = null): Pending<FailureOutcome> =
        synchronized(lock) {
            if (stopped) {
                // After shutdown: no mutation and no completion effect. The resolved outcome is already complete, so
                // an awaiting caller does not hang, but the callback never fires.
                val snap = snapshot.get()
                val outcome = FailureOutcome(stateOf(snap), snap.failureCount)
                Pending(outcome.state, CompletableDeferred(outcome))
            } else {
                wantReseed = false // the first local mutation makes the snapshot authoritative
                val write = admitFailure()
                val resolved = enqueueWrite(write, onResolved)
                Pending(stateOf(snapshot.get()), resolved)
            }
        }

    /**
     * Records a successful authentication, which resets the counters. Returns the [Pending.immediate] Available
     * state and a [Pending.resolved] that completes with whether the durable clear committed. A reset advances the
     * streak, fencing off any older in-flight failure so it cannot reinstate a lockout after this success. The
     * in-memory reset is applied at admission regardless of the durable outcome, so a failed clear never re-locks an
     * authenticated user; [Pending.resolved] surfaces the failure so a caller can report it (a failed clear can
     * leave the pre-reset deadline in storage, an R-007 residual cleared by the next successful unlock). [onResolved],
     * when given, is invoked once off the caller thread with the commit result, for the synchronous legacy callers
     * that cannot await.
     */
    fun submitSuccess(onResolved: ((Boolean) -> Unit)? = null): Pending<Boolean> =
        synchronized(lock) {
            if (stopped) {
                // After shutdown: no mutation and no completion effect (the callback never fires). Nothing was
                // written, so this resolves committed=true: there is no persistence failure to report.
                Pending(LockoutState.Available, CompletableDeferred(true))
            } else {
                wantReseed = false
                val write = admitReset()
                val resolved = enqueueReset(write, onResolved)
                Pending(LockoutState.Available, resolved)
            }
        }

    /** Convenience for the awaited path: records a failure and suspends until its durable outcome resolves. */
    suspend fun recordFailure(): FailureOutcome = submitFailure().resolved.await()

    /**
     * Convenience for the awaited path: records a success and suspends until its durable clear resolves, returning
     * whether it committed. The in-memory reset already applied at admission either way.
     */
    suspend fun recordSuccess(): Boolean = submitSuccess().resolved.await()

    // Admits a failure under [lock]: assigns a revision, updates and publishes the snapshot, and returns the write
    // to enqueue. A threshold failure enforces at once through the in-memory fallback (fail-secure); the recorded
    // (durable) deadline is promoted only when the write commits.
    private fun admitFailure(): PendingWrite {
        val current = snapshot.get()
        val revision = nextRevision++
        val newCount = current.failureCount + 1
        val nowMono = elapsedRealtime()
        return if (newCount >= FAILURE_THRESHOLD) {
            val duration = lockoutDurationFor(newCount)
            // Enforce at once through the fallback (the state reads degraded until the write commits). The recorded
            // deadlines are carried on the write and promoted only on a committed, still-current threshold write, so
            // a returning older success cannot mark a superseded lockout and an uncommitted one never looks durable.
            val fallbackMono = maxOf(current.fallbackMonoDeadline, nowMono + duration) // extend, never shorten
            snapshot.set(
                current.copy(
                    revision = revision,
                    failureCount = newCount,
                    fallbackMonoDeadline = fallbackMono,
                )
            )
            PendingWrite(
                revision,
                current.streak,
                Kind.FAILURE,
                LockoutSnapshot(newCount, clock() + duration),
                count = newCount,
                wasThreshold = true,
                fallbackDurationMs = duration,
                recordedWallDeadline = clock() + duration,
                recordedMonoDeadline = nowMono + duration,
            )
        } else {
            // Below threshold: only the count changes. If its write fails, the fallback still blocks the next
            // attempt (an unpersistable count cannot be trusted). Its persist keeps the recorded deadline as-is.
            snapshot.set(current.copy(revision = revision, failureCount = newCount))
            PendingWrite(
                revision,
                current.streak,
                Kind.FAILURE,
                LockoutSnapshot(newCount, current.recordedWallDeadline),
                count = newCount,
                wasThreshold = false,
                fallbackDurationMs = BASE_LOCKOUT_MS,
                recordedWallDeadline = current.recordedWallDeadline,
                recordedMonoDeadline = current.recordedMonoDeadline,
            )
        }
    }

    // Admits a reset under [lock]: clears the counters and both deadlines, advances the streak, publishes Available.
    private fun admitReset(): PendingWrite {
        val current = snapshot.get()
        val revision = nextRevision++
        val streak = current.streak + 1
        snapshot.set(
            Snapshot(
                revision = revision,
                streak = streak,
                failureCount = 0,
                recordedWallDeadline = NOT_LOCKED,
                recordedMonoDeadline = NOT_LOCKED,
                fallbackMonoDeadline = NOT_LOCKED,
            )
        )
        return PendingWrite(
            revision,
            streak,
            Kind.RESET,
            LockoutSnapshot(0, NOT_LOCKED),
            count = 0,
            wasThreshold = false,
            fallbackDurationMs = 0L,
            recordedWallDeadline = NOT_LOCKED,
            recordedMonoDeadline = NOT_LOCKED,
        )
    }

    // Enqueues a failure write on the dispatcher (FIFO, admission order) and returns its own resolved outcome (this
    // operation's count and durability, not the latest global snapshot). The completion publish and the callback are
    // gated together under [lock] by [stopped], so no effect runs once the manager has stopped.
    private fun enqueueWrite(write: PendingWrite, onResolved: ((FailureOutcome) -> Unit)?): Deferred<FailureOutcome> =
        scope.async(ioDispatcher) {
            val committed = runWrite(write.persist)
            val outcome = opOutcome(write, committed)
            synchronized(lock) {
                if (!stopped) {
                    applyCompletion(write, committed)
                    onResolved?.invoke(outcome)
                }
            }
            outcome
        }

    // Enqueues a reset write on the dispatcher and resolves whether the durable clear committed. The completion
    // publish and the callback are gated together under [lock] by [stopped], so no effect runs once the manager has
    // stopped; the Deferred still resolves the commit result so an awaiting caller never hangs and can report a
    // failed clear (a write throw is contained by [runWrite] as a false result, never a thrown Deferred).
    private fun enqueueReset(write: PendingWrite, onResolved: ((Boolean) -> Unit)?): Deferred<Boolean> =
        scope.async(ioDispatcher) {
            val committed = runWrite(write.persist)
            synchronized(lock) {
                if (!stopped) {
                    applyCompletion(write, committed)
                    onResolved?.invoke(committed)
                }
            }
            committed
        }

    // Builds this operation's resolved outcome from its own admission-time facts and its write result. A recorded
    // lockout requires a committed threshold write; a degraded lockout is in-memory only. currentState() remains the
    // authoritative answer for enforcement; this per-operation result drives audit and intruder capture.
    private fun opOutcome(write: PendingWrite, committed: Boolean): FailureOutcome {
        val state = when {
            write.wasThreshold && committed -> LockoutState.LockedOut(write.fallbackDurationMs, degraded = false)
            write.wasThreshold -> LockoutState.LockedOut(write.fallbackDurationMs, degraded = true)
            committed -> LockoutState.Available // a below-threshold count was persisted; no lockout
            else -> LockoutState.LockedOut(write.fallbackDurationMs, degraded = true) // below-threshold fallback
        }
        return FailureOutcome(state, write.count)
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // write fault: false is fail-secure, never propagated
    private fun runWrite(persist: LockoutSnapshot): Boolean =
        try {
            storage.write(persist)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }

    // Applies one write's completion under [lock]. A committed threshold write promotes its recorded deadlines (only
    // while its revision is current, so a returning older success is dropped); the derived degraded flag then decides
    // whether that (possibly already-elapsed) recorded deadline actually covers the active lockout. A failed
    // failure-write arms the degraded fallback while its streak is current.
    private fun applyCompletion(write: PendingWrite, committed: Boolean) {
        val current = snapshot.get()
        if (committed) {
            // Promote the recorded deadlines only for a still-current threshold write. A committed below-threshold
            // write persists a count, not a deadline, so it never touches the recorded lockout or the fallback.
            val recordsThisLockout = write.kind == Kind.FAILURE && write.wasThreshold
            if (recordsThisLockout && write.revision == current.revision) {
                snapshot.set(
                    current.copy(
                        recordedWallDeadline = write.recordedWallDeadline,
                        recordedMonoDeadline = write.recordedMonoDeadline,
                    )
                )
            }
            // A reset success, or a stale success, needs no further publish: admission already published it.
        } else {
            if (write.kind == Kind.FAILURE && write.streak == current.streak) {
                // Start the fallback window at completion time, not admission time, so a write that stalled past the
                // window installs a fresh (not already-expired) fallback that blocks the next attempt.
                val fallback = maxOf(current.fallbackMonoDeadline, elapsedRealtime() + write.fallbackDurationMs)
                snapshot.set(current.copy(fallbackMonoDeadline = fallback))
            }
            // A failed reset leaves the in-memory cleared state as-is; storage keeps the old deadline (residual).
        }
    }

    // ---- Cold-start re-seed --------------------------------------------------------------------

    /**
     * A lazy off-main re-seed after a failed construction read. It wholesale-seeds only while initialization is
     * untouched (no local mutation has made the snapshot authoritative). The first local mutation clears [wantReseed]
     * permanently, so a re-seed can never read a stale durable deadline and resurrect a reset whose write failed. It
     * checks [stopped] under [lock] at both admission and publication, so a re-seed in flight when [shutdown] returns
     * cannot publish to a stopped manager.
     */
    private fun triggerReseed() {
        if (!wantReseed) return
        val start = synchronized(lock) {
            if (stopped || !wantReseed) false else reseedInFlight.compareAndSet(false, true)
        }
        if (!start) return
        scope.launch(ioDispatcher) {
            val persisted = runRead()
            synchronized(lock) {
                if (!stopped && wantReseed && persisted != null) {
                    snapshot.set(
                        snapshot.get().copy(
                            failureCount = persisted.failureCount,
                            recordedWallDeadline = persisted.lockoutUntil,
                            recordedMonoDeadline = NOT_LOCKED,
                            fallbackMonoDeadline = NOT_LOCKED,
                        )
                    )
                    wantReseed = false // the persisted lockout is picked up; stop retrying
                }
                // A read that still fails, a local mutation, or a shutdown leaves the snapshot as-is (a still-set
                // wantReseed retries later; a stopped manager never re-seeds again).
            }
            reseedInFlight.set(false)
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // re-seed read fault: retry later, never crash
    private fun runRead(): LockoutSnapshot? =
        try {
            storage.read()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    // ---- Lifecycle -----------------------------------------------------------------------------

    /**
     * Stops the manager. It marks [stopped] under [lock], which rejects later admissions and makes every completion
     * (the in-memory publish and the callback) a gated no-op. It does not cancel the work scope, so an in-flight
     * write drains to completion (its durable data reaches storage) and its Deferred resolves, but its completion
     * effect is dropped by the [stopped] check. It is non-suspend and holds no lock across IO, so it never blocks on
     * disk. The manager is a process-lifetime singleton, so production rarely calls this. Stopping the manager is not
     * how one runtime stops: a runtime cancels its own scope and leaves this shared manager up.
     */
    fun shutdown() {
        synchronized(lock) { stopped = true }
    }

    companion object {
        const val FAILURE_THRESHOLD = 5
        const val BASE_LOCKOUT_MS = 30_000L
        const val MAX_LOCKOUT_MS = 30 * 60_000L

        fun lockoutDurationFor(failureCount: Int): Long {
            val doublings = (failureCount - FAILURE_THRESHOLD).coerceIn(0, 20)
            return (BASE_LOCKOUT_MS shl doublings).coerceAtMost(MAX_LOCKOUT_MS)
        }

        private const val NOT_LOCKED = 0L
        private const val NANOS_PER_MILLI = 1_000_000L
        private val EMPTY_SNAPSHOT = Snapshot(
            revision = 0L,
            streak = 0L,
            failureCount = 0,
            recordedWallDeadline = NOT_LOCKED,
            recordedMonoDeadline = NOT_LOCKED,
            fallbackMonoDeadline = NOT_LOCKED,
        )
    }
}
