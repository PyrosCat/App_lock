@file:Suppress("MagicNumber", "TooGenericExceptionCaught")

package com.applock.platform.spike

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * M7 WP0 SPIKE (throwaway). Draws / removes a single `TYPE_APPLICATION_OVERLAY` window carrying the
 * stable [SpikeConfig.OVERLAY_WINDOW_TITLE] so the OV-4 probe can assert its z-order. Plain Views
 * only — the ComposeView-in-overlay hosting (ADR-020 D5) is WP2's job, not the WP0 race/biometric
 * proof. Full-screen, focusable and touch-modal, so it blocks the task underneath (SDS §8.5).
 */
class OverlayController(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var overlayView: View? = null

    val isShowing: Boolean get() = overlayView != null

    fun show(targetPackage: String, eventTs: Long = 0L) {
        if (overlayView != null) return
        val view = buildView(targetPackage)
        try {
            windowManager.addView(view, buildParams())
            overlayView = view
            if (eventTs > 0L) logFirstDraw(view, eventTs)
        } catch (e: Exception) {
            Log.e(SpikeConfig.LOG_TAG, "overlay addView failed (SYSTEM_ALERT_WINDOW granted?)", e)
        }
    }

    // End-to-end latency (§11 / NFR-PERF-012): transition event -> overlay's FIRST frame drawn.
    // detection-lag (poll) is logged by the service; this adds the overlay-draw tail.
    private fun logFirstDraw(view: View, eventTs: Long) {
        view.viewTreeObserver.addOnDrawListener(object : ViewTreeObserver.OnDrawListener {
            private var done = false
            override fun onDraw() {
                if (done) return
                done = true
                Log.i(SpikeConfig.LOG_TAG, "E2E transition->overlay ~${System.currentTimeMillis() - eventTs}ms")
                val self = this
                view.post { view.viewTreeObserver.removeOnDrawListener(self) }
            }
        })
    }

    fun dismiss() {
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
    }

    private fun buildParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            0, // flags 0 = focusable + touch-modal (input does not pass to the task beneath)
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.CENTER
            title = SpikeConfig.OVERLAY_WINDOW_TITLE
        }

    private fun buildView(targetPackage: String): View {
        val root = FrameLayout(context).apply { setBackgroundColor(Color.parseColor("#F2000000")) }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        column.addView(
            TextView(context).apply {
                text = "M7 SPIKE LOCK\n$targetPackage"
                setTextColor(Color.WHITE)
                textSize = 20f
                gravity = Gravity.CENTER
            },
        )
        column.addView(
            Button(context).apply {
                text = "Biometric"
                setOnClickListener { SpikeBiometricActivity.launch(context, targetPackage) }
            },
        )
        column.addView(
            Button(context).apply {
                text = "Dismiss"
                setOnClickListener { dismiss() }
            },
        )
        root.addView(
            column,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        return root
    }
}
