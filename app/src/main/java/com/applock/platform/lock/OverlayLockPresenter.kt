package com.applock.platform.lock

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.applock.R
import com.applock.data.SettingsRepository
import com.applock.presentation.authentication.BiometricHostActivity
import com.applock.presentation.authentication.BiometricLaunchGate
import com.applock.presentation.authentication.LeaseHandle
import com.applock.presentation.authentication.LockScreen
import com.applock.presentation.authentication.PinAttemptResult
import com.applock.presentation.theme.AppLockTheme
import com.applock.security.CredentialRepository
import com.applock.security.LockoutManager
import com.applock.security.LockoutState
import com.applock.service.engine.LockCompletionBridge
import com.applock.service.engine.LockCompletionSink
import com.applock.service.engine.LockPresentation
import com.applock.service.engine.LockPresenter
import com.applock.service.engine.RequestToken
import com.applock.service.engine.SafeDestination
import com.applock.service.engine.SurfaceApplyResult
import com.applock.service.engine.SurfaceToken
import com.applock.service.engine.UnlockMethod
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The real [LockPresenter] (M7 WP2 change E): App Lock's baseline lock presentation as a single warm
 * `TYPE_APPLICATION_OVERLAY` window that hosts the reusable C1 [LockScreen] in a [ComposeView].
 *
 * ## Warm, add-once window
 * The overlay is added to the [WindowManager] ONCE and then kept for the process. (WP0 swGPU finding: a
 * per-lock add and remove misbehaved.) [present] and [dismiss] toggle the window's visibility and its
 * focus/touch flags with `updateViewLayout`. They never add or remove the window. `dismiss` makes the window
 * GONE and pass-through (`FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE`), so the app underneath receives input;
 * `present` makes it visible and touch-modal again. Both are idempotent and NON-throwing (they return a
 * [SurfaceApplyResult]): the interpreter contains a throw and, for dismiss, leaves the surface up, so a
 * throwing hide could strand the overlay. A missing overlay grant returns [SurfaceApplyResult.Unavailable].
 * An addView or draw failure returns [SurfaceApplyResult.Failed].
 *
 * ## FLAG_SECURE and the window title
 * The overlay carries `FLAG_SECURE` on non-debuggable builds (FR-171). A debuggable build drops it, so fleet
 * screencap verification works. The window carries the stable [OVERLAY_WINDOW_TITLE], so the harness can
 * assert its presence and z-order with `dumpsys window`.
 *
 * ## Runtime boundary
 * The runtime is the SOLE caller of [present] and [dismiss], so every apply result feeds the authoritative
 * presentation-health fact. This presenter renders directly from the [LockPresentation] passed to [present]
 * (it keeps the full DTO), and never reconciles the overlay on its own. It reaches the runtime only through
 * the app-scoped [completionSink] (a surface reports an unlock or a safe-dismiss). A dead window token
 * ([WindowManager.BadTokenException]) drops the host and returns [SurfaceApplyResult.Failed]; the runtime's
 * own bounded re-drive re-adds it on the next [present]. The presenter does NOT call
 * `LockEngineRuntime.retryPresentation` from a present() fault: that re-arms the re-drive budget, so a
 * present()-internal fault would defeat the bound. retryPresentation is F's edge-triggered recovery (a
 * restored overlay grant, or an out-of-band host loss), not a present()-failure path. A surface NEVER
 * dismisses itself: on an accepted unlock the runtime drives [dismiss]. Because the [ComposeView] is warm and
 * add-once, the hosted [LockScreen] is scoped `key(request) { LockScreen(...) }`, so a lock-request
 * supersession does not leak the prior request's entered digits or wrong-PIN prompt into the next.
 */
