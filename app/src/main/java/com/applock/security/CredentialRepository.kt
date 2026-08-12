package com.applock.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the PIN hash + salt in EncryptedSharedPreferences backed by the
 * Android Keystore. Plain PIN is never persisted (TAS §5.2).
 *
 * New credentials use Argon2id (FR-011). Phase 1 installs stored PBKDF2 —
 * those verify against the legacy hash once, then are transparently
 * re-hashed to Argon2id on the next successful unlock.
 */
class CredentialRepository(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "applock_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun isPinSet(): Boolean = prefs.contains(KEY_HASH)

    fun setPin(pin: CharArray) {
        storeArgon2(pin)
        pin.fill(' ')
    }

    fun verifyPin(pin: CharArray): Boolean {
        val result = when (prefs.getString(KEY_ALGO, ALGO_PBKDF2)) {
            ALGO_ARGON2ID -> verifyArgon2(pin)
            else -> verifyLegacyPbkdf2(pin).also { ok ->
                if (ok) storeArgon2(pin) // upgrade-on-verify
            }
        }
        pin.fill(' ')
        return result
    }

    private fun storeArgon2(pin: CharArray) {
        val salt = Argon2PinHasher.newSalt()
        val hash = Argon2PinHasher.hash(pin, salt)
        prefs.edit()
            .putString(KEY_ALGO, ALGO_ARGON2ID)
            .putString(KEY_SALT, salt.encode())
            .putString(KEY_HASH, hash.encode())
            .putInt(KEY_ARGON2_MEMORY_KIB, Argon2PinHasher.MEMORY_KIB)
            .putInt(KEY_ARGON2_ITERATIONS, Argon2PinHasher.ITERATIONS)
            .putInt(KEY_ARGON2_PARALLELISM, Argon2PinHasher.PARALLELISM)
            .remove(KEY_PBKDF2_ITERATIONS)
            .apply()
    }

    private fun verifyArgon2(pin: CharArray): Boolean {
        val salt = prefs.getString(KEY_SALT, null)?.decode() ?: return false
        val hash = prefs.getString(KEY_HASH, null)?.decode() ?: return false
        return Argon2PinHasher.verify(
            pin = pin,
            salt = salt,
            memoryKib = prefs.getInt(KEY_ARGON2_MEMORY_KIB, Argon2PinHasher.MEMORY_KIB),
            iterations = prefs.getInt(KEY_ARGON2_ITERATIONS, Argon2PinHasher.ITERATIONS),
            parallelism = prefs.getInt(KEY_ARGON2_PARALLELISM, Argon2PinHasher.PARALLELISM),
            expected = hash,
        )
    }

    private fun verifyLegacyPbkdf2(pin: CharArray): Boolean {
        val salt = prefs.getString(KEY_SALT, null)?.decode() ?: return false
        val hash = prefs.getString(KEY_HASH, null)?.decode() ?: return false
        val iterations = prefs.getInt(KEY_PBKDF2_ITERATIONS, PinHasher.ITERATIONS)
        return PinHasher.verify(pin, salt, iterations, hash)
    }

    private fun ByteArray.encode(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.decode(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val KEY_ALGO = "pin_algo"
        const val ALGO_PBKDF2 = "PBKDF2"
        const val ALGO_ARGON2ID = "ARGON2ID"
        const val KEY_SALT = "pin_salt"
        const val KEY_HASH = "pin_hash"
        const val KEY_PBKDF2_ITERATIONS = "pin_iterations"
        const val KEY_ARGON2_MEMORY_KIB = "pin_argon2_m"
        const val KEY_ARGON2_ITERATIONS = "pin_argon2_t"
        const val KEY_ARGON2_PARALLELISM = "pin_argon2_p"
    }
}
