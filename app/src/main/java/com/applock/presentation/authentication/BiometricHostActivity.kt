package com.applock.presentation.authentication

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import com.applock.R
import com.applock.service.engine.Epoch
import com.applock.service.engine.LockCompletionBridge
import com.applock.service.engine.RequestId
import com.applock.service.engine.RequestToken
import com.applock.service.engine.UnlockMethod

/**
 * The transparent biometric host for the overlay lock (M7 WP2 change E). The overlay is a WindowManager
 * window, not an Activity, and `androidx.biometric.BiometricPrompt` requires a [FragmentActivity] host, so
 * the overlay launches this Activity as a background-activity-launch permitted by its own visible overlay
 * window (ADR-020 case (a)). It is transparent (its manifest theme), carries its own `FLAG_SECURE` on
 * non-debuggable builds, and is `exported=false` / `excludeFromRecents` / `taskAffinity=""`.
 *
 * ## Request identity and the single-flight lease
 * The intent carries the FULL request token, [EXTRA_EPOCH] + [EXTRA_REQUEST_ID], plus the single-flight
 * [EXTRA_LEASE_ID] the presenter claimed. The Activity saves all three in `savedInstanceState`, reports the
 * token back through [LockCompletionBridge] as a [RequestToken], and acknowledges and releases the gate by
 * (token, lease). If the system recreates this Activity in a FRESH process, the gate (a process singleton) is
 * empty, so `acknowledge` returns false and the host finishes SILENTLY without authenticating. Per ADR-020,
 * full process death does not restore the old request: the fresh runtime re-derives and presents a new one.
 * (Carrying the real id, not defaulting to 0, also means a stale completion, if one were ever dispatched,
 * could not collide with a fresh id-0 request.) The `onCreate` acknowledgement is what tells
 * [BiometricLaunchGate] a host actually started: an unacknowledged lease (an aborted background launch that
 * never reached here) expires and is reclaimed, so a launch that fails silently never wedges the gate.
 *
 * ## No self-dismiss
 * On an accepted biometric this reports success and finishes ITSELF (closing the transparent host); it
 * never hides the overlay. The runtime is the sole presenter caller: it drives the overlay's dismiss on
 * the accepted unlock. A cancel or hardware error reports a biometric-cancel (which never touches the
 * lockout counter) and finishes, leaving the overlay's PIN pad as the fallback.
 */
class BiometricHostActivity : FragmentActivity() {