class OverlayLockPresenter internal constructor(
    private val context: Context,
    private val credentialRepository: CredentialRepository,
    private val lockoutManager: LockoutManager,
    private val settings: SettingsRepository,
    private val completionSink: LockCompletionSink = LockCompletionBridge,
    windowHost: OverlayWindowHost? = null,
) : LockPresenter {

    // The WindowManager seam, injected ONCE at construction and never reassigned, so every add, update, and
    // remove routes through one host. The default is the production WindowManager delegate; the fleet fault
    // test passes a throwing decorator. It is defaulted, so the LockPresenter, runtime, and DI contract is
    // unchanged: F constructs the presenter as before, with no window-host argument.
    private val windowHost: OverlayWindowHost =
        windowHost ?: WindowManagerOverlayHost(context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)

    // FLAG_SECURE on non-debuggable builds only, so a debuggable fleet build stays screencap-able (FR-171).
    private val secure = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0

    // The single warm host. Non-null once added; kept for the process. Reset only on a dead-token rebuild.
    private var overlayRoot: FrameLayout? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    // What the ComposeView renders. present() sets it; dismiss() clears it. Compose observes it for
    // recomposition, and the render keys the Lock surface by its full request token (C1 contract).
    private val currentPresentation = mutableStateOf<LockPresentation?>(null)

    // Guards the FR-002/FR-007 auto-prompt to once per request token, tracked OUTSIDE Compose so a Compose
    // host rebuild (a BadToken re-add) does not re-auto-launch the same request. Main-thread only.
    private var lastAutoPromptedToken: RequestToken? = null

    // The bounded auto-launch retry budget for the CURRENT request, also tracked OUTSIDE Compose so a host
    // rebuild does not reset it and re-arm the retries. Main-thread only. autoRetryCount is meaningful only
    // for autoRetryToken (a new request resets it).
    private var autoRetryToken: RequestToken? = null
    private var autoRetryCount = 0

    // Non-throwing surface: a missing grant is Unavailable, a dead token triggers a rebuild-and-retry, and
    // any other WindowManager fault becomes Failed. The interpreter contains a throw, so this must not throw.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override fun present(presentation: LockPresentation): SurfaceApplyResult {
        // Fail-secure: without the grant the overlay cannot draw, so report Unavailable. (The health fact
        // treats it as an enforcement gap; the overlayGrant fact records the missing grant; protection is
        // gated on the grant, Decision D-P2-2.) canDrawOverlays is itself a system call, so a throw from it
        // becomes a Failed apply here, not an escape from the non-throwing surface.
        val canDraw = try {
            OverlayPermission.canDrawOverlays(context)
        } catch (e: RuntimeException) {
            return SurfaceApplyResult.Failed("overlay_check_exception")
        }
        if (!canDraw) return SurfaceApplyResult.Unavailable
        return try {
            ensureAdded() // adds the window in the DISMISSED state (GONE + pass-through), never blocking
            // Fail-atomic: the fallible updateViewLayout (the reveal to interactive) runs FIRST, on a copied
            // LayoutParams, so a throw leaves the live window untouched. On a warm re-present that is the prior
            // surface, still up. On a COLD add it is the just-added dismissed window (GONE + FLAG_NOT_TOUCHABLE),
            // never a blank, touch-modal overlay over the app underneath. Only after the reveal succeeds do we
            // set the visible state and the rendered presentation.
            applyWindowFlags(interactiveFlags())
            overlayRoot?.visibility = View.VISIBLE
            currentPresentation.value = presentation
            SurfaceApplyResult.Success
        } catch (e: WindowManager.BadTokenException) {
            // The window token died. Drop the dead host and report Failed. The runtime's own BOUNDED re-drive
            // re-adds it on the next present() (ensureAdded), so a persistent fault stops after
            // MAX_REDRIVE_ATTEMPTS. The presenter does NOT trigger retryPresentation here: that re-arms the
            // budget, so a present()-internal fault would defeat the bound (a busy retry loop).
            resetHost()
            SurfaceApplyResult.Failed("bad_token")
        } catch (e: RuntimeException) {
            SurfaceApplyResult.Failed(e.javaClass.simpleName)
        }
    }

    @Suppress("TooGenericExceptionCaught") // non-throwing surface: any WindowManager fault becomes Failed
    override fun dismiss(): SurfaceApplyResult {
        if (overlayRoot == null) return SurfaceApplyResult.Success // already absent: idempotent no-op
        return try {
            // Fail-atomic + fail-closed: apply pass-through FIRST via a copied LayoutParams. Only after it
            // succeeds do we hide the window and clear the content. A throw returns Failed with the guard
            // still fully up (visible + touch-modal), so a failed hide never blanks a still-reported surface.
            applyWindowFlags(passthroughFlags())
            overlayRoot?.visibility = View.GONE
            currentPresentation.value = null
            SurfaceApplyResult.Success
        } catch (e: RuntimeException) {
            SurfaceApplyResult.Failed(e.javaClass.simpleName)
        }
    }

    /** Releases the overlay window. For F / tests; production is process-lifetime so this rarely runs. */
    fun release() = resetHost()

    // ---- Window management (main thread; the runtime calls present/dismiss on the main dispatcher) ------

    @Suppress("TooGenericExceptionCaught") // transactional cleanup on ANY add fault, then rethrow to present()
    private fun ensureAdded() {
        if (overlayRoot != null) return
        val owner = OverlayLifecycleOwner().apply { start() }
        val composeView = ComposeView(context).apply {
            setContent { OverlayContent() }
        }
        // Added in the DISMISSED state: GONE and pass-through (FLAG_NOT_TOUCHABLE, via buildParams), so a
        // window that is added but not yet revealed blocks nothing. present() reveals it (interactive and
        // VISIBLE) only after the fallible reveal succeeds, so a failed cold add never strands a blank,
        // touch-modal overlay over the app underneath.
        //
        // Set the ViewTree owners on this root view, not on the child ComposeView. The WindowManager attaches
        // this root view. Compose reads the window recomposer from the root view and calls
        // findViewTreeLifecycleOwner() on it. If only the child holds the owners, Compose does not find them, and
        // composition fails at attach. Set the owners before addView.
        val root = OverlayRoot(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            addView(composeView)
            visibility = View.GONE
        }
        try {
            windowHost.addView(root, buildParams()) // may throw BadTokenException / on a draw failure
        } catch (e: RuntimeException) {
            // Transactional: the fields are still null, so resetHost() cannot reach this owner/composition.
            // Tear them down here (destroy the owner + clear its ViewModelStore; detach the ComposeView so
            // its composition disposes) and rethrow, so present() maps it to Failed and the next present
            // retries from a clean slate rather than leaking a RESUMED owner per attempt.
            owner.stop()
            root.removeAllViews()
            throw e
        }
        overlayRoot = root
        lifecycleOwner = owner
    }

    // Failure-atomic AND total (non-throwing). resetHost runs inside present()'s BadToken catch, so a throw
    // here would ESCAPE the non-throwing surface (a sibling catch does not cover a throw from within a catch).
    // It must not orphan the window (clear the handles while the window is still up) nor wedge it (keep the
    // handles for a window that is gone, so ensureAdded() early-returns forever and every present() updates a
    // dead view). So it clears the handles only once the window is CONFIRMED gone. If the window is still
    // attached, it removes it. A removal that throws may have detached the view first, so it RE-QUERIES
    // attachment: it clears on confirmed detached and retains on confirmed still-attached. The attachment query
    // and the owner teardown are both contained. An indeterminate attachment (the query itself throws) RETAINS,
    // because orphaning a live touch-modal window is worse than a stale handle. currentPresentation is cleared
    // only on a confirmed teardown.
    private fun resetHost() {
        val root = overlayRoot
        if (root != null && attached(root, ifIndeterminate = true)) {
            val removed = runCatching { windowHost.removeViewImmediate(root) }.isSuccess
            if (!removed && attached(root, ifIndeterminate = true)) return // still up: retain, do not orphan
        }
        clearHostHandles()
    }

    /** [OverlayWindowHost.isAttached], contained: a throwing query returns [ifIndeterminate], the safe default. */
    private fun attached(root: View, ifIndeterminate: Boolean): Boolean =
        runCatching { windowHost.isAttached(root) }.getOrDefault(ifIndeterminate)

    private fun clearHostHandles() {
        runCatching { lifecycleOwner?.stop() } // contained: keep resetHost total even if owner teardown throws
        overlayRoot = null
        lifecycleOwner = null
        currentPresentation.value = null
    }

    /**
     * Applies [flags] to the warm window through a COPIED [WindowManager.LayoutParams], so a throwing
     * `updateViewLayout` leaves the live params (and thus the current visibility / interactivity) unchanged.
     * The caller commits `visibility` and the rendered presentation only after this returns.
     */
    private fun applyWindowFlags(flags: Int) {
        val root = overlayRoot ?: return
        val params = WindowManager.LayoutParams().apply {
            copyFrom(root.layoutParams as WindowManager.LayoutParams)
            this.flags = flags
        }
        windowHost.updateViewLayout(root, params)
    }

    private fun buildParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Added pass-through (FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE): the window is born in the dismissed
            // state and present() flips it to interactive, so a cold add that fails to reveal blocks nothing.
            passthroughFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            title = OVERLAY_WINDOW_TITLE
            gravity = Gravity.CENTER
        }

    // The window-flag policy is a pure, JVM-tested computation (OverlayWindowFlags): FLAG_SECURE on a
    // non-debug build in both states, and the modality bits only when dismissed (pass-through).
    private fun interactiveFlags(): Int = OverlayWindowFlags.forState(secure = secure, interactive = true)

    private fun passthroughFlags(): Int = OverlayWindowFlags.forState(secure = secure, interactive = false)

    // ---- Composition -----------------------------------------------------------------------------------

    @Composable
    private fun OverlayContent() {
        val presentation by currentPresentation
        AppLockTheme {
            when (val p = presentation) {
                is LockPresentation.Lock -> key(p.request) { LockSurface(p) }
                is LockPresentation.Checking -> CheckingShield(p.readiness)
                is LockPresentation.Recovery -> RecoveryShield(p.readiness)
                null -> Unit // dismissed; the window is GONE
            }
        }
    }

    @Composable
    private fun LockSurface(lock: LockPresentation.Lock) {
        val appLabel = remember(lock.target) { resolveAppLabel(lock.target) }
        val biometricsAvailable = remember { biometricsAvailable() }
        // Offer biometrics at once (FR-002/FR-007), once per request token (this composition is
        // key(request)-scoped). driveAutoLaunch marks the request prompted ONLY after the host acknowledges its
        // exact lease, not when startActivity returns (a silently aborted BAL also returns). It waits
        // cancellably for a foreign owner and, after an unacknowledged lease expires, makes one bounded retry
        // then gives up (PIN and the manual button remain). The effect is key(request)-scoped, so a
        // supersession cancels the wait. launchBiometric re-checks the lockout on each attempt.
        LaunchedEffect(lock.request) {
            if (biometricsAvailable) {
                driveAutoLaunch(
                    request = lock.request,
                    alreadyPrompted = { it == lastAutoPromptedToken },
                    markPrompted = { lastAutoPromptedToken = it },
                    retriesUsed = ::autoRetriesUsed,
                    recordRetry = ::recordAutoRetry,
                    maxRetries = MAX_AUTO_RETRIES,
                    launch = { launchBiometric(it, appLabel) },
                    awaitAck = { handle -> withTimeoutOrNull(ACK_TIMEOUT_MS) { handle.awaitAck() } != null },
                    pollDelayMs = GATE_POLL_MS,
                )
            }
        }
        LockScreen(
            appLabel = appLabel,
            biometricsAvailable = biometricsAvailable,
            lockoutRemaining = ::lockoutRemaining,
            onPinEntered = { pin -> onPinEntered(lock.request, pin) },
            onBiometricClick = { launchBiometric(lock.request, appLabel) },
        )
    }

    /** The [android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION] escape and a plain home escape. */
    @Composable
    private fun RecoveryShield(readiness: SurfaceToken) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.overlay_recovery_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.overlay_recovery_body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = { escapeToOverlaySettings(readiness) }) {
                    Text(stringResource(R.string.overlay_recovery_grant))
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { escapeHome(readiness) }) {
                    Text(stringResource(R.string.overlay_go_home))
                }
            }
        }
    }

    /**
     * The Loading/checking shield: a spinner and text, plus the escape control the escape-hatch rule
     * (M7_PLAN §2.3) requires of every shield, so the user is never trapped behind a shield that outlives
     * Home/Back. Checking is a transient not-ready state, so the escape opens App Lock's own screen.
     */
    @Composable
    private fun CheckingShield(readiness: SurfaceToken) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.overlay_checking),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                TextButton(onClick = { escapeToAppLock(readiness) }) {
                    Text(stringResource(R.string.overlay_open_app_lock))
                }
            }
        }
    }

    // ---- Surface completions (all through the app-scoped sink; the surface never self-dismisses) --------

    private fun onPinEntered(request: RequestToken, pin: CharArray): PinAttemptResult {
        if (lockoutRemaining() > 0) return PinAttemptResult.IGNORED
        return if (credentialRepository.verifyPin(pin)) {
            // Report success; the runtime drives dismiss(). Keep the input (CORRECT) so the pad does not
            // flash a cleared state before the runtime hides the overlay. If the sink is unbound the
            // completion is dropped and the overlay stays up (fail-closed), still showing the PIN.
            completionSink.unlockSucceeded(request, UnlockMethod.PIN)
            PinAttemptResult.CORRECT
        } else {
            completionSink.unlockFailed(request, UnlockMethod.PIN)
            PinAttemptResult.INCORRECT
        }
    }

    /**
     * Launches the biometric host for [request] and reports the [LaunchOutcome]. Single-flight across the
     * overlay-to-host window boundary (the legacy in-instance guard cannot cross it): it claims for this
     * request before the background-activity-launch and receives a [LeaseHandle], and it releases (this token +
     * lease) only if the launch itself throws. The host acknowledges the lease in onCreate (which completes the
     * handle's latch) and releases its own (token + lease) when done. A non-throwing `startActivity` means the
     * launch was only REQUESTED, not that a host started (a silently aborted BAL also returns), so this returns
     * [LaunchOutcome.Requested] carrying the lease to AWAIT: the auto-launch consumes the once-per-request
     * prompt only after that lease is acknowledged. If our token already owns a lease (for example, a manual
     * tap launched a host), no second host starts and the existing lease is returned to await. The launch is a
     * BAL permitted by the visible overlay window (ADR-020 case (a)). The transparent host carries the full
     * request token (epoch + id), the lease id, and the label for the prompt title.
     */
    private fun launchBiometric(request: RequestToken, label: String): LaunchOutcome {
        // Re-check the lockout just before claiming or launching: the composable's countdown polls, so its
        // button (and the auto-launch retry) can lag the lockout onset by one poll interval (this matches the
        // legacy Activity's guard). F's manager-owned degraded countdown replaces this query without changing it.
        if (lockoutRemaining() > 0) return LaunchOutcome.LockedOut
        return when (val claim = BiometricLaunchGate.claim(request)) {
            // A different request owns the live prompt; the caller decides whether to wait or drop.
            BiometricLaunchGate.ClaimOutcome.BusyOther -> LaunchOutcome.BusyOther
            // Our token already owns a lease (no second host): return it so the caller awaits ITS acknowledgement
            // rather than assuming a prompt exists (the lease may still be an unacknowledged pending launch).
            is BiometricLaunchGate.ClaimOutcome.OwnedByToken -> LaunchOutcome.Requested(claim.lease)
            is BiometricLaunchGate.ClaimOutcome.Claimed -> {
                val launched = runCatching {
                    val intent = BiometricHostActivity.createIntent(context, request, claim.lease.leaseId, label)
                    context.startActivity(intent)
                }.isSuccess
                if (launched) {
                    LaunchOutcome.Requested(claim.lease)
                } else {
                    // Free the just-made claim (matched by token AND lease) so a retry can proceed immediately.
                    BiometricLaunchGate.release(request, claim.lease.leaseId)
                    LaunchOutcome.LaunchFailed
                }
            }
        }
    }

    // The auto-launch retry budget, main-thread only, surviving Compose host rebuilds (see the fields above).
    private fun autoRetriesUsed(request: RequestToken): Int =
        if (request == autoRetryToken) autoRetryCount else 0

    private fun recordAutoRetry(request: RequestToken) {
        if (request != autoRetryToken) {
            autoRetryToken = request
            autoRetryCount = 0
        }
        autoRetryCount++
    }

    private fun escapeHome(token: SurfaceToken) {
        completionSink.safeDismissRequested(token, SafeDestination.HOME)
    }

    private fun escapeToAppLock(token: SurfaceToken) {
        completionSink.safeDismissRequested(token, SafeDestination.APP_LOCK)
    }

    private fun escapeToOverlaySettings(token: SurfaceToken) {
        completionSink.safeDismissRequested(
            token,
            SafeDestination.OVERLAY_SETTINGS,
            OverlayPermission.overlaySettingsPackages(context),
        )
    }

    /**
     * The back control: a leave-without-auth to HOME over whatever surface is live (never reveals the app).
     * The runtime drives the actual dismiss on an accepted safe-dismiss; the sink result is not consulted
     * here, because Back stays fail-closed (see [OverlayRoot]) whether or not a sink is currently bound.
     */
    private fun onBack() {
        val token: SurfaceToken = when (val p = currentPresentation.value) {
            is LockPresentation.Lock -> p.request
            is LockPresentation.Checking -> p.readiness
            is LockPresentation.Recovery -> p.readiness
            null -> return
        }
        completionSink.safeDismissRequested(token, SafeDestination.HOME)
    }

    // ---- Read helpers (D-P2-1: the overlay reads the lockout manager live) -----------------------------

    private fun lockoutRemaining(): Long =
        (lockoutManager.currentState() as? LockoutState.LockedOut)?.remainingMs ?: 0L

    private fun biometricsAvailable(): Boolean =
        settings.biometricUnlockEnabled &&
            BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    @Suppress("DEPRECATION", "SwallowedException") // getApplicationInfo(pkg, 0): the flags overload is 33+
    private fun resolveAppLabel(packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }

    /**
     * The overlay root. On API 33+ it registers an [OnBackInvokedCallback] at
     * [OnBackInvokedDispatcher.PRIORITY_OVERLAY] while attached: targetSdk 36 routes Back through the
     * dispatcher and no longer delivers `KEYCODE_BACK`, so the leave-without-auth escape must ride the new
     * API. The [dispatchKeyEvent] path is the pre-33 fallback, and it is FAIL-CLOSED: while any guard surface
     * is presented it consumes Back unconditionally, so an unbound (or busy) sink can never let Back fall
     * through to reveal the app.
     */
    private inner class OverlayRoot(context: Context) : FrameLayout(context) {

        private var backCallback: OnBackInvokedCallback? = null

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val callback = OnBackInvokedCallback { onBack() }
                findOnBackInvokedDispatcher()
                    ?.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback)
                backCallback = callback
            }
        }

        override fun onDetachedFromWindow() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                backCallback?.let { findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(it) }
                backCallback = null
            }
            super.onDetachedFromWindow()
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode != KeyEvent.KEYCODE_BACK || currentPresentation.value == null) {
                return super.dispatchKeyEvent(event)
            }
            // Fail-closed: a presented guard consumes EVERY Back action (down and up), so the framework can
            // never act on the down before our up handler runs. The leave-without-auth fires once, on a
            // non-cancelled up (a cancelled up follows a long-press or an interrupted sequence).
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onBack()
            return true
        }
    }

    companion object {
        /** The overlay window's STABLE title. The harness asserts on it via `dumpsys window`; do not change. */
        const val OVERLAY_WINDOW_TITLE = "AppLockOverlay"

        /** Poll cadence for the auto-launch wait while another request owns the biometric gate. */
        private const val GATE_POLL_MS = 100L

        /** At most one automatic re-launch after an unacknowledged lease expires; then PIN + manual remain. */
        private const val MAX_AUTO_RETRIES = 1

        // How long the auto-launch awaits host acknowledgement: just past the gate's lease timeout, so timing
        // out here coincides with the lease becoming reclaimable (the next launch mints a fresh lease).
        private const val ACK_TIMEOUT_MS = BiometricLaunchGate.LEASE_TIMEOUT_MS + 1_000L
    }
}

