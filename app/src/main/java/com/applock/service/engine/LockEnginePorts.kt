package com.applock.service.engine

/**
 * The outbound ports that the [LockEngineRuntime] interpreter drives (M7 WP2 change D). The vocabulary of each
 * port stays in `service/`. Thus no `presentation` type goes inward, and the interpreter stays pure Kotlin (R2).
 * Each port is Android-bound at runtime. Its real adapter is in `platform/` (R2-exempt) or is wired in `di/`.
 * The adapters land in change E (`OverlayLockPresenter`, `SafeNavigator`, `TimerScheduler`, `HomeResolver`) or
 * change F (the audit adapter, the intruder-capture adapter, and the diagnostics sink). A fake replaces each
 * port in the change-D JVM tests. This is why the interpreter uses ports and not the concrete Android
 * collaborators: you can test the concurrency cases, the resilience policies, and the safe-dismiss order only
 * with controllable presentation, timer, navigation, and diagnostics seams.
 */

/**
 * Shows or hides the on-screen lock surface. [present] applies a lock surface or a shield surface. [dismiss]
 * hides the surface. Both methods return a [SurfaceApplyResult], so a failure to apply the surface is a value
 * and not only a throw. Both methods MUST be idempotent: to apply the surface that is already on screen, or to
 * dismiss a surface that is already absent, gives [SurfaceApplyResult.Success]. The interpreter calls these
 * methods on one ordered main queue, so two present or dismiss calls cannot interleave.
 *
 * The interpreter is the ONLY caller of these methods. Thus every apply outcome sets the authoritative
 * presentation fact of the enforcement health: a success clears a prior failure, and a non-success starts a
 * bounded, surface-keyed re-drive. The change-E adapter can READ [EngineState.surface] to know what to show,
 * but it MUST NOT apply a health-affecting surface change by itself. To signal a host recreation (for example,
 * the ComposeView rebuilds) or a restored capability, the adapter calls [LockEngineRuntime.retryPresentation].
 * Thus the re-apply goes through the runtime and the health stays correct. If the adapter reconciled the overlay
 * by itself, a failed recreation could hold the health at `Succeeded`, or a success could hold it at `Failed`
 * after the runtime stopped.
 */
interface LockPresenter {
    fun present(presentation: LockPresentation): SurfaceApplyResult
    fun dismiss(): SurfaceApplyResult
}

/**
 * What the interpreter tells the [LockPresenter] to show. The interpreter projects it from the post-reduction
 * [EngineState]. The three cases map one-to-one to the reducer's [Surface]. But they also carry the process
 * [Epoch], which the pure projection omits. The [Epoch] is constant for the process, so it belongs at the
 * interpreter boundary and not in the reducer. Thus a [Lock] carries the full [RequestToken], so a change-E
 * surface can complete against it, and a shield carries its [ReadinessToken], which is its dismiss identity.
 * The name comes from the port method `present(LockPresentation)`. The three cases are its lock, checking, and
 * recovery surfaces.
 */
sealed interface LockPresentation {
    val target: String

    /** The PIN or biometric lock surface for the request [request]. */
    data class Lock(override val target: String, val request: RequestToken) : LockPresentation

    /** The checking readiness shield ([HoldPhase.CHECKING]) for [readiness]. */
    data class Checking(override val target: String, val readiness: ReadinessToken) : LockPresentation

    /** The recovery readiness shield ([HoldPhase.RECOVERY]) for [readiness]. */
    data class Recovery(override val target: String, val readiness: ReadinessToken) : LockPresentation
}

/**
 * The outcome of an apply of a surface, from [LockPresenter.present] or [LockPresenter.dismiss]. The interpreter
 * puts it into the [EnforcementHealth] presentation fact after each apply. Thus it is the signal for "did the
 * presenter reach the desired surface?", for both show and hide. [Success] means the presenter applied the
 * surface. This includes a no-op, when the surface already matched. [Unavailable] means it could not apply the
 * surface for a reason that can recover, such as a missing overlay grant. [Failed] means it could not apply the
 * surface for a different reason. [reason] is a short diagnostic and is never shown to the user. The health
 * projection treats [Unavailable] and [Failed] as not healthy. This is fail-secure: a surface that does not
 * reach its desired state is an enforcement gap, whatever the cause.
 */
sealed interface SurfaceApplyResult {
    data object Success : SurfaceApplyResult
    data object Unavailable : SurfaceApplyResult
    data class Failed(val reason: String) : SurfaceApplyResult
}

/**
 * Starts a leave-without-auth [SafeDestination] over the surface that is on screen (an [Effect.NavigateSafely]).
 * [navigate] returns true when the destination starts. It returns false when it cannot start, because nothing
 * resolved or the launch threw. The interpreter sends back exactly one attempt-keyed follow-up for that Boolean.
 * For true, it sends [EngineEvent.SafeDismissNavigationIssued], and the reducer then hides the surface. For
 * false, it sends [EngineEvent.SafeDismissNavigationFailed], and the reducer reverts the escape so the control
 * can retry. This port is separate from [LockPresenter]: to show a surface and to leave one for a destination
 * are separate tasks, and only the navigation drives the two-step handshake.
 */
interface SafeNavigator {
    fun navigate(destination: SafeDestination): Boolean
}

