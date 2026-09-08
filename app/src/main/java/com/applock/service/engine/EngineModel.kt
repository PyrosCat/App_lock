package com.applock.service.engine

import com.applock.domain.PolicyState
import com.applock.security.LockoutState

/**
 * Pure model for the lock engine request-identity and readiness state machine (M7 WP2).
 *
 * The on-screen surface is one sum type, [GuardState]. A lock, a readiness shield, and an in-flight
 * leave-without-auth escape thus cannot occur together: the type gives this guarantee, and no field
 * invariant is necessary. One concern lives longer than the visible surface: the post-navigation arrival
 * exemption. It is a separate [EngineState.exemption] field, because it must coexist with the shield that
 * an intervening app raises while the escape waits for its destination. [LockEngineReducer] is pure and
 * deterministic. It performs no side effect; it emits an [Effect], and an adapter performs the effect.
 */

/** Random for each process. A token with a foreign epoch is a ghost from a dead process, and is rejected. */
@JvmInline
value class Epoch(val value: Long)

/** Identity of one lock request. The reducer creates it only on entry to a Lock. */
@JvmInline
value class RequestId(val n: Long)

/** Identity of one failed attempt. Created for each [EngineEvent.UnlockFailed], so its lockout follow-up matches. */
@JvmInline
value class FailureId(val n: Long)

/** Correlates a [EngineEvent.LockoutRecorded] follow-up with the exact failure that produced it. */
data class FailureToken(val epoch: Epoch, val id: FailureId)

/** Identity of one accepted safe-dismiss attempt. Created for each accepted [EngineEvent.SafeDismissRequested]. */
@JvmInline
value class AttemptId(val n: Long)

/**
 * Correlates a [Effect.NavigateSafely] with its one follow-up ([EngineEvent.SafeDismissNavigationIssued]
 * or [EngineEvent.SafeDismissNavigationFailed]). It is different from the [SurfaceToken] that authorized
 * the escape. A failed navigation reverts the escape, and a retry runs over the same, unchanged surface,
 * so the surface token is identical. Only a per-attempt id stops a late or duplicate follow-up from
 * attempt N-1 from completing or cancelling attempt N.
 */
data class AttemptToken(val epoch: Epoch, val id: AttemptId)

/** Monotonic in a process. Identifies the current readiness shield and each scheduled `T_ready` timer. */
@JvmInline
value class Generation(val n: Long)

/** Keys a scheduled `T_ready` timer to the shield that owns it. A fired timer must match it exactly. */
data class TimerToken(val epoch: Epoch, val generation: Generation)

/**
 * Identifies the surface that is live now. It lets the reducer key a leave-without-auth navigation to the
 * exact surface that requested it, and drop that navigation after a supersession replaces the surface. The
 * reducer validates a [EngineEvent.SafeDismissRequested] against the live surface through this union. The
 * escape then gets its own [AttemptToken] for the [Effect.NavigateSafely] round-trip.
 */
sealed interface SurfaceToken {
    val epoch: Epoch
}

/** Correlates an unlock completion, a biometric cancel, or a safe-dismiss with the lock request that authorized it. */
data class RequestToken(override val epoch: Epoch, val id: RequestId) : SurfaceToken

/**
 * Identifies the current readiness shield (Checking or Recovery) by its [generation]. This is the state's
 * generation while a shield stands, so the reducer drops a shield's safe-dismiss after a supersession
 * advances the generation. It is different from [TimerToken]: a Recovery shield has no timer, but still
 * needs an identity to be dismissed from.
 */
data class ReadinessToken(override val epoch: Epoch, val generation: Generation) : SurfaceToken

/** The closed set of destinations a safe-dismiss can navigate to. Never an arbitrary intent. */
enum class SafeDestination { HOME, APP_LOCK, OVERLAY_SETTINGS }

enum class UnlockMethod { PIN, BIOMETRIC }

/** Audit events the reducer emits. The adapter maps these to `SecurityEventType`. */
enum class AuditEvent {
    LOCK_TRIGGERED,
    UNLOCK_SUCCESS,
    BIOMETRIC_UNLOCK_SUCCESS,
    UNLOCK_FAILURE,
    LOCKOUT_TRIGGERED,
}

/** Foreground identity, classified at the edge, so the reducer needs no PackageManager. */
sealed interface Foreground {
    /** App Lock's own package. Never a target, and never dismisses a live surface (it *is* our surface). */
    data object Own : Foreground

