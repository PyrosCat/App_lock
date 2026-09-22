@file:Suppress("MagicNumber")

package com.applock.e2e

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.applock.R
import com.applock.data.SettingsRepository
import com.applock.platform.lock.OverlayLockPresenter
import com.applock.platform.lock.OverlayPermission
import com.applock.presentation.authentication.BiometricHostActivity
import com.applock.presentation.authentication.BiometricLaunchGate
import com.applock.security.CredentialRepository
import com.applock.security.LockoutManager
import com.applock.security.LockoutSnapshot
import com.applock.security.LockoutStorage
import com.applock.service.engine.Epoch
import com.applock.service.engine.Generation
import com.applock.service.engine.LockCompletionBridge
import com.applock.service.engine.LockCompletionResult
import com.applock.service.engine.LockCompletionSink
import com.applock.service.engine.LockPresentation
import com.applock.service.engine.ReadinessToken
import com.applock.service.engine.RequestId
import com.applock.service.engine.RequestToken
import com.applock.service.engine.SafeDestination
import com.applock.service.engine.SurfaceApplyResult
import com.applock.service.engine.SurfaceToken
import com.applock.service.engine.UnlockMethod
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * M7 WP2 change E. The Gate-2 real-surface test for the production [OverlayLockPresenter] on a device. It
 * presents each surface as a real `TYPE_APPLICATION_OVERLAY` window hosting the C1 Compose UI and asserts,
 * with cross-window inspection (UiAutomator), what the SSOT Gate 2 requires:
 *
 *  - **Content per surface:** the Lock surface renders the PIN pad; the Checking / Recovery shields render
 *    their text and their escape controls.
 *  - **Underlying-touch blocking (a real sentinel, not a focus inference):** an interactive overlay blocks a
 *    touch to the [SentinelActivity] beneath it; a dismissed (pass-through) overlay lets the touch through.
 *  - **Interaction through the surface UI:** Back leaves to HOME; the Checking / Recovery escape controls
 *    submit the right `SafeDismissRequested`; the biometric button launches [BiometricHostActivity], whose
 *    cancel reports to the completion sink.
 *
 * All completions route through [LockCompletionBridge] to a [RecordingSink] bound in [setUp].
 *
 * **FLAG_SECURE is split** (this androidTest APK is debuggable, which intentionally drops FLAG_SECURE so
 * these content assertions can read the window): the flag POLICY is proven in pure JVM by
 * `OverlayWindowFlagsTest`, and the deployed non-debug overlay's secure flag is proven by the §11 fleet
 * screencap / window-inspection check on a release build.
 *
 * **Fleet-run.** This dev box cannot boot emulators, so this is authored here and executed on the NucBox /
 * Moto G / FTL lanes (§10). Tagged [OverlayRaceTest] so the fast per-push `ci` group skips it (it needs the
 * overlay appops grant and a real window) and the `full` / connected / FTL lanes run it.
 */
@RunWith(AndroidJUnit4::class)
@OverlayRaceTest
class OverlayLockPresenterUiTest {

    private val instr = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val device: UiDevice = UiDevice.getInstance(instr)
    private val epoch = Epoch(1L)

    private lateinit var settings: SettingsRepository
    private lateinit var presenter: OverlayLockPresenter
    private lateinit var sink: RecordingSink
    private var priorBiometric: Boolean? = null // nullable + captured BEFORE the assumption, so a skip restores
    private var priorOverlayOp: AppOpMode = AppOpMode.Unknown

    @Before
    fun setUp() {
        // Snapshot the device state we change BEFORE the assumption: JUnit still runs @After when a setup
        // assumption fails, so a skipped run must not leave the overlay app-op forced to allow or the biometric
        // setting on a stale default.
        val priorOp = overlayOpMode()
        priorOverlayOp = priorOp
        // Force the grant (and later restore it) ONLY when we could actually READ the prior mode, so an
        // unreadable appops result never clobbers a real grant on teardown; otherwise rely on the existing
        // grant and let the assumption below skip the run if it is absent.
        if (priorOp is AppOpMode.Known) sh("appops set $APP_PKG android:system_alert_window allow")
        settings = SettingsRepository(context)
        priorBiometric = settings.biometricUnlockEnabled
        assumeTrue("overlay not grantable on this image", OverlayPermission.canDrawOverlays(context))

        settings.biometricUnlockEnabled = false // default: no biometric button / auto-launch (a test opts in)
        BiometricLaunchGate.resetForTest() // the gate is a process singleton; clear any prior test's claim
        instr.runOnMainSync {
            presenter = OverlayLockPresenter(
                context = context,
                credentialRepository = CredentialRepository(context),
                // In-memory lockout storage (no lockout -> the Lock surface renders the PIN pad, not the
                // countdown), so the test never mutates the installed app's real encrypted lockout preferences.
                lockoutManager = LockoutManager(InMemoryLockoutStorage()),
                settings = settings,
            )
        }
        // Route every surface / host completion to one recorder for the interaction assertions.
        LockCompletionBridge.resetForTest()
        sink = RecordingSink()
        assertTrue(LockCompletionBridge.bind(sink))
    }