    private var epoch: Long = 0L
    private var requestId: Long = 0L
    private var leaseId: Long = 0L
    private var completed = false
    private val model: BiometricHostViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!debuggable) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }

        // On recreation, savedInstanceState is authoritative (it carries the epoch of the process that
        // launched us); otherwise read the launching intent. A missing token is a malformed launch.
        val source = savedInstanceState ?: intent.extras
        if (source == null || !source.hasRequestExtras()) {
            finish()
            return
        }
        epoch = source.getLong(EXTRA_EPOCH)
        requestId = source.getLong(EXTRA_REQUEST_ID)
        leaseId = source.getLong(EXTRA_LEASE_ID)

        showPrompt()
    }

    /** The full token (epoch + id) and the single-flight lease must all be present; else it is a malformed launch. */
    private fun Bundle.hasRequestExtras(): Boolean =
        containsKey(EXTRA_EPOCH) && containsKey(EXTRA_REQUEST_ID) && containsKey(EXTRA_LEASE_ID)

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(EXTRA_EPOCH, epoch)
        outState.putLong(EXTRA_REQUEST_ID, requestId)
        outState.putLong(EXTRA_LEASE_ID, leaseId)
    }

    override fun onDestroy() {
        // Token+lease-scoped release fallback for a finish that skipped the terminal completions (an abrupt
        // end). Idempotent with completeSucceeded/completeCancelled (a no-op once our (token, lease) no longer
        // owns the gate). A configuration change recreates us with the prompt still up, so keep the claim
        // across it; the auth-invoked guard then prevents a re-authenticate.
        if (!isChangingConfigurations) BiometricLaunchGate.release(token(), leaseId)
        super.onDestroy()
    }

    private fun token(): RequestToken = RequestToken(Epoch(epoch), RequestId(requestId))

    private fun showPrompt() {
        // Construct the prompt on EVERY onCreate, BEFORE any early return, so a configuration-change
        // recreation rebinds the callback to this instance (androidx keeps the visible prompt). Only the
        // first creation authenticates: a config recreation retains authInvoked (a non-persisted ViewModel),
        // so re-authenticating would cancel and replace the prompt.
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback())
        if (model.authInvoked) return
        // Ownership gate (enforced, not merely recorded): acknowledge succeeds ONLY when the gate still holds
        // our exact, unexpired (token, lease). It returns false for a free gate (full process death; per
        // ADR-020 we do NOT restore the old request), an expired (abandoned) lease, or a lease a newer request
        // reclaimed. In every false case this host finishes WITHOUT a completion rather than show a stale prompt
        // that would block the fresh runtime's new request; its onDestroy release() is then a no-op. The
        // acknowledge precedes canAuthenticate(), so a stale host never dispatches a cancel.
        if (!BiometricLaunchGate.acknowledge(token(), leaseId)) {
            finish()
            return
        }
        // Initial launch (whose ViewModel has authInvoked=false). The canAuthenticate() check lives on THIS
        // path only: a transient non-success on a config recreation must not skip the rebind above.
        if (BiometricManager.from(this).canAuthenticate(BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_SUCCESS) {
            completeCancelled() // no usable biometric: fall back to the overlay PIN pad
            return
        }
        model.authInvoked = true
        val appLabel = intent.getStringExtra(EXTRA_TARGET_LABEL) ?: getString(R.string.app_name)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title, appLabel))
            .setNegativeButtonText(getString(R.string.biometric_prompt_negative))
            .setAllowedAuthenticators(BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }

    private fun callback() = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
            completeSucceeded()

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) =
            completeCancelled() // user dismissed or hardware error: PIN fallback (FR-007)

        override fun onAuthenticationFailed() {
            // Unrecognized finger/face. The prompt stays up and counts its own retries; a system lockout
            // arrives as onAuthenticationError. No event, so the lockout counter is untouched.
        }
    }

    private fun completeSucceeded() {
        if (completed) return
        completed = true
        BiometricLaunchGate.release(token(), leaseId) // free BEFORE dispatch, so the flow's end is gate-free
        LockCompletionBridge.unlockSucceeded(token(), UnlockMethod.BIOMETRIC)
        finish() // close the host; the runtime dismisses the overlay on the accepted unlock
    }

    private fun completeCancelled() {
        if (completed) return
        completed = true
        BiometricLaunchGate.release(token(), leaseId) // free before dispatch, so a manual retry can re-claim
        LockCompletionBridge.biometricCancelled(token())
        finish()
    }

    companion object {
        private const val EXTRA_EPOCH = "epoch"
        private const val EXTRA_REQUEST_ID = "request_id"
        private const val EXTRA_LEASE_ID = "lease_id"
        private const val EXTRA_TARGET_LABEL = "target_label"

        /**
         * Builds the launch intent, carrying the full [token] (epoch + id), the single-flight [leaseId] the
         * presenter claimed (the host acknowledges and releases it), and a display label.
         */
        fun createIntent(context: Context, token: RequestToken, leaseId: Long, targetLabel: String? = null): Intent =
            Intent(context, BiometricHostActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_EPOCH, token.epoch.value)
                .putExtra(EXTRA_REQUEST_ID, token.id.n)
                .putExtra(EXTRA_LEASE_ID, leaseId)
                .apply { targetLabel?.let { putExtra(EXTRA_TARGET_LABEL, it) } }
    }
}

/**
 * Holds whether [BiometricHostActivity] has already invoked `authenticate()`. A [ViewModel] survives a
 * configuration change but NOT process death, which is exactly the distinction the host needs: a rotation
 * retains `authInvoked`, so it must NOT re-authenticate the already-visible prompt. A process-death
 * recreation instead gets a FRESH ViewModel (`authInvoked=false`) and re-runs `onCreate`, where it finds the
 * process-local gate empty, fails acknowledgement, and finishes silently WITHOUT authenticating (ADR-020:
 * process death does not restore the old request; the fresh runtime re-derives and presents a new one).
 */
class BiometricHostViewModel : ViewModel() {
    var authInvoked = false
}
