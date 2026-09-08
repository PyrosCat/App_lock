package com.applock.service.engine

import com.applock.domain.PolicyState
import com.applock.security.LockoutState

/**
 * Pure request-identity and readiness reducer (M7 WP2): `(State, Event) -> (State, Effects)`. It is
 * deterministic, and has no I/O and no clock. Time enters through `elapsedRealtimeMs` on events, and through
 * [EngineEvent.TimerFired]. The on-screen surface is a single [GuardState] slot, so a lock, a readiness
 * shield, and an in-flight leave-without-auth escape cannot occur together. A separate
 * [EngineState.exemption] holds the post-navigation arrival exemption, which must coexist with a shield over
 * an intervening app.
 *
 * Identity: the reducer re-locks on each foreground event, but audit-logs only the first entry into a
 * target's lock, and it rejects a completion whose token does not match the active request. Readiness: while
 * policy cannot classify a foreground, the reducer holds the target behind a "checking" shield. The shield
 * escalates to "recovery" when `T_ready` elapses, or after policy has failed. The reducer never lets the
 * foreground through in this state.
 */
object LockEngineReducer {

    /** Provisional `T_ready`: how long a "checking" hold waits for readiness before it escalates to recovery. */
    const val T_READY_MS = 5_000L

    fun reduce(state: EngineState, event: EngineEvent): Reduction = when (event) {
        is EngineEvent.ForegroundObserved -> onForeground(state, event.foreground)
        is EngineEvent.PolicyStateChanged -> onPolicyStateChanged(state, event)
        is EngineEvent.TimerFired -> onTimerFired(state, event)
        is EngineEvent.UnlockSucceeded -> onUnlockSucceeded(state, event)
        is EngineEvent.UnlockFailed -> onUnlockFailed(state, event)
        is EngineEvent.LockoutRecorded -> onLockoutRecorded(state, event)
        is EngineEvent.BiometricCancelled -> onBiometricCancelled(state, event)
        is EngineEvent.SafeDismissRequested -> onSafeDismissRequested(state, event)
        is EngineEvent.SafeDismissNavigationIssued -> onSafeDismissNavigationIssued(state, event)
        is EngineEvent.SafeDismissNavigationFailed -> onSafeDismissNavigationFailed(state, event)
        EngineEvent.ScreenOff -> onScreenOff(state)
    }

    // ---- Foreground observations ---------------------------------------------------------------

    private fun onForeground(state: EngineState, foreground: Foreground): Reduction = when (foreground) {
        // Own and Transient are true no-ops. Neither is a lock target. Neither means the user left the
        // previous app. Neither ends an in-flight safe-exit (a transient system window can flash between the
        // launch of the escape destination and its arrival). So the reducer keeps the whole state.
        Foreground.Own, Foreground.Transient -> Reduction(state, emptyList())
        // Home is a proven non-target: it ends any in-flight escape, then allows through (it dismisses).
        Foreground.Home -> onRealForeground(endEscape(state), RealForeground.Home, Decision.ALLOW)
        is Foreground.Other -> onOther(state, foreground)
    }