    @After
    fun tearDown() {
        if (::presenter.isInitialized) instr.runOnMainSync { presenter.release() }
        LockCompletionBridge.resetForTest()
        BiometricLaunchGate.resetForTest()
        // Restore only what we actually captured (both snapshots are taken before the assumption); an Unknown
        // overlay-op snapshot is left untouched so an unreadable read never clobbers a real grant.
        priorBiometric?.let { settings.biometricUnlockEnabled = it }
        (priorOverlayOp as? AppOpMode.Known)?.let { sh("appops set $APP_PKG android:system_alert_window ${it.mode}") }
    }

    // ---- Content per surface ---------------------------------------------------------------------

    @Test
    fun lockSurfaceRendersThePinPad() {
        assertEquals(SurfaceApplyResult.Success, present(LockPresentation.Lock(TARGET, request(1))))
        assertTrue("overlay window not present after present(Lock)", overlayPresent())
        assertVisible(text(R.string.enter_pin))
        assertVisible("5") // a PIN key: the pad rendered, not merely a window
        assertVisible(TARGET) // the app label (an uninstalled target resolves to its package name)
    }

    @Test
    fun checkingShieldRendersAndEscapesToAppLock() {
        val readiness = ReadinessToken(epoch, Generation(1))
        assertEquals(SurfaceApplyResult.Success, present(LockPresentation.Checking(TARGET, readiness)))
        assertTrue("overlay window not present for the Checking shield", overlayPresent())
        assertVisible(text(R.string.overlay_checking))

        tap(waitFor(text(R.string.overlay_open_app_lock)))
        val escape = sink.safeDismiss.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        assertNotNull("no safe-dismiss from the Checking escape control", escape)
        assertEquals(SafeDestination.APP_LOCK, escape!!.destination)
        assertEquals(readiness, escape.token)
    }

    @Test
    fun recoveryShieldRendersAndExposesBothEscapes() {
        val readiness = ReadinessToken(epoch, Generation(2))
        assertEquals(SurfaceApplyResult.Success, present(LockPresentation.Recovery(TARGET, readiness)))
        assertTrue("overlay window not present for the Recovery shield", overlayPresent())
        assertVisible(text(R.string.overlay_recovery_title))
        assertVisible(text(R.string.overlay_recovery_grant))
        assertVisible(text(R.string.overlay_go_home))

        // Grant permission -> OVERLAY_SETTINGS with the resolved exempt package(s) (the C2 contract).
        tap(waitFor(text(R.string.overlay_recovery_grant)))
        val grant = sink.safeDismiss.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        assertNotNull("no safe-dismiss from the grant control", grant)
        assertEquals(SafeDestination.OVERLAY_SETTINGS, grant!!.destination)
        assertEquals(readiness, grant.token)
        assertTrue("OVERLAY_SETTINGS escape must carry exempt packages", grant.exemptPackages.isNotEmpty())

        // The surface never self-dismisses, so the go-home control is still live on the same shield.
        tap(waitFor(text(R.string.overlay_go_home)))
        val home = sink.safeDismiss.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        assertNotNull("no safe-dismiss from the go-home control", home)
        assertEquals(SafeDestination.HOME, home!!.destination)
        assertEquals(readiness, home.token)
    }

    // ---- Back --------------------------------------------------------------------------------------

    @Test
    fun backOverTheLockLeavesToHome() {
        val request = request(3)
        assertEquals(SurfaceApplyResult.Success, present(LockPresentation.Lock(TARGET, request)))
        assertVisible(text(R.string.enter_pin))

        device.pressBack()
        val back = sink.safeDismiss.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        assertNotNull("Back over a lock must submit a safe-dismiss, never reveal the app", back)
        assertEquals(SafeDestination.HOME, back!!.destination)
        assertEquals(request, back.token)
    }

    // ---- Underlying-touch sentinel (real touch, not a focus inference) -----------------------------