/**
 * The result of an [OverlayLockPresenter] biometric launch attempt. [Requested] carries the [LeaseHandle] to
 * await: the launch call returned (a fresh host started, or our token already owned a lease), but that does
 * not prove a host is up (a silently aborted background launch also returns). So the auto-launch loop awaits
 * the lease's acknowledgement latch before it consumes the request's once-per-request auto-prompt.
 */
internal sealed interface LaunchOutcome {
    /** The launch call returned. Await [lease]'s acknowledgement to confirm a host actually started. */
    data class Requested(val lease: LeaseHandle) : LaunchOutcome

    /** A different request owns the gate; the caller waits for it to free rather than launch. */
    data object BusyOther : LaunchOutcome

    /**
     * A lockout is in effect: do not launch, and do not consume the auto-prompt. The auto-launch loop EXITS on
     * this. It does not wait through the lockout, and it does not auto-prompt when the lockout later expires
     * (this matches the legacy Activity: the countdown and the manual button remain; no prompt pops unbidden).
     */
    data object LockedOut : LaunchOutcome

    /** The startActivity itself threw; the claim was released. Do not consume the auto-prompt. */
    data object LaunchFailed : LaunchOutcome
}

/**
 * Drives the once-per-request biometric auto-launch (FR-002/FR-007). It marks the request prompted ONLY after
 * the host acknowledges the launched lease ([awaitAck] returns true), never because the launch call returned
 * (a silently aborted background launch returns without a host). On [LaunchOutcome.BusyOther] (a foreign
 * request owns the gate) it waits [pollDelayMs] and retries cancellably; the caller runs this in a
 * `key(request)` `LaunchedEffect`, so a superseding request cancels it, and foreign-owner polling does not
 * count against the retry budget. On [LaunchOutcome.Requested] it awaits acknowledgement. If the lease expires
 * unacknowledged, it makes at most [maxRetries] further attempts, then gives up WITHOUT marking (PIN and the
 * manual button remain). The budget is read and recorded through [retriesUsed] and [recordRetry], which the
 * caller backs OUTSIDE Compose so a host rebuild does not reset it. [LaunchOutcome.LockedOut] and
 * [LaunchOutcome.LaunchFailed] EXIT without marking. Extracted as a pure suspend function, so the
 * acknowledgement-gating, bounded retry, and cancellation are JVM-testable.
 */
