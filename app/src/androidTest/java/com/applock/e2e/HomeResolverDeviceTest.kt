package com.applock.e2e

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.applock.platform.lock.PackageManagerHomeResolver
import com.applock.platform.lock.PackageManagerHomeResolver.Resolution
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M7 WP2 change F3. Checks on a device the platform behaviour that the JVM tests of [PackageManagerHomeResolver]
 * assume. Each test uses the production `Context` constructor, so the real `resolveActivity` call runs.
 *
 *  - The resolver returns the current HOME role holder, and one resolve is cheap. The latency is logged.
 *  - After a default-launcher change, a new foreground episode returns the new launcher, and the former launcher
 *    is no longer home. The time until `PackageManager` reports the change is logged.
 *  - With the HOME role cleared, the resolver agrees with `resolveActivity`. The platform answer is OEM-specific
 *    (the chooser, which maps to [Resolution.NoDefault], or a launcher that it still resolves), so it is logged.
 *
 * The second launcher is [FakeHomeActivity], a disabled debug-only activity of App Lock itself. Each test that
 * needs it enables it, so App Lock becomes a HOME candidate, and [restoreDefaultLauncher] restores the original
 * role holder, then resets the component. The tests change the device's default launcher for a few seconds. They
 * skip below API 29, where the HOME role does not exist. They also skip where `cmd role get-role-holders` is not
 * available.
 */
