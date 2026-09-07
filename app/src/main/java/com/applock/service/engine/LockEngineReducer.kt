package com.applock.service.engine

import com.applock.domain.PolicyState
import com.applock.security.LockoutState

/**
 * Pure request-identity + readiness reducer (M7 WP2 Phase 1): `(State, Event) -> (State, Effects)`,
 * deterministic, with no I/O and no clock (time enters via `elapsedRealtimeMs` on events and via
 * [EngineEvent.TimerFired]). It owns the single-surface invariant ([EngineState.activeRequest] and
 * [EngineState.readinessHold] are mutually exclusive) and the lock / readiness lifecycle.
 *
 * Identity: it re-locks on every foreground event but audit-logs only the first entry into a target's
 * lock, and it rejects any completion whose token does not match the active request. Readiness: while
 * policy cannot classify a foreground it holds the target behind a "checking" shield (escalating to
 * "recovery" when `T_ready` elapses or policy has failed) rather than allowing it through.
 */
object LockEngineReducer {

    /** Provisional `T_ready`: how long a "checking" hold waits for readiness before escalating to recovery. */
    const val T_READY_MS = 5_000L

    fun reduce(state: EngineState, event: EngineEvent): Reduction = when (event) {
        is EngineEvent.ForegroundObserved -> onForeground(state, event.foreground)
        is EngineEvent.PolicyStateChanged -> onPolicyStateChanged(state, event)
        is EngineEvent.TimerFired -> onTimerFired(state, event)
        is EngineEvent.UnlockSucceeded -> onUnlockSucceeded(state, event)
        is EngineEvent.UnlockFailed -> onUnlockFailed(state, event)
        is EngineEvent.LockoutRecorded -> onLockoutRecorded(state, event)
        is EngineEvent.BiometricCancelled -> onBiometricCancelled(state, event)
        is EngineEvent.Dismissed -> onDismissed(state, event)
        EngineEvent.ScreenOff -> onScreenOff(state)
    }

    // ---- Foreground observations ---------------------------------------------------------------

    private fun onForeground(state: EngineState, foreground: Foreground): Reduction = when (foreground) {
        // Own and Transient are no-ops: neither is a lock target, and neither means the user left the
        // previous app, so the active request, the live surface, and the last foreground are preserved.
        Foreground.Own, Foreground.Transient -> Reduction(state, emptyList())
        Foreground.Home -> onRealForeground(state, RealForeground.Home, Decision.ALLOW)
        is Foreground.Other -> onRealForeground(
            state,
            RealForeground.Other(foreground.packageName),
            decide(state.policy, foreground.packageName, foreground.hasValidSession),
        )
    }

    /**
     * The pure decision for a real foreground: lock a protected app without a session, hold anything
     * that cannot yet be proven unprotected (Loading), recover when readiness has failed, else allow.
     */
    private fun decide(policy: PolicyState, target: String, hasValidSession: Boolean): Decision = when (policy) {
        is PolicyState.Ready ->
            if (target in policy.packages && !hasValidSession) Decision.LOCK else Decision.ALLOW
        PolicyState.Loading -> Decision.HOLD
        is PolicyState.Failed -> Decision.RECOVER
    }

    private fun onRealForeground(state: EngineState, current: RealForeground, decision: Decision): Reduction {
        val effects = mutableListOf<Effect>()
        val currentPackage = (current as? RealForeground.Other)?.packageName
        val previous = state.lastForeground

        // A genuine switch to a different real app relocks the one the user just left, exactly once.
        if (previous is RealForeground.Other && previous.packageName != currentPackage) {
            effects += Effect.NoteAppLeft(previous.packageName)
        }

        val advanced = state.copy(
            lastForeground = current,
            lastForegroundSeq = state.lastForegroundSeq + 1,
        )

        val next = if (current is RealForeground.Other && decision != Decision.ALLOW) {
            when (decision) {
                Decision.LOCK -> applyLock(advanced, effects, current.packageName)
                Decision.HOLD -> applyReadinessHold(advanced, effects, current.packageName, HoldPhase.CHECKING)
                Decision.RECOVER -> applyReadinessHold(advanced, effects, current.packageName, HoldPhase.RECOVERY)
                Decision.ALLOW -> advanced // unreachable: guarded by the outer condition
            }
        } else {
            applyAllow(advanced, effects)
        }
        return Reduction(next, effects)
    }

