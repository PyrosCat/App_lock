package com.applock.e2e

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM tests for [parseAppOpMode] (M7 WP2 change E, round 9), the pure parser the Gate-2 suites use to snapshot
 * the overlay app-op. `appops get` output varies by version / OEM, so this proves the parser reads the package
 * mode case-insensitively, skips a `Uid mode:` line, treats "No operations" as the default, and, crucially,
 * returns [AppOpMode.Unknown] (never a spurious "default") for empty, error, or unrecognized output, so a
 * caller never clobbers a real grant on restore.
 */
class AppOpsParsingTest {

    private val op = "system_alert_window"

    @Test
    fun `reads the uppercase debug op name appops actually prints`() {
        assertEquals(AppOpMode.Known("allow"), parseAppOpMode(op, "SYSTEM_ALERT_WINDOW: allow"))
    }

    @Test
    fun `reads a lowercase op name too`() {
        assertEquals(AppOpMode.Known("ignore"), parseAppOpMode(op, "system_alert_window: ignore"))
    }

    @Test
    fun `reads the mode when a trailing time or attribution follows`() {
        assertEquals(AppOpMode.Known("allow"), parseAppOpMode(op, "SYSTEM_ALERT_WINDOW: allow; time=+1h2m3s ago"))
    }

    @Test
    fun `skips a Uid mode line and reads the package mode`() {
        val out = """
            Uid mode: SYSTEM_ALERT_WINDOW: ignore
            SYSTEM_ALERT_WINDOW: allow
        """.trimIndent()
        assertEquals(AppOpMode.Known("allow"), parseAppOpMode(op, out))
    }

    @Test
    fun `tolerates an android-prefixed op name`() {
        assertEquals(AppOpMode.Known("deny"), parseAppOpMode(op, "android:SYSTEM_ALERT_WINDOW: deny"))
    }

    @Test
    fun `treats an explicit default mode as known`() {
        assertEquals(AppOpMode.Known("default"), parseAppOpMode(op, "SYSTEM_ALERT_WINDOW: default"))
    }

    @Test
    fun `treats a No operations response as the known default`() {
        assertEquals(AppOpMode.Known("default"), parseAppOpMode(op, "No operations."))
    }

    @Test
    fun `empty output is unknown, never a spurious default`() {
        assertEquals(AppOpMode.Unknown, parseAppOpMode(op, ""))
        assertEquals(AppOpMode.Unknown, parseAppOpMode(op, "   \n  "))
    }

    @Test
    fun `a shell error is unknown`() {
        assertEquals(AppOpMode.Unknown, parseAppOpMode(op, "Error: no uid for com.applock"))
    }

    @Test
    fun `an unrecognized line for a different op is unknown`() {
        assertEquals(AppOpMode.Unknown, parseAppOpMode(op, "CAMERA: allow"))
    }
}
