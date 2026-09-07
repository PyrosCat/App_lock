package com.applock.service.engine

import com.applock.security.LockoutState

/**
 * Pure model for the lock engine's request-identity state machine (M7 WP2 Phase 1, change A).
 *
 * This is the identity / lock lifecycle only. Readiness (the atomic `PolicyState` folded into
 * `Checking`/`Recovery` surfaces, the `T_ready` timer, and the `PolicyStateChanged` transitions)
 * lands as change B and extends these types. [LockEngineReducer] is pure and deterministic; every
 * side effect (navigation, logging, session / lockout / intruder mutations) is an emitted [Effect]
 * an adapter performs, so the core needs no injected Android ports.
 */

/** Random per process; a token carrying a foreign epoch is a ghost from a dead process and is rejected. */
@JvmInline
value class Epoch(val value: Long)

/** Identity of one lock request; minted only on entry to Lock. */
@JvmInline
value class RequestId(val n: Long)

/** Correlates an unlock / dismiss completion with the request that authorized it. */
data class RequestToken(val epoch: Epoch, val id: RequestId)

/** Identity of one failed attempt, minted per [EngineEvent.UnlockFailed] so its lockout follow-up matches back. */
@JvmInline
value class FailureId(val n: Long)

/** Correlates a [EngineEvent.LockoutRecorded] follow-up with the exact failure that produced it. */
data class FailureToken(val epoch: Epoch, val id: FailureId)

enum class UnlockMethod { PIN, BIOMETRIC }

/** Audit events the reducer emits; the adapter maps these to `SecurityEventType`. */
enum class AuditEvent {
    LOCK_TRIGGERED,
    UNLOCK_SUCCESS,
    BIOMETRIC_UNLOCK_SUCCESS,
    UNLOCK_FAILURE,
    LOCKOUT_TRIGGERED,
}

/** Foreground identity, classified at the edge so the reducer needs no PackageManager. */
sealed interface Foreground {
    /** App Lock's own package: never a target and never dismisses a live surface (it *is* our surface). */
    data object Own : Foreground

    /** A transient system window (System UI, IME, "android"): a non-target observation, never a target. */
    data object Transient : Foreground

    /** The launcher: a proven non-target; dismisses any live surface and ends the prior app's session. */
    data object Home : Foreground

    /** A real application; [protected] and [hasValidSession] are resolved at the edge under Ready policy. */
    data class Other(
        val packageName: String,
        val protected: Boolean,
        val hasValidSession: Boolean,
    ) : Foreground
}

/** The last real foreground the reducer accepted; Own / Transient never advance it. */
sealed interface RealForeground {
    data object Home : RealForeground
    data class Other(val packageName: String) : RealForeground
}

/**
 * What the presentation layer should show; a pure projection of [EngineState]. The presenter is
 * **state-observing**: it renders the current [EngineState.surface], so after any surface recreation
 * (rotation, Activity/overlay re-creation, process re-creation) it reconstructs the same target and
 * [RequestId] from state alone — no reducer event, and no lost or duplicated request. [Effect.Present]
 * / [Effect.DismissSurface] are imperative hints for the WindowManager path; the projection is the
 * authoritative source, and it carries the [RequestId] so a reconstructed surface completes correctly.
 */
sealed interface Surface {
    data object None : Surface
    data class Lock(val target: String, val id: RequestId) : Surface
}

data class LockRequest(val id: RequestId, val target: String)

/**
 * A failed attempt awaiting its lockout outcome; keyed by [FailureId] and consumed once by its
 * follow-up. [streak] records the lockout streak it belongs to, so a follow-up from before a success
 * cannot update the post-success projection.
 */
data class PendingFailure(val target: String, val method: UnlockMethod, val streak: Int)

data class EngineState(
    val epoch: Epoch,
    val nextRequestId: Long = 0L,
    val nextFailureId: Long = 0L,
    val lastForeground: RealForeground? = null,
    val activeRequest: LockRequest? = null,
    /**
     * Failed attempts awaiting their lockout outcome, keyed by [FailureId]. A map, not a single slot,
     * so concurrent in-flight failures each keep their own outcome and a later success never drops one.
     */
    val pendingFailures: Map<FailureId, PendingFailure> = emptyMap(),
    /**
     * Latest lockout outcome, so the state-observing lock surface can render it. In-memory projection
     * of the persisted `LockoutManager`; the adapter seeds it from `currentState()` at construction.
     */
    val lockout: LockoutState = LockoutState.Available,
    /**
     * Failure count that produced [lockout] — the lockout store's monotonic revision within a streak.
     * Only a follow-up at least this recent may update [lockout], so an out-of-order follow-up cannot
     * regress the projection; reset on unlock success (the store's counter resets too).
     */
    val lockoutRevision: Int = 0,
    /**
     * Current lockout streak, incremented on each unlock success. A pending failure stamped with an
     * earlier streak may still capture/audit, but must not update [lockout] — the manager was reset.
     */
    val lockoutStreak: Int = 0,
    val supersedeCount: Long = 0L,
    val lastForegroundSeq: Long = 0L,
) {
    /** Surface is derived, never stored independently, so it cannot drift from the request state. */
    val surface: Surface
        get() = activeRequest?.let { Surface.Lock(it.target, it.id) } ?: Surface.None
}

sealed interface EngineEvent {
    data class ForegroundObserved(val foreground: Foreground, val elapsedRealtimeMs: Long) : EngineEvent
    data class UnlockSucceeded(val token: RequestToken, val method: UnlockMethod) : EngineEvent
    data class UnlockFailed(val token: RequestToken, val method: UnlockMethod) : EngineEvent

    /**
     * Adapter follow-up to [Effect.RecordUnlockFailure]: the lockout outcome for that exact failure,
     * keyed by its [FailureToken]. The adapter records the failure against the lockout manager, reads
     * the resulting [LockoutState] and count, and feeds this back so the lockout audit +
     * intruder-capture decision stay in the pure reducer. Rejected unless it matches a pending failure
     * (the reducer tracks multiple concurrent pending failures), so a duplicated or unsolicited callback
     * cannot fabricate a lockout/capture.
     */
    data class LockoutRecorded(
        val token: FailureToken,
        val lockout: LockoutState,
        val failureCount: Int,
    ) : EngineEvent

    /** The user cancelled the biometric prompt: a token-keyed no-op — the target stays locked. */
    data class BiometricCancelled(val token: RequestToken) : EngineEvent
    data class Dismissed(val token: RequestToken) : EngineEvent
    data object ScreenOff : EngineEvent
}

sealed interface Effect {
    data class Present(val surface: Surface) : Effect
    data object DismissSurface : Effect
    data object GoHome : Effect
    data class Log(val event: AuditEvent, val packageName: String?) : Effect
    data class NoteAppLeft(val packageName: String) : Effect
    data class MarkUnlocked(val packageName: String) : Effect
    data class RecordUnlockSuccess(val packageName: String, val method: UnlockMethod) : Effect

    /**
     * Adapter records the failure + reads the resulting [LockoutState]/count, then dispatches
     * [EngineEvent.LockoutRecorded] carrying this [FailureToken] back.
     */
    data class RecordUnlockFailure(
        val token: FailureToken,
        val packageName: String,
        val method: UnlockMethod,
    ) : Effect

    /** Adapter calls the intruder-capture manager with this failure count (it owns the threshold policy). */
    data class CaptureIntruder(val packageName: String, val method: UnlockMethod, val failureCount: Int) : Effect
    data object ClearSessions : Effect
}

data class Reduction(val state: EngineState, val effects: List<Effect>)