    /** A transient system window (System UI, IME, "android"). A non-target observation, never a target. */
    data object Transient : Foreground

    /** The launcher. A proven non-target: it dismisses any live surface and ends the prior app's session. */
    data object Home : Foreground

    /**
     * A real application. The edge resolves [hasValidSession] from the session manager. The reducer derives
     * whether the app is protected from [PolicyState]; this class does not supply it.
     */
    data class Other(val packageName: String, val hasValidSession: Boolean) : Foreground
}

/** The last real foreground the reducer accepted. Own and Transient never advance it. */
sealed interface RealForeground {
    data object Home : RealForeground
    data class Other(val packageName: String) : RealForeground
}

/**
 * A blocking guard that owns the screen, over [target]: a lock ([Lock]) or a readiness shield. The shield
 * is [Checking] while [PolicyState.Loading], and [Recovery] after readiness cannot be established. A
 * [Checking] always carries its `T_ready` [timer]; a [Recovery] never does. The "phase implies timer"
 * invariant is thus structural.
 */
sealed interface BlockingGuard {
    val target: String
    data class Lock(override val target: String, val id: RequestId) : BlockingGuard
    data class Checking(override val target: String, val timer: TimerToken) : BlockingGuard
    data class Recovery(override val target: String) : BlockingGuard
}

/**
 * The single on-screen surface slot. [None] shows nothing. [Guarding] shows a [BlockingGuard]. [LeavingFor]
 * is step 1 of a safe-dismiss: the reducer has emitted the navigation, but the [guarding] surface still
 * stands (and the presenter renders it) while the reducer waits for step 2. Because these are one sum type,
 * a lock, a shield, and an in-flight escape cannot occur together, with no runtime invariant.
 */
sealed interface GuardState {
    data object None : GuardState

    data class Guarding(val guard: BlockingGuard) : GuardState

    /**
     * A leave-without-auth request in flight over the still-visible [guarding] surface. [surfaceToken] is
     * the surface token that authorized the escape; it proves *which guard* started it. [attempt] is the
     * unique per-request token the handshake echoes ([Effect.NavigateSafely] ->
     * [EngineEvent.SafeDismissNavigationIssued] / [EngineEvent.SafeDismissNavigationFailed]), so a follow-up
     * from a previous, reverted attempt over the same surface cannot complete or cancel this one.
     * [destination] and [approved] give the target, and (for OVERLAY_SETTINGS) which resolved package(s) its
     * arrival can show. A foreground for a different target supersedes [guarding] and ends the escape by the
     * normal surface-replacement path. The origin re-emitting is an idempotent re-presentation. Step 2 tears
     * [guarding] down and, for OVERLAY_SETTINGS, hands off to [EngineState.exemption].
     */
    data class LeavingFor(
        val guarding: BlockingGuard,
        val surfaceToken: SurfaceToken,
        val attempt: AttemptToken,
        val destination: SafeDestination,
        val approved: Set<String>,
    ) : GuardState {
        init {
            require((destination == SafeDestination.OVERLAY_SETTINGS) == approved.isNotEmpty()) {
                "an OVERLAY_SETTINGS escape must carry approved arrival packages, and only it may"
            }
            val surfaceMatchesGuarding = when (val t = surfaceToken) {
                is RequestToken -> guarding is BlockingGuard.Lock && guarding.id == t.id
                is ReadinessToken -> guarding is BlockingGuard.Checking || guarding is BlockingGuard.Recovery
            }
            require(surfaceMatchesGuarding) {
                "the surface token must match the guarding surface (RequestToken<->Lock, ReadinessToken<->shield)"
            }
            require(destination != SafeDestination.OVERLAY_SETTINGS || surfaceToken is ReadinessToken) {
                "an OVERLAY_SETTINGS escape must originate from a readiness shield (a ReadinessToken)"
            }
        }
    }
}

/**
 * The post-step-2 arrival exemption for an [SafeDestination.OVERLAY_SETTINGS] escape. The shield is gone
 * and the navigation is issued, but the resolved destination package(s) ([approved]) can foreground without
 * a new shield. It is independent of [GuardState]: while it waits for the arrival, a *different* app still
 * raises its own shield, and one surface slot cannot hold both. This type always represents an
 * OVERLAY_SETTINGS escape from a readiness shield, so it has no `destination` field, and it requires
 * [surfaceToken] to be a [ReadinessToken]. A `RequestToken`-backed exemption could bypass a protected app
 * after policy is `Ready`, so the type does not permit one. [surfaceToken] (the shield the escape came
 * from, not an [AttemptToken]) and [origin] are kept for provenance. [arrived] records which approved
 * package arrived, so a later `Ready` policy can re-evaluate it.
 */
