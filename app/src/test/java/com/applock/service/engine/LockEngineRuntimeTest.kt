package com.applock.service.engine

import com.applock.domain.LockSessionManager
import com.applock.domain.PolicyState
import com.applock.domain.RelockPolicy
import com.applock.security.LockoutManager
import com.applock.security.LockoutSnapshot
import com.applock.security.LockoutState
import com.applock.security.LockoutStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext

/**
 * M7 WP2 change D interpreter tests. [LockEngineReducerTest] and [LockEngineSafeDismissTest] already cover the
 * pure transitions. These tests prove that the impure host wires them to the collaborators correctly and keeps
 * the concurrency guarantees:
 *  - the four concurrency cases (timer vs Loading->Ready, completion vs supersession, the synchronous
 *    RecordUnlockFailure follow-up, N's effects not overtaken by N+1),
 *  - biometric never changes the lockout counter,
 *  - the three-fact enforcement-health aggregation,
 *  - the safe-dismiss order end-to-end (the interpreter issues the navigation before the surface comes down).
 *
 * The test drives every input through the runtime's single channel on a [StandardTestDispatcher]. The test
 * advances that scheduler explicitly. Thus inputs queued with no [Harness.idle] between them sit in the channel
 * together and drain in one pass, so the ordering and queue-order-classification assertions are real. "com.a"
 * and "com.b" are protected. "com.free" is not. "com.launcher" is the home.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass") // one cohesive interpreter test suite; splitting it would scatter related cases
class LockEngineRuntimeTest {

    private val epoch = Epoch(1)
    private val protectedPackages = setOf("com.a", "com.b")
    private var nowMs = 0L
    private var runtimeScope: CoroutineScope? = null
    private var lockoutManagerRef: LockoutManager? = null

    @After
    fun tearDown() {
        lockoutManagerRef?.shutdown() // stop the lockout persistence coroutines
        runtimeScope?.cancel() // stop the drain and the policy collector coroutines
    }

    // ---- Fakes ---------------------------------------------------------------------------------

    /** In-memory lockout store, so the real LockoutManager runs on the JVM. When [error] is set, EVERY access
     *  (a read or a write) throws. This models a persistent storage fault, like a broken EncryptedPrefs: the seed
     *  read fails, and a write throws (which the manager contains as a degraded fallback). */
    private class FakeLockoutStorage(
        @Volatile var error: Exception? = null,
        seed: LockoutSnapshot = LockoutSnapshot(0, 0L),
    ) : LockoutStorage {
        @Volatile private var stored: LockoutSnapshot = seed

        // Optional latches so a test can park a write in flight (to prove a shutdown barrier).
        @Volatile
        var writeEntered: CountDownLatch? = null

        @Volatile
        var writeProceed: CountDownLatch? = null

        override fun read(): LockoutSnapshot {
            error?.let { throw it }
            return stored
        }
        override fun write(snapshot: LockoutSnapshot): Boolean {
            writeEntered?.countDown()
            writeProceed?.await()
            error?.let { throw it }
            stored = snapshot
            return true
        }
    }

    private class FakePresenter(
        private val log: MutableList<String>,
        var nextResult: SurfaceApplyResult = SurfaceApplyResult.Success,
        var failuresLeft: Int = 0, // present()/dismiss() throw while > 0 (decrement): a transient or persistent fault
    ) : LockPresenter {
        var visible = false
            private set

        override fun present(presentation: LockPresentation): SurfaceApplyResult {
            val kind = when (presentation) {
                is LockPresentation.Lock -> "Lock"
                is LockPresentation.Checking -> "Checking"
                is LockPresentation.Recovery -> "Recovery"
            }
            log += "present:$kind:${presentation.target}"
            if (failuresLeft > 0) {
                failuresLeft--
                error("present fault")
            }
            visible = true
            return nextResult
        }

        override fun dismiss(): SurfaceApplyResult {
            log += "dismiss"
            if (failuresLeft > 0) {
                failuresLeft--
                error("dismiss fault")
            }
            visible = false
            return SurfaceApplyResult.Success
        }

        /** Simulates the host (for example, the ComposeView or overlay host) torn down under a healthy runtime. */
        fun loseHost() {
            visible = false
        }
    }

    private class FakeNavigator(
        private val log: MutableList<String>,
        var launched: Boolean = true,
        var error: Exception? = null, // when set, navigate() throws it (a launch that throws)
    ) : SafeNavigator {
        val calls = mutableListOf<SafeDestination>()
        override fun navigate(destination: SafeDestination): Boolean {
            calls += destination
            log += "navigate:$destination"
            error?.let { throw it }
            return launched
        }
    }

    private class FakeDiagnostics(var error: Exception? = null) : RuntimeDiagnostics {
        val reports = mutableListOf<Pair<String, String>>()
        override fun report(port: String, reason: String) {
            error?.let { throw it } // a diagnostics sink that itself throws; safeReport must contain it
            reports += port to reason
        }
    }

    /** Keeps every scheduled callback so a test can fire a timer even after it was cancelled (the real race).
     *  schedule() and shutdown() are serialized on one monitor, modelling the port's required atomicity: a
     *  schedule() racing shutdown() cannot pass the terminal check and then arm after the clear. */
    private class FakeTimerScheduler(var error: Exception? = null) : TimerScheduler {
        private val lock = Any()
        val active = linkedMapOf<TimerToken, (TimerToken) -> Unit>()
        private val everScheduled = linkedMapOf<TimerToken, (TimerToken) -> Unit>()
        val cancelled = mutableListOf<TimerToken>()

        var armed = true // when false, schedule() reports "not armed" without throwing (postDelayed == false)
        private var terminated = false // after shutdown(): schedule() is permanently rejected

        // Optional latches for the overlap race test: schedule() parks (still holding [lock], past the terminal
        // check, before arming), so a concurrent shutdown() must wait for the monitor.
        var scheduleEntered: CountDownLatch? = null
        var scheduleProceed: CountDownLatch? = null

        override fun schedule(token: TimerToken, delayMs: Long, onFire: (TimerToken) -> Unit): Boolean =
            synchronized(lock) {
                error?.let { throw it } // a scheduler that cannot arm the timer
                if (terminated || !armed) return@synchronized false // terminal reject OR a dying looper
                scheduleEntered?.countDown()
                scheduleProceed?.await()
                active[token] = onFire
                everScheduled[token] = onFire
                true
            }

        override fun cancel(token: TimerToken): Unit = synchronized(lock) {
            cancelled += token
            active.remove(token)
            Unit
        }

        var shutdownCount = 0
        override fun shutdown(): Unit = synchronized(lock) {
            shutdownCount++
            terminated = true // later schedule() calls are rejected, so an in-flight schedule cannot arm
            active.clear()
        }

        val latest: TimerToken get() = everScheduled.keys.last()
        fun fire(token: TimerToken) = everScheduled[token]?.invoke(token)
    }

    private class FakeHomeResolver(
        private val homePackages: Set<String>,
        var error: Exception? = null,
    ) : HomeResolver {
        override fun isHome(packageName: String): Boolean {
            error?.let { throw it } // a resolver whose PackageManager call throws
            return packageName in homePackages
        }
    }

    private class FakeAuditLog(var error: Exception? = null) : AuditLog {
        val events = mutableListOf<Pair<AuditEvent, String?>>()

        // Optional latches: record() counts [entered] down then awaits [proceed], so a test can park a caller
        // mid-operation (used to prove shutdown() is a barrier against an in-flight self-gate call).
        var entered: CountDownLatch? = null
        var proceed: CountDownLatch? = null
        override fun record(event: AuditEvent, packageName: String?) {
            entered?.countDown()
            proceed?.await()
            error?.let { throw it }
            events += event to packageName
        }
    }

    private class FakeIntruderCapture(var error: Exception? = null) : IntruderCapturePort {
        val calls = mutableListOf<Triple<String, UnlockMethod, Int>>()
        override fun onAuthFailure(packageName: String, method: UnlockMethod, failureCount: Int) {
            error?.let { throw it }
            calls += Triple(packageName, method, failureCount)
        }
    }

    /** A main dispatcher that the test can pause. Dispatched blocks queue until [release]. Thus a distinct Main
     *  can hold an effect mid-flight, to prove that event N+1 waits while N's main-dispatched effect runs. */
    private class PausableDispatcher : CoroutineDispatcher() {
        private val queued = ArrayDeque<Runnable>()
        private var paused = true
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (paused) queued += block else block.run()
        }

        fun release() {
            paused = false
            while (queued.isNotEmpty()) queued.removeFirst().run()
        }
    }

    @Suppress("LongParameterList") // a test holder aggregating the runtime and its fakes
    private class Harness(
        val runtime: LockEngineRuntime,
        val policyFlow: MutableStateFlow<PolicyState>,
        val presenter: FakePresenter,
        val navigator: FakeNavigator,
        val timer: FakeTimerScheduler,
        val audit: FakeAuditLog,
        val intruder: FakeIntruderCapture,
        val health: EnforcementHealth,
        val lockoutManager: LockoutManager,
        val sessionManager: LockSessionManager,
        val home: FakeHomeResolver,
        val diagnostics: FakeDiagnostics,
        val scheduler: TestCoroutineScheduler,
        val scope: CoroutineScope,
        val log: MutableList<String>,
    ) {
        /** The live lock's request token, or fails the test if nothing is locked. */
        val lockToken: RequestToken get() = RequestToken(Epoch(1), runtime.state.value.activeRequest!!.id)

        /** Drains every queued input and effect follow-up to a fixed point. */
        fun idle() = scheduler.advanceUntilIdle()

        /**
         * Runs the suspend self-gate success on the test scope and drains. The admission runs under the lifecycle
         * barrier; the durable clear is awaited on the same scheduler, so [idle] resolves it.
         */
        fun selfGateSuccess(packageName: String, method: UnlockMethod = UnlockMethod.PIN) {
            scope.launch { runtime.onUnlockSuccess(packageName, method) }
            idle()
        }

        /** Runs the suspend self-gate failure on the test scope, drains, and returns its resolved lockout state. */
        fun selfGateFailure(packageName: String, method: UnlockMethod = UnlockMethod.PIN): LockoutState {
            var result: LockoutState? = null
            scope.launch { result = runtime.onUnlockFailure(packageName, method) }
            idle()
            return result ?: error("self-gate failure did not resolve")
        }
    }

    @Suppress("LongParameterList") // optional per-test knobs, each defaulted
    private fun buildRuntime(
        policy: PolicyState = PolicyState.Ready(protectedPackages),
        navigatorLaunched: Boolean = true,
        presentResult: SurfaceApplyResult = SurfaceApplyResult.Success,
        homePackages: Set<String> = setOf("com.launcher"),
        storage: FakeLockoutStorage = FakeLockoutStorage(),
        main: CoroutineDispatcher? = null,
        realLockoutIo: Boolean = false,
    ): Harness {
        val log = mutableListOf<String>()
        val policyFlow = MutableStateFlow(policy)
        val presenter = FakePresenter(log, presentResult)
        val navigator = FakeNavigator(log, navigatorLaunched)
        val timer = FakeTimerScheduler()
        val audit = FakeAuditLog()
        val intruder = FakeIntruderCapture()
        val home = FakeHomeResolver(homePackages)
        val diagnostics = FakeDiagnostics()
        val health = EnforcementHealth()
        // The drain runs on a StandardTestDispatcher whose scheduler the test advances explicitly, so inputs
        // queued without an intervening idle() sit in the channel together. mainDispatcher defaults to the same
        // scheduler; a test can pass a distinct (pausable) Main to hold an effect mid-flight.
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val scope = CoroutineScope(dispatcher)
        runtimeScope = scope
        // By default the manager persists on the SAME test dispatcher, so idle() drives its writes deterministically.
        // The lifecycle barrier test needs real thread blocking, so it opts into a real single-thread dispatcher.
        val lockoutManager =
            if (realLockoutIo) {
                LockoutManager(
                    storage,
                    clock = { nowMs },
                    elapsedRealtime = { nowMs },
                    ioDispatcher = Executors.newSingleThreadExecutor { r -> Thread(r).apply { isDaemon = true } }
                        .asCoroutineDispatcher(),
                )
            } else {
                LockoutManager(storage, clock = { nowMs }, elapsedRealtime = { nowMs }, ioDispatcher = dispatcher)
            }
        lockoutManagerRef = lockoutManager
        val sessionManager = LockSessionManager(policyProvider = { RelockPolicy.IMMEDIATE }, clock = { nowMs })
        val runtime = LockEngineRuntime(
            epoch = epoch,
            scope = scope,
            mainDispatcher = main ?: dispatcher,
            policyState = policyFlow,
            sessionManager = sessionManager,
            lockoutManager = lockoutManager,
            presenter = presenter,
            navigator = navigator,
            timerScheduler = timer,
            homeResolver = home,
            auditLog = audit,
            intruderCapture = intruder,
            enforcementHealth = health,
            diagnostics = diagnostics,
            ownPackageName = "com.applock",
            elapsedRealtime = { nowMs },
        )
        scheduler.advanceUntilIdle() // process the StateFlow's initial policy emission
        log.clear() // drop any startup present so per-test logs start clean
        return Harness(
            runtime, policyFlow, presenter, navigator, timer, audit, intruder, health, lockoutManager,
            sessionManager, home, diagnostics, scheduler, scope, log,
        )
    }

    // ---- Baseline: lock / allow / screen-off wiring --------------------------------------------

    @Test
    fun `a protected foreground with no session locks and presents`() {
        val h = buildRuntime()
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        assertEquals("com.a", h.runtime.state.value.activeRequest?.target)
        assertEquals(listOf("present:Lock:com.a"), h.log)
        assertEquals(listOf(AuditEvent.LOCK_TRIGGERED to "com.a"), h.audit.events)
    }

    @Test
    fun `home is classified as a non-target and dismisses the surface`() {
        val h = buildRuntime()
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        h.runtime.onAppForegrounded("com.launcher")
        h.idle()
        assertEquals(Surface.None, h.runtime.state.value.surface)
        assertEquals(listOf("present:Lock:com.a", "dismiss"), h.log)
    }

    @Test
    fun `screen off clears sessions and tears down the surface`() {
        val h = buildRuntime()
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        h.runtime.onScreenOff()
        h.idle()
        assertEquals(Surface.None, h.runtime.state.value.surface)
        assertTrue(h.log.contains("dismiss"))
    }

    // ---- Concurrency case 1: timer vs Loading -> Ready -----------------------------------------

    @Test
    fun `a checking timer that fires after Ready re-evaluation is inert`() {
        val h = buildRuntime(policy = PolicyState.Loading)
        // Under Loading, a protected foreground is held behind a checking shield with a T_ready timer.
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        assertEquals(HoldPhase.CHECKING, h.runtime.state.value.readinessHold?.phase)
        val timerToken = h.timer.latest

        // Ready arrives and re-evaluates the hold fail-secure: com.a is protected, so it locks (and the
        // checking timer is cancelled).
        h.policyFlow.value = PolicyState.Ready(protectedPackages)
        h.idle()
        assertEquals("com.a", h.runtime.state.value.activeRequest?.target)
        assertTrue(timerToken in h.timer.cancelled)

        // The stale timer fires anyway (cancel is best-effort). It must not morph the lock into a recovery
        // shield: the reducer rejects a timer under a non-Loading policy.
        val before = h.runtime.state.value.surface
        h.timer.fire(timerToken)
        h.idle()
        assertEquals(before, h.runtime.state.value.surface)
        assertNull(h.runtime.state.value.readinessHold)
    }

    // ---- Concurrency case 2: completion vs supersession ----------------------------------------

    @Test
    fun `a unlock success for a superseded request is dropped`() {
        val h = buildRuntime()
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        val staleToken = h.lockToken // request id 0, for com.a

        h.runtime.onAppForegrounded("com.b") // supersedes: com.b now locked (request id 1)
        h.idle()
        assertEquals("com.b", h.runtime.state.value.activeRequest?.target)

        h.runtime.unlockSucceeded(staleToken, UnlockMethod.PIN)
        h.idle()
        // com.b stays locked; the stale success neither unlocked nor dismissed anything.
        assertEquals("com.b", h.runtime.state.value.activeRequest?.target)
        assertFalse(h.log.contains("dismiss"))
    }

    // ---- Concurrency case 3: the synchronous RecordUnlockFailure follow-up ---------------------

    @Test
    fun `a PIN failure audits, captures the intruder, and keeps the app locked`() {
        val h = buildRuntime()
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        val token = h.lockToken

        h.runtime.unlockFailed(token, UnlockMethod.PIN)
        h.idle()

        assertEquals(listOf(Triple("com.a", UnlockMethod.PIN, 1)), h.intruder.calls)
        assertTrue(h.audit.events.contains(AuditEvent.UNLOCK_FAILURE to "com.a"))
        assertTrue(h.audit.events.none { it.first == AuditEvent.LOCKOUT_TRIGGERED }) // below threshold
        assertEquals("com.a", h.runtime.state.value.activeRequest?.target) // still locked
    }

    @Test
    fun `the threshold PIN failure triggers a lockout audit and projects LockedOut`() {
        val h = buildRuntime()
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        val token = h.lockToken

        repeat(LockoutManager.FAILURE_THRESHOLD) {
            h.runtime.unlockFailed(token, UnlockMethod.PIN)
            h.idle()
        }

        assertTrue(h.audit.events.any { it.first == AuditEvent.LOCKOUT_TRIGGERED && it.second == "com.a" })
        assertEquals(LockoutManager.FAILURE_THRESHOLD, h.intruder.calls.last().third)
        assertTrue(h.runtime.state.value.lockout is LockoutState.LockedOut)
    }

    // ---- Concurrency case 4: N's effects are not overtaken by N+1 ------------------------------

    @Test
    fun `event N+1 waits while N's main effect is in flight on a distinct Main`() {
        // Drain and Main are DISTINCT dispatchers, and Main is pausable, so this proves the ordering across a
        // real dispatcher hop: while N's present is held on Main, N+1 must not have been reduced yet.
        val main = PausableDispatcher()
        val h = buildRuntime(main = main)
        h.runtime.onAppForegrounded("com.a") // N: locks, presents (present hops to the paused Main)
        h.runtime.onAppForegrounded("com.free") // N+1: would allow + dismiss
        h.idle()
        // N reduced and published its lock, but its present is parked on Main and N+1 has NOT been reduced
        // (otherwise the state would already be None from the allow).
        assertTrue(h.log.isEmpty())
        assertEquals("com.a", h.runtime.state.value.activeRequest?.target)
        // Release Main: N's present runs, the drain resumes, then N+1 is finally processed, in order.
        main.release()
        h.idle()
        assertEquals(listOf("present:Lock:com.a", "dismiss"), h.log)
        assertEquals(Surface.None, h.runtime.state.value.surface)
    }

    @Test
    fun `a foreground queued behind screen-off is classified after sessions clear`() {
        val h = buildRuntime()
        h.sessionManager.markUnlocked("com.a") // com.a currently has a valid unlock session
        // Screen-off and a re-foreground of com.a are queued together (nothing advances between them). The
        // drain must clear sessions first, then classify the foreground, so the now-stale "has a session"
        // value cannot allow the protected app through. Classifying at the caller would snapshot the session
        // before the queued ScreenOff cleared it, and the app would be wrongly allowed.
        h.runtime.onScreenOff()
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        assertEquals("com.a", h.runtime.state.value.activeRequest?.target)
    }

    // ---- Biometric never touches the lockout counter -------------------------------------------

    @Test
    fun `biometric cancels and success never record a failure or lockout`() {
        val h = buildRuntime()
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        val token = h.lockToken

        repeat(3) {
            h.runtime.biometricCancelled(token)
            h.idle()
        }
        assertEquals(0, h.lockoutManager.failureCount())
        assertTrue(h.intruder.calls.isEmpty())
        assertTrue(h.audit.events.none { it.first == AuditEvent.UNLOCK_FAILURE })

        h.runtime.unlockSucceeded(token, UnlockMethod.BIOMETRIC)
        h.idle()
        assertTrue(h.audit.events.any { it.first == AuditEvent.BIOMETRIC_UNLOCK_SUCCESS && it.second == "com.a" })
        assertNull(h.runtime.state.value.activeRequest)
    }

    // ---- Enforcement-health aggregation --------------------------------------------------------

    @Test
    fun `health is the conjunction of the three facts and starts fail-secure`() {
        val health = EnforcementHealth()
        assertFalse(health.state.value.healthy)
        health.submitDetector(EnforcementHealth.Detector.Enabled)
        health.submitOverlayGrant(EnforcementHealth.OverlayGrant.Granted)
        health.submitPresentation(EnforcementHealth.Presentation.Succeeded)
        assertTrue(health.state.value.healthy)
        health.submitPresentation(EnforcementHealth.Presentation.Failed("draw"))
        assertFalse(health.state.value.healthy)
    }

    @Test
    fun `a failed present submits a failed presentation fact`() {
        val h = buildRuntime(presentResult = SurfaceApplyResult.Failed("draw"))
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        assertTrue(h.health.state.value.presentation is EnforcementHealth.Presentation.Failed)
    }

    @Test
    fun `an unavailable present is treated as a failed presentation fact (fail-secure)`() {
        val h = buildRuntime(presentResult = SurfaceApplyResult.Unavailable)
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        assertTrue(h.health.state.value.presentation is EnforcementHealth.Presentation.Failed)
    }

    @Test
    fun `an unknown presentation is healthy once detector and overlay are positive`() {
        val health = EnforcementHealth()
        health.submitDetector(EnforcementHealth.Detector.Enabled)
        health.submitOverlayGrant(EnforcementHealth.OverlayGrant.Granted)
        // No present has run yet, so presentation stays Unknown; health requires only presentation != Failed.
        assertTrue(health.state.value.presentation is EnforcementHealth.Presentation.Unknown)
        assertTrue(health.state.value.healthy)
    }

    @Test
    fun `a persistently throwing present fails health and the consumer survives`() {
        val h = buildRuntime()
        h.presenter.failuresLeft = 100 // every present (initial + all re-drives) throws
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        // The throw becomes a Failed presentation fact, and the lock state was still published before it.
        assertTrue(h.health.state.value.presentation is EnforcementHealth.Presentation.Failed)
        assertEquals("com.a", h.runtime.state.value.activeRequest?.target)
        // The consumer is alive: clear the fault and a later input is still processed.
        h.presenter.failuresLeft = 0
        h.runtime.onScreenOff()
        h.idle()
        assertEquals(Surface.None, h.runtime.state.value.surface)
    }

    @Test
    fun `a transiently failing dismiss recovers via re-drive - overlay hides and health clears`() {
        val h = buildRuntime()
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        assertTrue(h.presenter.visible) // the overlay is up
        h.presenter.failuresLeft = 1 // the next dismiss throws once, then the re-drive succeeds
        h.runtime.onAppForegrounded("com.launcher") // Home -> allow -> dismiss fails, then re-drives
        h.idle()
        // The bounded re-drive is the executable recovery path: the overlay is eventually hidden and health
        // self-heals to a success, not left permanently Failed.
        assertFalse(h.presenter.visible)
        assertTrue(h.health.state.value.presentation is EnforcementHealth.Presentation.Succeeded)
    }

    @Test
    fun `a persistently failing dismiss leaves the overlay up fail-secure and the consumer survives`() {
        val h = buildRuntime()
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        h.presenter.failuresLeft = 100 // every dismiss (initial + all re-drives) throws
        h.runtime.onAppForegrounded("com.launcher") // Home -> allow -> dismiss keeps failing
        h.idle()
        // The reducer published None, but the presenter could not tear the overlay down: it stays up
        // (fail-secure over-block) and health is Failed and observable, not silent.
        assertEquals(Surface.None, h.runtime.state.value.surface)
        assertTrue(h.presenter.visible)
        assertTrue(h.health.state.value.presentation is EnforcementHealth.Presentation.Failed)
        // The consumer is alive: clear the fault and a later foreground still locks.
        h.presenter.failuresLeft = 0
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        assertEquals("com.a", h.runtime.state.value.activeRequest?.target)
    }

    @Test
    fun `a throwing audit log does not stop the lock surface or the consumer`() {
        val h = buildRuntime()
        h.audit.error = RuntimeException("audit boom") // Log precedes Present for a new lock
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        // The audit throw was contained, so interpretation continued to the Present that follows it.
        assertEquals("com.a", h.runtime.state.value.activeRequest?.target)
        assertEquals(listOf("present:Lock:com.a"), h.log)
    }

    @Test
    fun `a throwing intruder capture does not kill the consumer`() {
        val h = buildRuntime()
        h.intruder.error = RuntimeException("capture boom")
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        val token = h.lockToken
        h.runtime.unlockFailed(token, UnlockMethod.PIN) // CaptureIntruder throws, is contained
        h.idle()
        // The app stays locked, and a later foreground is still processed.
        assertEquals("com.a", h.runtime.state.value.activeRequest?.target)
        h.runtime.onAppForegrounded("com.b")
        h.idle()
        assertEquals("com.b", h.runtime.state.value.activeRequest?.target)
    }

    @Test
    fun `a timer that throws on schedule escalates the checking shield to recovery`() {
        val h = buildRuntime(policy = PolicyState.Loading)
        h.timer.error = RuntimeException("schedule boom")
        h.runtime.onAppForegrounded("com.a") // Loading -> checking hold + ScheduleTimer (throws)
        h.idle()
        // The shield never hangs on Checking: the failed schedule escalates it to Recovery (still blocking).
        assertEquals(HoldPhase.RECOVERY, h.runtime.state.value.readinessHold?.phase)
        assertTrue(h.diagnostics.reports.any { it.first == "timer_schedule" })
    }

    @Test
    fun `a timer that is rejected without throwing escalates and is diagnosed`() {
        val h = buildRuntime(policy = PolicyState.Loading)
        h.timer.armed = false // schedule returns false (e.g. postDelayed on a dying looper), no throw
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        // A non-throwing rejection must escalate too, so a Checking hold is never left armed forever, and the
        // dying-looper case is not invisible: it reports a stable "rejected" reason.
        assertEquals(HoldPhase.RECOVERY, h.runtime.state.value.readinessHold?.phase)
        assertTrue(h.diagnostics.reports.contains("timer_schedule" to "rejected"))
    }

    @Test
    fun `a throwing home resolver is treated as not-home and still locks a protected app`() {
        val h = buildRuntime()
        h.home.error = RuntimeException("resolve boom") // isHome throws for every package
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        // isHome throwing is fail-secure "not home", so com.a is evaluated as a real app and locks.
        assertEquals("com.a", h.runtime.state.value.activeRequest?.target)
        assertTrue(h.diagnostics.reports.any { it.first == "home_resolver" })
    }

    @Test
    fun `a contained adapter failure is reported to diagnostics, not lost silently`() {
        val h = buildRuntime()
        h.audit.error = RuntimeException("audit boom")
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        // A broken observational adapter is visible: exactly one report, naming the audit port.
        assertEquals(listOf("audit"), h.diagnostics.reports.map { it.first })
    }

    @Test
    fun `a throwing diagnostics sink cannot kill the consumer`() {
        val h = buildRuntime()
        h.audit.error = RuntimeException("audit boom") // audit fails, so the runtime tries to report it
        h.diagnostics.error = RuntimeException("diagnostics boom") // and the report itself throws
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        // Both failures are contained: interpretation still reaches the Present that follows the audit.
        assertEquals("com.a", h.runtime.state.value.activeRequest?.target)
        assertEquals(listOf("present:Lock:com.a"), h.log)
    }

    // ---- Lifecycle: a cancelled scope closes the channel; input is rejected, not accumulated -------

    @Test
    fun `after the scope is cancelled, input is rejected and reported, not silently accumulated`() {
        val h = buildRuntime()
        runtimeScope?.cancel()
        h.idle() // let the drain observe cancellation and close the channel
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        // The input is not processed (no consumer), and the rejection is visible, not a silent accumulation.
        assertNull(h.runtime.state.value.activeRequest)
        assertTrue(h.diagnostics.reports.any { it.first == "runtime" })
    }

    @Test
    fun `shutdown closes the channel and later input is rejected`() {
        val h = buildRuntime()
        h.runtime.shutdown()
        h.idle()
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        assertNull(h.runtime.state.value.activeRequest)
        assertTrue(h.diagnostics.reports.any { it.first == "runtime" })
    }

    @Test
    fun `input submitted immediately after shutdown is rejected synchronously, not buffered`() {
        val h = buildRuntime()
        h.runtime.shutdown()
        // Deliberately NO idle() between shutdown and this input: the drain has not yet observed cancellation,
        // so only a synchronous channel close inside shutdown() can reject this. It must be reported now, not
        // buffered silently into a consumer that is about to stop.
        h.runtime.onAppForegrounded("com.a")
        assertTrue(h.diagnostics.reports.any { it.first == "runtime" && it.second == "input_rejected" })
        h.idle()
        assertNull(h.runtime.state.value.activeRequest)
    }

    @Test
    fun `shutdown cancels a pending re-drive instead of leaking a rejected enqueue`() {
        val h = buildRuntime()
        h.presenter.failuresLeft = 100 // the present fails, so applySurface schedules a delayed re-drive
        h.runtime.onAppForegrounded("com.a")
        // Process the foreground, the failed present, and the re-drive scheduling WITHOUT advancing past the
        // re-drive's delay, so the re-drive coroutine is left suspended and pending.
        h.scheduler.runCurrent()
        assertTrue(h.health.state.value.presentation is EnforcementHealth.Presentation.Failed)

        // Shut down while the re-drive is pending. It is a workScope child, so shutdown cancels it together
        // with the drain; advancing past REDRIVE_DELAY_MS must therefore fire no leaked enqueue into the
        // now-closed channel. (A re-drive launched on the raw injected scope would survive shutdown, fire, and
        // report an "input_rejected" here.)
        h.runtime.shutdown()
        h.idle()
        assertFalse(h.diagnostics.reports.any { it.first == "runtime" && it.second == "input_rejected" })
    }

    @Test
    fun `shutdown cancels an armed checking timer so no callback survives teardown`() {
        val h = buildRuntime(policy = PolicyState.Loading)
        h.runtime.onAppForegrounded("com.a") // Loading -> checking shield + an armed T_ready timer
        h.idle()
        assertEquals(HoldPhase.CHECKING, h.runtime.state.value.readinessHold?.phase)
        assertTrue(h.timer.active.isNotEmpty()) // a timer is armed
        h.runtime.shutdown()
        // Teardown terminally shuts down the scheduler (there is no reducer pass to emit the matching
        // CancelTimer), so every armed timer is cancelled and no onFire callback survives the stop.
        assertEquals(1, h.timer.shutdownCount)
        assertTrue(h.timer.active.isEmpty())
    }

    @Test
    fun `after shutdown the scheduler permanently rejects a schedule so no timer can arm`() {
        val h = buildRuntime(policy = PolicyState.Loading)
        h.runtime.shutdown()
        // The scheduler is now terminal: a schedule() that races teardown (an in-flight scheduleTimer resuming
        // after the drain is cancelled) is rejected, so no armed timer can survive the stop.
        val armed = h.timer.schedule(TimerToken.ReadinessTimer(epoch, Generation(99)), 1_000L) { }
        assertFalse(armed)
        assertTrue(h.timer.active.isEmpty())
    }

    @Test(timeout = 15_000)
    fun `a schedule racing shutdown never leaves an armed timer (atomic on one monitor)`() {
        // The port requires schedule() and shutdown() to be atomic. Prove the fake models it: a schedule()
        // parked mid-call (past the terminal check, holding the monitor) blocks a concurrent shutdown(), so the
        // pair can never interleave into "checked not-terminated, then armed after the clear".
        val timer = FakeTimerScheduler()
        val entered = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        timer.scheduleEntered = entered
        timer.scheduleProceed = proceed

        val schedulerThread = thread { timer.schedule(TimerToken.ReadinessTimer(epoch, Generation(1)), 1_000L) { } }
        try {
            assertTrue("schedule never entered", entered.await(2, TimeUnit.SECONDS))
            val shutdownThread = thread { timer.shutdown() }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (shutdownThread.state != Thread.State.BLOCKED && System.nanoTime() < deadline) Thread.onSpinWait()
            assertEquals(Thread.State.BLOCKED, shutdownThread.state) // shutdown waits for the schedule's monitor
            proceed.countDown() // schedule arms and releases; shutdown then runs and clears the just-armed timer
            shutdownThread.join(3_000)
        } finally {
            proceed.countDown()
            schedulerThread.join(3_000)
        }
        // Whichever order the monitor granted, no armed timer survives the shutdown.
        assertTrue(timer.active.isEmpty())
        assertEquals(1, timer.shutdownCount)
    }

    @Test
    fun `shutdown blocks until an in-flight self-gate admission completes`() {
        // The lockout persistence runs on a real dispatcher here, so the parked audit blocks a real thread that
        // holds the lifecycle monitor while a second thread's shutdown() must wait on it.
        val h = buildRuntime(realLockoutIo = true)
        val entered = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        h.audit.entered = entered
        h.audit.proceed = proceed
        val shutdownReturned = AtomicBoolean(false)

        // Thread A: a self-gate failure that parks inside its (first) audit write, holding the lifecycle monitor
        // through the whole admission (the audit and the lockout admission are one unit under the barrier).
        val selfGateThread = thread { runBlocking { h.runtime.onUnlockFailure("com.a") } }
        try {
            assertTrue("self-gate never entered", entered.await(2, TimeUnit.SECONDS))
            // Thread B: shutdown() must block on the lifecycle monitor A holds; it cannot return mid admission.
            val shutdownThread = thread {
                h.runtime.shutdown()
                shutdownReturned.set(true)
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (shutdownThread.state != Thread.State.BLOCKED && System.nanoTime() < deadline) Thread.onSpinWait()
            assertEquals(Thread.State.BLOCKED, shutdownThread.state)
            assertFalse("shutdown returned mid self-gate admission", shutdownReturned.get())

            proceed.countDown() // let A finish its admission; only then can shutdown acquire the monitor
            shutdownThread.join(3_000)
            assertTrue(shutdownReturned.get())
        } finally {
            proceed.countDown()
            selfGateThread.join(3_000)
        }
        // A's self-gate admission ran to completion before shutdown returned (the barrier held).
        assertEquals(1, h.lockoutManager.failureCount())
    }

    @Test
    fun `self-gate calls after shutdown are rejected and mutate nothing`() {
        val h = buildRuntime()
        h.runtime.shutdown()
        h.idle()
        // The self-gate bypasses the channel, so it needs its own stopped guard. onUnlockFailure is a
        // fail-secure deny (LockedOut) with no lockout / audit / capture mutation.
        val state = runBlocking { h.runtime.onUnlockFailure("com.a") }
        assertTrue(state is LockoutState.LockedOut)
        assertEquals(0, h.lockoutManager.failureCount())
        assertTrue(h.audit.events.isEmpty())
        assertTrue(h.intruder.calls.isEmpty())
        // onUnlockSuccess is a reported no-op: no session is marked unlocked.
        runBlocking { h.runtime.onUnlockSuccess("com.a") }
        assertFalse(h.sessionManager.hasValidSession("com.a"))
        // Both were reported to diagnostics, not silently dropped.
        assertTrue(h.diagnostics.reports.any { it.first == "runtime" && it.second == "self_gate_after_shutdown" })
    }

    @Test
    fun `a self-gate whose write is in flight starts no audit or capture once shutdown returns`() {
        // The post-await audit and capture re-enter the lifecycle barrier and recheck stopped, so a shutdown
        // that returned while persistence was in flight prevents any new effect. The lockout persistence runs on a
        // real dispatcher so the parked write blocks a real thread.
        val storage = FakeLockoutStorage()
        val h = buildRuntime(storage = storage, realLockoutIo = true)
        val entered = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        storage.writeEntered = entered
        storage.writeProceed = proceed
        val done = CountDownLatch(1)

        // The self-gate admits (auditing UNLOCK_FAILURE), then parks inside its durable write.
        thread {
            runBlocking { h.runtime.onUnlockFailure("com.a") }
            done.countDown()
        }
        assertTrue("write never entered", entered.await(2, TimeUnit.SECONDS))

        h.runtime.shutdown() // returns while the write is still in flight
        proceed.countDown() // the write completes; the post-await recheck must now skip audit and capture
        assertTrue("self-gate never completed", done.await(3, TimeUnit.SECONDS))

        assertTrue("capture must not start after shutdown", h.intruder.calls.isEmpty())
        assertTrue(h.audit.events.none { it.first == AuditEvent.LOCKOUT_TRIGGERED })
        assertTrue(h.diagnostics.reports.any { it.first == "runtime" && it.second == "self_gate_after_shutdown" })
    }

    // ---- Re-drive budget is per-surface and recoverable after exhaustion ------------------------

    @Test
    fun `exhausting the re-drive budget for one surface does not starve the next`() {
        val h = buildRuntime()
        h.presenter.failuresLeft = 100 // com.a's present + all its re-drives fail, exhausting its budget
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        assertTrue(h.health.state.value.presentation is EnforcementHealth.Presentation.Failed)
        // A different protected app now foregrounds; its budget is fresh, and (fault cleared) it presents.
        h.presenter.failuresLeft = 0
        h.runtime.onAppForegrounded("com.b")
        h.idle()
        assertTrue(h.presenter.visible)
        assertEquals("com.b", h.runtime.state.value.activeRequest?.target)
        assertTrue(h.health.state.value.presentation is EnforcementHealth.Presentation.Succeeded)
    }

    @Test
    fun `retryPresentation reapplies the current surface after a healthy present (host recreation)`() {
        val h = buildRuntime()
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        assertTrue(h.presenter.visible)
        assertTrue(h.health.state.value.presentation is EnforcementHealth.Presentation.Succeeded)
        val presentsBefore = h.log.count { it == "present:Lock:com.a" }

        // The host is torn down while the runtime still believes the surface stands (the last apply Succeeded).
        // E signals the recreation via retryPresentation(); the runtime must re-apply the CURRENT surface even
        // though presentation was healthy (a state-guarded retry would be a silent no-op and leave the overlay
        // absent under a healthy fact).
        h.presenter.loseHost()
        h.runtime.retryPresentation()
        h.idle()

        assertTrue(h.presenter.visible) // the overlay is restored
        assertEquals(presentsBefore + 1, h.log.count { it == "present:Lock:com.a" }) // it really re-presented
    }

    @Test
    fun `retryPresentation reconciles a surface that exhausted its budget while the capability was down`() {
        val h = buildRuntime()
        h.presenter.failuresLeft = 100 // present keeps failing: the budget exhausts, the surface is unreconciled
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        assertFalse(h.presenter.visible)
        assertTrue(h.health.state.value.presentation is EnforcementHealth.Presentation.Failed)
        // The capability is restored (fault cleared) and E signals it: the surface reconciles without any new
        // state transition.
        h.presenter.failuresLeft = 0
        h.runtime.retryPresentation()
        h.idle()
        assertTrue(h.presenter.visible)
        assertTrue(h.health.state.value.presentation is EnforcementHealth.Presentation.Succeeded)
    }

    // ---- Self-gate (App Lock's own PIN gate: authoritative in the runtime, observational contained) --------

    @Test
    fun `self-gate success marks the session, resets lockout, and audits`() {
        val h = buildRuntime()
        h.selfGateSuccess("com.a")
        assertTrue(h.sessionManager.hasValidSession("com.a")) // authoritative session mutation
        assertEquals(0, h.lockoutManager.failureCount()) // lockout reset
        assertTrue(h.audit.events.contains(AuditEvent.UNLOCK_SUCCESS to "com.a"))
    }

    @Test
    fun `self-gate biometric success audits the biometric event`() {
        val h = buildRuntime()
        h.selfGateSuccess("com.a", UnlockMethod.BIOMETRIC)
        assertTrue(h.audit.events.contains(AuditEvent.BIOMETRIC_UNLOCK_SUCCESS to "com.a"))
    }

    @Test
    fun `self-gate failure records the lockout and returns the real state`() {
        val h = buildRuntime()
        var state: LockoutState = LockoutState.Available
        repeat(LockoutManager.FAILURE_THRESHOLD) { state = h.selfGateFailure("com.a") }
        // The authoritative mutation ran (real count + state), and the observational audit / capture fired.
        assertTrue(state is LockoutState.LockedOut)
        assertFalse("a persisted threshold lockout is recorded", (state as LockoutState.LockedOut).degraded)
        assertEquals(LockoutManager.FAILURE_THRESHOLD, h.lockoutManager.failureCount())
        assertTrue(h.audit.events.contains(AuditEvent.LOCKOUT_TRIGGERED to "com.a"))
        assertEquals(LockoutManager.FAILURE_THRESHOLD, h.intruder.calls.last().third)
    }

    @Test
    fun `self-gate failure resolves a degraded lockout when the durable write fails`() {
        val h = buildRuntime(storage = FakeLockoutStorage(error = RuntimeException("storage boom")))
        // The durable write fails, so the manager contains it as a degraded in-memory lockout: it denies (never a
        // spurious Available), the fault is reported, and the drain survives. A degraded lockout is not audited as
        // a recorded LOCKOUT_TRIGGERED, but the capture still fires with the actual count (R-007).
        val state = h.selfGateFailure("com.a")
        assertTrue(state is LockoutState.LockedOut)
        assertTrue("an unpersistable failure enforces in memory", (state as LockoutState.LockedOut).degraded)
        assertTrue(h.diagnostics.reports.any { it.first == "lockout_record" })
        assertTrue(h.audit.events.contains(AuditEvent.UNLOCK_FAILURE to "com.a"))
        assertFalse(h.audit.events.any { it.first == AuditEvent.LOCKOUT_TRIGGERED }) // degraded is never recorded
        assertEquals(1, h.intruder.calls.last().third) // the actual count, never a fabricated threshold
    }

    @Test
    fun `self-gate success reports a diagnostic when the reset write fails but still unlocks`() {
        val storage = FakeLockoutStorage()
        val h = buildRuntime(storage = storage)
        storage.error = RuntimeException("storage boom") // the reset write throws (contained by the manager)
        h.selfGateSuccess("com.a")
        // The authoritative session mutation and the in-memory reset both stand (no re-lock), and the failed
        // durable clear is surfaced through the background observer, not swallowed.
        assertTrue(h.sessionManager.hasValidSession("com.a"))
        assertEquals(0, h.lockoutManager.failureCount())
        assertTrue(h.diagnostics.reports.any { it.first == "lockout_reset" })
    }

    @Test
    fun `a throwing observational adapter does not break the self-gate result`() {
        val h = buildRuntime()
        h.audit.error = RuntimeException("audit boom")
        h.intruder.error = RuntimeException("capture boom")
        // Audit and capture are observational and contained, so the authoritative lockout state still returns.
        val state = h.selfGateFailure("com.a")
        assertEquals(LockoutState.Available, state) // one failure, below threshold, write succeeds
        assertEquals(1, h.lockoutManager.failureCount()) // authoritative mutation still happened
        assertTrue(h.diagnostics.reports.any { it.first == "audit" })
        assertTrue(h.diagnostics.reports.any { it.first == "intruder_capture" })
    }

    // ---- Android-backed lockout storage can throw; the runtime stays total --------------------------

    @Test
    fun `a lockout storage that throws at construction degrades but the runtime still locks`() {
        val h = buildRuntime(storage = FakeLockoutStorage(error = RuntimeException("storage boom")))
        // The seed read failed, so construction degraded (Available projection, reported) instead of crashing;
        // the runtime is still usable and locks a protected app.
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        assertEquals("com.a", h.runtime.state.value.activeRequest?.target)
        assertTrue(h.diagnostics.reports.any { it.first == "lockout_read" })
    }

    @Test
    fun `a request-token unlock success survives a failing lockout reset write`() {
        val storage = FakeLockoutStorage()
        val h = buildRuntime(storage = storage)
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        val token = h.lockToken
        storage.error = RuntimeException("storage boom") // the reset write throws (contained by the manager)
        h.runtime.unlockSucceeded(token, UnlockMethod.PIN)
        h.idle()
        // The reset write failure is contained in memory (a documented residual: storage keeps the old deadline);
        // the surface still comes down and the drain survives. The failed durable clear is now observable through
        // the background observer, not swallowed.
        assertEquals(Surface.None, h.runtime.state.value.surface)
        assertTrue(h.diagnostics.reports.any { it.first == "lockout_reset" })
        storage.error = null
        h.runtime.onAppForegrounded("com.b")
        h.idle()
        assertEquals("com.b", h.runtime.state.value.activeRequest?.target)
    }

    @Test
    fun `the overlay dismisses on success even while the reset write is stalled`() {
        // The drain must not await the reset commit before dismissing. The lockout persistence runs on a real
        // dispatcher so the reset write can stall on a gate while the drain (on the test scheduler) continues.
        val storage = FakeLockoutStorage()
        val h = buildRuntime(storage = storage, realLockoutIo = true)
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        val token = h.lockToken
        assertTrue("the overlay should be up before the unlock", h.presenter.visible)

        // Gate the reset write so its commit() stalls indefinitely.
        val entered = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        storage.writeEntered = entered
        storage.writeProceed = proceed

        h.runtime.unlockSucceeded(token, UnlockMethod.PIN)
        h.idle() // the drain admits the reset (its write stalls), then continues to DismissSurface

        assertTrue("the reset write never stalled", entered.await(2, TimeUnit.SECONDS))
        assertEquals(Surface.None, h.runtime.state.value.surface) // dismissed despite the stalled reset write
        assertFalse(h.presenter.visible)

        // The drain is not frozen by the stalled reset write: a queued screen-off is still processed. The unlock
        // marked com.a's session; screen-off must clear it while the reset commit is still parked.
        assertTrue(h.sessionManager.hasValidSession("com.a"))
        h.runtime.onScreenOff()
        h.idle()
        assertFalse("screen-off processed despite the stalled reset write", h.sessionManager.hasValidSession("com.a"))

        proceed.countDown() // release the stalled write for cleanup
        h.idle()
    }

    @Test
    fun `the drain keeps processing while a failure write is stalled and each completion keeps its count`() {
        // The event loop does not wait for persistence on the failure path: the single drain admits each failure
        // synchronously (the count and the lockout publish at once) and awaits persistence off the drain, so a
        // stalled commit() cannot freeze queued events. The lockout persistence runs on a real dispatcher so the
        // first failure write stalls on a gate while the drain (on the test scheduler) keeps going. A regression
        // would re-freeze the drain.
        val storage = FakeLockoutStorage()
        val h = buildRuntime(storage = storage, realLockoutIo = true)
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        val token = h.lockToken

        // Gate the writes: the single-thread persistence dispatcher parks in the first write, holding every later
        // write behind it (FIFO), so all five failure writes are in flight or queued and none resolves.
        val entered = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        storage.writeEntered = entered
        storage.writeProceed = proceed

        repeat(LockoutManager.FAILURE_THRESHOLD) { h.runtime.unlockFailed(token, UnlockMethod.PIN) }
        h.idle() // all five admissions run on the drain; the writes stall on the gate

        assertTrue("the first failure write never stalled", entered.await(2, TimeUnit.SECONDS))
        // Enforcement is immediate from the admitted snapshot, even though no durable write has resolved.
        assertEquals(LockoutManager.FAILURE_THRESHOLD, h.lockoutManager.failureCount())
        assertTrue(h.lockoutManager.currentState() is LockoutState.LockedOut)
        assertTrue("captures ride the off-drain completions, still pending", h.intruder.calls.isEmpty())

        // The drain is not frozen by the stalled writes: a queued screen-off is processed.
        h.runtime.onScreenOff()
        h.idle()
        assertEquals(Surface.None, h.runtime.state.value.surface)

        // Release persistence; the queued writes drain FIFO, and each off-drain completion fires its capture with
        // its own count and target, in admission order. Pump the test scheduler until the completions land (they
        // resume from the real dispatcher).
        proceed.countDown()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (h.intruder.calls.size < LockoutManager.FAILURE_THRESHOLD && System.nanoTime() < deadline) h.idle()

        assertEquals(listOf(1, 2, 3, 4, 5), h.intruder.calls.map { it.third })
        assertTrue("every completion kept its target", h.intruder.calls.all { it.first == "com.a" })
        // The threshold completion audited a recorded lockout; the below-threshold ones did not.
        assertTrue(h.audit.events.contains(AuditEvent.LOCKOUT_TRIGGERED to "com.a"))
    }

    @Test
    fun `a request-token unlock failure resolves a degraded lockout when the durable write fails`() {
        val storage = FakeLockoutStorage()
        val h = buildRuntime(storage = storage)
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        val token = h.lockToken
        storage.error = RuntimeException("storage boom") // the failure write throws (contained as degraded)
        h.runtime.unlockFailed(token, UnlockMethod.PIN)
        h.idle()
        // The app stays locked, the EngineState projection is a degraded LockedOut (in-memory enforcement), the
        // fault is reported, and the drain survives. A degraded lockout is never audited as recorded (R-007).
        assertEquals("com.a", h.runtime.state.value.activeRequest?.target)
        val projected = h.runtime.state.value.lockout
        assertTrue(projected is LockoutState.LockedOut)
        assertTrue((projected as LockoutState.LockedOut).degraded)
        assertTrue(h.diagnostics.reports.any { it.first == "lockout_record" })
        assertFalse(h.audit.events.any { it.first == AuditEvent.LOCKOUT_TRIGGERED })
    }

    // ---- Safe-dismiss ordering (end-to-end through the interpreter) ----------------------------

    @Test
    fun `a safe dismiss issues the navigation but keeps the surface up until the destination arrives`() {
        val h = buildRuntime()
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        val token = h.lockToken

        h.runtime.safeDismissRequested(token, SafeDestination.HOME)
        h.idle()

        // navigate(HOME) runs while the surface still stands. The issued echo does NOT dismiss (a silent
        // START_ABORTED returns success too). So the guarded app never flashes, and the lock stays up until HOME
        // actually arrives.
        assertEquals(listOf("present:Lock:com.a", "navigate:HOME"), h.log)
        assertEquals(Surface.Lock("com.a", RequestId(0)), h.runtime.state.value.surface)
        assertTrue(h.runtime.state.value.pendingSafeDismiss!!.navigationIssued)

        // HOME arrives (the launcher foregrounds): now the surface comes down.
        h.runtime.onAppForegrounded("com.launcher")
        h.idle()
        assertEquals(listOf("present:Lock:com.a", "navigate:HOME", "dismiss"), h.log)
        assertEquals(Surface.None, h.runtime.state.value.surface)
    }

    @Test
    fun `a safe-dismiss escape reverts on the arrival timeout when the destination never arrives`() {
        val h = buildRuntime()
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        val token = h.lockToken
        h.runtime.safeDismissRequested(token, SafeDestination.HOME)
        h.idle()
        // The launch returned success, but nothing foregrounds. The armed arrival timeout fires and reverts the
        // escape. The lock stays up (reconciled with Ready), and nothing was dismissed.
        h.timer.fire(h.timer.latest)
        h.idle()
        assertNull(h.runtime.state.value.pendingSafeDismiss) // escape reverted
        assertEquals(Surface.Lock("com.a", RequestId(0)), h.runtime.state.value.surface)
        assertFalse(h.log.contains("dismiss"))
    }

    @Test
    fun `the arrival timeout is cancelled when the destination arrives`() {
        val h = buildRuntime()
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        val token = h.lockToken
        h.runtime.safeDismissRequested(token, SafeDestination.HOME)
        h.idle()
        val arrivalTimer = h.timer.latest
        h.runtime.onAppForegrounded("com.launcher") // HOME arrives and completes the escape
        h.idle()
        assertTrue(arrivalTimer in h.timer.cancelled) // the now-moot timeout was cancelled
        assertEquals(Surface.None, h.runtime.state.value.surface)
    }

    @Test
    fun `the arrival timeout is cancelled on screen off`() {
        val h = buildRuntime()
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        val token = h.lockToken
        h.runtime.safeDismissRequested(token, SafeDestination.HOME)
        h.idle()
        val arrivalTimer = h.timer.latest
        h.runtime.onScreenOff()
        h.idle()
        assertTrue(arrivalTimer in h.timer.cancelled)
    }

    @Test
    fun `a failed arrival-timeout arm reverts the escape fail-secure`() {
        val h = buildRuntime()
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        val token = h.lockToken
        h.timer.armed = false // the arrival timeout cannot be armed (a dying looper)
        h.runtime.safeDismissRequested(token, SafeDestination.HOME)
        h.idle()
        // The launch succeeded, but the escape cannot be bounded, so it reverts now (fail-secure). The lock stays
        // up, nothing was dismissed, and the rejection is reported.
        assertNull(h.runtime.state.value.pendingSafeDismiss)
        assertEquals(Surface.Lock("com.a", RequestId(0)), h.runtime.state.value.surface)
        assertFalse(h.log.contains("dismiss"))
        assertTrue(h.diagnostics.reports.contains("arrival_timeout" to "rejected"))
    }

    @Test
    fun `an app-lock escape completes on the app-lock foreground signal`() {
        val h = buildRuntime(policy = PolicyState.Failed("boom"))
        h.runtime.onAppForegrounded("com.a") // Failed -> a Recovery shield over com.a
        h.idle()
        val token = ReadinessToken(epoch, h.runtime.state.value.generation)
        h.runtime.safeDismissRequested(token, SafeDestination.APP_LOCK)
        h.idle()
        assertTrue(h.runtime.state.value.pendingSafeDismiss!!.navigationIssued) // navigation issued, surface up
        val attempt = (h.runtime.state.value.guardState as GuardState.LeavingFor).attempt
        h.runtime.appLockForegrounded(attempt) // App Lock reports it is on screen (stamped with the attempt)
        h.idle()
        assertEquals(Surface.None, h.runtime.state.value.surface)
        assertNull(h.runtime.state.value.pendingSafeDismiss)
    }

    @Test
    fun `a stale app-lock foreground signal does not complete a later escape`() {
        val h = buildRuntime(policy = PolicyState.Failed("boom"))
        h.runtime.onAppForegrounded("com.a") // Recovery(com.a)
        h.idle()
        val token = ReadinessToken(epoch, h.runtime.state.value.generation)
        h.runtime.safeDismissRequested(token, SafeDestination.APP_LOCK)
        h.idle()
        val staleAttempt = (h.runtime.state.value.guardState as GuardState.LeavingFor).attempt
        // The launch aborted silently: the arrival timeout reverts attempt 0, then a retry starts attempt 1.
        h.timer.fire(h.timer.latest)
        h.idle()
        h.runtime.safeDismissRequested(token, SafeDestination.APP_LOCK)
        h.idle()
        assertTrue(h.runtime.state.value.pendingSafeDismiss != null) // attempt 1 is in flight
        // A delayed signal stamped with attempt 0 must not complete attempt 1's escape.
        h.runtime.appLockForegrounded(staleAttempt)
        h.idle()
        assertTrue(h.runtime.state.value.pendingSafeDismiss != null) // still in flight, not torn down
        assertEquals(HoldPhase.RECOVERY, h.runtime.state.value.readinessHold?.phase)
    }

    @Test
    fun `shutdown cancels an armed arrival timeout so no callback survives teardown`() {
        val h = buildRuntime()
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        val token = h.lockToken
        h.runtime.safeDismissRequested(token, SafeDestination.HOME)
        h.idle()
        assertTrue(h.timer.active.isNotEmpty()) // the arrival timeout is armed
        h.runtime.shutdown()
        assertEquals(1, h.timer.shutdownCount)
        assertTrue(h.timer.active.isEmpty()) // terminal shutdown cancelled it
    }

    @Test
    fun `a failed safe-dismiss navigation reverts the escape and keeps the surface`() {
        val h = buildRuntime(navigatorLaunched = false)
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        val token = h.lockToken

        h.runtime.safeDismissRequested(token, SafeDestination.HOME)
        h.idle()

        // Navigation was attempted but could not launch, so the escape reverts: the lock still stands and
        // nothing was dismissed.
        assertEquals(listOf("present:Lock:com.a", "navigate:HOME"), h.log)
        assertEquals(Surface.Lock("com.a", RequestId(0)), h.runtime.state.value.surface)
    }

    @Test
    fun `a navigation that throws reverts the escape and the consumer survives`() {
        val h = buildRuntime()
        h.runtime.onAppForegrounded("com.a")
        h.idle()
        val token = h.lockToken

        h.navigator.error = RuntimeException("launch boom") // startActivity throws
        h.runtime.safeDismissRequested(token, SafeDestination.HOME)
        h.idle()

        // The throw became the failure follow-up, so the escape reverts to a plain lock and nothing was
        // dismissed. This is exactly the Boolean-false path, now proven for the exception path too.
        assertEquals(listOf("present:Lock:com.a", "navigate:HOME"), h.log)
        assertEquals(Surface.Lock("com.a", RequestId(0)), h.runtime.state.value.surface)
        // The consumer is alive: a later foreground is still processed.
        h.runtime.onAppForegrounded("com.launcher")
        h.idle()
        assertEquals(Surface.None, h.runtime.state.value.surface)
    }

    // ---- Lockout projection seeding ------------------------------------------------------------

    @Test
    fun `the lockout projection is seeded from the manager at construction`() {
        val storage = FakeLockoutStorage(seed = LockoutSnapshot(0, 60_000L))
        val h = buildRuntime(storage = storage)
        assertTrue(h.runtime.state.value.lockout is LockoutState.LockedOut)
    }
}
