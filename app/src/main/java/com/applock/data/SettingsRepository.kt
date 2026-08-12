package com.applock.data

import android.content.Context
import androidx.core.content.edit
import com.applock.domain.IntruderPolicy
import com.applock.domain.RelockPolicy

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

    /** Intruder selfie (FR-081). Off until the user opts in and grants CAMERA. */
    var intruderCaptureEnabled: Boolean
        get() = prefs.getBoolean(KEY_INTRUDER_CAPTURE, false)
        set(value) = prefs.edit { putBoolean(KEY_INTRUDER_CAPTURE, value) }

    /** Failed attempts before an intruder event fires (FR-081, default 5). */
    var intruderCaptureThreshold: Int
        get() = prefs.getInt(KEY_INTRUDER_THRESHOLD, IntruderPolicy.DEFAULT_THRESHOLD)
        set(value) = prefs.edit { putInt(KEY_INTRUDER_THRESHOLD, value) }

    private companion object {
        const val KEY_RELOCK_POLICY = "relock_policy"
        const val KEY_BIOMETRIC_UNLOCK = "biometric_unlock"
        const val KEY_INTRUDER_CAPTURE = "intruder_capture"
        const val KEY_INTRUDER_THRESHOLD = "intruder_capture_threshold"
    }
}
