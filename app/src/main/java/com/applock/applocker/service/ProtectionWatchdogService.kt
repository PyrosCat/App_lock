package com.applock.applocker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.applock.R
import com.applock.core.Graph
import com.applock.core.database.SecurityEventEntity
import com.applock.core.database.SecurityEventType
import com.applock.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground watchdog (FR-179): keeps the process warm and periodically
 * verifies that the accessibility service backing app detection is still
 * enabled. If protection is expected but the permission was revoked, raises
 * a high-priority notification that deep-links to accessibility settings.
 *
 * Stops itself when there is nothing to protect so the ongoing notification
 * doesn't linger for unconfigured installs.
 */
class ProtectionWatchdogService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wasHealthy = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForeground(ONGOING_NOTIFICATION_ID, ongoingNotification())
        scope.launch {
            while (isActive) {
                checkProtectionHealth()
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun checkProtectionHealth() {
        val expectProtection = Graph.credentialRepository.isPinSet() &&
            Graph.policyManager.protectedPackages.value.isNotEmpty()
        if (!expectProtection) {
            stopSelf()
            return
        }

        val healthy = AppDetectionService.isEnabled(this)
        val notifications = NotificationManagerCompat.from(this)
        if (!healthy) {
            if (wasHealthy) {
                Log.w(TAG, "Accessibility service disabled while apps are protected")
                Graph.appScope.launch {
                    Graph.database.securityEventDao().insert(
                        SecurityEventEntity(
                            eventType = SecurityEventType.PROTECTION_DISABLED,
                            packageName = null,
                        )
                    )
                }
            }
            if (notifications.areNotificationsEnabled()) {
                runCatching { notifications.notify(ALERT_NOTIFICATION_ID, alertNotification()) }
            }
        } else {
            notifications.cancel(ALERT_NOTIFICATION_ID)
        }
        wasHealthy = healthy
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                getString(R.string.watchdog_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                getString(R.string.protection_lost_title),
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
    }

    private fun ongoingNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(getString(R.string.watchdog_notification_title))
            .setContentText(getString(R.string.watchdog_notification_body))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun alertNotification(): Notification {
        val openAccessibilitySettings = PendingIntent.getActivity(
            this,
            1,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(getString(R.string.protection_lost_title))
            .setContentText(getString(R.string.protection_lost_body))
            .setContentIntent(openAccessibilitySettings)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    companion object {
        private const val TAG = "ProtectionWatchdog"
        private const val CHANNEL_STATUS = "protection_status"
        private const val CHANNEL_ALERTS = "protection_alerts"
        private const val ONGOING_NOTIFICATION_ID = 1
        private const val ALERT_NOTIFICATION_ID = 2
        private const val CHECK_INTERVAL_MS = 60_000L

        /**
         * Foreground-service starts are restricted in some contexts (background
         * on 12+, some receiver types on 15+) — treat failure as non-fatal, the
         * next MainActivity launch retries.
         */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ProtectionWatchdogService::class.java),
                )
            } catch (e: Exception) {
                Log.w(TAG, "Unable to start watchdog", e)
            }
        }
    }
}
