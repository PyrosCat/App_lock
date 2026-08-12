package com.applock.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * SQLCipher passphrase: 32 random bytes, hex-encoded, held in
 * EncryptedSharedPreferences so the material at rest is wrapped by the
 * Android Keystore (FR-163). The hex string is used as the passphrase both
 * by the Room open-helper factory and by the plaintext-migration ATTACH.
 */
class DatabaseKeyProvider(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "applock_db_key",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getOrCreateKey(): String {
        prefs.getString(KEY_DB_PASSPHRASE, null)?.let { return it }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val hex = buildString(64) { bytes.forEach { append("%02x".format(it)) } }
        prefs.edit().putString(KEY_DB_PASSPHRASE, hex).apply()
        return hex
    }

    private companion object {
        const val KEY_DB_PASSPHRASE = "db_passphrase"
    }
}