    /**
     * A real-application foreground. While an [SafeDestination.OVERLAY_SETTINGS] escape is in flight, the
     * reducer allows the resolved destination through instead of a new shield. Without this, the R-005 hold
     * over any `Other` under Loading or Failed would trap the user before the grant screen. Only an exact
     * member of the escape's `approved` set is exempt; a stray or unrelated app stays shielded.
     *
     * A same-target re-emission during an in-flight ([GuardState.LeavingFor]) escape is an **idempotent
     * re-presentation** of the current surface. A re-evaluation could morph the surface (Loading could
     * re-open a Checking generation, or a now-valid session could allow the target through) and strand the
     * escape or invalidate its token, so it must not run. A foreground for a different target supersedes the
     * surface and ends the escape by the normal surface-replacement path. Post-step-2, a not-yet-arrived
     * exemption stays armed while an intruder is shielded; after the destination arrives, a switch to another
     * app ends the exemption.
     */
    private fun onOther(state: EngineState, foreground: Foreground.Other): Reduction {
        val pkg = foreground.packageName
        val guardState = state.guardState
        val exemption = state.exemption

        // 1. Pre-step-2: the escape's own surface still stands. The origin re-emitting is an idempotent
        //    re-presentation. This test runs BEFORE the arrival test. So when the guarded origin is itself an
        //    approved destination package (the guarded app IS Settings, and the escape targets the
        //    overlay-grant screen in that same package), the reducer does not mistake a same-origin
        //    observation for the destination's arrival, and does not tear the shield down before step 2
        //    confirms the navigation. A re-presentation keeps the surface, token, and escape intact; the
        //    foreground sequence still advances, so a consumer or oracle can acknowledge the observation.
        if (guardState is GuardState.LeavingFor && pkg == guardState.guarding.target) {
            return represent(state, pkg)
        }

        // 2. The approved destination arrives (pre- or post-step-2). Pre-step-2 any approved package is the
        //    arrival. Post-step-2, before anything arrives any approved package is the arrival; after one has
        //    arrived only that exact package stays exempt (its own re-presentations across Settings
        //    sub-screens), so a switch to a *different* approved package falls through to the app path below.
        val arrival = when {
            guardState is GuardState.LeavingFor -> pkg in guardState.approved
            exemption != null -> exemption.arrived?.let { pkg == it } ?: (pkg in exemption.approved)
            else -> false
        }
        if (arrival) return onSafeExitArrival(state, pkg)

        // 3. A different real app. It supersedes an in-flight escape (applyX below replaces the LeavingFor
        //    with a guard). If the exempt destination has already arrived, it also ends the exemption. A
        //    not-yet-arrived exemption stays armed while its intruder is shielded.
        val rebased = if (exemption != null && exemption.arrived != null) state.copy(exemption = null) else state
        val decision = decide(rebased.policy, pkg, foreground.hasValidSession)
        return onRealForeground(rebased, RealForeground.Other(pkg), decision)
    }

    /**
     * An idempotent re-presentation of the current surface during an in-flight escape. It keeps the whole
     * surface, token, and escape, and advances only the foreground sequence, so the observation is
     * acknowledged.
     */
    private fun represent(state: EngineState, pkg: String): Reduction = Reduction(
        state.copy(
            lastForeground = RealForeground.Other(pkg),
            lastForegroundSeq = state.lastForegroundSeq + 1,
        ),
        listOf(Effect.Present(state.surface)),
    )

    /**
     * An approved escape destination has come to the foreground. Allow it through (never a shield), latch it
     * as the exemption's [ArrivalExemption.arrived] so a later `Ready` policy can re-evaluate it, and tear
     * down whatever surface stands (the escape's own shield in ordering B, or an intruder's shield
     * post-step-2).
     */
    private fun onSafeExitArrival(state: EngineState, pkg: String): Reduction {
        val effects = mutableListOf<Effect>()
        val previous = state.lastForeground
        if (previous is RealForeground.Other && previous.packageName != pkg) {
            effects += Effect.NoteAppLeft(previous.packageName)
        }
        val hadSurface = state.liveGuard != null
        cancelCheckingTimer(state, effects)
        if (hadSurface) effects += Effect.DismissSurface
        return Reduction(
            state.copy(
                guardState = GuardState.None,
                exemption = arrivalExemptionFor(state, pkg),
                lastForeground = RealForeground.Other(pkg),
                lastForegroundSeq = state.lastForegroundSeq + 1,
                generation = advanceIf(state.generation, hadSurface),
            ),
            effects,
        )
    }

    /** Refreshes the standing exemption with [pkg] as arrived, or creates one from the in-flight escape. */
    private fun arrivalExemptionFor(state: EngineState, pkg: String): ArrivalExemption =
        state.exemption?.copy(arrived = pkg)
            ?: (state.guardState as GuardState.LeavingFor).let {
                ArrivalExemption(it.surfaceToken, it.guarding.target, it.approved, arrived = pkg)
            }

    /**
     * Ends any in-flight escape: an in-flight [GuardState.LeavingFor] reverts to its plain guard, and the
     * reducer drops any standing exemption. A no-op when neither is present.
     */
    private fun endEscape(state: EngineState): EngineState {
        val guardState = (state.guardState as? GuardState.LeavingFor)?.let { GuardState.Guarding(it.guarding) }
            ?: state.guardState
        return if (guardState === state.guardState && state.exemption == null) {
            state
        } else {
            state.copy(guardState = guardState, exemption = null)
        }
    }

