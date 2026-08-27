@file:Suppress("MagicNumber")

package com.applock.e2e

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M7 WP0 — the durable OV-4 rapid-relaunch race check (M7_PLAN.md WP0 output #2 / §11).
 *
 * Black-box on purpose: it drives the app only through `am` / `appops` shell (run as the `shell`
 * uid via [android.app.UiAutomation.executeShellCommand]) and observes only `dumpsys window`, so it
 * has **no compile coupling** to the throwaway spike. That is what lets it survive the spike's WP0
 * deletion and be **repointed at the production overlay in WP2** by changing the two constants below
 * ([POLL_SERVICE], [OVERLAY_TITLE]) — the assertions stay identical (WP1/WP2/WP6 + FTL regression
 * asset).
 *
 * The race: fire a storm of `am start` at a protected app; the app-lock overlay must be present and
 * **on top (focused)** afterwards — **never `ABSENT`** (the R-002 / F4 failure). Counts default light
 * for CI; the fleet/FTL rig overrides them to the §11 protocol via instrumentation args, e.g.
 * `-e ov4_bursts 50 -e ov4_relaunches 20 -e ov4_repeat 5`.
 */
@RunWith(AndroidJUnit4::class)
@OverlayRaceTest
class OverlayRaceUiTest {

    private enum class Z { TOP, BEHIND, ABSENT }

    private lateinit var targetPkg: String
    private lateinit var targetComponent: String

    @Before
    fun grantAndStart() {
        // Resolve the target app per-device (§10 purpose = api × oem coverage, not one fixed app):
        // the first TARGET_APP_CANDIDATE that is installed AND has a launcher activity. This lets the
        // same artifact run on the AOSP emulator api sweep (via the AOSP clock) and on OEM/GMS
        // devices — Moto G, FTL (via Maps or the OEM clock) — rather than skipping wherever one
        // hardcoded package is absent.
        val resolved = TARGET_APP_CANDIDATES.asSequence()
            .filter { sh("pm path $it").contains("package:") }
            .map { it to launcherComponent(it) }
            .firstOrNull { (_, comp) -> comp.contains("/") }
        assumeTrue(
            "no suitable target app (normal, non-Settings, launchable) on this device: tried $TARGET_APP_CANDIDATES",
            resolved != null,
        )
        targetPkg = resolved!!.first
        targetComponent = resolved.second
        Log.i("M7SpikeTest", "OV-4 target app: $targetPkg ($targetComponent)")
        sh("appops set $APP_PKG android:get_usage_stats allow")
        sh("appops set $APP_PKG android:system_alert_window allow")
        assumeTrue(
            "overlay op not granted",
            sh("appops get $APP_PKG android:system_alert_window").contains("allow"),
        )
        // Foreground the app so the spike FGS may start, then hand it the target + interval.
        sh("am start -n $APP_PKG/$LAUNCHER")
        SystemClock.sleep(500)
        sh("am start-foreground-service -n $POLL_SERVICE --es target $targetPkg --el interval 400")
        SystemClock.sleep(500)
        home()
    }

    /** The launcher Activity component for [pkg], or "" if none (varies by image/OEM). */
    private fun launcherComponent(pkg: String): String =
        sh("cmd package resolve-activity --brief -c android.intent.category.LAUNCHER $pkg")
            .trim().lineSequence().lastOrNull { it.contains("/") }?.trim().orEmpty()

    @After
    fun stop() {
        sh("am start-foreground-service -n $POLL_SERVICE -a com.applock.spike.STOP")
        home()
    }

    @Test
    fun overlayNeverAbsentUnderRelaunchBurst() {
        val bursts = arg("ov4_bursts", 5)
        val relaunches = arg("ov4_relaunches", 10)
        val repeat = arg("ov4_repeat", 1)

        var absent = 0
        var behind = 0
        var top = 0
        repeat(repeat) {
            repeat(bursts) {
                settle()
                repeat(relaunches) { sh("am start -n $targetComponent") }
                when (awaitOverlay()) {
                    Z.TOP -> top++
                    Z.BEHIND -> behind++
                    Z.ABSENT -> absent++
                }
            }
        }

        val total = top + behind + absent
        val log = "OV-4 overlay race: TOP=$top BEHIND=$behind ABSENT=$absent of $total " +
            "(bursts=$bursts relaunches=$relaunches repeat=$repeat)"
        Log.i("M7SpikeTest", log) // always emit the counts (assert message only prints on failure)
        // Hard budget (§11): ABSENT = 0 on any burst; BEHIND tolerated only as sub-poll flicker (<=2%).
        assertEquals("$log — ABSENT must be 0 (R-002/F4 exposure)", 0, absent)
        assertEquals(
            "$log — BEHIND over the 2% self-healing budget",
            true,
            behind <= maxOf(1, total * 2 / 100),
        )
    }

    /** Poll `dumpsys window` up to T_APPEAR for the overlay; TOP once it holds focus. */
    private fun awaitOverlay(): Z {
        val deadline = SystemClock.uptimeMillis() + T_APPEAR_MS
        var seen = Z.ABSENT
        while (SystemClock.uptimeMillis() < deadline) {
            val z = zOrder()
            if (z == Z.TOP) return Z.TOP
            if (z == Z.BEHIND) seen = Z.BEHIND
            SystemClock.sleep(SAMPLE_MS)
        }
        return seen
    }

    private fun zOrder(): Z {
        // `dumpsys window` (not `... windows`) carries BOTH the window list and the mCurrentFocus
        // line; `dumpsys window windows` omits mCurrentFocus, which made every present overlay read
        // as BEHIND. (dumpsys format varies by API — revalidate the grep across the §10 lanes / FTL.)
        val dump = sh("dumpsys window")
        val present = dump.contains(OVERLAY_TITLE)
        val focused = dump.lineSequence()
            .firstOrNull { it.contains("mCurrentFocus") }
            ?.contains(OVERLAY_TITLE) == true
        return when {
            focused -> Z.TOP
            present -> Z.BEHIND
            else -> Z.ABSENT
        }
    }

    private fun settle() {
        home()
        sh("am start-foreground-service -n $POLL_SERVICE -a com.applock.spike.DISMISS")
        SystemClock.sleep(300)
    }

    private fun home() {
        sh("input keyevent 3")
        SystemClock.sleep(200)
    }

    private fun arg(name: String, def: Int): Int =
        InstrumentationRegistry.getArguments().getString(name)?.toIntOrNull() ?: def

    private fun sh(cmd: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
    }

    private companion object {
        const val APP_PKG = "com.applock"
        const val LAUNCHER = "com.applock.platform.spike.SpikeLauncherActivity"

        // WP0 = the spike targets. WP2 repoints these two to the production detection service +
        // overlay window title; the rest of the test is unchanged.
        const val POLL_SERVICE = "com.applock/com.applock.platform.spike.UsagePollService"
        const val OVERLAY_TITLE = "AppLockSpikeOverlay"

        // Target-app candidates, tried in order — the first INSTALLED one with a launcher activity is
        // used (resolved in @Before). Each must be a NORMAL app, deliberately NOT Settings:
        // Android force-hides TYPE_APPLICATION_OVERLAY over Settings / permission screens
        // (HIDE_NON_SYSTEM_OVERLAY), so an overlay could never read "on top" there (Moto G, WP0).
        //
        // The list spans OEM/GMS and AOSP so the sweep covers both axes without skipping:
        //   • Google Maps / Google clock — present on Google-certified OEMs incl. Samsung/Xiaomi
        //     (where the AOSP clock package is absent), covering the FTL multi-OEM lane;
        //   • AOSP clock / calculators — present on plain AOSP emulator images, covering the
        //     api-level sweep (ci/full GMD lanes) where no GMS app exists.
        // (A fresh device could raise a first-run *system* permission dialog for Maps — an
        // overlay-hiding surface; `am start` on the resolved launcher lands on the app's own UI,
        // and the clock candidates avoid it entirely, so resolution prefers whatever is present.)
        val TARGET_APP_CANDIDATES = listOf(
            "com.google.android.apps.maps",   // GMS — most OEMs incl. Samsung/Xiaomi
            "com.google.android.deskclock",   // Google Clock — GMS devices
            "com.android.deskclock",          // AOSP clock — emulator images
            "com.google.android.calculator",  // GMS calculator
            "com.android.calculator2",        // AOSP calculator — older/emulator images
        )

        const val T_APPEAR_MS = 1500L
        const val SAMPLE_MS = 100L
    }
}
