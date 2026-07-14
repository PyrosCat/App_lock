package com.applock.core

import android.content.Context
import androidx.core.content.edit
import com.applock.applocker.session.RelockPolicy

/** App-level settings backed by SharedPreferences. */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("applock_settings", Context.MODE_PRIVATE)

    var relockPolicy: RelockPolicy
        get() = RelockPolicy.valueOf(
            prefs.getString(KEY_RELOCK_POLICY, RelockPolicy.IMMEDIATE.name)!!
        )
        set(value) = prefs.edit { putString(KEY_RELOCK_POLICY, value.name) }

    /** Biometric unlock alongside PIN (FR-002/FR-006). Only honored when hardware allows. */
    var biometricUnlockEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_UNLOCK, true)
        set(value) = prefs.edit { putBoolean(KEY_BIOMETRIC_UNLOCK, value) }

    private companion object {
        const val KEY_RELOCK_POLICY = "relock_policy"
        const val KEY_BIOMETRIC_UNLOCK = "biometric_unlock"
    }
}