data class ArrivalExemption(
    val surfaceToken: SurfaceToken,
    val origin: String,
    val approved: Set<String>,
    val arrived: String? = null,
) {
    init {
        require(approved.isNotEmpty()) {
            "an arrival exemption only ever exists for an OVERLAY_SETTINGS escape with resolved packages"
        }
        require(surfaceToken is ReadinessToken) {
            "an OVERLAY_SETTINGS arrival exemption may originate only from a readiness shield (a ReadinessToken)"
        }
        require(arrived == null || arrived in approved) {
            "an arrived package must be one of the approved destination packages"
        }
    }
}

/** A readiness shield projection ([EngineState.readinessHold]): a "checking" or "recovery" surface. */
enum class HoldPhase { CHECKING, RECOVERY }

/** Read-model projection of a live readiness shield. [timer] is non-null only for [HoldPhase.CHECKING]. */
data class ReadinessHold(val target: String, val phase: HoldPhase, val timer: TimerToken?) {
    init {
        require((phase == HoldPhase.CHECKING) == (timer != null)) {
            "a CHECKING hold must carry a T_ready timer and a RECOVERY hold must not"
        }
    }
}

/**
 * What the presentation layer shows. It is a pure projection of [EngineState.guardState]. The presenter is
 * **state-observing**: it renders the current [EngineState.surface]. So after any surface recreation
 * (rotation, Activity/overlay re-creation, process re-creation) it rebuilds the same target from state
 * alone, and for [Lock] the same [RequestId]. No reducer event is necessary, and no request is lost or
 * duplicated. [Effect.Present] and [Effect.DismissSurface] are imperative hints for the WindowManager path;
 * the projection is the authoritative source.
 */
sealed interface Surface {
    data object None : Surface
    data class Lock(val target: String, val id: RequestId) : Surface
    data class Checking(val target: String) : Surface
    data class Recovery(val target: String) : Surface
}

/** Read-model projection of a live lock ([EngineState.activeRequest]). */
data class LockRequest(val id: RequestId, val target: String)

/** Read-model projection of the in-flight safe-dismiss ([EngineState.pendingSafeDismiss]). */
data class PendingSafeDismiss(
    val surfaceToken: SurfaceToken,
    val destination: SafeDestination,
    val origin: String,
    val approved: Set<String> = emptySet(),
    val arrived: String? = null,
    val navigationIssued: Boolean = false,
)

/**
 * A failed attempt that waits for its lockout outcome. It is keyed by [FailureId], and its follow-up
 * consumes it once. [streak] records the lockout streak it belongs to, so a follow-up from before a success
 * cannot update the post-success projection.
 */
data class PendingFailure(val target: String, val method: UnlockMethod, val streak: Int)

