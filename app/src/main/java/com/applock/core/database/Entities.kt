package com.applock.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "protected_apps")
data class ProtectedAppEntity(
    @PrimaryKey val packageName: String,
    val enabled: Boolean = true,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "security_events")
data class SecurityEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventType: String,
    val packageName: String?,
    val timestamp: Long = System.currentTimeMillis(),
)

object SecurityEventType {
    const val LOCK_TRIGGERED = "LOCK_TRIGGERED"
    const val UNLOCK_SUCCESS = "UNLOCK_SUCCESS"
    const val UNLOCK_FAILURE = "UNLOCK_FAILURE"
    const val APP_PROTECTED = "APP_PROTECTED"
    const val APP_UNPROTECTED = "APP_UNPROTECTED"
    const val LOCKOUT_TRIGGERED = "LOCKOUT_TRIGGERED"
    const val BIOMETRIC_UNLOCK_SUCCESS = "BIOMETRIC_UNLOCK_SUCCESS"
    const val BOOT_COMPLETED = "BOOT_COMPLETED"
    const val PROTECTION_DISABLED = "PROTECTION_DISABLED"
}
