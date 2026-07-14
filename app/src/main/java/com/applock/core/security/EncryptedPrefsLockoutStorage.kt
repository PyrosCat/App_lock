package com.applock.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Lockout counters in EncryptedSharedPreferences so they persist across
 * process death and can't be trivially edited (FR-174).
 */
class EncryptedPrefsLockoutStorage(context: Context) : LockoutStorage {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "applock_lockout",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override var failureCount: Int
        get() = prefs.getInt(KEY_FAILURE_COUNT, 0)
        set(value) = prefs.edit { putInt(KEY_FAILURE_COUNT, value) }

    override var lockoutUntil: Long
        get() = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        set(value) = prefs.edit { putLong(KEY_LOCKOUT_UNTIL, value) }

    private companion object {
        const val KEY_FAILURE_COUNT = "failure_count"
        const val KEY_LOCKOUT_UNTIL = "lockout_until"
    }
}
