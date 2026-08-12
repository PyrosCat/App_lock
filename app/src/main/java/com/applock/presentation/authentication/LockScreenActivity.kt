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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Shown on top of a protected app when authentication is required.
 * FragmentActivity (not ComponentActivity) because androidx.biometric
 * requires one to host its prompt.
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
                Surface(modifier = Modifier.fillMaxSize()) {
                    var error by rememberSaveable { mutableStateOf(false) }
                    var lockoutRemainingMs by remember { mutableLongStateOf(lockoutRemaining()) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            lockoutRemainingMs = lockoutRemaining()
                            delay(250)
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = appLabel,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        if (lockoutRemainingMs > 0) {
                            LockoutCountdown(lockoutRemainingMs)
                        } else {
                            Text(
                                text = getString(
                                    if (error) R.string.pin_incorrect else R.string.enter_pin
                                ),
                                color = if (error) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(32.dp))
                            PinPad(
                                onPinComplete = { pin ->
                                    if (lockoutRemaining() > 0) return@PinPad true
                                    if (credentialRepository.verifyPin(pin)) {
                                        authenticated = true
                                        lockEngine.onUnlockSuccess(targetPackage)
                                        finish()
                                        false // don't clear — we're leaving
                                    } else {
                                        error = true
                                        lockEngine.onUnlockFailure(targetPackage)
                                        true // clear input, let the user retry
                                    }
                                },
                            )
                            if (biometricsAvailable) {
                                Spacer(Modifier.height(16.dp))
                                TextButton(onClick = { showBiometricPrompt(appLabel) }) {
                                    Icon(
                                        Icons.Filled.Fingerprint,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        text = getString(R.string.unlock_with_biometrics),
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // FR-002/FR-007: offer biometrics right away; PIN pad stays behind
        // the prompt as the fallback.
        if (biometricsAvailable && lockoutRemaining() == 0L) {
            showBiometricPrompt(appLabel)
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

@Composable
private fun LockoutCountdown(remainingMs: Long) {
    val totalSeconds = (remainingMs + 999) / 1000
    val formatted = "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.lockout_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.lockout_countdown, formatted),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
