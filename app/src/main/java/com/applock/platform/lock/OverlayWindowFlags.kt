package com.applock.platform.lock

import android.view.WindowManager

/**
 * The window-flag policy for the warm overlay lock window (M7 WP2 change E), factored out as a pure `Int`
 * computation so it is JVM-unit-testable without a `WindowManager` or a device: the referenced `FLAG_*`
 * values are compile-time constants, so the computation carries no runtime Android dependency.
 * [OverlayLockPresenter] applies the result through `updateViewLayout`.
 *
 * `FLAG_SECURE` is present on non-debuggable builds (FR-171) in BOTH states, so the interactive <->
 * pass-through toggle never drops it. Interactive = focusable + touch-modal (no extra flags). Pass-through
 * adds `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE` so input reaches the app underneath while the guard is
 * dismissed. This is the pure home of the SSOT Gate-2 secure-flag assertion the debuggable instrumentation
 * APK cannot make (that build drops `FLAG_SECURE` for screencap).
 */
internal object OverlayWindowFlags {

    /** The window flags for a [secure] build in the interactive or pass-through ([interactive] = false) state. */
    fun forState(secure: Boolean, interactive: Boolean): Int {
        val base = if (secure) WindowManager.LayoutParams.FLAG_SECURE else 0
        return if (interactive) {
            base
        } else {
            base or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
    }
}