    @Test
    fun interactiveOverlayBlocksUnderlyingTouchAndPassthroughAllowsIt() {
        ActivityScenario.launch(SentinelActivity::class.java).use {
            device.waitForIdle()
            val x = device.displayWidth / 2
            val y = (device.displayHeight * 0.12).toInt() // over the overlay, clear of any control

            // Interactive: the touch-modal overlay must eat the tap; the app beneath must not see it.
            assertEquals(SurfaceApplyResult.Success, present(LockPresentation.Checking(TARGET, ReadinessToken(epoch, Generation(9)))))
            assertVisible(text(R.string.overlay_checking))
            SentinelActivity.touched.set(false)
            repeat(3) {
                device.click(x, y)
                device.waitForIdle()
            }
            assertFalse("an interactive overlay must block the underlying touch", SentinelActivity.touched.get())

            // Dismissed (pass-through): the tap must now reach the app beneath.
            dismiss()
            assertTrue("a dismissed overlay must let the underlying touch through", tapReachesSentinel(x, y))
        }
    }

    // ---- Biometric through the presenter UI --------------------------------------------------------

    @Test
    fun biometricAutoPromptThenManualRetryReportCancelToTheSink() {
        // Needs an enrolled authenticator, so the presenter offers biometrics and the prompt appears; skipped
        // on an image with none. The no-enrollment token-carrying path is covered separately below.
        assumeTrue(
            "no usable biometric enrolled",
            BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS,
        )
        settings.biometricUnlockEnabled = true // so biometrics are available on the Lock surface

        val request = request(5)
        assertEquals(SurfaceApplyResult.Success, present(LockPresentation.Lock(TARGET, request)))

        // FR-002/FR-007 auto-launch shows the system prompt with no tap; cancel it via the negative button.
        tap(waitFor(text(R.string.biometric_prompt_negative)))
        val autoCancel = sink.biometricCancelled.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        assertNotNull("the auto-launched prompt's cancel must report a biometric-cancel", autoCancel)
        assertEquals(request, autoCancel)

        // The host frees the gate before dispatching the cancel, but wait explicitly for it to be free before
        // the retry so a slow device's onDestroy timing cannot swallow the tap.
        assertTrue("gate must be free before a manual retry", poll { BiometricLaunchGate.claimedBy == null })
        tap(waitFor(text(R.string.unlock_with_biometrics)))
        tap(waitFor(text(R.string.biometric_prompt_negative)))
        val manualCancel = sink.biometricCancelled.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        assertNotNull("a manual retry after cancel must re-launch and report a cancel", manualCancel)
        assertEquals(request, manualCancel)
    }

    @Test
    fun biometricHostCarriesTheRequestTokenWhenNoEnrollment() {
        // Deterministic where no biometric is enrolled (the AOSP fleet emulators): the host takes the cancel
        // path and reports the full token. On an enrolled device the prompt would show (that path is above).
        assumeTrue(
            "biometric enrolled; the prompt would block this check",
            BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_SUCCESS,
        )
        val token = RequestToken(epoch, RequestId(7))
        // Pre-claim the lease as the presenter would: acknowledge no longer adopts a free gate (a stale,
        // restored host must finish silently per ADR-020), so the host only proceeds for the exact claimed
        // lease. Claim immediately before the launch so the lease is well within its timeout at onCreate.
        val leaseId = (BiometricLaunchGate.claim(token) as BiometricLaunchGate.ClaimOutcome.Claimed).lease.leaseId
        context.startActivity(BiometricHostActivity.createIntent(context, token, leaseId, targetLabel = "Target"))

        val cancelled = sink.biometricCancelled.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        assertNotNull("the host must report a completion", cancelled)
        assertEquals("the host must carry the full epoch+id token", token, cancelled)
    }

    // ---- Biometric single-flight on real hardware: aborted or slow launch (API 35 and 36) ----------
    // These tests confirm the gate timing against the device's real elapsed-realtime clock. setUp restores the
    // production clock. M7_PLAN requires this fleet check before any change to the timeout. BiometricAutoLaunchTest
    // covers the auto-launch loop logic in JVM (bounded retry, no premature consumption); these tests confirm the
    // on-device primitives that the loop uses.

