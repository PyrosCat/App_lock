package com.applock.service.engine

import com.applock.domain.LockSessionManager
import com.applock.domain.PolicyState
import com.applock.security.LockoutManager
import com.applock.security.LockoutState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The lock-engine interpreter (M7 WP2 change D). It is the impure host around the pure [LockEngineReducer]. It
 * owns the [EngineState], accepts raw inputs, and interprets each [Effect] that the reducer emits. It drives the
 * real collaborators, or faked ones in the JVM tests. It is a JVM-testable rewrite of
 * [com.applock.service.ApplicationLockEngine], and it does NOT replace it: there is no Hilt cutover here, so
 * production still drives the old `LockScreenActivity` path (FR-027 stays `partial`). The DI swap and the real
 * adapters are changes E and F.
 *
 * ## Lifetime
 * This is a process-lifetime singleton. F MUST give it the **application scope**, not a service scope. Thus the
 * single consumer lives as long as the process: the detector service can restart, but the engine state and its
 * consumer stay. [shutdown] closes the input channel for a clean teardown. After [shutdown], or after the scope
 * is cancelled (which also closes the channel), the runtime REJECTS more input and reports it to [diagnostics].
 * It never accumulates input in a channel that has no consumer.
 *
 * ## Single logical consumer
 * The runtime queues every input as an [Input] on one [Channel]. One coroutine drains the channel and is the
 * ONLY consumer. Thus it processes inputs strictly one at a time: `receive -> reduce -> publish (single-writer
 * StateFlow) -> interpret the effects in order`. The serialization is structural (the single drain coroutine,
 * not thread affinity), so a plain [scope] is sufficient. DI can still confine it as defence in depth. The
 * runtime ENQUEUES an effect follow-up onto the same channel and never does a reentrant reduce. Each
 * safety-critical presenter or navigation effect completes on [mainDispatcher] before the drain receives event
 * N+1. Thus N+1 never overtakes N's surface change. The runtime publishes the state BEFORE it interprets the
 * effects. This is C2-safe: a leave-without-auth keeps the surface in [GuardState.LeavingFor] until the
 * destination is confirmed to have arrived.
 *
 * ## Resilience
 * The runtime calls every Android-bound port through an explicit failure policy. Thus no adapter fault can stop
 * the single consumer (a throw, or a non-throwing rejection; the runtime always rethrows cancellation). Present
 * and dismiss record their outcome to the presentation fact of the enforcement health (a success self-heals a
 * prior failure). On a non-success they start a bounded, surface-keyed re-drive. The runtime reports diagnostics
 * through [safeReport], so even a diagnostics sink that throws cannot propagate.
 *
 * ## What stays out of the reducer
 * Edge classification (a raw package to a [Foreground]) runs in the consumer, in queue order. Thus the runtime
 * reads the session fact after any earlier effect, such as a `ScreenOff` that cleared the sessions. The
 * package-keyed self-gate (App Lock's own PIN gate, never a reducer surface) is here. The AUTHORITATIVE session
 * and lockout mutations use the in-process managers directly, which is reliable. The OBSERVATIONAL audit and
 * capture are each contained. Thus a broken observability adapter can never disable the lockout count.
 */
@Suppress("LongParameterList") // the interpreter drives every reducer effect, so each collaborator is explicit
class LockEngineRuntime(
    // Identity and concurrency.
    private val epoch: Epoch,
    private val scope: CoroutineScope,
    private val mainDispatcher: CoroutineDispatcher,
    // The policy source. The reducer's PolicyStateChanged events come from this flow.
    policyState: StateFlow<PolicyState>,
    // JVM-pure collaborators. The runtime drives them directly. Their mutators are @Synchronized.
    private val sessionManager: LockSessionManager,
    private val lockoutManager: LockoutManager,
    // Outbound ports. They are Android-bound at runtime, and faked in the JVM tests.
    private val presenter: LockPresenter,
    private val navigator: SafeNavigator,
    private val timerScheduler: TimerScheduler,
    private val homeResolver: HomeResolver,
    private val auditLog: AuditLog,
    private val intruderCapture: IntruderCapturePort,
    // The enforcement-health owner. The interpreter submits the presentation fact to it.
    private val enforcementHealth: EnforcementHealth,
    // A no-throw sink for contained adapter failures. It is required (no default), so F cannot forget to bind it.
    private val diagnostics: RuntimeDiagnostics,
    // Our own package. A foreground of it classifies as Foreground.Own.
    private val ownPackageName: String,
    // The edge timestamp for ForegroundObserved. The reducer does not use it today. Production supplies
    // SystemClock.elapsedRealtime through DI, so a future grace-timing use needs no API change.
    private val elapsedRealtime: () -> Long = { 0L },
) {

    // The runtime seeds this from the persisted lockout at construction. The read is total: a storage failure
    // degrades to Available and is reported, and never crashes construction. It is the per-streak projection for
    // the overlay path. The manager stays the truth, and the overlay reads the manager live (D-P2-1).
    private val _state = MutableStateFlow(EngineState(epoch = epoch, lockout = readLockoutSafely()))

    /** The authoritative engine state. The change-E state-observing presenter renders [EngineState.surface]. */
    val state: StateFlow<EngineState> = _state.asStateFlow()

    // UNLIMITED, so an edge producer or an effect follow-up never suspends and never drops. The drain is the
    // only consumer, so a send succeeds and the order is FIFO. This holds until the channel closes (shutdown or
    // scope cancellation). After that a send fails and is reported, and is not accumulated silently.
    private val inputs = Channel<Input>(Channel.UNLIMITED)

    // All of the runtime coroutines (the drain, the policy collector, the re-drive retries) are children of this
    // one job. The job is itself a child of the injected scope. Thus the app scope cancels them on process
    // teardown, and [shutdown] can cancel every producer and the consumer together, not only close the channel.
    private val workJob = SupervisorJob(scope.coroutineContext[Job])
    private val workScope = CoroutineScope(scope.coroutineContext + workJob)

    // The re-drive state. Only the single drain mutates it (from applySurface or forceReconcile). It holds the
    // surface that the last apply targeted, and the bounded retries left for it (reset when the surface changes).
    private var lastReconcileTarget: Surface? = null
    private var redriveAttemptsLeft = MAX_REDRIVE_ATTEMPTS

    // The lifecycle monitor. It makes [shutdown] a BARRIER for the synchronous self-gate entry points, which
    // bypass the channel. Each self-gate op holds it for the whole op. [shutdown] acquires it before it sets
    // [stopped]. Thus [shutdown] waits for any in-flight self-gate op, and every later call sees [stopped] and
    // rejects. A plain @Volatile flag gives visibility but not this check-then-act atomicity, so a call could
    // read stopped==false and then mutate after [shutdown] returned.
    private val lifecycleLock = Any()

    // [shutdown] sets this true. The runtime reads and acts on it only under [lifecycleLock]. The closed channel
    // rejects the channel-based inputs; the self-gate needs this flag because it does not ride the channel.
    private var stopped = false

    init {
        // One drain coroutine: the single logical consumer. On any exit (including scope cancellation) it closes
        // the channel. Thus later sends fail visibly and do not accumulate with no consumer.
        workScope.launch {
            try {
                for (input in inputs) handle(input)
            } finally {
                inputs.close()
            }
        }
        // The policy source. A StateFlow re-emits its current value on subscription. Thus the runtime reconciles
        // the reducer's initial Loading with the real policy as soon as the collector starts.
        workScope.launch {
            policyState.collect { submit(EngineEvent.PolicyStateChanged(it)) }
        }
    }

    // ---- Edge inputs (old public API) ----------------------------------------------------------

    /**
     * A foreground observation from the detector. The runtime queues the raw package and the timestamp. It
     * classifies the observation in the consumer (see [handle]). Thus it reads the session fact AFTER it
     * interprets every earlier queued event. To classify here at the caller would snapshot the session before a
     * queued [EngineEvent.ScreenOff] cleared it. A protected app could then pass through on the stale "has a
     * session" value, just after screen-off.
     */
    fun onAppForegrounded(packageName: String) = enqueue(Input.RawForeground(packageName, elapsedRealtime()))

    fun onScreenOff() = submit(EngineEvent.ScreenOff)

    // ---- Request-token completions (new; the change-E surfaces call these) ---------------------

    /** The user authenticated the lock request [token], with a PIN or biometric. */
    fun unlockSucceeded(token: RequestToken, method: UnlockMethod) =
        submit(EngineEvent.UnlockSucceeded(token, method))

    /** A PIN attempt for the lock request [token] failed. Biometric never uses this method. */
    fun unlockFailed(token: RequestToken, method: UnlockMethod) =
        submit(EngineEvent.UnlockFailed(token, method))

    /** The user cancelled the biometric prompt for [token]. It never changes the lockout counter. */
    fun biometricCancelled(token: RequestToken) = submit(EngineEvent.BiometricCancelled(token))

    /**
     * App Lock's own foreground UI reports it is on screen (F wires `MainActivity.onResume` here). It is the
     * ARRIVAL confirmation for an in-flight APP_LOCK safe-dismiss escape. [token] is the attempt that launched App
     * Lock. F stamps the launch intent with the attempt's epoch and id, and `MainActivity.onResume` echoes it back.
     * So a stale resume (a slow launch that lands after a timeout, or a background return) carries the old attempt,
     * and the reducer drops it instead of completing a later escape. It rides the channel like every other input,
     * so it stays ordered with the foreground stream and the handshake follow-ups.
     */
    fun appLockForegrounded(token: AttemptToken) = submit(EngineEvent.AppLockForeground(token))

    /** A leave-without-auth request from the live surface [token] to [destination]. */
    fun safeDismissRequested(
        token: SurfaceToken,
        destination: SafeDestination,
        exemptPackages: Set<String> = emptySet(),
    ) = submit(EngineEvent.SafeDismissRequested(token, destination, exemptPackages))

    /**
     * Change E calls this to re-apply the presentation without a state transition. It calls it for a host
     * recreation (the ComposeView or overlay host rebuilds and can drop an applied surface), or for a restored
     * capability (for example, the system re-grants the overlay after the re-drive budget was exhausted). It
     * re-arms the budget and re-applies the current surface UNCONDITIONALLY, even after a healthy present. A
     * recreation can leave the surface absent while the runtime still believes it stands. A budget-guarded retry
     * would be a silent no-op there, and would strand the surface under a healthy `presentation` fact. The
     * runtime is the only presenter caller, so the re-apply goes through it and the health stays correct. E MUST
     * NOT re-apply the surface itself, because that would desync the authoritative fact.
     */
    fun retryPresentation() = enqueue(Input.ForceReconcile)

    /**
     * INITIATES teardown. It does not join the drain. Under [lifecycleLock] it is a BARRIER for the synchronous
     * self-gate: it waits for any in-flight self-gate op and rejects later ones. It marks the runtime [stopped].
     * It closes the input channel synchronously, before it returns, so a channel input submitted just after is
     * rejected and reported, and never accepted into a consumer that is about to stop. It terminally shuts down
     * the timer scheduler: this cancels every armed `T_ready` timer and rejects later schedules, so a `schedule`
     * that races the teardown cannot leave an armed timer. It then REQUESTS drain cancellation with
     * `workScope.cancel()`, in a `finally` so a scheduler throw cannot skip it. It does NOT join the drain.
     *
     * The drain stops COOPERATIVELY. An effect already synchronously in progress on it (an audit, a manager
     * mutation, a presenter or navigator call) runs to completion before the drain observes cancellation. Thus it
     * can finish AFTER shutdown() returns. So on return the runtime guarantees this: no new self-gate mutation
     * begins, no new input is accepted, and no new timer arms. But the drain is not fully quiesced. A hard
     * barrier that also waited for the drain would make shutdown `suspend` and `cancelAndJoin` the work job. The
     * runtime does not do this on purpose, because it forces a suspend API and a coroutine-driven test harness.
     * The runtime is process-lifetime, so production rarely calls this.
     */
    fun shutdown() {
        synchronized(lifecycleLock) {
            stopped = true
            inputs.close()
            try {
                guard("timer_cancel") { timerScheduler.shutdown() }
            } finally {
                workScope.cancel()
            }
        }
    }

    // ---- Package-keyed self-gate (App Lock's own PIN gate; runs on the caller / UI thread) -------

    /**
     * App Lock's own gate accepted [packageName]. This is not a reducer surface. The authoritative session mutation
     * ([LockSessionManager.markUnlocked], in-memory and reliable) and the lockout reset admission both run under
     * [lifecycleLock], so they complete as one unit against [shutdown]. The durable clear is awaited outside the
     * lock, so a stalled disk cannot hold the barrier. The success audit is observational and contained. After
     * [shutdown] it is a reported no-op and mutates nothing. The self-gate bypasses the channel, so it carries its
     * own guard. It is suspend; F6 wires it to a coroutine on the caller.
     */
    suspend fun onUnlockSuccess(packageName: String, method: UnlockMethod = UnlockMethod.PIN) {
        val pending = synchronized(lifecycleLock) {
            if (stopped) {
                safeReport("runtime", "self_gate_after_shutdown")
                return
            }
            sessionManager.markUnlocked(packageName)
            guard("audit") { auditLog.record(successAudit(method), packageName) }
            lockoutManager.submitSuccess() // admission only: publishes the reset, enqueues the durable clear
        }
        awaitReset(pending) // await the durable clear outside the barrier
    }

    /**
     * App Lock's own gate rejected [packageName]. Enforcement is immediate: the admission publishes the counted
     * lockout to the manager's snapshot at once, which the live lock-screen path reads. This suspend method itself,
     * however, does not return immediately — it awaits the durable outcome and returns the resolved state, so the
     * caller learns durability (recorded vs degraded) before acting. It attempts the `UNLOCK_FAILURE` audit first and
     * admits the lockout mutation, both under [lifecycleLock], so they complete as one unit against [shutdown]. The
     * durable write is awaited outside the lock. On resolution it does the conditional `LOCKOUT_TRIGGERED` audit and
     * the intruder capture, which are observational and each contained. The manager itself contains a storage
     * failure: a failed write resolves as a degraded lockout (in-memory, still blocking) rather than a throw, so
     * `LOCKOUT_TRIGGERED` is emitted only for a recorded lockout, while capture fires on every failure with the
     * actual count (R-007). After [shutdown] it reports and returns a blocked lockout state without any mutation. The
     * post-await audit and capture re-enter [lifecycleLock] and recheck [stopped], so a [shutdown] that returned
     * while the write was in flight is a hard barrier: no new audit or capture starts afterwards. The self-gate
     * bypasses the channel, so it carries its own guard.
     */
    suspend fun onUnlockFailure(packageName: String, method: UnlockMethod = UnlockMethod.PIN): LockoutState {
        val pending = synchronized(lifecycleLock) {
            if (stopped) {
                safeReport("runtime", "self_gate_after_shutdown")
                // A blocked state, with no mutation.
                return LockoutState.LockedOut(LockoutManager.BASE_LOCKOUT_MS, degraded = true)
            }
            guard("audit") { auditLog.record(AuditEvent.UNLOCK_FAILURE, packageName) }
            lockoutManager.submitFailure() // admission only: publishes the count and deadline, enqueues the write
        }
        val outcome = awaitFailure(pending)
        synchronized(lifecycleLock) {
            if (stopped) {
                // [shutdown] returned while the write was in flight: do not start audit or capture.
                safeReport("runtime", "self_gate_after_shutdown")
                return outcome.state
            }
            val state = outcome.state
            if (state is LockoutState.LockedOut && !state.degraded) {
                guard("audit") { auditLog.record(AuditEvent.LOCKOUT_TRIGGERED, packageName) }
            }
            guard("intruder_capture") { intruderCapture.onAuthFailure(packageName, method, outcome.count) }
        }
        return outcome.state
    }

    private fun successAudit(method: UnlockMethod): AuditEvent =
        if (method == UnlockMethod.BIOMETRIC) AuditEvent.BIOMETRIC_UNLOCK_SUCCESS else AuditEvent.UNLOCK_SUCCESS

    // ---- Drain loop ----------------------------------------------------------------------------

    private suspend fun handle(input: Input) = when (input) {
        is Input.Event -> process(input.event)
        // Classify here, in the consumer, so the session fact reflects every earlier effect (see
        // onAppForegrounded). Home, Own, and Transient are stable, but the runtime must not snapshot the session
        // read before an earlier queued ScreenOff cleared it.
        is Input.RawForeground ->
            process(EngineEvent.ForegroundObserved(classify(input.packageName), input.elapsedRealtimeMs))
        // A bounded, surface-keyed re-drive of a present or dismiss that failed before (see scheduleRedrive).
        is Input.RedrivePresentation -> redrive(input.target)
        // A host-recreation or capability-restored retry (see retryPresentation). Re-arm the budget and re-apply
        // the current surface unconditionally.
        Input.ForceReconcile -> forceReconcile()
    }

    private suspend fun process(event: EngineEvent) {
        val before = _state.value
        val reduction = LockEngineReducer.reduce(before, event)
        _state.value = reduction.state // single-writer publish, before interpretation (C2-safe)
        for (effect in reduction.effects) interpret(effect, reduction.state)
        reconcileArrivalTimeout(before, reduction.state)
    }

    /**
     * Cancels the arrival timeout of a safe-dismiss escape that this event ended. [navigate] arms the timeout on
     * the interpreter side, so its cancel is on the interpreter side too. When the in-flight escape's
     * [AttemptToken] changed between [before] and [after] (the escape completed on arrival, reverted, was
     * superseded, or screen-off cleared it), it cancels that attempt's [TimerToken.ArrivalTimer]. The cancel is an
     * optimization, not the correctness boundary: a late fire that races it feeds a [EngineEvent.SafeDismissTimedOut]
     * that the reducer's [AttemptToken] check drops. A cancel of a token that was never armed (a launch that failed
     * before the arm) is a no-op. [shutdown] cancels every armed timer terminally.
     */
    private fun reconcileArrivalTimeout(before: EngineState, after: EngineState) {
        val endedAttempt = (before.guardState as? GuardState.LeavingFor)?.attempt ?: return
        val liveAttempt = (after.guardState as? GuardState.LeavingFor)?.attempt
        if (liveAttempt != endedAttempt) {
            val arrivalTimer = TimerToken.ArrivalTimer(endedAttempt.epoch, endedAttempt.id)
            guard("timer_cancel") { timerScheduler.cancel(arrivalTimer) }
        }
    }

    /**
     * Interprets one effect. The runtime calls every Android-bound port through a failure policy. Thus no adapter
     * fault can stop the single consumer before it renders a blocking surface (audit precedes `Present` for a new
     * lock; timer scheduling precedes `Present` for a checking shield). The policy is explicit per port. The
     * observational ports ([auditLog], [intruderCapture], timer cancel) report and continue. A timer that cannot
     * arm escalates the shield to Recovery. The presenter and navigator carry their own boundaries.
     * [sessionManager] is JVM-pure, so the runtime calls it directly. [lockoutManager] is Android-backed in
     * production, so it goes through the total lockout boundary ([resetLockoutSafely] and [recordFailureSafely]).
     */
    private suspend fun interpret(effect: Effect, state: EngineState) {
        when (effect) {
            is Effect.Present -> present(effect.surface, state)
            Effect.DismissSurface -> dismiss()
            is Effect.NavigateSafely -> navigate(effect.destination, effect.attempt)
            is Effect.ScheduleTimer -> scheduleTimer(effect.token, effect.delayMs)
            is Effect.CancelTimer -> guard("timer_cancel") { timerScheduler.cancel(effect.token) }
            is Effect.Log -> guard("audit") { auditLog.record(effect.event, effect.packageName) }
            is Effect.NoteAppLeft -> sessionManager.onAppLeft(effect.packageName)
            is Effect.MarkUnlocked -> sessionManager.markUnlocked(effect.packageName)
            is Effect.RecordUnlockSuccess -> resetLockoutSafely()
            is Effect.RecordUnlockFailure -> recordFailure(effect)
            is Effect.CaptureIntruder -> guard("intruder_capture") {
                intruderCapture.onAuthFailure(effect.packageName, effect.method, effect.failureCount)
            }
            Effect.ClearSessions -> sessionManager.clearAllSessions()
        }
    }

    // ---- Surface reconciliation ----------------------------------------------------------------

    /** Presents the surface and records the outcome ([applySurface]). A throw becomes a Failed outcome. */
    private suspend fun present(surface: Surface, state: EngineState) {
        val presentation = presentationFor(surface, state) ?: return // Surface.None presents nothing
        val result = onMain({ presenter.present(presentation) }, { SurfaceApplyResult.Failed("present_exception") })
        applySurface(surface, result)
    }

    /** Dismisses the surface and records the outcome ([applySurface]). A throw becomes a Failed outcome. */
    private suspend fun dismiss() {
        val result = onMain({ presenter.dismiss() }, { SurfaceApplyResult.Failed("dismiss_exception") })
        applySurface(Surface.None, result)
    }

    /**
     * Records a present or dismiss outcome and drives recovery, keyed to the [target] surface. The re-drive
     * budget is PER SURFACE. It resets when the desired surface changes, so an exhausted budget for one request
     * never starves the next. A success clears any prior failure (the health self-heals) and re-arms the budget.
     * A non-success schedules a bounded, delayed re-drive to the current desired surface. Once the budget is
     * exhausted, the re-drives stop (the health stays Failed and observable). A later desired-surface change (a
     * fresh budget) or a [retryPresentation] restarts the reconciliation, so it is never stuck for good.
     */
    private fun applySurface(target: Surface, result: SurfaceApplyResult) {
        enforcementHealth.submitPresentation(result.toHealthFact())
        if (target != lastReconcileTarget) { // a new desired surface: a fresh reconciliation with a fresh budget
            lastReconcileTarget = target
            redriveAttemptsLeft = MAX_REDRIVE_ATTEMPTS
        }
        if (result is SurfaceApplyResult.Success) {
            redriveAttemptsLeft = MAX_REDRIVE_ATTEMPTS
        } else if (redriveAttemptsLeft > 0) {
            redriveAttemptsLeft--
            scheduleRedrive(target)
        }
        // else: the budget is exhausted. The re-drives stop and the health stays Failed (observable). A later
        // desired-surface change (a fresh budget) or retryPresentation() restarts the reconciliation.
    }

    /** Re-applies the current desired surface, only if it still matches [target]. Otherwise the retry is stale. */
    private suspend fun redrive(target: Surface) {
        if (_state.value.surface == target) reconcileCurrent()
    }

    /**
     * A host-recreation or capability-restored retry ([retryPresentation]). It re-arms the re-drive budget and
     * re-applies the current surface UNCONDITIONALLY. It acts even when the last apply Succeeded. A host
     * recreation (the ComposeView rebuilds) can drop an applied surface while the runtime still believes it
     * stands. A state guard ("was the last apply reconciled?") would make that recreation a silent no-op, and
     * would leave the surface absent under a healthy `presentation` fact. [reconcileCurrent] re-applies through
     * the presenter. The presenter's present and dismiss are idempotent, so a re-apply of an already-correct
     * surface is a benign success that re-submits the health fact from the real apply outcome.
     */
    private suspend fun forceReconcile() {
        redriveAttemptsLeft = MAX_REDRIVE_ATTEMPTS
        reconcileCurrent()
    }

    /** Applies the current desired surface: present a guard, or dismiss when there is none. It is idempotent. */
    private suspend fun reconcileCurrent() {
        val current = _state.value
        val surface = current.surface
        if (surface == Surface.None) dismiss() else present(surface, current)
    }

    /**
     * Schedules one bounded, delayed re-drive for [target]. [applySurface] already checked and decremented the
     * budget. [redrive] discards a stale re-drive when the desired surface changed. The re-drive rides the drain,
     * so it stays serialized with every other input.
     */
    private fun scheduleRedrive(target: Surface) {
        workScope.launch {
            delay(REDRIVE_DELAY_MS)
            enqueue(Input.RedrivePresentation(target))
        }
    }

    // ---- Navigation ----------------------------------------------------------------------------

    /**
     * Launches [destination], then enqueues exactly one attempt-keyed follow-up. When the launch cannot start (a
     * `false` return or a throw, both through [onMain]), it enqueues "failed" and the reducer reverts the escape.
     * When the launch starts, it arms an attempt-keyed arrival timeout FIRST, then enqueues "issued". The surface
     * stays up until the destination is confirmed to have arrived. So a background `START_ABORTED`, which also
     * returns success and never foregrounds, reverts on the timeout instead of stranding the surface. If the
     * timeout cannot be armed, it fails secure and reverts now, rather than leave the escape unbounded. A
     * supersession that arrived first makes the follow-up stale, and the reducer drops it. Thus the newer surface
     * never comes down for an old navigation, and no navigation is left without a bound.
     */
    private suspend fun navigate(destination: SafeDestination, attempt: AttemptToken) {
        val launched = onMain({ navigator.navigate(destination) }, { false })
        if (!launched) {
            submit(EngineEvent.SafeDismissNavigationFailed(attempt))
            return
        }
        if (!armArrivalTimeout(attempt)) {
            submit(EngineEvent.SafeDismissNavigationFailed(attempt))
            return
        }
        submit(EngineEvent.SafeDismissNavigationIssued(attempt))
    }

    /**
     * Arms the attempt-keyed arrival timeout through the [timerScheduler] seam, so it inherits the scheduler's
     * shutdown atomicity and scheduling-failure semantics. It returns whether the timer was armed. A `false` return
     * (a dying looper) or a throw both report to diagnostics and count as not-armed, so [navigate] reverts the
     * escape fail-secure. A real arrival cancels the timer ([reconcileArrivalTimeout]). On expiry the scheduler
     * feeds [EngineEvent.SafeDismissTimedOut] through [onTimerFire].
     */
    @Suppress("TooGenericExceptionCaught") // scheduling failure (false or throw) -> fail-secure revert
    private fun armArrivalTimeout(attempt: AttemptToken): Boolean {
        val token = TimerToken.ArrivalTimer(attempt.epoch, attempt.id)
        return try {
            val scheduled = timerScheduler.schedule(token, ARRIVAL_TIMEOUT_MS, ::onTimerFire)
            if (!scheduled) safeReport("arrival_timeout", "rejected")
            scheduled
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            safeReport("arrival_timeout", e.javaClass.simpleName)
            false
        }
    }

    // ---- Boundaries & helpers ------------------------------------------------------------------

    /**
     * Runs [block] on [mainDispatcher] and returns its result. It converts any non-cancellation throw to
     * [onError]. Every presenter and navigator call goes through here. Thus a misbehaving Android adapter can
     * never propagate a throw out of the drain loop and stop the single consumer. It rethrows a
     * [CancellationException], so scope teardown is not mistaken for an adapter failure.
     */
    @Suppress("TooGenericExceptionCaught") // resilience boundary: any adapter throw is routed to onError
    private suspend fun <T> onMain(block: () -> T, onError: (Exception) -> T): T =
        try {
            withContext(mainDispatcher) { block() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(e)
        }

    /**
     * Runs an observational side effect (audit, capture, timer cancel, self-gate observational work). On any
     * non-cancellation throw it reports the failing [port] to [diagnostics] (through [safeReport]) and continues.
     * Thus a misbehaving adapter cannot kill the consumer. It contains only a SYNCHRONOUS throw, one that
     * surfaces before the port method returns. An adapter that delivers asynchronously (audit and capture persist
     * off-thread) can fail after it returns, beyond this boundary. That adapter MUST self-report to
     * [RuntimeDiagnostics] (see the port contracts). So this reports a synchronous fault, not every fault.
     */
    @Suppress("TooGenericExceptionCaught") // observational adapter: report-and-continue (synchronous throws)
    private fun guard(port: String, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            safeReport(port, e.javaClass.simpleName)
        }
    }

    /**
     * Reports to [diagnostics], and contains a diagnostics sink that itself throws. This is the last-resort
     * boundary. A sink that cannot even record a failure has nowhere left to report. So the runtime drops the
     * throw instead of letting it propagate into the caller, which is often safety-critical.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // last resort: a throwing sink cannot be reported
    private fun safeReport(port: String, reason: String) {
        try {
            diagnostics.report(port, reason)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // the diagnostics sink itself failed; there is nothing left that could record it
        }
    }

    /**
     * Arms the `T_ready` timer. The scheduler can fail to arm it, with a `false` return (a dying looper) or a
     * throw. The checking shield would then hang forever. So the interpreter escalates it to Recovery: it feeds
     * the matching [EngineEvent.TimerFired] back. This stays fail-secure, because the shield keeps blocking. It
     * reports both rejection modes to diagnostics.
     */
    @Suppress("TooGenericExceptionCaught") // scheduling failure (false or throw) -> escalate to Recovery
    private fun scheduleTimer(token: TimerToken.ReadinessTimer, delayMs: Long) {
        val armed = try {
            val scheduled = timerScheduler.schedule(token, delayMs, ::onTimerFire)
            if (!scheduled) safeReport("timer_schedule", "rejected") // a non-throwing rejection (dying looper)
            scheduled
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            safeReport("timer_schedule", e.javaClass.simpleName)
            false
        }
        if (!armed) submit(EngineEvent.TimerFired(token))
    }

    /**
     * Admits the failure on the drain loop (its count and deadline publish synchronously, so enforcement updates at
     * once), then awaits the durable outcome and enqueues the [EngineEvent.LockoutRecorded] follow-up off the drain,
     * on [workScope]. Persistence is never awaited on the single drain, so a stalled `commit()` cannot freeze queued
     * runtime events. The follow-up carries the same [FailureToken], so the lockout audit and the intruder capture
     * stay in the pure reducer, which keys the follow-up by that token and tolerates its off-drain ordering. A
     * storage failure resolves as a degraded [LockoutState.LockedOut] (the reducer resolves the pending failure and
     * fires the capture, but never a recorded `LOCKOUT_TRIGGERED`).
     */
    private fun recordFailure(effect: Effect.RecordUnlockFailure) {
        val pending = lockoutManager.submitFailure() // admission is synchronous; enforcement is published now
        workScope.launch {
            val outcome = awaitFailure(pending) // await the durable outcome off the drain
            submit(EngineEvent.LockoutRecorded(effect.token, outcome.state, outcome.count))
        }
    }

    /**
     * The scheduler's fired callback, for both timer kinds. A [TimerToken.ReadinessTimer] feeds the readiness
     * [EngineEvent.TimerFired]. A [TimerToken.ArrivalTimer] feeds the attempt-keyed
     * [EngineEvent.SafeDismissTimedOut]. It only enqueues. The reducer rejects a stale or superseded timer, so an
     * extra fire is benign.
     */
    private fun onTimerFire(token: TimerToken) = when (token) {
        is TimerToken.ReadinessTimer -> submit(EngineEvent.TimerFired(token))
        is TimerToken.ArrivalTimer ->
            submit(EngineEvent.SafeDismissTimedOut(AttemptToken(token.epoch, token.attemptId)))
    }

    // ---- Total lockout boundary (production storage is Android-backed and can throw) ------------

    /** Reads the persisted lockout for the seed projection. A storage failure degrades to Available and is reported. */
    @Suppress("TooGenericExceptionCaught") // storage read failure: degrade the projection, never crash construction
    private fun readLockoutSafely(): LockoutState {
        val state = try {
            lockoutManager.currentState()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            safeReport("lockout_read", e.javaClass.simpleName)
            LockoutState.Available
        }
        // The manager contains a seed read failure itself (currentState does not throw), so surface the degraded
        // cold start here, once, for diagnostics. The manager retries the read off-main (its re-seed).
        if (lockoutManager.seedReadFailed()) safeReport("lockout_read", "seed_degraded")
        return state
    }

    /**
     * Resets the lockout on unlock success (the drain path). The reset clears the counters in memory at once; the
     * durable write is awaited separately, off the drain on [workScope], so a stalled `commit()` cannot delay the
     * overlay dismissal or the queued events. The reducer emits `RecordUnlockSuccess` before `DismissSurface`, so
     * awaiting the clear on the drain would strand an authenticated user behind the overlay.
     */
    private fun resetLockoutSafely() {
        val pending = lockoutManager.submitSuccess()
        workScope.launch { awaitReset(pending) }
    }

    /**
     * Awaits a failure admission's durable outcome. It rethrows cancellation, and maps any other unexpected throw to
     * a reported degraded outcome (a blocked state) carrying the actual in-memory count. A resolved degraded outcome
     * (the durable write failed and the manager fell back to in-memory enforcement) is reported too, so a storage
     * fault stays observable even though the manager contains it.
     */
    @Suppress("TooGenericExceptionCaught") // await failure: rethrow cancellation, otherwise a contained degraded block
    private suspend fun awaitFailure(
        pending: LockoutManager.Pending<LockoutManager.FailureOutcome>,
    ): LockoutManager.FailureOutcome {
        val outcome = try {
            pending.resolved.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            safeReport("lockout_record", e.javaClass.simpleName)
            return LockoutManager.FailureOutcome(
                LockoutState.LockedOut(LockoutManager.BASE_LOCKOUT_MS, degraded = true),
                lockoutManager.failureCount(),
            )
        }
        val state = outcome.state
        if (state is LockoutState.LockedOut && state.degraded) safeReport("lockout_record", "storage_degraded")
        return outcome
    }

    /**
     * Awaits a reset admission's durable clear and reports a failed clear. A resolved not-committed result (the
     * manager contained a `commit() == false` or a write throw as a false outcome) is reported to diagnostics, so a
     * failed reset stays observable. The in-memory reset is never undone here, so the user is not re-locked; storage
     * can keep the pre-reset deadline until the next successful unlock (R-007 residual d). It rethrows cancellation
     * and reports any other unexpected throw.
     */
    @Suppress("TooGenericExceptionCaught") // await reset: rethrow cancellation, otherwise report and continue
    private suspend fun awaitReset(pending: LockoutManager.Pending<Boolean>) {
        val committed = try {
            pending.resolved.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            safeReport("lockout_reset", e.javaClass.simpleName)
            return
        }
        if (!committed) safeReport("lockout_reset", "storage_degraded")
    }

    // ---- Edge helpers --------------------------------------------------------------------------

    /**
     * Classifies a raw foreground package, in the consumer. Own and the transient system windows are proven
     * non-targets. The runtime resolves the launcher through [homeResolver], which is load-bearing: a
     * misclassified launcher would draw a recovery shield under a `Failed` policy. Everything else is a real app.
     * The runtime reads its session here, after any earlier queued effect, so the reducer sees the current
     * session fact.
     */
    private fun classify(packageName: String): Foreground = when {
        packageName == ownPackageName -> Foreground.Own
        packageName in TRANSIENT_PACKAGES -> Foreground.Transient
        isHome(packageName) -> Foreground.Home
        else -> Foreground.Other(packageName, sessionManager.hasValidSession(packageName))
    }

    /**
     * Total home resolution. A resolver that throws is reported and treated as "not home", so the runtime
     * evaluates the package as a real app. This is fail-secure, because Home is the only classification that lets
     * a foreground through without evaluation. A launcher misread as a real app is at worst over-blocked.
     */
    @Suppress("TooGenericExceptionCaught") // fail-secure: a throwing resolver is reported and treated "not home"
    private fun isHome(packageName: String): Boolean =
        try {
            homeResolver.isHome(packageName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            safeReport("home_resolver", e.javaClass.simpleName)
            false
        }

    /** Adds the process [epoch] that the pure projection omits, and keys each shield to the current generation. */
    private fun presentationFor(surface: Surface, state: EngineState): LockPresentation? = when (surface) {
        is Surface.Lock -> LockPresentation.Lock(surface.target, RequestToken(epoch, surface.id))
        is Surface.Checking -> LockPresentation.Checking(surface.target, ReadinessToken(epoch, state.generation))
        is Surface.Recovery -> LockPresentation.Recovery(surface.target, ReadinessToken(epoch, state.generation))
        Surface.None -> null
    }

    private fun submit(event: EngineEvent) = enqueue(Input.Event(event))

    /** The single input choke point. A send that fails (the channel is closed) is reported, and never lost silently. */
    private fun enqueue(input: Input) {
        if (inputs.trySend(input).isFailure) safeReport("runtime", "input_rejected")
    }

    private fun SurfaceApplyResult.toHealthFact(): EnforcementHealth.Presentation = when (this) {
        SurfaceApplyResult.Success -> EnforcementHealth.Presentation.Succeeded
        // Fail-secure: a surface that did not reach its desired state is an enforcement gap. This holds whether
        // the grant was missing (Unavailable) or the apply failed (Failed). The separate overlayGrant fact still
        // records a missing grant on its own.
        SurfaceApplyResult.Unavailable -> EnforcementHealth.Presentation.Failed("unavailable")
        is SurfaceApplyResult.Failed -> EnforcementHealth.Presentation.Failed(reason)
    }

    /**
     * A queued input. An [Event] is a ready [EngineEvent]. A [RawForeground] is an unclassified observation. The
     * consumer classifies it, so the runtime reads its session fact in queue order. [RedrivePresentation]
     * re-applies one desired surface after a present or dismiss failed (discarded if the surface changed since).
     * [ForceReconcile] re-arms the re-drive budget and re-applies the current surface (a capability retry).
     */
    private sealed interface Input {
        data class Event(val event: EngineEvent) : Input
        data class RawForeground(val packageName: String, val elapsedRealtimeMs: Long) : Input
        data class RedrivePresentation(val target: Surface) : Input
        data object ForceReconcile : Input
    }

    private companion object {
        /** Transient system windows: a non-target observation, never a lock target (matches the old engine). */
        val TRANSIENT_PACKAGES = setOf("com.android.systemui", "android")

        /** Bounded retries for a failed present or dismiss re-drive, per desired surface. Any success resets it. */
        const val MAX_REDRIVE_ATTEMPTS = 3

        /** Delay before a re-drive, so a transient adapter failure has time to clear. */
        const val REDRIVE_DELAY_MS = 250L

        /**
         * Provisional bound for a safe-dismiss escape to reach its destination before it reverts. A launch that
         * silently aborts (a background `START_ABORTED`) returns success and never foregrounds, so the escape would
         * otherwise stay in [GuardState.LeavingFor] forever. The surface stays up the whole time (fail secure), so
         * this bound only sets how long the "leaving" state lasts before it reconciles with policy.
         */
        const val ARRIVAL_TIMEOUT_MS = 5_000L
    }
}
