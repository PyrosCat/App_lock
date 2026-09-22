@file:Suppress("MagicNumber")

package com.applock.e2e

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.applock.R
import com.applock.data.SettingsRepository
import com.applock.platform.lock.OverlayLockPresenter
import com.applock.platform.lock.OverlayPermission
import com.applock.platform.lock.OverlayWindowHost
import com.applock.platform.lock.WindowManagerOverlayHost
import com.applock.security.CredentialRepository
import com.applock.security.LockoutManager
import com.applock.security.LockoutSnapshot
import com.applock.security.LockoutStorage
import com.applock.service.engine.Epoch
import com.applock.service.engine.Generation
import com.applock.service.engine.LockCompletionBridge
import com.applock.service.engine.LockPresentation
import com.applock.service.engine.ReadinessToken
import com.applock.service.engine.RequestId
import com.applock.service.engine.RequestToken
import com.applock.service.engine.SurfaceApplyResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M7 WP2 change E. Fault-injection coverage for [OverlayLockPresenter]'s window-host reset path, driving a
 * DECORATOR around the REAL `WindowManager` (via the [OverlayWindowHost] seam) rather than faking the whole
 * Android surface. It proves the failure-atomic reset: an attached-removal failure retains the window (no
 * orphan, no duplicate add), an already-detached root is cleaned up without a removal call, and a reset
 * followed by a different presentation re-adds and renders the new surface.
 *
 * **Fleet-run.** Authored here and executed on the NucBox / Moto G / FTL lanes (§10); tagged [OverlayRaceTest]
 * so the fast per-push `ci` group skips it. Gate 2 stays open until it passes on the fleet.
 */
@RunWith(AndroidJUnit4::class)
@OverlayRaceTest
class OverlayWindowHostFaultUiTest {

    private val instr = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val device: UiDevice = UiDevice.getInstance(instr)
    private val epoch = Epoch(1L)

    private lateinit var realHost: WindowManagerOverlayHost
    private lateinit var host: FaultInjectingHost
    private lateinit var presenter: OverlayLockPresenter
    private var priorBiometric: Boolean? = null // nullable + captured BEFORE the assumption, so a skip restores
    private var priorOverlayOp: AppOpMode = AppOpMode.Unknown

    @Before
    fun setUp() {
        // Snapshot the device state we are about to change BEFORE the assumption: JUnit still runs @After when a
        // setup assumption fails, so a skipped run must not leave the overlay app-op forced to allow, nor write
        // a stale default biometric value.
        val priorOp = overlayOpMode()
        priorOverlayOp = priorOp
        // Force the grant (and later restore it) ONLY when we could READ the prior mode, so an unreadable
        // appops result never clobbers a real grant on teardown; otherwise rely on the existing grant.
        if (priorOp is AppOpMode.Known) sh("appops set $APP_PKG android:system_alert_window allow")
        val settings = SettingsRepository(context)
        priorBiometric = settings.biometricUnlockEnabled
        assumeTrue("overlay not grantable on this image", OverlayPermission.canDrawOverlays(context))
        LockCompletionBridge.resetForTest()

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        realHost = WindowManagerOverlayHost(wm)
        host = FaultInjectingHost(realHost)
        // Biometrics off, so a Lock surface does not auto-launch and perturb these window-lifecycle checks.
        settings.biometricUnlockEnabled = false
        instr.runOnMainSync {
            presenter = OverlayLockPresenter(
                context = context,
                credentialRepository = CredentialRepository(context),
                // In-memory lockout storage, so the test never mutates the installed app's real encrypted
                // lockout preferences (a connected-device run must leave its security config untouched).
                lockoutManager = LockoutManager(InMemoryLockoutStorage()),
                settings = settings,
                windowHost = host, // inject the fault-injecting decorator via the constructor seam
            )
        }
    }

    @After
    fun tearDown() {
        if (::host.isInitialized) {
            host.failRemoval = false
            host.detachThenFailRemoval = false
            host.failIsAttached = false
        }
        if (::presenter.isInitialized) instr.runOnMainSync { runCatching { presenter.release() } }
        LockCompletionBridge.resetForTest()
        // Restore only what we actually captured (both snapshots are taken before the assumption); an Unknown
        // overlay-op snapshot is left untouched so an unreadable read never clobbers a real grant.
        priorBiometric?.let { SettingsRepository(context).biometricUnlockEnabled = it }
        (priorOverlayOp as? AppOpMode.Known)?.let { sh("appops set $APP_PKG android:system_alert_window ${it.mode}") }
    }