@Suppress("LongParameterList") // deliberate: the bookkeeping seams are explicit so the loop is JVM-testable
internal suspend fun driveAutoLaunch(
    request: RequestToken,
    alreadyPrompted: (RequestToken) -> Boolean,
    markPrompted: (RequestToken) -> Unit,
    retriesUsed: (RequestToken) -> Int,
    recordRetry: (RequestToken) -> Unit,
    maxRetries: Int,
    launch: (RequestToken) -> LaunchOutcome,
    awaitAck: suspend (LeaseHandle) -> Boolean,
    pollDelayMs: Long,
) {
    // alreadyPrompted OR an exhausted retry budget (both survive a Compose host rebuild) stop a re-armed
    // re-launch, so the total launches for a request are bounded across every (re)entry of this effect.
    if (alreadyPrompted(request) || retriesUsed(request) >= maxRetries) return
    var running = true
    while (running) {
        when (val outcome = launch(request)) {
            // A lockout suppresses; a launch that threw exits. Neither marks nor retries.
            LaunchOutcome.LockedOut, LaunchOutcome.LaunchFailed -> running = false
            LaunchOutcome.BusyOther -> delay(pollDelayMs) // foreign owner: wait, NOT a retry, loop again
            is LaunchOutcome.Requested ->
                if (awaitAck(outcome.lease)) {
                    markPrompted(request) // a host actually came up: the auto-prompt is genuinely spent
                    running = false
                } else if (retriesUsed(request) >= maxRetries) {
                    running = false // lease expired unacknowledged and the retry budget is spent: give up unmarked
                } else {
                    recordRetry(request) // spend one bounded retry: loop and re-launch
                }
        }
    }
}

/**
 * The minimal owner set a [ComposeView] needs outside an Activity: a [Lifecycle], a [ViewModelStore], and
 * a [SavedStateRegistry]. Held at RESUMED while the overlay is added, so Compose runs; cleared on teardown.
 */
private class OverlayLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun start() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