    /**
     * The pure decision for a real foreground: lock a protected app that has no session; hold anything the
     * reducer cannot yet prove unprotected (Loading); recover after readiness has failed; else allow.
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

        // A genuine switch to a different real app re-locks the one the user just left, exactly once.
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
                Decision.ALLOW -> advanced // unreachable: the outer condition guards it
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
            is PolicyState.Ready -> onReady(withPolicy, effects, policy.packages)
            is PolicyState.Failed -> enterRecoveryIfNeeded(withPolicy, effects)
            PolicyState.Loading -> withPolicy
        }
        return Reduction(next, effects)
    }

    /**
     * Ready restores normal evaluation. An **arrived** exempt destination is a standing allowed foreground
     * with no shield, so `reevaluateHoldOnReady` alone would leave it exposed. Re-evaluate it directly: lock
     * it fail-secure if it is now protected, else clear the exemption and leave it allowed.
     *
     * Otherwise the reducer **preserves an in-flight escape through this race**, unless it is an
     * OVERLAY_SETTINGS escape. A Lock escape survives, because Ready preserves an active lock. A HOME or
     * APP_LOCK readiness escape survives too: to end it here would re-lock the original target, invalidate
     * its readiness token, and strand the escape (the follow-up would be dropped, and `Foreground.Own` cannot
     * dismiss the new lock). These escapes carry no arrival exemption, so to preserve them opens no bypass.
     * Only an OVERLAY_SETTINGS escape keeps the stricter behavior: its exemption could bypass a protected app
     * after `Ready`, and it never survives into `Ready`, so it reverts to its shield and is re-evaluated here.
     */
    private fun onReady(state: EngineState, effects: MutableList<Effect>, packages: Set<String>): EngineState {
        val arrived = state.exemption?.arrived
        if (arrived != null) {
            val cleared = state.copy(exemption = null)
            return if (arrived in packages) applyLock(cleared, effects, arrived) else cleared
        }
        val guardState = state.guardState
        if (guardState is GuardState.LeavingFor && guardState.destination != SafeDestination.OVERLAY_SETTINGS) {
            return state // a Lock, HOME, or APP_LOCK escape: preserved intact, so its navigation completes
        }
        return reevaluateHoldOnReady(endEscape(state), effects, packages)
    }

    private fun reevaluateHoldOnReady(
        state: EngineState,
        effects: MutableList<Effect>,
        packages: Set<String>,
    ): EngineState {
        val hold = state.readinessHold ?: return state // no shield: an active lock (if any) is preserved
        // Fail-secure override: a protected held target locks even if a session may exist. The reducer does
        // not consult the session at re-evaluation. One extra re-auth is better than any bypass window.
        return if (hold.target in packages) {
            applyLock(state, effects, hold.target)
        } else {
            applyAllow(state, effects)
        }
    }

    private fun enterRecoveryIfNeeded(state: EngineState, effects: MutableList<Effect>): EngineState {
        val guardState = state.guardState
        val last = state.lastForeground
        return when {
            // An in-flight escape keeps its wrapper, token, and generation. A Checking escape still escalates
            // to Recovery on Failed (this matches the Failed contract). The reducer cancels its T_ready timer
            // and morphs only the guarding phase. This is the same safe Checking->Recovery transition that
            // onTimerFired performs, so the generation is unchanged, and the escape token stays valid. A Lock
            // escape or an already-Recovery escape stays as it is (Failed does not un-protect a lock, and does
            // not re-escalate recovery).
            guardState is GuardState.LeavingFor ->
                (guardState.guarding as? BlockingGuard.Checking)?.let { checking ->
                    cancelCheckingTimer(state, effects)
                    effects += Effect.Present(Surface.Recovery(checking.target))
                    state.copy(guardState = guardState.copy(guarding = BlockingGuard.Recovery(checking.target)))
                } ?: state
            state.activeRequest != null -> state // a locked target stays locked; Failed does not un-protect
            state.readinessHold != null ->
                applyReadinessHold(state, effects, state.readinessHold!!.target, HoldPhase.RECOVERY)
            // A surface-free OVERLAY_SETTINGS escape waits for its arrival, so do not raise a shield over the
            // abandoned target again: the exempt arrival will follow, and a new shield would flash a shield
            // that the arrival immediately tears back down.
            state.exemption != null -> state
            last is RealForeground.Other -> applyReadinessHold(state, effects, last.packageName, HoldPhase.RECOVERY)
            else -> state // Home, or nothing in the foreground yet: no recovery shield to raise
        }
    }

