package com.applock.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Lockout counters in EncryptedSharedPreferences so they persist across process death and can't be trivially
 * edited (FR-174).
 *
 * [write] calls [SharedPreferences.Editor.commit] directly and returns its Boolean, so a failed durable write
 * surfaces to [LockoutManager] instead of being swallowed. The KTX `edit(commit = true)` extension discards the
 * result, so it is not used here (R-007). Both counters commit as one operation, so a reader never sees a
 * half-updated pair. Atomic file replacement prevents a partially-updated snapshot, but it does not guarantee the
 * newest snapshot survived a death mid-commit, so durability is confirmed only by the commit return (a residual).
 * [read] surfaces a decryption or IO fault by throwing, which the manager degrades around.
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

    override fun read(): LockoutSnapshot =
        LockoutSnapshot(
            failureCount = prefs.getInt(KEY_FAILURE_COUNT, 0),
            lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L),
        )

    override fun write(snapshot: LockoutSnapshot): Boolean =
        prefs.edit()
            .putInt(KEY_FAILURE_COUNT, snapshot.failureCount)
            .putLong(KEY_LOCKOUT_UNTIL, snapshot.lockoutUntil)
            .commit()

    private companion object {
        const val KEY_FAILURE_COUNT = "failure_count"
        const val KEY_LOCKOUT_UNTIL = "lockout_until"
    }
}
