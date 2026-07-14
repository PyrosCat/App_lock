package com.applock.authentication.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.applock.R
import com.applock.core.Graph
import com.applock.ui.theme.AppLockTheme

/**
 * Shown on top of a protected app when authentication is required.
 */
class LockScreenActivity : ComponentActivity() {

    private lateinit var targetPackage: String
    private var authenticated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE) ?: run {
            finish(); return
        }

        // Back must not reveal the protected app underneath.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Graph.lockEngine.onLockScreenDismissed(targetPackage)
                finish()
            }
        })

        val appLabel = resolveAppLabel(targetPackage)

        setContent {
            AppLockTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var error by rememberSaveable { mutableStateOf(false) }
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
                                if (Graph.credentialRepository.verifyPin(pin)) {
                                    authenticated = true
                                    Graph.lockEngine.onUnlockSuccess(targetPackage)
                                    finish()
                                    false // don't clear — we're leaving
                                } else {
                                    error = true
                                    Graph.lockEngine.onUnlockFailure(targetPackage)
                                    true // clear input, let the user retry
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // If the lock screen loses foreground without a successful unlock
        // (e.g. user opened recents), don't leave a stale state behind.
        if (!authenticated && !isFinishing) finish()
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