    @Test
    fun attachedRemovalFailureRetainsTheWindowAndDoesNotDuplicateAdd() {
        assertEquals(SurfaceApplyResult.Success, present(checking(1)))
        assertVisible(text(R.string.overlay_checking))
        assertEquals(1, host.addCount)

        host.failRemoval = true
        release() // resetHost: still attached + removeViewImmediate throws -> retain the handles, do not orphan

        // Retained: presenting again reuses the warm window (ensureAdded no-ops), so there is no second add.
        assertEquals(SurfaceApplyResult.Success, present(checking(2)))
        assertEquals("a failed removal must not orphan the window and add a duplicate", 1, host.addCount)

        host.failRemoval = false
        release()
        assertEquals("a subsequent successful reset removes it exactly once", 1, host.removeCount)
    }

    @Test
    fun alreadyDetachedCleanupSkipsRemovalAndReAdds() {
        assertEquals(SurfaceApplyResult.Success, present(checking(1)))
        val root = host.lastAdded
        assertNotNull("the host must have added a root view", root)
        assertEquals(1, host.addCount)

        // Detach the window OUT OF BAND (bypassing the presenter), so its next reset observes it already gone.
        instr.runOnMainSync { realHost.removeViewImmediate(root!!) }
        assertTrue("the window should detach", poll { !host.isAttached(root!!) })

        // Arm failRemoval to PROVE the reset does not call removeViewImmediate on an already-detached root.
        host.failRemoval = true
        release() // resetHost: isAttached == false -> skip removal -> clear handles (no throw despite the arm)
        host.failRemoval = false

        // Handles were cleared, so a re-present adds a fresh window and renders the new surface.
        assertEquals(SurfaceApplyResult.Success, present(lock(9)))
        assertEquals("cleanup after an out-of-band detach must allow a fresh add", 2, host.addCount)
        assertVisible(text(R.string.enter_pin))
    }

    @Test
    fun reAddWithADifferentPresentationRendersTheNewSurface() {
        assertEquals(SurfaceApplyResult.Success, present(lock(1)))
        assertVisible(text(R.string.enter_pin))

        release()
        assertEquals(1, host.removeCount)

        assertEquals(SurfaceApplyResult.Success, present(checking(2)))
        assertEquals("a reset then a different presentation must add a fresh window", 2, host.addCount)
        assertVisible(text(R.string.overlay_checking))
    }

    @Test
    fun anIsAttachedFaultIsContainedAndConservativelyRetainsTheWindow() {
        assertEquals(SurfaceApplyResult.Success, present(checking(1)))
        assertEquals(1, host.addCount)

        // Both the attachment query AND the removal throw: resetHost must let neither escape (it runs inside
        // present()'s BadToken catch), and with attachment indeterminate it conservatively RETAINS rather than
        // orphan a possibly-live touch-modal window.
        host.failIsAttached = true
        host.failRemoval = true
        release() // must not throw despite both faults

        assertEquals(SurfaceApplyResult.Success, present(checking(2)))
        assertEquals("an indeterminate attachment must retain the warm window, not add a second", 1, host.addCount)

        host.failIsAttached = false
        host.failRemoval = false
        release()
        assertEquals("a clean reset then removes it exactly once", 1, host.removeCount)
    }

    @Test
    fun aRemovalThatDetachesThenThrowsClearsHandlesAndReAdds() {
        assertEquals(SurfaceApplyResult.Success, present(checking(1)))
        assertEquals(1, host.addCount)

        // The delegate removes the view (detaching it) and THEN throws: resetHost must re-query attachment,
        // observe the root gone, and CLEAR the handles rather than wedge forever on a stale non-null root.
        host.detachThenFailRemoval = true
        release()
        host.detachThenFailRemoval = false

        assertEquals(SurfaceApplyResult.Success, present(lock(9)))
        assertEquals("a detach-then-throw removal must not wedge; the next present re-adds", 2, host.addCount)
        assertVisible(text(R.string.enter_pin))
    }

    @Test
    fun aColdPresentWhoseFirstUpdateFailsLeavesNothingBlockingThenRecovers() {
        // #2 regression (round 8): a COLD add succeeds, but the first updateViewLayout (the reveal to
        // interactive) throws a non-BadToken fault. present() must report Failed WITHOUT stranding a blank,
        // touch-modal overlay: the window is added in the dismissed (GONE + pass-through) state and only
        // revealed once the fallible update succeeds, so a failed cold reveal blocks nothing.
        ActivityScenario.launch(SentinelActivity::class.java).use {
            device.waitForIdle()
            val x = device.displayWidth / 2
            val y = (device.displayHeight * 0.12).toInt() // over the overlay, clear of any control

            host.failNextUpdate = true
            assertEquals(SurfaceApplyResult.Failed("IllegalStateException"), present(lock(1)))
            assertEquals("the cold add still happens exactly once", 1, host.addCount)
            assertTrue("a failed cold reveal must not block the underlying touch", tapReachesSentinel(x, y))

            // A subsequent fault-free present reuses the warm window and renders the surface (recovery).
            assertEquals(SurfaceApplyResult.Success, present(lock(1)))
            assertEquals("recovery must reuse the warm window, not add a second", 1, host.addCount)
            assertVisible(text(R.string.enter_pin))
        }
    }

