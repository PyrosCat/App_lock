package com.applock.platform.lock

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.applock.service.engine.SafeDestination
import com.applock.service.engine.SafeNavigator

/**
 * The real [SafeNavigator] (M7 WP2 change E). It starts a leave-without-auth [SafeDestination] over the
 * standing surface, from the application context, so each launch carries `FLAG_ACTIVITY_NEW_TASK`.
 * [navigate] returns true when the destination starts, and false when nothing resolves or the launch
 * throws, so the interpreter can emit the matching two-step follow-up (issued vs failed).
 *
 * The closed [SafeDestination] set is never an arbitrary intent: HOME is the launcher, APP_LOCK is App
 * Lock's own launcher activity, and OVERLAY_SETTINGS is the system "display over other apps" screen for
 * this package. It is Android-bound, so its real-surface behaviour is covered by the fleet Gate-2
 * instrumentation, not a JVM test.
 */
class RealSafeNavigator(private val context: Context) : SafeNavigator {

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // port contract: never throw; a launch fault -> false
    override fun navigate(destination: SafeDestination): Boolean {
        return try {
            // intentFor is inside the boundary too: resolving the launch intent (getLaunchIntentForPackage)
            // is itself a system call that can throw, and the port contract is total (a fault -> false).
            val intent = intentFor(destination) ?: return false
            context.startActivity(intent)
            true
        } catch (e: RuntimeException) {
            false
        }
    }

    private fun intentFor(destination: SafeDestination): Intent? = when (destination) {
        SafeDestination.HOME ->
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // The launcher intent for our own package (App Lock's MainActivity), avoiding a presentation import.
        SafeDestination.APP_LOCK ->
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        SafeDestination.OVERLAY_SETTINGS ->
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.fromParts("package", context.packageName, null),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
