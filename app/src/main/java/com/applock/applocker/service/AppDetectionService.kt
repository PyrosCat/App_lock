package com.applock.applocker.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import com.applock.service.ApplicationLockEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Monitors foreground application changes via window-state accessibility
 * events (TAS §10) and forwards them to the lock engine.
 */
@AndroidEntryPoint
class AppDetectionService : AccessibilityService() {

    @Inject
    lateinit var lockEngine: ApplicationLockEngine

    // Read lazily in onReceive (screen-off, long after Hilt injection completes in onCreate).
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                lockEngine.onScreenOff()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        lockEngine.onAppForegrounded(packageName)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOffReceiver) }
        super.onDestroy()
    }

    companion object {
        /** Checks whether this accessibility service is enabled in system settings. */
        fun isEnabled(context: Context): Boolean {
            val enabled = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val expected = "${context.packageName}/${AppDetectionService::class.java.name}"
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