    // ---- Policy / readiness transitions --------------------------------------------------------

    private fun onPolicyStateChanged(state: EngineState, event: EngineEvent.PolicyStateChanged): Reduction {
        val withPolicy = state.copy(policy = event.policy)
        val effects = mutableListOf<Effect>()
        val next = when (val policy = event.policy) {
            is PolicyState.Ready -> reevaluateHoldOnReady(withPolicy, effects, policy.packages)
            is PolicyState.Failed -> enterRecoveryIfNeeded(withPolicy, effects)
            PolicyState.Loading -> withPolicy
        }
        return Reduction(next, effects)
    }

    private fun reevaluateHoldOnReady(
        state: EngineState,
        effects: MutableList<Effect>,
        packages: Set<String>,
    ): EngineState {
        val hold = state.readinessHold ?: return state // no hold: an active lock (if any) is preserved
        // Fail-secure override: a protected held target locks even if a session may exist. Session is
        // not consulted at re-evaluation — one extra re-auth is preferred over any bypass window.
        return if (hold.target in packages) {
            applyLock(state, effects, hold.target)
        } else {
            applyAllow(state, effects)
        }
    }

    private fun enterRecoveryIfNeeded(state: EngineState, effects: MutableList<Effect>): EngineState {
        val hold = state.readinessHold
        val last = state.lastForeground
        return when {
            state.activeRequest != null -> state // a locked target stays locked; Failed does not un-protect
            hold != null -> applyReadinessHold(state, effects, hold.target, HoldPhase.RECOVERY)
            last is RealForeground.Other -> applyReadinessHold(state, effects, last.packageName, HoldPhase.RECOVERY)
            else -> state // Home, or nothing foregrounded yet: no recovery shield to raise
        }
    }

    private fun onTimerFired(state: EngineState, event: EngineEvent.TimerFired): Reduction {
        val hold = state.readinessHold
        // Accept only the current checking hold's exact timer; a stale, superseded, or foreign-epoch
        // timer leaves no matching checking hold and is rejected.
        if (event.token.epoch != state.epoch || hold?.phase != HoldPhase.CHECKING || hold.timer != event.token) {
            return Reduction(state, emptyList())
        }
        // T_ready elapsed while still Loading: escalate to recovery; never dismiss to reveal the target.
        return Reduction(
            state.copy(readinessHold = hold.copy(phase = HoldPhase.RECOVERY, timer = null)),
            listOf(Effect.Present(Surface.Recovery(hold.target))),
        )
    }

    // ---- Surface transitions (maintain the activeRequest / readinessHold exclusivity) ----------

    private fun applyLock(state: EngineState, effects: MutableList<Effect>, target: String): EngineState {
        val existing = state.activeRequest
        if (existing != null && existing.target == target) {
            // The same target is already locked, so re-present it without a new id or a repeat log.
            effects += Effect.Present(Surface.Lock(target, existing.id))
            return state
        }
        // A new lock over an existing surface is an invalidating transition; a fresh lock over nothing
        // is not. (Any checking timer it replaces is also cancelled below.)
        val replacedSurface = existing != null || state.readinessHold != null
        cancelCheckingTimer(state, effects)
        val previousTarget = existing?.target ?: state.readinessHold?.target
        val superseded = previousTarget != null && previousTarget != target
        val requestId = RequestId(state.nextRequestId)
        effects += Effect.Log(AuditEvent.LOCK_TRIGGERED, target)
        effects += Effect.Present(Surface.Lock(target, requestId))
        return state.copy(
            activeRequest = LockRequest(requestId, target),
            readinessHold = null,
            nextRequestId = state.nextRequestId + 1,
            generation = advanceIf(state.generation, replacedSurface),
            supersedeCount = if (superseded) state.supersedeCount + 1 else state.supersedeCount,
        )
    }

