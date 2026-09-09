package com.applock.presentation.authentication

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.applock.R
import com.applock.data.SettingsRepository
import com.applock.presentation.theme.AppLockTheme
import com.applock.security.CredentialRepository
import com.applock.security.LockoutManager
import com.applock.security.LockoutState
import com.applock.service.ApplicationLockEngine
import com.applock.service.ApplicationLockEngine.UnlockMethod
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Shown on top of a protected app when authentication is required.
 * FragmentActivity (not ComponentActivity) because androidx.biometric
 * requires one to host its prompt.
 *
 * The authentication UI is the reusable [LockScreen] composable. This Activity gives it the
 * inputs and keeps the platform concerns: FLAG_SECURE, the back handler, the biometric prompt,
 * and the finish on pause. The system makes a new Activity instance for each lock request, so
 * [LockScreen] does not need a per-request key here.
 */
@AndroidEntryPoint
class LockScreenActivity : FragmentActivity() {

    @Inject
    lateinit var lockEngine: ApplicationLockEngine

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var credentialRepository: CredentialRepository

    @Inject
    lateinit var lockoutManager: LockoutManager

    private lateinit var targetPackage: String
    private var authenticated = false

    /** True while the system biometric dialog may be pausing us. */
    private var biometricInFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE) ?: run {
            finish(); return
        }

        // FR-171: block screenshots/recording of the auth screen. Debug builds
        // stay capturable so emulator verification via screencap keeps working.
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!debuggable) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }

        // Back must not reveal the protected app underneath.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                lockEngine.onLockScreenDismissed(targetPackage)
                finish()
            }
        })

        val appLabel = resolveAppLabel(targetPackage)
        val biometricsAvailable = settings.biometricUnlockEnabled &&
            BiometricManager.from(this)
                .canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

        setContent {
            AppLockTheme {
                LockScreen(
                    appLabel = appLabel,
                    biometricsAvailable = biometricsAvailable,
                    lockoutRemaining = ::lockoutRemaining,
                    onPinEntered = ::onPinEntered,
                    onBiometricClick = { showBiometricPrompt(appLabel) },
                )
            }
        }

        // FR-002/FR-007: offer biometrics right away; PIN pad stays behind
        // the prompt as the fallback.
        if (biometricsAvailable && lockoutRemaining() == 0L) {
            showBiometricPrompt(appLabel)
        }
    }

    /**
     * Checks a completed PIN entry and does the unlock work. It returns the result for [LockScreen]
     * to show. This is the former inline PinPad callback, without changes: during a lockout it
     * returns [PinAttemptResult.IGNORED] (the input clears, the error prompt does not change); a
     * correct PIN unlocks the app and closes the screen; a wrong PIN records the failure and returns
     * [PinAttemptResult.INCORRECT].
     */
    private fun onPinEntered(pin: CharArray): PinAttemptResult {
        if (lockoutRemaining() > 0) return PinAttemptResult.IGNORED
        return if (credentialRepository.verifyPin(pin)) {
            authenticated = true
            lockEngine.onUnlockSuccess(targetPackage)
            finish()
            PinAttemptResult.CORRECT
        } else {
            lockEngine.onUnlockFailure(targetPackage)
            PinAttemptResult.INCORRECT
        }
    }

    private fun lockoutRemaining(): Long =
        (lockoutManager.currentState() as? LockoutState.LockedOut)?.remainingMs ?: 0L

    private fun showBiometricPrompt(appLabel: String) {
        if (biometricInFlight || lockoutRemaining() > 0) return
        biometricInFlight = true
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    biometricInFlight = false
                    authenticated = true
                    lockEngine.onUnlockSuccess(targetPackage, UnlockMethod.BIOMETRIC)
                    finish()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // User dismissed or hardware error — fall back to PIN (FR-007).
                    biometricInFlight = false
                }

                override fun onAuthenticationFailed() {
                    // Unrecognized finger/face. The prompt stays up and counts
                    // its own retries; system lockout (errorCode LOCKOUT) lands
                    // in onAuthenticationError.
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title, appLabel))
            .setNegativeButtonText(getString(R.string.biometric_prompt_negative))
            .setAllowedAuthenticators(BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }

    override fun onPause() {
        super.onPause()
        // If the lock screen loses foreground without a successful unlock
        // (e.g. user opened recents), don't leave a stale state behind. The
        // biometric system dialog can also pause us — that one is fine.
        if (!authenticated && !isFinishing && !biometricInFlight) finish()
    }

    private fun resolveAppLabel(packageName: String): String = try {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }

    companion object {
        private const val EXTRA_TARGET_PACKAGE = "target_package"

        fun createIntent(context: Context, targetPackage: String): Intent =
            Intent(context, LockScreenActivity::class.java)
                .putExtra(EXTRA_TARGET_PACKAGE, targetPackage)
    }
}
