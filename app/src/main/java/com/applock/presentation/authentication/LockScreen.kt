package com.applock.presentation.authentication

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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.applock.R
import kotlinx.coroutines.delay

/**
 * The full-screen authentication surface for a protected app. It shows the app label and either a
 * lockout countdown, or the PIN pad with an optional biometric button.
 *
 * The composable is host-agnostic. It keeps only its own UI state (the wrong-PIN flag and the last
 * lockout time), and the PinPad keeps the entered digits. The host makes each security decision
 * through [lockoutRemaining], [onPinEntered] and [onBiometricClick]. [LockScreenActivity] hosts it
 * now, and the overlay presenter will host it later. The host must put it in an `AppLockTheme`.
 *
 * The host must reset the per-request state. If a host keeps one warm composition for more than one
 * request (the change E overlay presenter keeps one add-once ComposeView), it must wrap this call
 * with `key(requestToken) { LockScreen(...) }`. If it does not, a new request keeps the wrong-PIN
 * prompt and the entered digits from the previous request. The Activity makes a new composition for
 * each request, so it does not need a key.
 */
@Composable
fun LockScreen(
    appLabel: String,
    biometricsAvailable: Boolean,
    lockoutRemaining: () -> Long,
    onPinEntered: (CharArray) -> PinAttemptResult,
    onBiometricClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        var showPinError by rememberSaveable { mutableStateOf(false) }
        // The poll runs for the full composition. Read the latest lockoutRemaining on each tick,
        // not the callback from the first launch, so a host that supplies a new one stays correct.
        val latestLockoutRemaining by rememberUpdatedState(lockoutRemaining)
        var lockoutRemainingMs by remember { mutableLongStateOf(lockoutRemaining()) }
        LaunchedEffect(Unit) {
            while (true) {
                lockoutRemainingMs = latestLockoutRemaining()
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
                    text = stringResource(
                        if (showPinError) R.string.pin_incorrect else R.string.enter_pin
                    ),
                    color = if (showPinError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(32.dp))
                PinPad(
                    onPinComplete = { pin ->
                        when (onPinEntered(pin)) {
                            PinAttemptResult.CORRECT -> false // the host leaves; keep the input
                            PinAttemptResult.INCORRECT -> {
                                showPinError = true
                                true // clear the input to try again
                            }
                            PinAttemptResult.IGNORED -> true // clear the input; keep the error flag
                        }
                    },
                )
                if (biometricsAvailable) {
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = onBiometricClick) {
                        Icon(
                            Icons.Filled.Fingerprint,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(R.string.unlock_with_biometrics),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The result of a completed PIN entry. The host decides which result applies.
 *
 * The three results keep the behavior of the former inline PinPad callback. A correct PIN keeps the
 * input, because the host closes the screen. A wrong PIN shows the error prompt and clears the
 * input. An entry during an active lockout (the poll-interval race) clears the input, but does not
 * change the error flag. If [IGNORED] set the flag to false, the screen would lose an earlier
 * wrong-PIN prompt after the lockout ends.
 */
enum class PinAttemptResult {
    /** A correct PIN. The host closes the screen, so keep the input. */
    CORRECT,

    /** A wrong PIN. Show the error prompt and clear the input to try again. */
    INCORRECT,

    /** An entry during an active lockout. Clear the input, but keep the error flag. */
    IGNORED,
}

@Composable
private fun LockoutCountdown(remainingMs: Long) {
    // Round the lockout time up to whole seconds.
    val totalSeconds = (remainingMs + 999) / 1000
    val countdownText = "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
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
            text = stringResource(R.string.lockout_countdown, countdownText),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
