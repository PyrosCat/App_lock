package com.applock.service.engine

import com.applock.security.LockoutState

/**
 * Pure request-identity reducer (M7 WP2 Phase 1, change A): `(State, Event) -> (State, Effects)`,
 * deterministic, with no I/O and no clock. It owns the single-active-request invariant and the
 * lock/unlock lifecycle; readiness (holds, the T_ready timer, `PolicyStateChanged`) is change B.
 *
 * It re-locks on every foreground event but audit-logs only the first entry into a target's lock, and
 * it rejects any completion whose token does not match the active request (a wrong id, or a foreign
 * process epoch from before a restart).
 */
object LockEngineReducer {

    fun reduce(state: EngineState, event: EngineEvent): Reduction = when (event) {
        is EngineEvent.ForegroundObserved -> onForeground(state, event.foreground)
        is EngineEvent.UnlockSucceeded -> onUnlockSucceeded(state, event)
        is EngineEvent.UnlockFailed -> onUnlockFailed(state, event)
        is EngineEvent.LockoutRecorded -> onLockoutRecorded(state, event)
        is EngineEvent.BiometricCancelled -> onBiometricCancelled(state, event)
        is EngineEvent.Dismissed -> onDismissed(state, event)
        EngineEvent.ScreenOff -> onScreenOff(state)
    }

    private fun onForeground(state: EngineState, foreground: Foreground): Reduction = when (foreground) {
        // Own and Transient are no-ops: neither is a lock target, and neither means the user left the
        // previous app, so the active request, the live surface, and the last foreground are preserved.
        Foreground.Own, Foreground.Transient -> Reduction(state, emptyList())
        Foreground.Home -> onRealForeground(state, RealForeground.Home, lock = false)
        is Foreground.Other -> onRealForeground(
            state,
            RealForeground.Other(foreground.packageName),
            lock = foreground.protected && !foreground.hasValidSession,
        )
    }

    private fun onRealForeground(state: EngineState, current: RealForeground, lock: Boolean): Reduction {
        val effects = mutableListOf<Effect>()
        val currentPackage = (current as? RealForeground.Other)?.packageName
        val previous = state.lastForeground

        // A genuine switch to a different real app relocks the one the user just left, exactly once.
        if (previous is RealForeground.Other && previous.packageName != currentPackage) {
            effects += Effect.NoteAppLeft(previous.packageName)
        }

        var nextState = state.copy(
            lastForeground = current,
            lastForegroundSeq = state.lastForegroundSeq + 1,
        )

        if (lock && current is RealForeground.Other) {
            val existingRequest = state.activeRequest
            if (existingRequest != null && existingRequest.target == current.packageName) {
                // The same target is already locked, so re-present it without a new id or a repeat log.
                effects += Effect.Present(Surface.Lock(current.packageName, existingRequest.id))
            } else {
                // A new lock. If a different target was active, its request is superseded.
                if (existingRequest != null) {
                    nextState = nextState.copy(supersedeCount = nextState.supersedeCount + 1)
                }
                val requestId = RequestId(nextState.nextRequestId)
                nextState = nextState.copy(
                    activeRequest = LockRequest(requestId, current.packageName),
                    nextRequestId = nextState.nextRequestId + 1,
                )
                effects += Effect.Log(AuditEvent.LOCK_TRIGGERED, current.packageName)
                effects += Effect.Present(Surface.Lock(current.packageName, requestId))
            }
        } else if (state.activeRequest != null) {
            // The current foreground is not locked (Home, or an allowed app), so any live surface is
            // torn down. This completes the prior request rather than superseding it.
            nextState = nextState.copy(activeRequest = null)
            effects += Effect.DismissSurface
        }

        return Reduction(nextState, effects)
    }

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
        var nextState = state.copy(pendingFailures = state.pendingFailures - event.token.id)
        if (pendingFailure.streak == state.lockoutStreak && event.failureCount >= state.lockoutRevision) {
            nextState = nextState.copy(lockout = event.lockout, lockoutRevision = event.failureCount)
        }
        return Reduction(nextState, effects)
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
        val effects = mutableListOf<Effect>(Effect.ClearSessions)
        if (state.activeRequest != null) effects += Effect.DismissSurface
        return Reduction(
            state.copy(activeRequest = null, lastForeground = null),
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
}