    // ---- helpers -----------------------------------------------------------------------------------

    private fun lock(id: Long) = LockPresentation.Lock(TARGET, RequestToken(epoch, RequestId(id)))

    private fun checking(generation: Long) =
        LockPresentation.Checking(TARGET, ReadinessToken(epoch, Generation(generation)))

    private fun text(resId: Int): String = context.getString(resId)

    private fun present(p: LockPresentation): SurfaceApplyResult {
        lateinit var result: SurfaceApplyResult
        instr.runOnMainSync { result = presenter.present(p) }
        return result
    }

    private fun release() = instr.runOnMainSync { presenter.release() }

    private fun assertVisible(value: String) =
        assertTrue("expected on-screen text: \"$value\"", device.wait(Until.hasObject(By.text(value)), TIMEOUT_MS))

    /** Re-injects a tap until the sentinel records it (a pass-through overlay lets the touch reach it). */
    private fun tapReachesSentinel(x: Int, y: Int): Boolean = poll {
        SentinelActivity.touched.set(false)
        device.click(x, y)
        device.waitForIdle()
        SentinelActivity.touched.get()
    }

    private fun poll(condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return true
            SystemClock.sleep(SAMPLE_MS)
        }
        return condition()
    }

    private fun sh(cmd: String): String {
        val pfd = instr.uiAutomation.executeShellCommand(cmd)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
    }

    /** Snapshots the overlay app-op mode via the shared parser; Unknown when the output is unreadable. */
    private fun overlayOpMode(): AppOpMode =
        parseAppOpMode("system_alert_window", runCatching { sh("appops get $APP_PKG android:system_alert_window") }.getOrDefault(""))

    /** A fault-injecting decorator around the REAL [OverlayWindowHost]: it can throw on the seam ops and counts. */
    private class FaultInjectingHost(private val delegate: OverlayWindowHost) : OverlayWindowHost {
        @Volatile var failRemoval = false // throw BEFORE delegating (the view stays attached)
        @Volatile var detachThenFailRemoval = false // delegate the real removal (detaches), THEN throw
        @Volatile var failIsAttached = false // throw from the attachment query (an indeterminate result)
        @Volatile var failNextUpdate = false // throw ONCE on the next updateViewLayout (the present() reveal)
        @Volatile var addCount = 0
        @Volatile var removeCount = 0
        @Volatile var lastAdded: View? = null

        override fun addView(view: View, params: WindowManager.LayoutParams) {
            addCount++
            lastAdded = view
            delegate.addView(view, params)
        }

        override fun updateViewLayout(view: View, params: WindowManager.LayoutParams) {
            if (failNextUpdate) {
                failNextUpdate = false // one-shot: the recovery present() then updates cleanly
                throw IllegalStateException("injected update failure")
            }
            delegate.updateViewLayout(view, params)
        }

        override fun removeViewImmediate(view: View) {
            if (detachThenFailRemoval) {
                removeCount++
                delegate.removeViewImmediate(view) // real removal detaches the view...
                throw IllegalStateException("injected post-removal failure") // ...then a spurious throw
            }
            if (failRemoval) throw IllegalStateException("injected removal failure") // throws before detaching
            removeCount++
            delegate.removeViewImmediate(view)
        }

        override fun isAttached(view: View): Boolean {
            if (failIsAttached) throw IllegalStateException("injected isAttached failure")
            return delegate.isAttached(view)
        }
    }

    /** In-memory [LockoutStorage] so the test never mutates the app's real encrypted lockout preferences. */
    private class InMemoryLockoutStorage : LockoutStorage {
        private var snapshot = LockoutSnapshot(0, 0L)
        override fun read(): LockoutSnapshot = snapshot
        override fun write(snapshot: LockoutSnapshot): Boolean {
            this.snapshot = snapshot
            return true
        }
    }

    private companion object {
        const val APP_PKG = "com.applock"
        const val TARGET = "com.example.target"
        const val TIMEOUT_MS = 5_000L
        const val SAMPLE_MS = 100L
    }
}