    private fun applyReadinessHold(
        state: EngineState,
        effects: MutableList<Effect>,
        target: String,
        phase: HoldPhase,
    ): EngineState {
        val existing = state.readinessHold
        if (existing != null && existing.target == target && existing.phase == phase) {
            // The same hold is already shown, so re-present it without restarting the T_ready timer.
            effects += Effect.Present(holdSurface(target, phase))
            return state
        }
        val replacedSurface = state.activeRequest != null || existing != null
        cancelCheckingTimer(state, effects)
        val previousTarget = state.activeRequest?.target ?: existing?.target
        val superseded = previousTarget != null && previousTarget != target

        // A checking hold opens a fresh generation and schedules its T_ready timer on it; a recovery
        // hold has no timer and advances the generation only when it replaced an existing surface. The
        // new generation and the hold's timer are applied in a single copy, so no intermediate state
        // ever pairs a stale timer with a bumped generation.
        val newGeneration: Generation
        val timer: TimerToken?
        if (phase == HoldPhase.CHECKING) {
            newGeneration = Generation(state.generation.n + 1)
            timer = TimerToken(state.epoch, newGeneration)
            effects += Effect.ScheduleTimer(timer, T_READY_MS)
        } else {
            newGeneration = advanceIf(state.generation, replacedSurface)
            timer = null
        }
        effects += Effect.Present(holdSurface(target, phase))
        return state.copy(
            activeRequest = null,
            readinessHold = ReadinessHold(target, phase, timer),
            generation = newGeneration,
            supersedeCount = if (superseded) state.supersedeCount + 1 else state.supersedeCount,
        )
    }

    private fun applyAllow(state: EngineState, effects: MutableList<Effect>): EngineState {
        // Tearing down any live surface (a lock or a hold) is an invalidating transition.
        val hadSurface = state.activeRequest != null || state.readinessHold != null
        cancelCheckingTimer(state, effects)
        if (hadSurface) effects += Effect.DismissSurface
        return state.copy(
            activeRequest = null,
            readinessHold = null,
            generation = advanceIf(state.generation, hadSurface),
        )
    }

    private fun advanceIf(generation: Generation, advance: Boolean): Generation =
        if (advance) Generation(generation.n + 1) else generation

    /** Emits a CancelTimer for a live checking timer, if the current hold has one. */
    private fun cancelCheckingTimer(state: EngineState, effects: MutableList<Effect>) {
        val hold = state.readinessHold
        if (hold?.phase == HoldPhase.CHECKING && hold.timer != null) {
            effects += Effect.CancelTimer(hold.timer)
        }
    }

    private fun holdSurface(target: String, phase: HoldPhase): Surface = when (phase) {
        HoldPhase.CHECKING -> Surface.Checking(target)
        HoldPhase.RECOVERY -> Surface.Recovery(target)
    }

    // ---- Completions -------------------------------------------------------------------------

    private fun onUnlockSucceeded(state: EngineState, event: EngineEvent.UnlockSucceeded): Reduction {
        val request = matchedRequest(state, event.token) ?: return Reduction(state, emptyList())
        val auditEvent = when (event.method) {
            UnlockMethod.PIN -> AuditEvent.UNLOCK_SUCCESS
            UnlockMethod.BIOMETRIC -> AuditEvent.BIOMETRIC_UNLOCK_SUCCESS
        }
        // A success clears the request and resets the projected lockout, because the adapter's
        // recordSuccess clears the persisted counter and starts a new streak. Pending failures survive:
        // an already-recorded failure's outcome still fires when its follow-up arrives.
        return Reduction(
            state.copy(
                activeRequest = null,
                lockout = LockoutState.Available,
                lockoutRevision = 0,
                lockoutStreak = state.lockoutStreak + 1,
            ),
            listOf(
                Effect.MarkUnlocked(request.target),
                Effect.RecordUnlockSuccess(request.target, event.method),
                Effect.Log(auditEvent, request.target),
                Effect.DismissSurface,
            ),
        )
    }

