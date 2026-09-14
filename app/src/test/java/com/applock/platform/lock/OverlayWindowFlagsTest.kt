package com.applock.platform.lock

import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
import android.view.WindowManager.LayoutParams.FLAG_SECURE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for [OverlayWindowFlags] (M7 WP2 change E). They pin the SSOT Gate-2 flag assertions that the
 * debuggable instrumentation APK cannot make (that build drops FLAG_SECURE for screencap): FLAG_SECURE is
 * present on a non-debug build in BOTH states and is never dropped by the interactive <-> pass-through
 * toggle, and only the pass-through (dismissed) state adds the NOT_FOCUSABLE | NOT_TOUCHABLE modality bits.
 * The referenced `FLAG_*` values are compile-time constants, so this needs no `WindowManager` or device.
 */
class OverlayWindowFlagsTest {

    @Test
    fun `interactive on a non-debug build is secure, focusable, and touch-modal`() {
        val flags = OverlayWindowFlags.forState(secure = true, interactive = true)
        assertTrue("FLAG_SECURE must be set on a non-debug build", flags and FLAG_SECURE != 0)
        assertEquals("interactive must stay focusable", 0, flags and FLAG_NOT_FOCUSABLE)
        assertEquals("interactive must stay touch-modal", 0, flags and FLAG_NOT_TOUCHABLE)
    }

    @Test
    fun `pass-through on a non-debug build keeps FLAG_SECURE and drops focus and touch`() {
        val flags = OverlayWindowFlags.forState(secure = true, interactive = false)
        assertTrue("the interactive->pass-through toggle must not drop FLAG_SECURE", flags and FLAG_SECURE != 0)
        assertTrue("pass-through must be non-focusable", flags and FLAG_NOT_FOCUSABLE != 0)
        assertTrue("pass-through must be non-touchable", flags and FLAG_NOT_TOUCHABLE != 0)
    }

    @Test
    fun `a debuggable build drops FLAG_SECURE but keeps the modality behaviour`() {
        assertEquals(
            "a debuggable interactive window carries no flags",
            0,
            OverlayWindowFlags.forState(secure = false, interactive = true),
        )
        val passthrough = OverlayWindowFlags.forState(secure = false, interactive = false)
        assertEquals("a debuggable build must not set FLAG_SECURE", 0, passthrough and FLAG_SECURE)
        assertTrue("pass-through must still be non-focusable", passthrough and FLAG_NOT_FOCUSABLE != 0)
        assertTrue("pass-through must still be non-touchable", passthrough and FLAG_NOT_TOUCHABLE != 0)
    }
}
