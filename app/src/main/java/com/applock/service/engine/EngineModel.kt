package com.applock.service.engine

import com.applock.domain.PolicyState
import com.applock.security.LockoutState

/**
 * Pure model for the lock engine's request-identity and readiness state machine (M7 WP2 Phase 1).
 *
 * Change A introduced the identity / lock lifecycle; change B folds in readiness: the atomic
 * [PolicyState] drives `Checking`/`Recovery` holds and the `T_ready` timer, and whether an app is
 * protected is derived from [PolicyState.Ready] rather than supplied by the edge. [LockEngineReducer]
 * is pure and deterministic; every side effect (navigation, logging, timers, session / lockout /
 * intruder mutations) is an emitted [Effect] an adapter performs, so the core needs no Android ports.
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

/** Monotonic within a process; a fresh generation is minted for each scheduled `T_ready` timer. */
@JvmInline
value class Generation(val n: Long)

/** Keys a scheduled `T_ready` timer to the readiness hold that owns it; a fired timer must match exactly. */
data class TimerToken(val epoch: Epoch, val generation: Generation)

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

    /**
     * A real application. [hasValidSession] is resolved at the edge from the session manager; whether
     * the app is protected is derived by the reducer from [PolicyState], not supplied here.
     */
    data class Other(val packageName: String, val hasValidSession: Boolean) : Foreground
}

/** The last real foreground the reducer accepted; Own / Transient never advance it. */
sealed interface RealForeground {
    data object Home : RealForeground
    data class Other(val packageName: String) : RealForeground
}

/** A readiness hold shown while policy cannot classify the foreground: a "checking" or "recovery" shield. */
enum class HoldPhase { CHECKING, RECOVERY }

/**
 * A readiness hold over [target] (a "checking" shield while [PolicyState.Loading], or a "recovery"
 * shield once readiness cannot be established). [timer] is the pending `T_ready` token while
 * [HoldPhase.CHECKING], and null in [HoldPhase.RECOVERY].
 */
data class ReadinessHold(val target: String, val phase: HoldPhase, val timer: TimerToken?) {
    init {
        require((phase == HoldPhase.CHECKING) == (timer != null)) {
            "a CHECKING hold must carry a T_ready timer and a RECOVERY hold must not"
        }
    }
}

/**
 * What the presentation layer should show; a pure projection of [EngineState]. The presenter is
 * **state-observing**: it renders the current [EngineState.surface], so after any surface recreation
 * (rotation, Activity/overlay re-creation, process re-creation) it reconstructs the same target (and,
 * for [Lock], the same [RequestId]) from state alone — no reducer event, and no lost or duplicated
 * request. [Effect.Present] / [Effect.DismissSurface] are imperative hints for the WindowManager path;
 * the projection is the authoritative source.
 */
sealed interface Surface {
    data object None : Surface
    data class Lock(val target: String, val id: RequestId) : Surface
    data class Checking(val target: String) : Surface
    data class Recovery(val target: String) : Surface
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
    val policy: PolicyState = PolicyState.Loading,
    val nextRequestId: Long = 0L,
    val nextFailureId: Long = 0L,
    val generation: Generation = Generation(0L),
    val lastForeground: RealForeground? = null,
    /** The Lock lifecycle slot; mutually exclusive with [readinessHold]. */
    val activeRequest: LockRequest? = null,
    /** The readiness (Checking/Recovery) slot; mutually exclusive with [activeRequest]. */
    val readinessHold: ReadinessHold? = null,
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
    init {
        require(activeRequest == null || readinessHold == null) {
            "activeRequest and readinessHold are mutually exclusive: at most one surface at a time"
        }
        val timer = readinessHold?.timer
        require(timer == null || (timer.epoch == epoch && timer.generation == generation)) {
            "a live checking timer must carry the state's epoch and current generation"
        }
    }

    /** Surface is derived, never stored independently, so it cannot drift from the request/hold state. */
    val surface: Surface
        get() = when {
            activeRequest != null -> Surface.Lock(activeRequest.target, activeRequest.id)
            readinessHold != null -> when (readinessHold.phase) {
                HoldPhase.CHECKING -> Surface.Checking(readinessHold.target)
                HoldPhase.RECOVERY -> Surface.Recovery(readinessHold.target)
            }
            else -> Surface.None
        }
}

sealed interface EngineEvent {
    data class ForegroundObserved(val foreground: Foreground, val elapsedRealtimeMs: Long) : EngineEvent
    data class PolicyStateChanged(val policy: PolicyState) : EngineEvent
    data class TimerFired(val token: TimerToken) : EngineEvent
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
    data class ScheduleTimer(val token: TimerToken, val delayMs: Long) : Effect
    data class CancelTimer(val token: TimerToken) : Effect
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
