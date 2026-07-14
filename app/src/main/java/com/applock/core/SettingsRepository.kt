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

    private companion object {
        const val KEY_RELOCK_POLICY = "relock_policy"
    }
}