data class EngineState(
    val epoch: Epoch,
    val policy: PolicyState = PolicyState.Loading,
    val nextRequestId: Long = 0L,
    val nextFailureId: Long = 0L,
    val nextAttemptId: Long = 0L,
    val generation: Generation = Generation(0L),
    val lastForeground: RealForeground? = null,
    /** The single on-screen surface slot: none, a guard, or an in-flight escape over a guard. */
    val guardState: GuardState = GuardState.None,
    /**
     * The post-step-2 OVERLAY_SETTINGS arrival exemption, if any. Separate from [guardState], because it
     * lives longer than the escape's visible surface, and coexists with a shield over an intervening app.
     */
    val exemption: ArrivalExemption? = null,
    /**
     * Failed attempts that wait for their lockout outcome, keyed by [FailureId]. A map, not a single slot,
     * so each concurrent in-flight failure keeps its own outcome, and a later success drops none.
     */
    val pendingFailures: Map<FailureId, PendingFailure> = emptyMap(),
    /**
     * The latest lockout outcome, so the state-observing lock surface can render it. It is an in-memory
     * projection of the persisted `LockoutManager`; the adapter seeds it from `currentState()` at construction.
     */
    val lockout: LockoutState = LockoutState.Available,
    /**
     * The failure count that produced [lockout]: the lockout store's monotonic revision in a streak. Only a
     * follow-up at least this recent can update [lockout], so an out-of-order follow-up cannot regress the
     * projection. It resets on unlock success, because the store's counter resets too.
     */
    val lockoutRevision: Int = 0,
    /**
     * The current lockout streak; it increments on each unlock success. A pending failure stamped with an
     * earlier streak can still capture and audit, but must not update [lockout], because the manager reset.
     */
    val lockoutStreak: Int = 0,
    val supersedeCount: Long = 0L,
    val lastForegroundSeq: Long = 0L,
) {
    init {
        require(guardState !is GuardState.LeavingFor || exemption == null) {
            "an in-flight escape (LeavingFor) and a post-navigation exemption cannot coexist"
        }
        val checking = liveGuard as? BlockingGuard.Checking
        require(checking == null || (checking.timer.epoch == epoch && checking.timer.generation == generation)) {
            "a live checking shield's timer must carry the state's epoch and current generation"
        }
        val leaving = guardState as? GuardState.LeavingFor
        if (leaving != null) {
            require(leaving.surfaceToken.epoch == epoch && leaving.attempt.epoch == epoch) {
                "an in-flight escape's surface and attempt tokens must carry the current epoch"
            }
            val token = leaving.surfaceToken
            require(token !is ReadinessToken || token.generation == generation) {
                "a shield escape's surface token must carry the current generation"
            }
        }
        require(exemption == null || exemption.surfaceToken.epoch == epoch) {
            "an arrival exemption's surface token must carry the current epoch"
        }
    }

    /** The visible guard now: the guard, the guarding surface of an in-flight escape, or none. */
    val liveGuard: BlockingGuard?
        get() = when (val g = guardState) {
            is GuardState.Guarding -> g.guard
            is GuardState.LeavingFor -> g.guarding
            GuardState.None -> null
        }

    /** Read-model projection of a live lock; null unless the visible guard is a [BlockingGuard.Lock]. */
    val activeRequest: LockRequest?
        get() = (liveGuard as? BlockingGuard.Lock)?.let { LockRequest(it.id, it.target) }

    /** Read-model projection of a live readiness shield; null unless the visible guard is a shield. */
    val readinessHold: ReadinessHold?
        get() = when (val g = liveGuard) {
            is BlockingGuard.Checking -> ReadinessHold(g.target, HoldPhase.CHECKING, g.timer)
            is BlockingGuard.Recovery -> ReadinessHold(g.target, HoldPhase.RECOVERY, null)
            else -> null
        }

    /** Read-model projection of the in-flight safe-dismiss (pre-step-2 [GuardState.LeavingFor] or the exemption). */
    val pendingSafeDismiss: PendingSafeDismiss?
        get() = when (val g = guardState) {
            is GuardState.LeavingFor -> PendingSafeDismiss(
                surfaceToken = g.surfaceToken,
                destination = g.destination,
                origin = g.guarding.target,
                approved = g.approved,
                arrived = null,
                navigationIssued = false,
            )
            else -> exemption?.let {
                PendingSafeDismiss(
                    surfaceToken = it.surfaceToken,
                    destination = SafeDestination.OVERLAY_SETTINGS, // ArrivalExemption always represents this
                    origin = it.origin,
                    approved = it.approved,
                    arrived = it.arrived,
                    navigationIssued = true,
                )
            }
        }

    /** Surface is derived, never stored on its own, so it cannot drift from the guard state. */
    val surface: Surface
        get() = when (val g = liveGuard) {
            is BlockingGuard.Lock -> Surface.Lock(g.target, g.id)
            is BlockingGuard.Checking -> Surface.Checking(g.target)
            is BlockingGuard.Recovery -> Surface.Recovery(g.target)
            null -> Surface.None
        }
}

sealed interface EngineEvent {
    data class ForegroundObserved(val foreground: Foreground, val elapsedRealtimeMs: Long) : EngineEvent
    data class PolicyStateChanged(val policy: PolicyState) : EngineEvent
    data class TimerFired(val token: TimerToken) : EngineEvent
    data class UnlockSucceeded(val token: RequestToken, val method: UnlockMethod) : EngineEvent
    data class UnlockFailed(val token: RequestToken, val method: UnlockMethod) : EngineEvent