    private fun onTimerFired(state: EngineState, event: EngineEvent.TimerFired): Reduction {
        // Accept only the current checking surface's exact timer. A stale, superseded, or foreign-epoch timer
        // leaves no matching checking surface, and is rejected. The T_ready escalation is meaningful only
        // while still Loading. After policy is Ready (a preserved HOME/APP_LOCK escape freezes its Checking
        // surface for the navigation) or Failed (Failed already escalated the shield to Recovery), a fired
        // timer is inert, so it must not morph the frozen surface into Recovery under that policy.
        val checking = state.liveGuard as? BlockingGuard.Checking
        if (state.policy != PolicyState.Loading || event.token.epoch != state.epoch || checking?.timer != event.token) {
            return Reduction(state, emptyList())
        }
        // T_ready elapsed while still Loading: escalate to recovery; never dismiss to reveal the target. The
        // generation is unchanged, so an in-flight escape's token stays valid.
        val recovered = BlockingGuard.Recovery(checking.target)
        val guardState = when (val g = state.guardState) {
            is GuardState.Guarding -> GuardState.Guarding(recovered)
            is GuardState.LeavingFor -> g.copy(guarding = recovered)
            GuardState.None -> return Reduction(state, emptyList()) // unreachable: a checking surface implies a guard
        }
        return Reduction(state.copy(guardState = guardState), listOf(Effect.Present(Surface.Recovery(checking.target))))
    }

    // ---- Surface transitions -------------------------------------------------------------------

    private fun applyLock(state: EngineState, effects: MutableList<Effect>, target: String): EngineState {
        val existing = state.activeRequest
        if (existing != null && existing.target == target) {
            // The same target is already locked, so re-present it without a new id or a repeat log.
            effects += Effect.Present(Surface.Lock(target, existing.id))
            return state
        }
        // A new lock over an existing surface is an invalidating transition; a fresh lock over nothing is
        // not. (The reducer also cancels any checking timer it replaces, below.)
        val replacedSurface = state.liveGuard != null
        cancelCheckingTimer(state, effects)
        val previousTarget = state.liveGuard?.target
        val superseded = previousTarget != null && previousTarget != target
        val requestId = RequestId(state.nextRequestId)
        effects += Effect.Log(AuditEvent.LOCK_TRIGGERED, target)
        effects += Effect.Present(Surface.Lock(target, requestId))
        return state.copy(
            guardState = GuardState.Guarding(BlockingGuard.Lock(target, requestId)),
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
            // The same shield is already shown, so re-present it without a restart of the T_ready timer.
            effects += Effect.Present(holdSurface(target, phase))
            return state
        }
        val replacedSurface = state.liveGuard != null
        cancelCheckingTimer(state, effects)
        val previousTarget = state.liveGuard?.target
        val superseded = previousTarget != null && previousTarget != target

        // A checking hold opens a fresh generation and schedules its T_ready timer on it. A recovery hold has
        // no timer, and advances the generation only when it replaced an existing surface. The reducer applies
        // the new generation and the guard in a single copy, so no intermediate state ever pairs a stale timer
        // with a bumped generation.
        val newGeneration: Generation
        val guard: BlockingGuard
        if (phase == HoldPhase.CHECKING) {
            newGeneration = Generation(state.generation.n + 1)
            val timer = TimerToken(state.epoch, newGeneration)
            effects += Effect.ScheduleTimer(timer, T_READY_MS)
            guard = BlockingGuard.Checking(target, timer)
        } else {
            newGeneration = advanceIf(state.generation, replacedSurface)
            guard = BlockingGuard.Recovery(target)
        }
        effects += Effect.Present(holdSurface(target, phase))
        return state.copy(
            guardState = GuardState.Guarding(guard),
            generation = newGeneration,
            supersedeCount = if (superseded) state.supersedeCount + 1 else state.supersedeCount,
        )
    }

