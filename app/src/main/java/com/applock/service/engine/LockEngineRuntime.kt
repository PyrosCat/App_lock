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
 * effects. This is C2-safe: a leave-without-auth keeps the surface in [GuardState.LeavingFor] until step 2.
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
     * App Lock's own gate accepted [packageName]. This is not a reducer surface. The AUTHORITATIVE session
     * mutation ([LockSessionManager.markUnlocked], in-memory and reliable) always runs. The lockout reset and the
     * success audit are OBSERVATIONAL and each contained, so a broken storage or audit adapter cannot crash the
     * gate. It runs under [lifecycleLock], so it completes as one unit against [shutdown]. After [shutdown] it is
     * a reported no-op and mutates nothing. The self-gate bypasses the channel, so it carries its own guard.
     */
    fun onUnlockSuccess(packageName: String, method: UnlockMethod = UnlockMethod.PIN) {
        synchronized(lifecycleLock) {
            if (stopped) {
                safeReport("runtime", "self_gate_after_shutdown")
                return
            }
            sessionManager.markUnlocked(packageName)
            resetLockoutSafely()
            guard("audit") { auditLog.record(successAudit(method), packageName) }
        }
    }

    /**
     * App Lock's own gate rejected [packageName]. It returns the resulting lockout. It attempts the
     * `UNLOCK_FAILURE` audit FIRST, which matches the legacy engine order, so even a degraded attempt is
     * recorded. Then it does the AUTHORITATIVE lockout mutation through [recordFailureSafely]. Then it does the
     * conditional `LOCKOUT_TRIGGERED` audit and the intruder capture, which are OBSERVATIONAL and each contained.
     * If the authoritative mutation throws, [recordFailureSafely] returns and projects a CONTAINED SYNTHETIC
     * `LockedOut` observation, and never a spurious `Available`. The capture still fires, and the drain and gate
     * survive. That observation is not persisted and does not increment the durable counter, so it enforces
     * nothing on its own. No brute-force attempt is blocked by it until F supplies the authoritative countdown
     * (the real gate reads `LockoutManager.currentState()`). To converge that is R-007, change F. It runs under
     * [lifecycleLock], so it completes as one unit against [shutdown]. After [shutdown] it reports, and returns a
     * fail-closed-shaped lockout WITHOUT any mutation. The self-gate bypasses the channel, so it carries its own
     * guard.
     */
    fun onUnlockFailure(packageName: String, method: UnlockMethod = UnlockMethod.PIN): LockoutState {
        synchronized(lifecycleLock) {
            if (stopped) {
                safeReport("runtime", "self_gate_after_shutdown")
                return LockoutState.LockedOut(LockoutManager.BASE_LOCKOUT_MS) // a deny-shaped value, no mutation
            }
            guard("audit") { auditLog.record(AuditEvent.UNLOCK_FAILURE, packageName) }
            val outcome = recordFailureSafely()
            if (outcome.state is LockoutState.LockedOut) {
                guard("audit") { auditLog.record(AuditEvent.LOCKOUT_TRIGGERED, packageName) }
            }
            guard("intruder_capture") { intruderCapture.onAuthFailure(packageName, method, outcome.count) }
            return outcome.state
        }
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
        val reduction = LockEngineReducer.reduce(_state.value, event)
        _state.value = reduction.state // single-writer publish, before interpretation (C2-safe)
        for (effect in reduction.effects) interpret(effect, reduction.state)
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
     * Launches [destination], then enqueues exactly one attempt-keyed follow-up. It enqueues "issued" when the
     * launch started, and "failed" when it could not (a `false` return or a throw, both through [onMain]). A
     * supersession that arrived first makes the follow-up stale, and the reducer drops it. Thus the newer surface
     * never comes down for an old navigation.
     */
    private suspend fun navigate(destination: SafeDestination, attempt: AttemptToken) {
        val launched = onMain({ navigator.navigate(destination) }, { false })
        submit(
            if (launched) {
                EngineEvent.SafeDismissNavigationIssued(attempt)
            } else {
                EngineEvent.SafeDismissNavigationFailed(attempt)
            },
        )
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
    private fun scheduleTimer(token: TimerToken, delayMs: Long) {
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
     * Records the failure on the drain loop, then enqueues the [EngineEvent.LockoutRecorded] follow-up with the
     * same [FailureToken]. Thus the lockout audit and the intruder capture stay in the pure reducer. The outcome
     * comes from [recordFailureSafely]. So a storage failure produces a fail-closed [LockoutRecorded] (the
     * reducer still resolves the pending failure and fires the audit and capture) and does not kill the drain.
     */
    private fun recordFailure(effect: Effect.RecordUnlockFailure) {
        val outcome = recordFailureSafely()
        submit(EngineEvent.LockoutRecorded(effect.token, outcome.state, outcome.count))
    }

    private fun onTimerFire(token: TimerToken) = submit(EngineEvent.TimerFired(token))

    // ---- Total lockout boundary (production storage is Android-backed and can throw) ------------

    /** Reads the persisted lockout for the seed projection. A storage failure degrades to Available and is reported. */
    @Suppress("TooGenericExceptionCaught") // storage read failure: degrade the projection, never crash construction
    private fun readLockoutSafely(): LockoutState =
        try {
            lockoutManager.currentState()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            safeReport("lockout_read", e.javaClass.simpleName)
            LockoutState.Available
        }

    /** Resets the lockout on unlock success. A storage failure is reported and does not kill the caller. */
    private fun resetLockoutSafely() = guard("lockout_reset") { lockoutManager.recordSuccess() }

    /**
     * Records a failure and reads its outcome atomically. If the storage write throws, this returns a CONTAINED
     * SYNTHETIC outcome: a base-window lockout at the failure threshold, and never a spurious `Available`. The
     * capture is not skipped, and the drain and gate survive the throw. The synthetic outcome is NOT persisted
     * and does NOT increment the durable counter, so it enforces nothing on its own. No attempt is blocked by it
     * until F supplies the authoritative countdown (the real gate reads `LockoutManager.currentState()`). Durable
     * degraded-storage enforcement, and no over-report of `LOCKOUT_TRIGGERED` for it, is R-007, change F.
     */
    @Suppress("TooGenericExceptionCaught") // storage write failure: contained synthetic deny, never a spurious open
    private fun recordFailureSafely(): LockoutManager.FailureOutcome =
        try {
            lockoutManager.recordFailureAndCount()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            safeReport("lockout_record", e.javaClass.simpleName)
            LockoutManager.FailureOutcome(
                LockoutState.LockedOut(LockoutManager.BASE_LOCKOUT_MS),
                LockoutManager.FAILURE_THRESHOLD,
            )
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
    }
}