/**
 * Schedules and cancels the `T_ready` readiness timer ([Effect.ScheduleTimer] and [Effect.CancelTimer]). The
 * real adapter (change E) posts on a Handler or a HandlerThread. [onFire] must send [EngineEvent.TimerFired]
 * with the exact [TimerToken] into the interpreter's single queue. It must never call the reducer reentrantly.
 * A [cancel] for a token that already fired, or that was never scheduled, is a no-op. The reducer already
 * rejects a stale timer or a foreign-epoch timer, so an extra fire does no harm. Thus [cancel] is an
 * optimization and not the correctness boundary.
 *
 * [schedule] returns whether the timer was armed. A false return (for example, `Handler.postDelayed` returns
 * false on a dying looper) or a throw both make the interpreter escalate the shield to Recovery. Thus a
 * Checking hold never waits on a timer that will not fire.
 *
 * [shutdown] is the TERMINAL teardown operation. [LockEngineRuntime.shutdown] calls it, so no armed [onFire]
 * survives a stop. There is no reducer pass at teardown to emit the matching [Effect.CancelTimer]. [shutdown]
 * cancels every pending callback. It also rejects all later [schedule] calls permanently (they return false).
 * Thus a [schedule] that races the teardown cannot leave an armed timer. The real adapter MUST make [shutdown]
 * atomic with [schedule], for example both `synchronized`. Thus a racing pair either arms then cancels, or is
 * rejected, and no timer survives. [shutdown] MUST be safe to call with nothing armed, and it MUST be idempotent.
 */
interface TimerScheduler {
    fun schedule(token: TimerToken, delayMs: Long, onFire: (TimerToken) -> Unit): Boolean
    fun cancel(token: TimerToken)
    fun shutdown()
}

/**
 * Tells whether a raw foreground package is the current home launcher. The interpreter uses it to classify the
 * package as [Foreground.Home] in the consumer. The result is security-relevant in both directions. The
 * interpreter does not evaluate a Home foreground, so a demoted former launcher that is still classified Home
 * bypasses the lock (fail-dangerous). A new launcher that is not recognized is evaluated as a real app, and gets a
 * recovery shield under a Failed policy (fail-safe). The real adapter resolves `MAIN/HOME` through
 * `PackageManager`.
 *
 * [newForegroundEpisode] is true when [packageName] differs from the previous raw foreground observation, which
 * can be `Own` or `Transient`. Those observations never reach the resolver, so only the interpreter can set the
 * flag. Thus `A -> Own -> A` is a transition, not a repeat, and the adapter can resolve again.
 *
 * The adapter MUST keep a last-known-good launcher through a resolve failure, so a transient `PackageManager`
 * failure does not shield the launcher again and again. The adapter MUST NOT throw. The interpreter also treats a
 * throw as fail-secure "not home". The port keeps the classification pure and JVM-testable.
 */
interface HomeResolver {
    fun isHome(packageName: String, newForegroundEpisode: Boolean): Boolean
}

/**
 * Records a security audit event ([Effect.Log], and the self-gate audits). The real adapter (change F) maps
 * [AuditEvent] to the persisted `SecurityEventType`. It writes off the interpreter thread, because audit is
 * observational and never on the safety-critical ordered path. It stays a port so the interpreter needs no Room
 * or entity vocabulary and stays JVM-testable.
 *
 * The adapter MUST be thread-safe, because the drain and the synchronous self-gate call [record] at the same
 * time. It MUST be non-blocking, because the self-gate holds the runtime lifecycle monitor across the call, so
 * a slow write delays [LockEngineRuntime.shutdown]. It MUST keep the order of the audit records. It writes
 * asynchronously, so the interpreter `guard` cannot see a failure that occurs after [record] returns. Thus the
 * adapter MUST catch failures in its own async job and report them to [RuntimeDiagnostics]. It MUST NOT drop
 * them. An application-scope adapter with one consumer (one queue that drains to the DAO) does all of this.
 */
interface AuditLog {
    fun record(event: AuditEvent, packageName: String?)
}

/**
 * Reports a failed unlock to the intruder-capture policy ([Effect.CaptureIntruder], and the self-gate). The
 * manager owns the threshold policy, so the interpreter always reports and never decides. The real adapter
 * (change F) wraps `IntruderCaptureManager.onAuthFailure`. It stays a port because that manager is Android-bound
 * (it uses the camera and storage), and the interpreter must stay JVM-testable.
 *
 * The delivery contract is the same as [AuditLog]. The adapter MUST be thread-safe, because the drain and the
 * synchronous self-gate call it at the same time. It MUST be non-blocking, because the self-gate holds the
 * lifecycle monitor across the call. The capture runs asynchronously. Thus the adapter MUST catch a failure in
 * its own job and report it to [RuntimeDiagnostics], and not let it become silent. The interpreter `guard`
 * contains only a synchronous throw before [onAuthFailure] returns.
 */
interface IntruderCapturePort {
    fun onAuthFailure(packageName: String, method: UnlockMethod, failureCount: Int)
}

/**
 * A no-throw sink for adapter failures that the interpreter contains (audit, capture, timer, home resolution,
 * the self-gate observational work, and a rejected input after shutdown). The interpreter reports the failing
 * [port] and a stable [reason] here, then continues. Thus a broken observational adapter does not become silent
 * security-event loss. It is a REQUIRED dependency (no default), so F cannot forget to bind it. The interpreter
 * still routes each call through an internal guard, so a sink that throws cannot propagate. The real sink
 * (change F) writes a log or a metric and MUST NOT throw. F's own asynchronous adapters ([AuditLog],
 * [IntruderCapturePort]) also report a delivery failure here when it happens after their method returned, beyond
 * the interpreter synchronous guard. Thus the sink MUST be thread-safe.
 */
fun interface RuntimeDiagnostics {
    fun report(port: String, reason: String)
}
