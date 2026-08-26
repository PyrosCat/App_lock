@file:Suppress("MagicNumber")

package com.applock.platform.spike

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.UserManager
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * M7 WP0 SPIKE (throwaway). A `specialUse` foreground service polling `UsageStatsManager.queryEvents`
 * to detect the foreground app, and driving [OverlayController] when the configured "protected"
 * package appears. Implements a **lite** slice of the §2.4 contract (ACTIVITY_RESUMED selection, a
 * `(package,timestamp)` cursor, `isUserUnlocked` gating, bootstrap-cursor-from-now); the full
 * contract — freshness `F`, wall-clock-jump guard, backoff, screen-off/unlock re-home — is WP3's
 * production `UsageAccessDetector`. Poll runs on the main looper (overlay ops need it; no wakelock).
 */
class UsagePollService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var usageStats: UsageStatsManager
    private lateinit var userManager: UserManager
    private lateinit var overlay: OverlayController

    private var pollCursor = 0L
    private var lastForeground: String? = null
    private var polling = false

    private val pollRunnable = object : Runnable {
        override fun run() {
            pollOnce()
            handler.postDelayed(this, SpikeState.pollIntervalMs)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        usageStats = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        userManager = getSystemService(Context.USER_SERVICE) as UserManager
        overlay = OverlayController(this)
        createChannel()
        pollCursor = System.currentTimeMillis()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        // Drivable by adb / the UIAutomator test: `--es target <pkg> --el interval <ms>`.
        intent?.getStringExtra(EXTRA_TARGET)?.let { SpikeState.protectedPackage = it }
        intent?.takeIf { it.hasExtra(EXTRA_INTERVAL) }
            ?.let { SpikeState.pollIntervalMs = it.getLongExtra(EXTRA_INTERVAL, SpikeState.pollIntervalMs) }
        when (intent?.action) {
            ACTION_STOP -> {
                stopPolling()
                stopSelf()
            }
            ACTION_SHOW_OVERLAY -> SpikeState.protectedPackage?.let { overlay.show(it) }
            ACTION_DISMISS_OVERLAY -> overlay.dismiss()
            ACTION_LAUNCH_BIOMETRIC ->
                SpikeState.protectedPackage?.let { SpikeBiometricActivity.launch(this, it) }
            else -> startPolling()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopPolling()
        overlay.dismiss()
        super.onDestroy()
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        pollCursor = System.currentTimeMillis()
        handler.post(pollRunnable)
        Log.i(SpikeConfig.LOG_TAG, "poll started @ ${SpikeConfig.DEFAULT_POLL_INTERVAL_MS}ms")
    }

    private fun stopPolling() {
        polling = false
        handler.removeCallbacks(pollRunnable)
    }

    private fun pollOnce() {
        if (!userManager.isUserUnlocked) return // §2.4: queryEvents yields nothing while locked
        val now = System.currentTimeMillis()
        val events = usageStats.queryEvents(pollCursor, now)
        val event = UsageEvents.Event()
        var latestPkg: String? = null
        var latestTs = pollCursor
        while (events.getNextEvent(event)) {
            val resumed = event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            if (resumed && event.timeStamp > pollCursor) {
                latestPkg = event.packageName
                latestTs = event.timeStamp
            }
        }
        pollCursor = maxOf(pollCursor, latestTs)
        if (latestPkg != null && latestPkg != lastForeground) {
            lastForeground = latestPkg
            onForeground(latestPkg, latestTs, now)
        }
    }

    private fun onForeground(pkg: String, eventTs: Long, now: Long) {
        when (pkg) {
            SpikeState.protectedPackage -> {
                overlay.show(pkg, eventTs)
                SpikeState.lastLatencyMs = now - eventTs
                Log.i(SpikeConfig.LOG_TAG, "LOCK $pkg detection-lag ~${now - eventTs}ms")
            }
            packageName -> Unit // our own surface (biometric host) is an allow — keep the overlay
            else -> if (overlay.isShowing) {
                overlay.dismiss()
                Log.i(SpikeConfig.LOG_TAG, "allow $pkg (overlay dismissed)")
            }
        }
    }

    private fun startAsForeground() {
        val notification = NotificationCompat.Builder(this, SpikeConfig.CHANNEL_ID)
            .setContentTitle("M7 WP0 spike")
            .setContentText("Usage-poll detection running")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()
        // Plain 2-arg start: the manifest foregroundServiceType="specialUse" is what the OS uses.
        // Mirrors ProtectionWatchdogService (works API 26-36); passing the SPECIAL_USE type constant
        // (API 34) explicitly can throw on older APIs, so we don't.
        startForeground(SpikeConfig.NOTIFICATION_ID, notification)
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            SpikeConfig.CHANNEL_ID,
            "M7 Spike",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_STOP = "com.applock.spike.STOP"
        const val ACTION_SHOW_OVERLAY = "com.applock.spike.SHOW"
        const val ACTION_DISMISS_OVERLAY = "com.applock.spike.DISMISS"
        const val ACTION_LAUNCH_BIOMETRIC = "com.applock.spike.BIOMETRIC"
        const val EXTRA_TARGET = "target"
        const val EXTRA_INTERVAL = "interval"
    }
}