@RunWith(AndroidJUnit4::class)
class HomeResolverDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val fakeHome = ComponentName(context, FakeHomeActivity::class.java)
    private lateinit var originalHolder: String

    @Before
    fun recordDefaultLauncher() {
        assumeTrue("the HOME role needs API 29", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val holders = homeRoleHolders()
        assumeTrue("expected exactly one default launcher, found $holders", holders.size == 1)
        originalHolder = holders.single()
        val (raw, waitedMs) = pollRawHomePackage { it == originalHolder }
        assertEquals(
            "resolveActivity must match the HOME role holder before the test (waited $waitedMs ms)",
            originalHolder,
            raw,
        )
    }

    @After
    fun restoreDefaultLauncher() {
        if (!::originalHolder.isInitialized) return
        // Switch the role to App Lock and back, so each step is a real holder change and the platform resets its
        // default home. A single add-role-holder does nothing when the platform has already given the role back to
        // the original launcher by itself, which the API 36 image does after clear-role-holders. The fake launcher is
        // reset last, so it is never the holder while it is disabled.
        setFakeHomeState(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        shell("cmd role add-role-holder $HOME_ROLE ${context.packageName}")
        shell("cmd role add-role-holder $HOME_ROLE $originalHolder")
        setFakeHomeState(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        assertEquals("the original default launcher must be restored", listOf(originalHolder), homeRoleHolders())
        val (raw, _) = pollRawHomePackage { it == originalHolder }
        assertEquals("resolveActivity must return the restored default launcher", originalHolder, raw)
    }

    @Test
    fun resolvesTheDefaultLauncherCheaply() {
        val resolver = PackageManagerHomeResolver(context)
        assertTrue(
            "the default launcher must be home (resolveActivity: ${rawHomePackage()})",
            resolver.isHome(originalHolder, true),
        )
        assertFalse("App Lock must not be home", resolver.isHome(context.packageName, true))

        // Within the TTL, every call with a new episode resolves again, so each sample is one real resolve.
        val samplesUs = List(LATENCY_SAMPLES) {
            val start = SystemClock.elapsedRealtimeNanos()
            resolver.isHome(originalHolder, true)
            (SystemClock.elapsedRealtimeNanos() - start) / NANOS_PER_MICRO
        }.sorted()
        val medianUs = samplesUs[samplesUs.size / 2]
        Log.i(TAG, "resolve latency over ${samplesUs.size} calls: median=${medianUs}us max=${samplesUs.last()}us")
        assertTrue(
            "the median resolve must stay under $MAX_MEDIAN_RESOLVE_US us (was $medianUs us)",
            medianUs < MAX_MEDIAN_RESOLVE_US,
        )
    }

    @Test
    fun aNewEpisodeAfterADefaultLauncherChangeReturnsTheNewLauncher() {
        val resolver = PackageManagerHomeResolver(context)
        assertTrue(
            "the default launcher must be home (resolveActivity: ${rawHomePackage()})",
            resolver.isHome(originalHolder, true),
        )

        enableFakeHome()
        shell("cmd role add-role-holder $HOME_ROLE ${context.packageName}")
        assertEquals(listOf(context.packageName), homeRoleHolders())
        val (raw, waitedMs) = pollRawHomePackage { it == context.packageName }
        assertEquals("PackageManager must report the new default launcher", context.packageName, raw)
        Log.i(TAG, "PackageManager reported the new default launcher after $waitedMs ms")

        assertTrue("the new default launcher must be home", resolver.isHome(context.packageName, true))
        assertFalse("the former launcher must not be home", resolver.isHome(originalHolder, true))
    }

    @Test
    fun withNoRoleHolderTheResolverAgreesWithThePlatform() {
        enableFakeHome() // two HOME candidates, so the platform can leave the role without a holder
        shell("cmd role clear-role-holders $HOME_ROLE")
        val holders = homeRoleHolders()
        assumeTrue("the platform kept or reassigned a default launcher: $holders", holders.isEmpty())
        // The platform answer can change (to the chooser) or stay (a launcher it still resolves), so wait for a
        // change but accept either result.
        val (raw, waitedMs) = pollRawHomePackage { it != originalHolder }
        Log.i(TAG, "with no HOME role holder, resolveActivity returned $raw (sampled after $waitedMs ms)")

        val resolution = Resolution.of(raw)
        val resolver = PackageManagerHomeResolver(context)
        for (candidate in listOf(originalHolder, context.packageName)) {
            assertEquals(
                "$candidate must be home exactly when the platform resolves HOME to it ($resolution)",
                resolution == Resolution.Resolved(candidate),
                resolver.isHome(candidate, true),
            )
        }
    }

    private fun enableFakeHome() = setFakeHomeState(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)

    /** The test runs in the App Lock process, so it can change its own component without a shell permission. */
    private fun setFakeHomeState(state: Int) =
        context.packageManager.setComponentEnabledSetting(fakeHome, state, PackageManager.DONT_KILL_APP)

    /**
     * The HOME role holders from `cmd role get-role-holders`. Older releases (for example, the API 30 CI image) do
     * not have this command and print an error on stdout. The tests skip there, because an error line must never be
     * read as a package name.
     */
    private fun homeRoleHolders(): List<String> {
        val output = shell("cmd role get-role-holders $HOME_ROLE").trim()
        val holders = output.split(';').filter { it.isNotBlank() }
        assumeTrue(
            "`cmd role get-role-holders` is not available here: $output",
            holders.all { PACKAGE_NAME.matches(it) },
        )
        return holders
    }

    /** The package that `resolveActivity` returns for `MAIN/HOME`, the same call as the production resolver. */
    @Suppress("DEPRECATION") // the int-flags overload is used below API 33
    private fun rawHomePackage(): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return info?.activityInfo?.packageName
    }

    /**
     * Polls [rawHomePackage] until [condition] holds or [POLL_TIMEOUT_MS] passes. It returns the last value and the
     * wait in ms. It does not fail on the timeout; the caller asserts on the value.
     */
    private fun pollRawHomePackage(condition: (String?) -> Boolean): Pair<String?, Long> {
        val start = SystemClock.elapsedRealtime()
        var raw = rawHomePackage()
        while (!condition(raw) && SystemClock.elapsedRealtime() - start < POLL_TIMEOUT_MS) {
            SystemClock.sleep(POLL_INTERVAL_MS)
            raw = rawHomePackage()
        }
        return raw to SystemClock.elapsedRealtime() - start
    }

    private fun shell(command: String): String {
        val pfd = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
    }

    private companion object {
        const val TAG = "HomeResolverDeviceTest"
        const val HOME_ROLE = "android.app.role.HOME"
        val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
        const val LATENCY_SAMPLES = 50
        const val NANOS_PER_MICRO = 1_000L

        /** A loose ceiling: it catches a pathological resolve cost, not normal device variance. */
        const val MAX_MEDIAN_RESOLVE_US = 50_000L
        const val POLL_TIMEOUT_MS = 5_000L
        const val POLL_INTERVAL_MS = 50L
    }
}
