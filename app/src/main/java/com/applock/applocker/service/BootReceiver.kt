package com.applock.applocker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.applock.core.Graph
import com.applock.core.database.SecurityEventEntity
import com.applock.core.database.SecurityEventType
import kotlinx.coroutines.launch

/**
 * Restores protection after reboot. Unlock sessions are in-memory, so they
 * are already invalidated by the reboot itself (FR-017); this only records
 * the boot and brings the watchdog back up.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Graph.appScope.launch {
            Graph.database.securityEventDao().insert(
                SecurityEventEntity(
                    eventType = SecurityEventType.BOOT_COMPLETED,
                    packageName = null,
                )
            )
        }
        ProtectionWatchdogService.start(context)
    }
}
