package com.applock.platform.spike

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * M7 WP0 SPIKE (throwaway). A transparent `FragmentActivity` hosting `BiometricPrompt`, launched
 * from the overlay as a **background-activity-launch permitted by the visible overlay window**
 * (ADR-020 case (a) — distinct from the FGS-start rule). WP0 proves this path works across API
 * 30/33/35/36; PIN is always the production fallback (not built in the spike, just logged).
 */
class SpikeBiometricActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = intent.getStringExtra(EXTRA_TARGET) ?: "?"
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback())
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("M7 Spike unlock")
            .setSubtitle(target)
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }

    private fun callback() = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            Log.i(SpikeConfig.LOG_TAG, "biometric SUCCESS")
            finish()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            Log.i(SpikeConfig.LOG_TAG, "biometric ERROR $errorCode $errString (PIN fallback in prod)")
            finish()
        }

        override fun onAuthenticationFailed() {
            Log.i(SpikeConfig.LOG_TAG, "biometric FAILED (retry)")
        }
    }

    companion object {
        private const val EXTRA_TARGET = "target"

        fun launch(context: Context, targetPackage: String) {
            val intent = Intent(context, SpikeBiometricActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_TARGET, targetPackage)
            }
            context.startActivity(intent) // BAL: permitted by the app's visible overlay window
        }
    }
}