    private fun applyAllow(state: EngineState, effects: MutableList<Effect>): EngineState {
        // To tear down any live surface (a lock or a shield) is an invalidating transition.
        val hadSurface = state.liveGuard != null
        cancelCheckingTimer(state, effects)
        if (hadSurface) effects += Effect.DismissSurface
        return state.copy(
            guardState = GuardState.None,
            generation = advanceIf(state.generation, hadSurface),
        )
    }

    private fun advanceIf(generation: Generation, advance: Boolean): Generation =
        if (advance) Generation(generation.n + 1) else generation

    /** Emits a CancelTimer for the live checking surface's timer, if the visible guard is a checking shield. */
    private fun cancelCheckingTimer(state: EngineState, effects: MutableList<Effect>) {
        (state.liveGuard as? BlockingGuard.Checking)?.let { effects += Effect.CancelTimer(it.timer) }
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
        // A success clears the lock and resets the projected lockout, because the adapter's recordSuccess
        // clears the persisted counter and starts a new streak. It also ends any in-flight escape over this
        // lock (authentication tears the surface down, not the handshake). Pending failures survive: an
        // already-recorded failure's outcome still fires later.
        return Reduction(
            state.copy(
                guardState = GuardState.None,
                exemption = null,
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
        // A single failure leaves the target locked. The reducer holds it as a pending failure, so its
        // lockout audit and intruder capture fire only when the matching follow-up consumes it. This stops a
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
        // Reject a foreign process epoch, or an id that is not pending (or no longer pending), so a duplicated
        // or unsolicited callback cannot fabricate a lockout or a capture.
        if (event.token.epoch != state.epoch) return Reduction(state, emptyList())
        val pendingFailure = state.pendingFailures[event.token.id] ?: return Reduction(state, emptyList())

        // Emit the audit and the capture for this failure. The lockout audit precedes the capture to match
        // the legacy engine, and the capture fires on every failure (the capture manager owns the threshold
        // policy).
        val effects = mutableListOf<Effect>()
        if (event.lockout is LockoutState.LockedOut) {
            effects += Effect.Log(AuditEvent.LOCKOUT_TRIGGERED, pendingFailure.target)
        }
        effects += Effect.CaptureIntruder(pendingFailure.target, pendingFailure.method, event.failureCount)

        // Consume the failure; the target stays locked. Update the lockout projection only from the current
        // streak (a pre-success follow-up must not re-lock after a success reset the manager), and, within
        // that streak, only from a newer outcome (so an out-of-order follow-up cannot regress it).
        var next = state.copy(pendingFailures = state.pendingFailures - event.token.id)
        if (pendingFailure.streak == state.lockoutStreak && event.failureCount >= state.lockoutRevision) {
            next = next.copy(lockout = event.lockout, lockoutRevision = event.failureCount)
        }
        return Reduction(next, effects)
    }

    private fun onBiometricCancelled(state: EngineState, event: EngineEvent.BiometricCancelled): Reduction {
        // A cancel is neither a failure nor a dismissal, so it never clears or alters the request. For the
        // active request it re-asserts the lock surface, so the PIN prompt shows after the system dialog
        // closes. A stale cancel from a superseded request is ignored.
        val request = matchedRequest(state, event.token) ?: return Reduction(state, emptyList())
        return Reduction(state, listOf(Effect.Present(Surface.Lock(request.target, request.id))))
    }

    // ---- Safe-dismiss (leave-without-auth) two-step handshake ----------------------------------

    /**
     * Step 1: a request to leave the live surface for [destination] without authentication. The reducer
     * **preserves** the surface: the guard becomes a [GuardState.LeavingFor] over the same [BlockingGuard],
     * and the reducer emits only [Effect.NavigateSafely]. So the destination launches over the standing
     * surface, and the guarded app never flashes. The reducer drops a token that does not match the live
     * surface (a stale request from a superseded surface, or a foreign-process epoch), and an invalid
     * OVERLAY_SETTINGS escape.
     *
     * At most one escape is ever in flight, so the reducer **drops** a request that arrives while a
     * [GuardState.LeavingFor] already stands; it never lets the request replace the escape. This keeps
     * navigation single-flight and the UX deterministic: one destination is launching, and a competing
     * request does not redirect it without notice. This is a policy choice, not a correctness crutch: the
     * per-request [AttemptToken] already binds each attempt's follow-up to that attempt, so an earlier
     * attempt's follow-up could not mis-consume a replacement. The caller of the dropped request retries
     * after this escape completes, fails (which reverts the guard), or is superseded by a genuine foreground.
     */
    private fun onSafeDismissRequested(state: EngineState, event: EngineEvent.SafeDismissRequested): Reduction {
        val guard = state.liveGuard ?: return Reduction(state, emptyList()) // no surface to leave
        val alreadyLeaving = state.guardState is GuardState.LeavingFor // one escape is already in flight
        // An OVERLAY_SETTINGS escape is valid only from a readiness shield with a resolved package.
        val validForDestination =
            event.destination != SafeDestination.OVERLAY_SETTINGS || isValidSettingsEscape(event)
        if (alreadyLeaving || !isLiveSurface(state, event.token) || !validForDestination) {
            return Reduction(state, emptyList())
        }
        // Only an OVERLAY_SETTINGS escape carries an arrival exemption; others need none. A new escape
        // replaces any standing exemption (at most one escape is in flight). The reducer creates a unique
        // attempt token, so a follow-up from an earlier attempt over the same (unchanged) surface cannot
        // satisfy this escape's follow-up: the two attempts would share an identical surface token.
        val approved = if (event.destination == SafeDestination.OVERLAY_SETTINGS) event.exemptPackages else emptySet()
        val attempt = AttemptToken(state.epoch, AttemptId(state.nextAttemptId))
        return Reduction(
            state.copy(
                guardState = GuardState.LeavingFor(guard, event.token, attempt, event.destination, approved),
                exemption = null,
                nextAttemptId = state.nextAttemptId + 1,
            ),
            listOf(Effect.NavigateSafely(event.destination, attempt)),
        )
    }

    /**
     * An OVERLAY_SETTINGS escape is valid only from a readiness shield (a [ReadinessToken]), and only with at
     * least one resolved destination package to exempt. The reducer refuses a request from a Lock
     * ([RequestToken]), because the exemption could bypass a protected app after policy is `Ready` (the escape
     * belongs to the Loading/Failed shields, and never survives into `Ready`). The reducer refuses a request
     * with no resolved package, because it could not exempt the arrival, and Settings would get a new shield.
     */
    private fun isValidSettingsEscape(event: EngineEvent.SafeDismissRequested): Boolean =
        event.token is ReadinessToken && event.exemptPackages.isNotEmpty()

    /**
     * Step 2: the destination has launched, so now, and only now, tear the surface down. The reducer needs
     * the in-flight escape recorded at step 1 whose [GuardState.LeavingFor.attempt] matches this exact token.
     * (A stray or miswired follow-up with no navigation ever issued cannot clear the guard. A supersession
     * that replaced the escape makes this stale, and the reducer drops it: the newer surface must never come
     * down for the old navigation, which is itself benign.) The escape then hands off to an OVERLAY_SETTINGS
     * arrival exemption; every other destination needs none.
     */
    private fun onSafeDismissNavigationIssued(
        state: EngineState,
        event: EngineEvent.SafeDismissNavigationIssued,
    ): Reduction {
        val leaving = state.guardState as? GuardState.LeavingFor
        if (leaving == null || leaving.attempt != event.token) {
            return Reduction(state, emptyList())
        }
        val effects = mutableListOf<Effect>()
        cancelCheckingTimer(state, effects)
        // The user leaves the current foreground for a safe destination, so re-lock it (end its session under
        // the relock policy) before the reducer erases the foreground: the destination observation can no
        // longer emit this after lastForeground is cleared. An arrival-before-follow-up already emits it
        // through onRealForeground, and the reducer then drops this follow-up, so there is no duplicate.
        (state.lastForeground as? RealForeground.Other)?.let { effects += Effect.NoteAppLeft(it.packageName) }
        effects += Effect.DismissSurface
        // Hand off to an arrival exemption for OVERLAY_SETTINGS; other destinations need none. The reducer
        // clears lastForeground: the user has left the old target for a safe destination, so a later Failed
        // must not rebuild a shield over it (Home resets it on observation; APP_LOCK's Own never would).
        val exemption = if (leaving.approved.isNotEmpty()) {
            ArrivalExemption(leaving.surfaceToken, leaving.guarding.target, leaving.approved)
        } else {
            null
        }
        return Reduction(
            state.copy(
                guardState = GuardState.None,
                exemption = exemption,
                lastForeground = null,
                generation = Generation(state.generation.n + 1),
            ),
            effects,
        )
    }

    /**
     * The failure sibling of [onSafeDismissNavigationIssued]: the destination could not launch, so revert the
     * in-flight escape to its plain guard, and allow a retry. The reducer needs the recorded escape whose
     * [GuardState.LeavingFor.attempt] matches this exact attempt token, so it drops a stale failure from a
     * since-superseded or already-reverted attempt, and never reverts a newer surface.
     *
     * The policy may have moved on while the (now-failed) navigation was in flight: Ready or Failed can arrive
     * over a preserved HOME/APP_LOCK escape without an end to it. So the reducer reconciles the reverted guard
     * with the current policy, exactly as a fresh [EngineEvent.PolicyStateChanged] would. Under Loading the
     * restored guard stands. Under Failed a shield escalates to Recovery (a lock is preserved). Under Ready a
     * shield is re-evaluated (a protected target locks fail-secure, an unprotected target dismisses) and a lock
     * is preserved. Without this, a Checking or Recovery shield restored under Ready would stand frozen forever
     * (its `T_ready` timer is inert after policy leaves Loading).
     */
    private fun onSafeDismissNavigationFailed(
        state: EngineState,
        event: EngineEvent.SafeDismissNavigationFailed,
    ): Reduction {
        val leaving = state.guardState as? GuardState.LeavingFor
        if (leaving == null || leaving.attempt != event.token) {
            return Reduction(state, emptyList())
        }
        val reverted = state.copy(guardState = GuardState.Guarding(leaving.guarding))
        val effects = mutableListOf<Effect>()
        val next = when (val policy = reverted.policy) {
            PolicyState.Loading -> reverted
            is PolicyState.Failed -> enterRecoveryIfNeeded(reverted, effects)
            is PolicyState.Ready -> onReady(reverted, effects, policy.packages)
        }
        return Reduction(next, effects)
    }

    /**
     * Whether [token] identifies the surface that is live *now*: the same process epoch, and either the
     * active request's id (a Lock) or, while a shield stands, the state's current generation. Any
     * supersession advances the request id or the generation, so a token created for a since-replaced surface
     * no longer matches.
     */
    private fun isLiveSurface(state: EngineState, token: SurfaceToken): Boolean {
        if (token.epoch != state.epoch) return false
        return when (token) {
            is RequestToken -> state.activeRequest?.id == token.id
            is ReadinessToken -> state.readinessHold != null && token.generation == state.generation
        }
    }

    private fun onScreenOff(state: EngineState): Reduction {
        // Screen off ends every session, so tear down any live surface, forget the last foreground, and end
        // any in-flight escape. It is a reset boundary, so the generation always advances (even from a
        // surface-free state). The timer cancel and the dismiss stay conditional on a live surface.
        val effects = mutableListOf<Effect>(Effect.ClearSessions)
        val hadSurface = state.liveGuard != null
        cancelCheckingTimer(state, effects)
        if (hadSurface) effects += Effect.DismissSurface
        return Reduction(
            state.copy(
                guardState = GuardState.None,
                exemption = null,
                lastForeground = null,
                generation = Generation(state.generation.n + 1),
            ),
            effects,
        )
    }

    /**
     * Returns the active request when [token] matches it (the same process epoch and request id), or null to
     * reject a stale or foreign-process completion.
     */
    private fun matchedRequest(state: EngineState, token: RequestToken): LockRequest? {
        val request = state.activeRequest ?: return null
        return request.takeIf { token.epoch == state.epoch && token.id == request.id }
    }

    private enum class Decision { ALLOW, LOCK, HOLD, RECOVER }
}