    private fun onUnlockFailed(state: EngineState, event: EngineEvent.UnlockFailed): Reduction {
        val request = matchedRequest(state, event.token) ?: return Reduction(state, emptyList())
        // A single failure leaves the target locked. It is held as a pending failure so its lockout
        // audit and intruder capture fire only when the matching follow-up consumes it, which stops a
        // duplicated or unsolicited callback from fabricating them.
        val failureId = FailureId(state.nextFailureId)
        val pendingFailure = PendingFailure(request.target, event.method, state.lockoutStreak)
        return Reduction(
            state.copy(
                nextFailureId = state.nextFailureId + 1,
                pendingFailures = state.pendingFailures + (failureId to pendingFailure),
            ),
            listOf(
                Effect.Log(AuditEvent.UNLOCK_FAILURE, request.target),
                Effect.RecordUnlockFailure(FailureToken(state.epoch, failureId), request.target, event.method),
            ),
        )
    }

    private fun onLockoutRecorded(state: EngineState, event: EngineEvent.LockoutRecorded): Reduction {
        // Reject a foreign process epoch or an id that is not (or is no longer) pending, so a duplicated
        // or unsolicited callback cannot fabricate a lockout or a capture.
        if (event.token.epoch != state.epoch) return Reduction(state, emptyList())
        val pendingFailure = state.pendingFailures[event.token.id] ?: return Reduction(state, emptyList())

        // Emit the audit and capture for this failure. The lockout audit precedes the capture to match
        // the legacy engine, and the capture fires on every failure (the capture manager owns the
        // threshold policy).
        val effects = mutableListOf<Effect>()
        if (event.lockout is LockoutState.LockedOut) {
            effects += Effect.Log(AuditEvent.LOCKOUT_TRIGGERED, pendingFailure.target)
        }
        effects += Effect.CaptureIntruder(pendingFailure.target, pendingFailure.method, event.failureCount)

        // Consume the failure; the target stays locked. Update the lockout projection only from the
        // current streak (a pre-success follow-up must not re-lock after a success reset the manager)
        // and, within that streak, only from a newer outcome (so an out-of-order follow-up cannot
        // regress it).
        var next = state.copy(pendingFailures = state.pendingFailures - event.token.id)
        if (pendingFailure.streak == state.lockoutStreak && event.failureCount >= state.lockoutRevision) {
            next = next.copy(lockout = event.lockout, lockoutRevision = event.failureCount)
        }
        return Reduction(next, effects)
    }

    private fun onBiometricCancelled(state: EngineState, event: EngineEvent.BiometricCancelled): Reduction {
        // A cancel is neither a failure nor a dismissal, so it never clears or alters the request. For
        // the active request it re-asserts the lock surface, so the PIN prompt shows once the system
        // dialog closes; a stale cancel from a superseded request is ignored.
        val request = matchedRequest(state, event.token) ?: return Reduction(state, emptyList())
        return Reduction(state, listOf(Effect.Present(Surface.Lock(request.target, request.id))))
    }

    private fun onDismissed(state: EngineState, event: EngineEvent.Dismissed): Reduction {
        matchedRequest(state, event.token) ?: return Reduction(state, emptyList())
        // GoHome precedes the dismiss so the protected task is never revealed as the surface comes down.
        return Reduction(
            state.copy(activeRequest = null),
            listOf(Effect.GoHome, Effect.DismissSurface),
        )
    }

    private fun onScreenOff(state: EngineState): Reduction {
        // Screen off ends every session, so tear down any live surface and forget the last foreground.
        // It is a reset boundary, so the generation always advances (even from a surface-free state);
        // the timer cancel and the dismiss stay conditional on there actually being a live surface.
        val effects = mutableListOf<Effect>(Effect.ClearSessions)
        val hadSurface = state.activeRequest != null || state.readinessHold != null
        cancelCheckingTimer(state, effects)
        if (hadSurface) effects += Effect.DismissSurface
        return Reduction(
            state.copy(
                activeRequest = null,
                readinessHold = null,
                lastForeground = null,
                generation = Generation(state.generation.n + 1),
            ),
            effects,
        )
    }

    /**
     * Returns the active request when [token] matches it (same process epoch and request id), or null
     * to reject a stale or foreign-process completion.
     */
    private fun matchedRequest(state: EngineState, token: RequestToken): LockRequest? {
        val request = state.activeRequest ?: return null
        return request.takeIf { token.epoch == state.epoch && token.id == request.id }
    }

    private enum class Decision { ALLOW, LOCK, HOLD, RECOVER }
}