    @Test
    fun abortedBiometricLaunchReclaimsAfterTimeoutAndLeavesThePinPad() {
        val request = request(11)
        // This models an aborted background launch. The presenter claims a lease before startActivity, but the
        // host does not start (START_ABORTED is not fatal), so the lease stays unacknowledged.
        val abortedClaim = BiometricLaunchGate.claim(request) as BiometricLaunchGate.ClaimOutcome.Claimed
        assertEquals("the aborted launch's token owns the gate", request, BiometricLaunchGate.claimedBy)
        assertFalse("an unstarted host cannot acknowledge", abortedClaim.lease.isAcknowledged)

        // The PIN pad stays available while the aborted lease is open. setUp disables biometrics, so nothing
        // auto-launches over the pad.
        assertEquals(SurfaceApplyResult.Success, present(LockPresentation.Lock(TARGET, request)))
        assertVisible(text(R.string.enter_pin))
        assertVisible("5")

        // After the real timeout, the unacknowledged lease becomes reclaimable, so a later request is not blocked
        // behind the dead lease.
        SystemClock.sleep(BiometricLaunchGate.LEASE_TIMEOUT_MS + RECLAIM_MARGIN_MS)
        val laterRequest = request(12)
        assertTrue(
            "an abandoned lease must be reclaimable, not blocked",
            BiometricLaunchGate.claim(laterRequest) is BiometricLaunchGate.ClaimOutcome.Claimed,
        )
        assertEquals(laterRequest, BiometricLaunchGate.claimedBy)
    }

    @Test
    fun slowBiometricAckWithinTimeoutIsHonouredAndPastTimeoutIsRejected() {
        // A slow but valid launch acknowledges before the timeout. The gate keeps its lease, and a different
        // request must wait instead of a second prompt.
        val slowRequest = request(13)
        val slowLease = (BiometricLaunchGate.claim(slowRequest) as BiometricLaunchGate.ClaimOutcome.Claimed).lease
        SystemClock.sleep(BiometricLaunchGate.LEASE_TIMEOUT_MS / 2)
        assertTrue("a within-timeout acknowledgement must be honoured", BiometricLaunchGate.acknowledge(slowRequest, slowLease.leaseId))
        assertEquals(
            "a foreign request must wait behind an acknowledged (live) prompt",
            BiometricLaunchGate.ClaimOutcome.BusyOther,
            BiometricLaunchGate.claim(request(14)),
        )
        BiometricLaunchGate.release(slowRequest, slowLease.leaseId)

        // The gate rejects an acknowledgement that arrives after the timeout. The lease is abandoned, and another
        // request can reclaim it, so a late host finishes and does not take a newer request.
        val lateRequest = request(15)
        val lateLease = (BiometricLaunchGate.claim(lateRequest) as BiometricLaunchGate.ClaimOutcome.Claimed).lease
        SystemClock.sleep(BiometricLaunchGate.LEASE_TIMEOUT_MS + RECLAIM_MARGIN_MS)
        assertFalse("a past-timeout acknowledgement must be rejected", BiometricLaunchGate.acknowledge(lateRequest, lateLease.leaseId))
    }

    @Test
    fun staleBiometricHostWithoutAMatchingLeaseFinishesSilently() {
        // ADR-020: a host must finish, and must not authenticate or report, if the gate does not hold its lease.
        // This happens after a process death, or for an aborted launch's late host. setUp cleared the gate with
        // resetForTest, so acknowledge() returns false before a prompt shows.
        val token = RequestToken(epoch, RequestId(16))
        context.startActivity(BiometricHostActivity.createIntent(context, token, leaseId = 9_999L, targetLabel = "Target"))
        assertNull(
            "a stale host without a matching lease must finish silently, not report a cancel",
            sink.biometricCancelled.poll(SILENT_FINISH_MS, TimeUnit.MILLISECONDS),
        )
        assertNull(
            "a stale host must not report an unlock",
            sink.unlockSucceeded.poll(NO_REPORT_POLL_MS, TimeUnit.MILLISECONDS),
        )
    }

    // ---- Biometric success from the overlay (opt-in emulator lane) ---------------------------------

