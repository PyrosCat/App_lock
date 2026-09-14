package com.applock.platform.lock

import android.view.View
import android.view.WindowManager

/**
 * A narrow seam over the `WindowManager` operations [OverlayLockPresenter] performs on its warm overlay
 * window (M7 WP2 change E). It exists so the add / update / remove / attachment behaviour is fault-injectable
 * on the fleet: an instrumentation test wraps the REAL host in a decorator that throws on removal, rather
 * than faking the whole Android surface. It is NOT a `LockPresenter`, runtime, or DI contract: the presenter
 * keeps it as an internal, defaulted constructor seam whose production value delegates to `WindowManager`.
 */
internal interface OverlayWindowHost {
    fun addView(view: View, params: WindowManager.LayoutParams)
    fun updateViewLayout(view: View, params: WindowManager.LayoutParams)

    /** Synchronous removal (main thread), so a non-throwing return means the window is confirmed gone. */
    fun removeViewImmediate(view: View)

    /** Whether [view] is currently attached to a window; the presenter's failure-atomic reset reads it. */
    fun isAttached(view: View): Boolean
}

/** The production [OverlayWindowHost]: a thin delegate to the real [WindowManager]. */
internal class WindowManagerOverlayHost(private val windowManager: WindowManager) : OverlayWindowHost {
    override fun addView(view: View, params: WindowManager.LayoutParams) = windowManager.addView(view, params)

    override fun updateViewLayout(view: View, params: WindowManager.LayoutParams) =
        windowManager.updateViewLayout(view, params)

    override fun removeViewImmediate(view: View) = windowManager.removeViewImmediate(view)

    override fun isAttached(view: View): Boolean = view.isAttachedToWindow
}
