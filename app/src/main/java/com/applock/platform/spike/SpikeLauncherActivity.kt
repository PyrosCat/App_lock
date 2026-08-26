@file:Suppress("MagicNumber")

package com.applock.platform.spike

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * M7 WP0 SPIKE (throwaway) control panel. A tappable surface for the fleet operator (and adb) to
 * grant-check, set the "protected" target, drive the poll service, and read the last detection lag.
 * The decisive OV-4 measurements are automated by the durable UIAutomator test; this is the manual
 * driver for latency / biometric / battery observation.
 */
class SpikeLauncherActivity : FragmentActivity() {

    private lateinit var status: TextView
    private lateinit var targetInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 48)
        }
        targetInput = EditText(this).apply {
            hint = "protected package"
            setText("com.google.android.deskclock")
        }
        status = TextView(this)

        column.addView(TextView(this).apply { text = "M7 WP0 platform spike" })
        column.addView(targetInput)
        column.addView(
            button("Set target") {
                SpikeState.protectedPackage = targetInput.text.toString().trim()
                refresh()
            },
        )
        column.addView(button("Usage-access settings") { open(Settings.ACTION_USAGE_ACCESS_SETTINGS) })
        column.addView(
            button("Overlay settings") {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName"),
                    ),
                )
            },
        )
        column.addView(button("Start poll service") { sendToService(null) })
        column.addView(button("Stop poll service") { sendToService(UsagePollService.ACTION_STOP) })
        column.addView(button("Show overlay") { sendToService(UsagePollService.ACTION_SHOW_OVERLAY) })
        column.addView(button("Dismiss overlay") { sendToService(UsagePollService.ACTION_DISMISS_OVERLAY) })
        column.addView(button("Launch biometric") { sendToService(UsagePollService.ACTION_LAUNCH_BIOMETRIC) })
        column.addView(button("Refresh status") { refresh() })
        column.addView(status)

        setContentView(ScrollView(this).apply { addView(column) })
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun button(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setOnClickListener { onClick() }
        }

    private fun open(action: String) = startActivity(Intent(action))

    private fun sendToService(action: String?) {
        val intent = Intent(this, UsagePollService::class.java).apply { setAction(action) }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun refresh() {
        status.text = buildString {
            appendLine("target: ${SpikeState.protectedPackage ?: "(none)"}")
            appendLine("overlay granted: ${Settings.canDrawOverlays(this@SpikeLauncherActivity)}")
            appendLine("usage granted: ${usageGranted()}")
            appendLine("last detection lag: ${SpikeState.lastLatencyMs} ms")
        }
    }

    @Suppress("DEPRECATION") // checkOpNoThrow(String,...) avoids the API-29 unsafeCheckOpNoThrow gate
    private fun usageGranted(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
