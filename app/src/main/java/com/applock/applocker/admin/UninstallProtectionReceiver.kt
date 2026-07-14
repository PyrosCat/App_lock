package com.applock.applocker.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.applock.R

/**
 * Opt-in uninstall protection: while this admin is active, Android refuses to
 * uninstall the app until the user deactivates it in system settings, which
 * gives the lock screen a chance to intervene. No device policies are used —
 * being an active admin is the whole mechanism.
 */
class UninstallProtectionReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.device_admin_disable_warning)

    companion object {

        private fun componentName(context: Context): ComponentName =
            ComponentName(context, UninstallProtectionReceiver::class.java)

        private fun dpm(context: Context): DevicePolicyManager =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        fun isActive(context: Context): Boolean =
            dpm(context).isAdminActive(componentName(context))

        /** System dialog asking the user to activate the admin. */
        fun activationIntent(context: Context): Intent =
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName(context))
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    context.getString(R.string.device_admin_description),
                )
            }

        fun deactivate(context: Context) {
            dpm(context).removeActiveAdmin(componentName(context))
        }
    }
}