    @Test
    fun biometricSuccessFromTheOverlayReportsUnlockSucceeded() {
        // Instrumentation cannot inject a real fingerprint, because that needs the emulator console
        // (adb emu finger touch <id>). This test runs only on the opt-in emulator lane. The fleet harness passes
        // -e inject_biometric_success 1 and injects the finger when it finds BIOMETRIC_READY_TAG in logcat. The
        // test is skipped on other lanes and when no authenticator is enrolled. It proves that a biometric unlock
        // from the overlay reaches the sink with the exact request token and the BIOMETRIC method.
        assumeTrue("opt-in emulator lane only (the harness injects the finger)", booleanArg("inject_biometric_success"))
        assumeTrue(
            "no usable biometric enrolled",
            BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS,
        )
        settings.biometricUnlockEnabled = true

        val request = request(17)
        assertEquals(SurfaceApplyResult.Success, present(LockPresentation.Lock(TARGET, request)))
        // The FR-002/FR-007 auto-launched prompt is visible when its negative button is on screen. Signal the harness.
        assertVisible(text(R.string.biometric_prompt_negative))
        Log.i(BIOMETRIC_READY_TAG, "request=${request.id.n}: inject the fingerprint now")

        val unlock = sink.unlockSucceeded.poll(BIOMETRIC_SUCCESS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        assertNotNull("biometric success must report unlockSucceeded to the sink", unlock)
        assertEquals("the exact request token", request, unlock!!.token)
        assertEquals("the BIOMETRIC method", UnlockMethod.BIOMETRIC, unlock.method)
    }

    // ---- helpers -----------------------------------------------------------------------------------

    private fun request(id: Long) = RequestToken(epoch, RequestId(id))

    /** A boolean instrumentation argument from `-e <name> 1|true`; false when the argument is absent. */
    private fun booleanArg(name: String): Boolean =
        InstrumentationRegistry.getArguments().getString(name).let { it == "1" || it == "true" }

    private fun text(resId: Int): String = context.getString(resId)

    private fun present(p: LockPresentation): SurfaceApplyResult {
        lateinit var result: SurfaceApplyResult
        instr.runOnMainSync { result = presenter.present(p) }
        return result
    }

    private fun dismiss() = instr.runOnMainSync { presenter.dismiss() }

    private fun assertVisible(value: String) =
        assertTrue("expected on-screen text: \"$value\"", device.wait(Until.hasObject(By.text(value)), TIMEOUT_MS))

    private fun waitFor(value: String): UiObject2? =
        device.wait(Until.findObject(By.text(value)), TIMEOUT_MS)

    private fun tap(target: UiObject2?) {
        assertNotNull("control not found on screen", target)
        target!!.click()
    }

    private fun overlayPresent(): Boolean = poll { sh("dumpsys window").contains(OVERLAY_TITLE) }

    /** Re-injects a tap until the sentinel records it (the pass-through flag apply can lag the dismiss call). */
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

    /** Records every completion so a test can assert what a surface / the host delivered. */
    private class RecordingSink : LockCompletionSink {
        val unlockSucceeded = LinkedBlockingQueue<Unlock>()
        val unlockFailed = LinkedBlockingQueue<RequestToken>()
        val biometricCancelled = LinkedBlockingQueue<RequestToken>()
        val safeDismiss = LinkedBlockingQueue<SafeDismiss>()

        /** A recorded unlock success: the reporting token and the method, so a test can assert both. */
        data class Unlock(val token: RequestToken, val method: UnlockMethod)

        data class SafeDismiss(
            val token: SurfaceToken,
            val destination: SafeDestination,
            val exemptPackages: Set<String>,
        )

        override fun unlockSucceeded(token: RequestToken, method: UnlockMethod): LockCompletionResult {
            unlockSucceeded.offer(Unlock(token, method))
            return LockCompletionResult.ACCEPTED
        }

        override fun unlockFailed(token: RequestToken, method: UnlockMethod): LockCompletionResult {
            unlockFailed.offer(token)
            return LockCompletionResult.ACCEPTED
        }

        override fun biometricCancelled(token: RequestToken): LockCompletionResult {
            biometricCancelled.offer(token)
            return LockCompletionResult.ACCEPTED
        }

        override fun safeDismissRequested(
            token: SurfaceToken,
            destination: SafeDestination,
            exemptPackages: Set<String>,
        ): LockCompletionResult {
            safeDismiss.offer(SafeDismiss(token, destination, exemptPackages))
            return LockCompletionResult.ACCEPTED
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
        val OVERLAY_TITLE = OverlayLockPresenter.OVERLAY_WINDOW_TITLE
        const val TIMEOUT_MS = 5_000L
        const val SAMPLE_MS = 100L

        // Extra time after the gate's LEASE_TIMEOUT, so an unacknowledged lease is reclaimable.
        const val RECLAIM_MARGIN_MS = 750L
        const val SILENT_FINISH_MS = 2_000L // the stale host must report nothing in this time
        const val NO_REPORT_POLL_MS = 250L // short poll to confirm that no unlock is reported
        const val BIOMETRIC_SUCCESS_TIMEOUT_MS = 20_000L // long wait for the harness to inject the finger
        const val BIOMETRIC_READY_TAG = "AppLockBiometricReady" // logcat marker: the prompt is visible, inject the finger
    }
}
