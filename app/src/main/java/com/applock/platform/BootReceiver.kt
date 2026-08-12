package com.applock.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.applock.data.SecurityEventDao
import com.applock.data.SecurityEventEntity
import com.applock.data.SecurityEventType
import com.applock.di.ApplicationScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Restores protection after reboot. Unlock sessions are in-memory, so they
 * are already invalidated by the reboot itself (FR-017); this only records
 * the boot and brings the watchdog back up.
 *
 * Uses a Hilt @EntryPoint (not @AndroidEntryPoint field injection): a
 * BroadcastReceiver's onReceive overrides an abstract member, so the
 * super.onReceive() call Hilt member injection would need does not compile.
 */
class BootReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootDependencies {
        @ApplicationScope
        fun appScope(): CoroutineScope
        fun securityEventDao(): SecurityEventDao
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val deps = EntryPointAccessors.fromApplication(
            context.applicationContext,
            BootDependencies::class.java,
        )
        deps.appScope().launch {
            deps.securityEventDao().insert(
                SecurityEventEntity(
                    eventType = SecurityEventType.BOOT_COMPLETED,
                    packageName = null,
                )
            )
        }
        ProtectionWatchdogService.start(context)
    }
}
