package com.applock.platform.lock

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * The overlay-permission capability helpers (M7 WP2 change E). App Lock's baseline lock presentation is a
 * `TYPE_APPLICATION_OVERLAY` window, so it needs the `SYSTEM_ALERT_WINDOW` "display over other apps" grant.
 * These helpers wrap the grant check, the grant intent, and the resolution of the grant screen's own
 * package(s).
 *
 * Per Decision D-P2-2 the grant GATES protection: F does not report or enable protection without
 * [canDrawOverlays], and verifies the grant path before the cutover flips (there is no residual Activity
 * fallback, which would contradict G's deletion). E supplies these helpers and the (F-revealed) grant UI;
 * F wires the [EnforcementHealth] `overlayGrant` fact and the restored-grant `retryPresentation`.
 */
object OverlayPermission {

    /** Whether the system grants `SYSTEM_ALERT_WINDOW` to this app. */
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** The intent that opens this package's "display over other apps" grant screen. */
    fun manageOverlayIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * The package(s) of the activity that services [manageOverlayIntent], for the C2 safe-dismiss
     * exemption: a readiness shield that escapes to OVERLAY_SETTINGS carries these in
     * `SafeDismissRequested.exemptPackages`, so the grant screen may foreground without a new shield while
     * every other app stays guarded. The intent resolves to the system Settings app; the resolver is added
     * as a fallback so an OEM whose overlay screen lives elsewhere is still exempt.
     */
    // Total (never throws): a resolve fault degrades to the stock Settings fallback. It is called
    // synchronously from a Recovery-shield button, before the completion reaches the runtime, so a throw
    // here would crash the overlay UI rather than surface as a Failed apply.
    @Suppress(
        "DEPRECATION", // the int-flags overload is used with flags 0; the ResolveInfoFlags overload is 33+
        "TooGenericExceptionCaught",
        "SwallowedException",
    )
    fun overlaySettingsPackages(context: Context): Set<String> {
        val resolved = try {
            context.packageManager
                .resolveActivity(manageOverlayIntent(context), 0)
                ?.activityInfo
                ?.packageName
        } catch (e: RuntimeException) {
            null
        }
        return setOfNotNull(resolved, SETTINGS_PACKAGE)
    }

    /** The stock Settings package: the OVERLAY_SETTINGS destination on almost every device. */
    private const val SETTINGS_PACKAGE = "com.android.settings"
}