    /**
     * Adapter follow-up to [Effect.RecordUnlockFailure]: the lockout outcome for that exact failure, keyed
     * by its [FailureToken]. The adapter records the failure against the lockout manager, reads the
     * resulting [LockoutState] and count, and feeds this back, so the lockout audit and the intruder-capture
     * decision stay in the pure reducer. The reducer rejects it unless it matches a pending failure (the
     * reducer tracks several concurrent pending failures), so a duplicate or unsolicited callback cannot
     * fabricate a lockout or a capture.
     */
    data class LockoutRecorded(
        val token: FailureToken,
        val lockout: LockoutState,
        val failureCount: Int,
    ) : EngineEvent

    /** The user cancelled the biometric prompt. A token-keyed no-op: the target stays locked. */
    data class BiometricCancelled(val token: RequestToken) : EngineEvent

    /**
     * A leave-without-auth request from the live surface (a Lock's Back/Home, or a shield's escape), keyed
     * by that surface's [SurfaceToken] and a closed [destination]. The reducer validates it against the live
     * surface, and **preserves** the surface: the reducer emits only [Effect.NavigateSafely], so the
     * destination launches over the standing surface, and the guarded app never appears. A token from a
     * surface that a supersession has replaced no longer matches, and is dropped.
     *
     * [exemptPackages] is the resolved package(s) of an [SafeDestination.OVERLAY_SETTINGS] destination (the
     * adapter resolves the intent's target before it dispatches). Only these are exempt from a new shield on
     * arrival. It is empty, and ignored, for every other destination.
     */
    data class SafeDismissRequested(
        val token: SurfaceToken,
        val destination: SafeDestination,
        val exemptPackages: Set<String> = emptySet(),
    ) : EngineEvent

    /**
     * The success follow-up to [Effect.NavigateSafely], after the destination has launched. It is keyed by
     * the escape's [AttemptToken], not the surface token (a retry over the same surface reuses the surface
     * token). It matches only the exact attempt that emitted the NavigateSafely, and only while that escape
     * is still in flight; then it tears the surface down. A supersession, or a follow-up from an
     * already-reverted earlier attempt, no longer matches and is dropped. The newer surface stands, and the
     * issued navigation is benign.
     */
    data class SafeDismissNavigationIssued(val token: AttemptToken) : EngineEvent

    /**
     * The failure follow-up to [Effect.NavigateSafely] when the destination could **not** launch (the launch
     * threw, or no activity resolved). It reverts the in-flight escape [GuardState.LeavingFor] to its plain
     * [GuardState.Guarding] (reconciled with the current policy) over the same standing surface, so the
     * escape control can retry. Without it, one transient launch failure would strand the surface in
     * `LeavingFor` (every retry is dropped) until an unrelated foreground superseded it. It is keyed by the
     * escape's [AttemptToken]; a stale one from a superseded or already-reverted attempt no longer matches,
     * and is dropped.
     */
    data class SafeDismissNavigationFailed(val token: AttemptToken) : EngineEvent
    data object ScreenOff : EngineEvent
}

sealed interface Effect {
    data class Present(val surface: Surface) : Effect
    data object DismissSurface : Effect

    /**
     * Launch [destination] over the standing surface, then feed back exactly one follow-up with this
     * [attempt] token: [EngineEvent.SafeDismissNavigationIssued] after navigation starts (so the reducer
     * tears the surface down, and only if that attempt is still the live escape), or
     * [EngineEvent.SafeDismissNavigationFailed] if the launch could not start (so the reducer reverts the
     * escape and the control can retry). The attempt token is unique for each request, so a follow-up cannot
     * be attributed to a different attempt over the same surface.
     */
    data class NavigateSafely(val destination: SafeDestination, val attempt: AttemptToken) : Effect
    data class ScheduleTimer(val token: TimerToken, val delayMs: Long) : Effect
    data class CancelTimer(val token: TimerToken) : Effect
    data class Log(val event: AuditEvent, val packageName: String?) : Effect
    data class NoteAppLeft(val packageName: String) : Effect
    data class MarkUnlocked(val packageName: String) : Effect
    data class RecordUnlockSuccess(val packageName: String, val method: UnlockMethod) : Effect

    /**
     * Adapter records the failure and reads the resulting [LockoutState] and count, then dispatches
     * [EngineEvent.LockoutRecorded] back with this [FailureToken].
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
