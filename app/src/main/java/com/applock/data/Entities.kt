package com.applock.data

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
    const val INTRUDER_CAPTURED = "INTRUDER_CAPTURED"
    const val DATABASE_RECOVERED = "DATABASE_RECOVERED"
}

/**
 * One record per intruder event (FR-082). [photoFileName] points into the
 * encrypted intruder photo store; null when the capture itself failed
 * (no camera permission, camera error) — the event is still logged.
 */
@Entity(tableName = "intruder_events")
data class IntruderEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String?,
    val authMethod: String,
    val failedAttempts: Int,
    val batteryPercent: Int,
    val orientation: String,
    val photoFileName: String?,
)

/**
 * Vault index row (FR-106). The encrypted payload lives in the vault
 * directory under [fileName] (a random UUID — the display name never
 * touches the filesystem).
 */
@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val importedAt: Long = System.currentTimeMillis(),
    val fileName: String,
)
