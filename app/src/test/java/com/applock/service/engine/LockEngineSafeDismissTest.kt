package com.applock.service.engine

import com.applock.domain.PolicyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7 WP2 Phase 2 (change C2, extended in F1): the pure reducer's token-keyed safe-dismiss handshake
 * ([EngineEvent.SafeDismissRequested] -> [Effect.NavigateSafely] -> an attempt-keyed
 * [EngineEvent.SafeDismissNavigationIssued] that merely RECORDS the launch, or
 * [EngineEvent.SafeDismissNavigationFailed] on a launch failure). F1 makes the issued echo stop tearing the
 * surface down: only a real ARRIVAL ([Foreground.Home], the destination package's foreground, or the
 * [EngineEvent.AppLockForeground] signal) ends the escape, and the interpreter's attempt-keyed
 * [EngineEvent.SafeDismissTimedOut] reverts a silently aborted launch. APP_LOCK and OVERLAY_SETTINGS are
 * shield-only escapes. Split out from [LockEngineReducerTest] so each suite stays focused. "com.a" / "com.b"
 * are protected; "com.android.settings" stands for the overlay grant screen the OVERLAY_SETTINGS escape
 * navigates to.
 */
// One large but cohesive suite: it is the single contract test for the safe-dismiss handshake.
@Suppress("LargeClass")
class LockEngineSafeDismissTest {

    private val epoch = Epoch(1)
    private val protectedPackages = setOf("com.a", "com.b")
    private val settings = "com.android.settings"

    private fun initial() = EngineState(epoch = epoch, policy = PolicyState.Ready(protectedPackages))
    private fun loading() = EngineState(epoch = epoch, policy = PolicyState.Loading)
    private fun failed() = EngineState(epoch = epoch, policy = PolicyState.Failed("boom"))

    private fun EngineState.observe(foreground: Foreground) =
        LockEngineReducer.reduce(this, EngineEvent.ForegroundObserved(foreground, elapsedRealtimeMs = 0))

    private fun other(packageName: String, session: Boolean = false) =
        Foreground.Other(packageName, hasValidSession = session)

    /** Performs step 1 (records the in-flight safe-dismiss over the live surface) and returns the state. */
    private fun EngineState.requestSafeDismiss(
        token: SurfaceToken,
        destination: SafeDestination,
        exemptPackages: Set<String> = emptySet(),
    ) = LockEngineReducer.reduce(this, EngineEvent.SafeDismissRequested(token, destination, exemptPackages)).state

    /** The unique attempt token of the in-flight escape recorded in this state (the handshake follow-up key). */
    private val EngineState.attempt: AttemptToken get() = (guardState as GuardState.LeavingFor).attempt

    /** Runs the success follow-up (step 2) over this state for the given attempt. */
    private fun EngineState.navIssued(attempt: AttemptToken) =
        LockEngineReducer.reduce(this, EngineEvent.SafeDismissNavigationIssued(attempt))

    /** Runs the failure follow-up over this state for the given attempt (the launch could not start). */
    private fun EngineState.navFailed(attempt: AttemptToken) =
        LockEngineReducer.reduce(this, EngineEvent.SafeDismissNavigationFailed(attempt))

    /** Runs the arrival-timeout follow-up over this state for the given attempt (the destination never arrived). */
    private fun EngineState.navTimedOut(attempt: AttemptToken) =
        LockEngineReducer.reduce(this, EngineEvent.SafeDismissTimedOut(attempt))

    /** App Lock's own foreground UI reports it is on screen (the APP_LOCK arrival confirmation for [attempt]). */
    private fun EngineState.appLockArrived(attempt: AttemptToken) =
        LockEngineReducer.reduce(this, EngineEvent.AppLockForeground(attempt))

    // ---- Step 1: request records the pending dismiss, preserves the surface, and only navigates -----

    @Test
    fun `safe-dismiss over a lock records the pending dismiss, preserves the surface, and navigates`() {
        val locked = initial().observe(other("com.a")).state
        val guard = (locked.guardState as GuardState.Guarding).guard
        val token = RequestToken(epoch, locked.activeRequest!!.id)
        val reduction = LockEngineReducer.reduce(locked, EngineEvent.SafeDismissRequested(token, SafeDestination.HOME))
        val attempt = AttemptToken(epoch, AttemptId(0))
        val leaving = GuardState.LeavingFor(guard, token, attempt, SafeDestination.HOME, emptySet())
        // Only the guard slot and the attempt counter change.
        assertEquals(locked.copy(guardState = leaving, nextAttemptId = 1), reduction.state)
        assertEquals(listOf(Effect.NavigateSafely(SafeDestination.HOME, attempt)), reduction.effects)
    }

    @Test
    fun `safe-dismiss over a recovery shield records it, preserves it, and navigates`() {
        val recovery = failed().observe(other("com.a")).state // a recovery hold (the other surface kind)
        val guard = (recovery.guardState as GuardState.Guarding).guard
        val token = ReadinessToken(epoch, recovery.generation)
        val reduction = LockEngineReducer.reduce(
            recovery,
            EngineEvent.SafeDismissRequested(token, SafeDestination.OVERLAY_SETTINGS, setOf(settings)),
        )
        val attempt = AttemptToken(epoch, AttemptId(0))
        val leaving = GuardState.LeavingFor(guard, token, attempt, SafeDestination.OVERLAY_SETTINGS, setOf(settings))
        // Only the guard slot and the attempt counter change.
        assertEquals(recovery.copy(guardState = leaving, nextAttemptId = 1), reduction.state)
        assertEquals(listOf(Effect.NavigateSafely(SafeDestination.OVERLAY_SETTINGS, attempt)), reduction.effects)
    }

    // ---- Step 2a: navigation-issued only RECORDS the issue; it never tears the surface down ---------

    @Test
    fun `the navigation-issued follow-up records the issue and keeps the surface up (no teardown)`() {
        val locked = initial().observe(other("com.a")).state
        assertEquals(Generation(0), locked.generation)
        val token = RequestToken(epoch, locked.activeRequest!!.id)
        val requested = locked.requestSafeDismiss(token, SafeDestination.HOME)
        val reduction = requested.navIssued(requested.attempt)
        // The launch only started (a silent START_ABORTED returns success too), so the lock stays up. Only a
        // real HOME arrival tears it down. The follow-up flips the projection flag and emits nothing.
        assertEquals(locked.activeRequest, reduction.state.activeRequest) // still locked
        assertEquals(Generation(0), reduction.state.generation) // no invalidating teardown
        assertTrue(reduction.state.pendingSafeDismiss!!.navigationIssued)
        assertTrue(reduction.effects.isEmpty())
    }

    @Test
    fun `a second navigation-issued follow-up for an already-issued escape is an idempotent no-op`() {
        val locked = initial().observe(other("com.a")).state
        val token = RequestToken(epoch, locked.activeRequest!!.id)
        val issued = locked.requestSafeDismiss(token, SafeDestination.HOME).let {
            it.navIssued(it.attempt).state
        }
        val again = issued.navIssued((issued.guardState as GuardState.LeavingFor).attempt)
        assertEquals(issued, again.state)
        assertTrue(again.effects.isEmpty())
    }

    @Test
    fun `a navigation-issued follow-up with no prior request cannot tear the guard down`() {
        val locked = initial().observe(other("com.a")).state // no SafeDismissRequested was ever issued
        val reduction = locked.navIssued(AttemptToken(epoch, AttemptId(0)))
        assertEquals(locked, reduction.state) // guard intact; a bare follow-up cannot change it
        assertTrue(reduction.effects.isEmpty())
    }

    @Test
    fun `a navigation-issued follow-up whose token differs from the recorded request is dropped`() {
        val locked = initial().observe(other("com.a")).state
        val token = RequestToken(epoch, locked.activeRequest!!.id)
        val requested = locked.requestSafeDismiss(token, SafeDestination.HOME)
        val wrong = AttemptToken(epoch, AttemptId(99))
        val reduction = requested.navIssued(wrong)
        assertEquals(requested, reduction.state) // still guarded, request still recorded
        assertTrue(reduction.effects.isEmpty())
    }

    // ---- Step 2b: only a real ARRIVAL tears the surface down --------------------------------------

    @Test
    fun `a HOME arrival after the navigation issued tears the lock down and advances the generation`() {
        val locked = initial().observe(other("com.a")).state
        assertEquals(Generation(0), locked.generation)
        val token = RequestToken(epoch, locked.activeRequest!!.id)
        val issued = locked.requestSafeDismiss(token, SafeDestination.HOME).let { it.navIssued(it.attempt).state }
        assertEquals("com.a", issued.activeRequest!!.target) // still locked until HOME actually arrives
        val reduction = issued.observe(Foreground.Home) // the launcher foregrounds: the HOME arrival
        assertNull(reduction.state.activeRequest)
        assertEquals(Generation(1), reduction.state.generation) // an invalidating teardown
        assertNull(reduction.state.pendingSafeDismiss) // HOME needs no arrival exemption
        // The app being left is relocked as its foreground is replaced by Home.
        assertEquals(listOf(Effect.NoteAppLeft("com.a"), Effect.DismissSurface), reduction.effects)
    }

    @Test
    fun `a HOME arrival before the navigation issued also tears the lock down (stale queued observation)`() {
        // The drain can process the destination foreground before the issued echo (navigate suspends on Main).
        val locked = initial().observe(other("com.a")).state
        val token = RequestToken(epoch, locked.activeRequest!!.id)
        val requested = locked.requestSafeDismiss(token, SafeDestination.HOME) // navigationIssued still false
        val reduction = requested.observe(Foreground.Home)
        assertNull(reduction.state.activeRequest) // HOME arrival completes the escape even before the echo
        // The now-stale issued echo matches no live escape and is dropped.
        val late = reduction.state.navIssued(requested.attempt)
        assertEquals(reduction.state, late.state)
        assertTrue(late.effects.isEmpty())
    }

    @Test
    fun `a HOME arrival over a checking shield cancels its timer, dismisses, and advances the generation`() {
        val checking = loading().observe(other("com.a")).state
        val checkingTimer = checking.readinessHold!!.timer!!
        assertEquals(Generation(1), checking.generation)
        val token = ReadinessToken(epoch, checking.generation)
        val issued = checking.requestSafeDismiss(token, SafeDestination.HOME).let { it.navIssued(it.attempt).state }
        val reduction = issued.observe(Foreground.Home)
        assertNull(reduction.state.readinessHold)
        assertEquals(Generation(2), reduction.state.generation)
        assertTrue(reduction.effects.contains(Effect.CancelTimer(checkingTimer)))
        assertTrue(reduction.effects.contains(Effect.NoteAppLeft("com.a")))
        assertTrue(reduction.effects.contains(Effect.DismissSurface))
    }

    // ---- Stale tokens are dropped; the newer surface always stands ------------------------------

    @Test
    fun `a safe-dismiss request from a superseded lock is dropped`() {
        val lockA = initial().observe(other("com.a")).state
        val staleToken = RequestToken(epoch, lockA.activeRequest!!.id) // id 0
        val lockB = lockA.observe(other("com.b")).state // supersedes to id 1
        val reduction = LockEngineReducer.reduce(
            lockB,
            EngineEvent.SafeDismissRequested(staleToken, SafeDestination.HOME),
        )
        assertEquals(lockB, reduction.state) // nothing recorded, guard intact
        assertTrue(reduction.effects.isEmpty())
    }

    @Test
    fun `a stale navigation-issued follow-up leaves the superseding lock standing`() {
        val lockA = initial().observe(other("com.a")).state
        val token = RequestToken(epoch, lockA.activeRequest!!.id) // id 0, from A
        val requested = lockA.requestSafeDismiss(token, SafeDestination.HOME)
        val lockB = requested.observe(other("com.b")).state // supersedes: id 1 for com.b, generation advanced
        val reduction = lockB.navIssued(requested.attempt)
        assertEquals(lockB, reduction.state) // never tear the newer surface down for the old navigation
        assertTrue(reduction.effects.isEmpty())
    }

    @Test
    fun `a stale navigation-issued follow-up leaves a superseding shield standing`() {
        val holdA = loading().observe(other("com.a")).state // checking hold, generation 1
        val token = ReadinessToken(epoch, holdA.generation)
        val requested = holdA.requestSafeDismiss(token, SafeDestination.HOME)
        val holdB = requested.observe(other("com.b")).state // supersedes: generation 2
        val reduction = holdB.navIssued(requested.attempt)
        assertEquals(holdB, reduction.state)
        assertTrue(reduction.effects.isEmpty())
    }

    @Test
    fun `a safe-dismiss with a foreign process epoch is rejected`() {
        val locked = initial().observe(other("com.a")).state
        val ghost = RequestToken(Epoch(999), locked.activeRequest!!.id)
        val reduction = LockEngineReducer.reduce(locked, EngineEvent.SafeDismissRequested(ghost, SafeDestination.HOME))
        assertEquals(locked, reduction.state)
        assertTrue(reduction.effects.isEmpty())
    }

    // ---- Overlay-settings escape: the destination must not be re-shielded (both event orders) -------

    /** A recovery shield over "com.a" (policy Failed) with the OVERLAY_SETTINGS escape already requested. */
    private fun escapeRequested(): EngineState {
        val shielded = failed().observe(other("com.a")).state
        val token = ReadinessToken(epoch, shielded.generation)
        return shielded.requestSafeDismiss(token, SafeDestination.OVERLAY_SETTINGS, setOf(settings))
    }

    @Test
    fun `an overlay-settings request with no resolved package is rejected, preserving the shield`() {
        val recovery = failed().observe(other("com.a")).state
        val token = ReadinessToken(epoch, recovery.generation)
        val reduction = LockEngineReducer.reduce(
            recovery,
            EngineEvent.SafeDismissRequested(token, SafeDestination.OVERLAY_SETTINGS, emptySet()),
        )
        assertEquals(recovery, reduction.state) // surface preserved, no exemption armed
        assertTrue(reduction.effects.isEmpty()) // and no navigation issued
    }

    @Test
    fun `an overlay-settings escape from a lock surface is rejected`() {
        val locked = initial().observe(other("com.a")).state // a Lock (RequestToken) under a Ready policy
        val token = RequestToken(epoch, locked.activeRequest!!.id)
        val reduction = LockEngineReducer.reduce(
            locked,
            EngineEvent.SafeDismissRequested(token, SafeDestination.OVERLAY_SETTINGS, setOf(settings)),
        )
        assertEquals(locked, reduction.state) // the lock stands; the escape cannot arm from a lock
        assertTrue(reduction.effects.isEmpty())
    }

    @Test
    fun `settings arriving under a ready policy that protects it is locked, not exempted`() {
        val requested = escapeRequested() // armed under Failed, approved = {settings}
        // Policy becomes Ready with settings itself protected, before settings arrives: exemption clears.
        val ready = LockEngineReducer.reduce(
            requested,
            EngineEvent.PolicyStateChanged(PolicyState.Ready(setOf(settings))),
        ).state
        assertNull(ready.pendingSafeDismiss)
        // Settings now arrives and is evaluated normally: protected + no session -> locked, not bypassed.
        val arrival = ready.observe(other(settings))
        assertEquals(settings, arrival.state.activeRequest!!.target)
    }

    @Test
    fun `settings arriving after the navigation-issued follow-up is allowed, not re-shielded`() {
        val requested = escapeRequested()
        // The issued echo only records the issue. The shield stays up until settings actually arrives.
        val issued = requested.navIssued(requested.attempt).state
        assertEquals(HoldPhase.RECOVERY, issued.readinessHold!!.phase) // shield still up
        assertTrue(issued.pendingSafeDismiss!!.navigationIssued)
        val arrival = issued.observe(other(settings)) // the destination arrives: now the shield comes down
        assertNull(arrival.state.readinessHold) // allowed through, no new shield
        assertNull(arrival.state.activeRequest)
        assertEquals(settings, arrival.state.pendingSafeDismiss!!.arrived)
        assertTrue(arrival.effects.contains(Effect.DismissSurface))
        assertTrue(arrival.effects.none { it is Effect.Present })
    }

    @Test
    fun `settings arriving before the follow-up tears the shield down and is allowed`() {
        val requested = escapeRequested()
        val arrival = requested.observe(other(settings)) // arrives while the shield still stands
        assertNull(arrival.state.readinessHold)
        assertEquals(settings, arrival.state.pendingSafeDismiss!!.arrived)
        assertTrue(arrival.effects.contains(Effect.DismissSurface))
        assertTrue(arrival.effects.none { it is Effect.Present })
        // The delayed follow-up is now stale (the shield's generation is gone) and is a harmless no-op.
        val issued = arrival.state.navIssued(requested.attempt)
        assertEquals(arrival.state, issued.state)
        assertTrue(issued.effects.isEmpty())
    }

    @Test
    fun `a supersession before the navigation-issued follow-up invalidates the escape`() {
        val requested = escapeRequested() // Recovery(com.a), escape armed but not yet issued
        // A different app supersedes com.a's shield before step 2 confirms the navigation.
        val supersede = requested.observe(other("com.b"))
        assertNull(supersede.state.pendingSafeDismiss) // the stale escape is invalidated
        assertEquals("com.b", supersede.state.readinessHold!!.target) // com.b is now shielded
        // Settings arriving now is not exempt (the com.a escape is gone): it is shielded, never allowed
        // to dismiss com.b's newer surface.
        val arrival = supersede.state.observe(other(settings))
        assertEquals(HoldPhase.RECOVERY, arrival.state.readinessHold!!.phase)
        assertEquals(settings, arrival.state.readinessHold!!.target)
        // The late follow-up for the superseded com.a surface is a harmless no-op.
        val issued = arrival.state.navIssued(requested.attempt)
        assertEquals(arrival.state, issued.state)
        assertTrue(issued.effects.isEmpty())
    }

    @Test
    fun `a same-target re-emission before the follow-up does not cancel a settings escape`() {
        val requested = escapeRequested() // Recovery(com.a), OVERLAY_SETTINGS armed, not issued
        // com.a re-emits its own accessibility event before the arrival: not a supersession, so the escape stays.
        val reappear = requested.observe(other("com.a")).state
        assertEquals(requested.pendingSafeDismiss, reappear.pendingSafeDismiss) // escape preserved
        assertEquals(HoldPhase.RECOVERY, reappear.readinessHold!!.phase) // com.a still shielded
        assertEquals(requested.lastForegroundSeq + 1, reappear.lastForegroundSeq) // processed, emits a new state
        // The arrival therefore still works: settings arrives, the shield comes down, and it is exempt.
        val arrival = reappear.observe(other(settings))
        assertNull(arrival.state.readinessHold)
        assertEquals(settings, arrival.state.pendingSafeDismiss!!.arrived)
    }

    @Test
    fun `a same-target re-emission before the arrival does not cancel an app-lock escape`() {
        val shielded = failed().observe(other("com.a")).state
        val token = ReadinessToken(epoch, shielded.generation)
        val requested = shielded.requestSafeDismiss(token, SafeDestination.APP_LOCK)
        val reappear = requested.observe(other("com.a")).state // com.a re-emits before the arrival
        assertEquals(requested.pendingSafeDismiss, reappear.pendingSafeDismiss) // escape not cancelled
        assertEquals(requested.lastForegroundSeq + 1, reappear.lastForegroundSeq) // processed, emits a new state
        // The App Lock arrival signal still tears the shield down (without the fix it would be stuck).
        val issued = reappear.appLockArrived(requested.attempt).state
        assertNull(issued.readinessHold)
        assertNull(issued.pendingSafeDismiss)
    }

    @Test
    fun `a same-origin re-emission over a recovery-escalated loading escape re-presents without invalidating`() {
        // Loading: Checking(com.a) escalates to Recovery(com.a) via T_ready, still under Loading.
        val checking = loading().observe(other("com.a")).state
        val timer = checking.readinessHold!!.timer!!
        val recovery = LockEngineReducer.reduce(checking, EngineEvent.TimerFired(timer)).state
        assertEquals(HoldPhase.RECOVERY, recovery.readinessHold!!.phase)
        val token = ReadinessToken(epoch, recovery.generation)
        val requested = recovery.requestSafeDismiss(token, SafeDestination.OVERLAY_SETTINGS, setOf(settings))
        // com.a re-emits under Loading (a fresh eval would re-open a Checking generation): must neither
        // throw the origin invariant nor morph the surface, and must keep the escape armed.
        val reappear = requested.observe(other("com.a")).state
        assertEquals(requested.pendingSafeDismiss, reappear.pendingSafeDismiss)
        assertEquals(HoldPhase.RECOVERY, reappear.readinessHold!!.phase)
        assertEquals(recovery.generation, reappear.generation) // no new generation opened
        assertEquals(requested.lastForegroundSeq + 1, reappear.lastForegroundSeq) // processed, emits a new state
        val arrival = reappear.observe(other(settings))
        assertNull(arrival.state.readinessHold)
    }

    @Test
    fun `a same-origin re-emission that now evaluates allow during a lock escape re-presents, not tears down`() {
        val locked = initial().observe(other("com.a")).state // Lock(com.a) under Ready
        val token = RequestToken(epoch, locked.activeRequest!!.id)
        val requested = locked.requestSafeDismiss(token, SafeDestination.HOME)
        // com.a re-emits now with a valid session (a fresh eval would ALLOW and dismiss the lock). During the
        // escape it is an idempotent re-presentation, so the lock and escape are preserved.
        val reappear = requested.observe(other("com.a", session = true)).state
        assertEquals(requested.activeRequest, reappear.activeRequest) // lock preserved
        assertEquals(requested.pendingSafeDismiss, reappear.pendingSafeDismiss) // escape preserved
        assertEquals(requested.lastForegroundSeq + 1, reappear.lastForegroundSeq) // processed, emits a new state
        val arrival = reappear.observe(Foreground.Home) // HOME actually arrives: now the lock comes down
        assertNull(arrival.state.activeRequest)
        assertTrue(arrival.effects.contains(Effect.DismissSurface))
    }

    @Test
    fun `the HOME arrival relocks the app being left as the foreground moves to Home`() {
        val shielded = failed().observe(other("com.a")).state
        val token = ReadinessToken(epoch, shielded.generation)
        val issued = shielded.requestSafeDismiss(token, SafeDestination.HOME).let { it.navIssued(it.attempt).state }
        val reduction = issued.observe(Foreground.Home)
        assertTrue(reduction.effects.contains(Effect.NoteAppLeft("com.a"))) // session ended for the app left
        assertEquals(RealForeground.Home, reduction.state.lastForeground) // foreground is now Home
        // A later Home observation must not emit a duplicate.
        val afterHome = reduction.state.observe(Foreground.Home)
        assertTrue(afterHome.effects.none { it is Effect.NoteAppLeft })
    }

    @Test
    fun `an arrival before the follow-up emits NoteAppLeft once and the stale follow-up does not duplicate it`() {
        val requested = escapeRequested()
        val arrival = requested.observe(other(settings)) // ordering B: settings arrives first
        assertEquals(1, arrival.effects.count { it == Effect.NoteAppLeft("com.a") })
        val issued = arrival.state.navIssued(requested.attempt)
        assertTrue(issued.effects.none { it is Effect.NoteAppLeft }) // stale follow-up: no duplicate
    }

    @Test
    fun `repeated settings window events stay exempt`() {
        val requested = escapeRequested()
        val firstArrival = requested.observe(other(settings)).state
        // Accessibility emits a fresh window-state event per settings sub-screen; each must stay allowed.
        val secondArrival = firstArrival.observe(other(settings))
        assertNull(secondArrival.state.readinessHold)
        assertEquals(settings, secondArrival.state.pendingSafeDismiss!!.arrived)
        assertTrue(secondArrival.effects.none { it is Effect.Present })
    }

    @Test
    fun `the original target re-emitting before settings stays shielded, and the escape survives`() {
        val requested = escapeRequested()
        val issued = requested.navIssued(requested.attempt).state // shield still up (issued != arrived)
        // The original protected target re-emits before Settings appears. It is the origin, so it is an
        // idempotent re-presentation of its own shield, and is never allowed through.
        val reappear = issued.observe(other("com.a"))
        assertEquals(HoldPhase.RECOVERY, reappear.state.readinessHold!!.phase)
        assertEquals("com.a", reappear.state.readinessHold!!.target)
        assertTrue(reappear.effects.none { it is Effect.DismissSurface })
        // The escape stays armed, so Settings is still allowed through when it does arrive.
        val arrival = reappear.state.observe(other(settings))
        assertNull(arrival.state.readinessHold)
        assertEquals(settings, arrival.state.pendingSafeDismiss!!.arrived)
    }

    @Test
    fun `an unrelated app arriving before settings stays shielded`() {
        val requested = escapeRequested()
        val issued = requested.navIssued(requested.attempt).state
        val intruder = issued.observe(other("com.evil")) // not the approved destination
        assertNull(intruder.state.activeRequest)
        assertEquals(HoldPhase.RECOVERY, intruder.state.readinessHold!!.phase)
        assertEquals("com.evil", intruder.state.readinessHold!!.target)
    }

    @Test
    fun `a transient window before arrival does not end the exemption`() {
        val requested = escapeRequested()
        val afterTransient = requested.observe(Foreground.Transient).state
        assertEquals(requested.pendingSafeDismiss, afterTransient.pendingSafeDismiss) // preserved
        val arrival = afterTransient.observe(other(settings)) // settings still allowed afterwards
        assertNull(arrival.state.readinessHold)
        assertEquals(settings, arrival.state.pendingSafeDismiss!!.arrived)
    }

    @Test
    fun `a different app after settings ends the exemption and is shielded`() {
        val requested = escapeRequested()
        val onSettings = requested.observe(other(settings)).state
        val switched = onSettings.observe(other("com.evil")) // leaving settings ends the escape
        assertNull(switched.state.pendingSafeDismiss)
        assertEquals(HoldPhase.RECOVERY, switched.state.readinessHold!!.phase)
        assertEquals("com.evil", switched.state.readinessHold!!.target)
    }

    @Test
    fun `a failed policy re-emission while the issued escape still stands keeps its shield without a flash`() {
        val requested = escapeRequested()
        val issued = requested.navIssued(requested.attempt).state // shield still up (issued != arrived)
        assertEquals(HoldPhase.RECOVERY, issued.readinessHold!!.phase)
        // Policy re-emits Failed before settings arrives. The still-guarding shield stays, without re-presenting.
        val reduction = LockEngineReducer.reduce(issued, EngineEvent.PolicyStateChanged(PolicyState.Failed("again")))
        assertEquals(HoldPhase.RECOVERY, reduction.state.readinessHold!!.phase) // shield preserved
        assertEquals(requested.pendingSafeDismiss?.destination, reduction.state.pendingSafeDismiss?.destination)
        assertTrue(reduction.effects.none { it is Effect.Present }) // zero flash
    }

    @Test
    fun `a failed policy re-emission after settings arrives does not re-raise a shield over the abandoned target`() {
        val requested = escapeRequested()
        val onSettings = requested.observe(other(settings)).state // settings arrived: surface gone, exemption armed
        assertNull(onSettings.readinessHold)
        // Policy re-emits Failed while the arrived exemption stands. The abandoned target must not be re-shielded.
        val reduction =
            LockEngineReducer.reduce(onSettings, EngineEvent.PolicyStateChanged(PolicyState.Failed("again")))
        assertNull(reduction.state.readinessHold)
        assertNull(reduction.state.activeRequest)
        assertTrue(reduction.effects.none { it is Effect.Present }) // zero flash
        assertEquals(settings, reduction.state.pendingSafeDismiss!!.arrived)
    }

    @Test
    fun `app-lock arrival then a failed re-emission raises no shield over the abandoned target`() {
        val shielded = failed().observe(other("com.a")).state // Recovery(com.a)
        val token = ReadinessToken(epoch, shielded.generation)
        val issued = shielded.requestSafeDismiss(token, SafeDestination.APP_LOCK).let {
            it.navIssued(it.attempt).state
        }
        val attempt = (issued.guardState as GuardState.LeavingFor).attempt
        val arrived = issued.appLockArrived(attempt).state // App Lock foregrounds: the escape completes
        assertNull(arrived.readinessHold)
        assertNull(arrived.pendingSafeDismiss) // APP_LOCK keeps no arrival exemption
        assertNull(arrived.lastForeground) // the abandoned target's foreground was cleared
        val reduction = LockEngineReducer.reduce(arrived, EngineEvent.PolicyStateChanged(PolicyState.Failed("again")))
        assertNull(reduction.state.readinessHold) // no shield rebuilt over com.a
        assertTrue(reduction.effects.none { it is Effect.Present })
    }

    @Test
    fun `a failed re-emission during a home escape keeps the shield until home is observed`() {
        val shielded = failed().observe(other("com.a")).state
        val token = ReadinessToken(epoch, shielded.generation)
        val issued = shielded.requestSafeDismiss(token, SafeDestination.HOME).let { it.navIssued(it.attempt).state }
        // Failed re-emits before Home is observed. The escape's shield stays, without a flash.
        val reduction = LockEngineReducer.reduce(issued, EngineEvent.PolicyStateChanged(PolicyState.Failed("again")))
        assertEquals(HoldPhase.RECOVERY, reduction.state.readinessHold!!.phase)
        assertTrue(reduction.effects.none { it is Effect.Present })
        // Home then arrives and completes the escape cleanly.
        val home = reduction.state.observe(Foreground.Home)
        assertNull(home.state.readinessHold)
        assertTrue(home.effects.contains(Effect.DismissSurface))
    }

    @Test
    fun `going home ends the exemption`() {
        val requested = escapeRequested()
        val reduction = requested.observe(Foreground.Home)
        assertNull(reduction.state.pendingSafeDismiss)
    }

    @Test
    fun `screen off ends the exemption`() {
        val requested = escapeRequested()
        val reduction = LockEngineReducer.reduce(requested, EngineEvent.ScreenOff)
        assertNull(reduction.state.pendingSafeDismiss)
    }

    @Test
    fun `a ready policy before arrival ends the exemption`() {
        val requested = escapeRequested()
        val reduction = LockEngineReducer.reduce(
            requested,
            EngineEvent.PolicyStateChanged(PolicyState.Ready(emptySet())),
        )
        assertNull(reduction.state.pendingSafeDismiss)
    }

    @Test
    fun `ready re-locks an arrived destination that is now protected (fail-secure)`() {
        val requested = escapeRequested()
        val onSettings = requested.observe(other(settings)).state // settings arrived and was allowed
        assertNull(onSettings.readinessHold)
        // Policy becomes Ready with the settings package itself protected: it must lock, not stay exposed.
        val reduction = LockEngineReducer.reduce(
            onSettings,
            EngineEvent.PolicyStateChanged(PolicyState.Ready(setOf(settings))),
        )
        val request = reduction.state.activeRequest!!
        assertEquals(settings, request.target)
        assertNull(reduction.state.pendingSafeDismiss)
        assertTrue(reduction.effects.contains(Effect.Present(Surface.Lock(settings, request.id))))
        assertTrue(reduction.effects.contains(Effect.Log(AuditEvent.LOCK_TRIGGERED, settings)))
    }

    @Test
    fun `ready leaves an arrived destination allowed when it is not protected`() {
        val requested = escapeRequested()
        val onSettings = requested.observe(other(settings)).state
        val reduction = LockEngineReducer.reduce(
            onSettings,
            EngineEvent.PolicyStateChanged(PolicyState.Ready(setOf("com.a"))),
        )
        assertNull(reduction.state.activeRequest)
        assertNull(reduction.state.readinessHold)
        assertNull(reduction.state.pendingSafeDismiss)
        assertTrue(reduction.effects.none { it is Effect.Present })
    }

    @Test
    fun `a ready re-emission during a lock's home escape preserves the escape`() {
        val locked = initial().observe(other("com.a")).state // Lock(com.a) under Ready
        val token = RequestToken(epoch, locked.activeRequest!!.id)
        val requested = locked.requestSafeDismiss(token, SafeDestination.HOME)
        // A harmless Ready re-emission must not drop the in-flight escape over the still-live lock.
        val afterReady = LockEngineReducer.reduce(
            requested,
            EngineEvent.PolicyStateChanged(PolicyState.Ready(protectedPackages)),
        ).state
        assertEquals(requested.activeRequest, afterReady.activeRequest) // lock preserved
        assertEquals(requested.pendingSafeDismiss, afterReady.pendingSafeDismiss) // escape preserved
        // The HOME arrival then dismisses the surface as intended (not stranded).
        val home = afterReady.observe(Foreground.Home)
        assertNull(home.state.activeRequest)
        assertTrue(home.effects.contains(Effect.DismissSurface))
    }

    // ---- Round-9 review fixes -----------------------------------------------------------------

    @Test
    fun `when the guarded origin is itself the sole approved destination, a same-package event never arrives`() {
        // The guarded app IS Settings, and the OVERLAY_SETTINGS escape targets the grant screen in that same
        // package. So the origin equals the sole approved package. A same-package observation is a conservative
        // origin re-present, never an arrival, because the reducer cannot prove it is the destination. The escape
        // can only end on the timeout backstop. It never tears the shield down early on this ambiguous event.
        val shielded = failed().observe(other(settings)).state // Recovery(settings)
        val token = ReadinessToken(epoch, shielded.generation)
        val requested = shielded.requestSafeDismiss(token, SafeDestination.OVERLAY_SETTINGS, setOf(settings))
        val issued = requested.navIssued(requested.attempt).state // issued, but same-package cannot confirm arrival
        val reappear = issued.observe(other(settings)) // re-presents the shield, never counted as an arrival
        assertTrue(reappear.effects.none { it is Effect.DismissSurface }) // shield not torn down
        assertEquals(HoldPhase.RECOVERY, reappear.state.readinessHold!!.phase) // shield preserved
        assertEquals(settings, reappear.state.readinessHold!!.target)
        assertEquals(SafeDestination.OVERLAY_SETTINGS, reappear.state.pendingSafeDismiss!!.destination) // still armed
        assertTrue(reappear.state.pendingSafeDismiss!!.navigationIssued)
        // The timeout is the only way out. It reverts the escape to its shield.
        val timedOut = reappear.state.navTimedOut(requested.attempt)
        assertNull(timedOut.state.pendingSafeDismiss)
        assertEquals(HoldPhase.RECOVERY, timedOut.state.readinessHold!!.phase)
    }

    @Test
    fun `after one approved destination arrives, switching to a different approved package ends the escape`() {
        val settings2 = "com.android.settings.overlay"
        val shielded = failed().observe(other("com.a")).state
        val token = ReadinessToken(epoch, shielded.generation)
        val requested =
            shielded.requestSafeDismiss(token, SafeDestination.OVERLAY_SETTINGS, setOf(settings, settings2))
        val issued = requested.navIssued(requested.attempt).state
        val arrivedFirst = issued.observe(other(settings)).state // A arrives, latched as arrived
        assertEquals(settings, arrivedFirst.pendingSafeDismiss!!.arrived)
        // Switching to the other approved package is a real navigation, not a second arrival: escape ends.
        val switched = arrivedFirst.observe(other(settings2))
        assertNull(switched.state.pendingSafeDismiss) // exemption cleared, escape over
        assertEquals(HoldPhase.RECOVERY, switched.state.readinessHold!!.phase) // settings2 shielded, not exempt
        assertEquals(settings2, switched.state.readinessHold!!.target)
    }

    @Test
    fun `ready preserves an app-lock readiness escape so its navigation is not stranded`() {
        val shielded = failed().observe(other("com.a")).state // Recovery(com.a)
        val token = ReadinessToken(epoch, shielded.generation)
        val requested = shielded.requestSafeDismiss(token, SafeDestination.APP_LOCK)
        // Ready arrives with com.a still protected: the escape must survive, not relock com.a and strand it.
        val afterReady = LockEngineReducer.reduce(
            requested,
            EngineEvent.PolicyStateChanged(PolicyState.Ready(protectedPackages)),
        ).state
        assertEquals(requested.pendingSafeDismiss, afterReady.pendingSafeDismiss) // escape preserved
        assertEquals(requested.generation, afterReady.generation) // token not invalidated
        assertNull(afterReady.activeRequest) // no new lock raised over com.a
        // The App Lock arrival then completes the escape instead of being stranded.
        val arrived = afterReady.appLockArrived(requested.attempt).state
        assertNull(arrived.readinessHold)
        assertNull(arrived.pendingSafeDismiss)
    }

    @Test
    fun `ready preserves a home readiness escape so its navigation is not stranded`() {
        val shielded = failed().observe(other("com.a")).state
        val token = ReadinessToken(epoch, shielded.generation)
        val requested = shielded.requestSafeDismiss(token, SafeDestination.HOME)
        val afterReady = LockEngineReducer.reduce(
            requested,
            EngineEvent.PolicyStateChanged(PolicyState.Ready(protectedPackages)),
        ).state
        assertEquals(requested.pendingSafeDismiss, afterReady.pendingSafeDismiss) // escape preserved
        val home = afterReady.observe(Foreground.Home)
        assertNull(home.state.readinessHold)
    }

    @Test
    fun `a second safe-dismiss request while one is already in flight is dropped, not allowed to replace it`() {
        val shielded = failed().observe(other("com.a")).state // Recovery(com.a)
        val token = ReadinessToken(epoch, shielded.generation)
        val requested = shielded.requestSafeDismiss(token, SafeDestination.HOME) // escape 1
        // A second request from the same still-live surface carries the same token: it must not replace escape 1.
        val second =
            LockEngineReducer.reduce(requested, EngineEvent.SafeDismissRequested(token, SafeDestination.APP_LOCK))
        assertEquals(requested, second.state) // unchanged: escape 1's HOME destination stands
        assertTrue(second.effects.isEmpty()) // no second navigation issued
        assertEquals(SafeDestination.HOME, (second.state.guardState as GuardState.LeavingFor).destination)
    }

    @Test
    fun `failed escalates an in-flight checking escape to recovery, cancelling its timer and keeping the escape`() {
        val checking = loading().observe(other("com.a")).state // Checking(com.a)
        val timer = checking.readinessHold!!.timer!!
        val token = ReadinessToken(epoch, checking.generation)
        val requested = checking.requestSafeDismiss(token, SafeDestination.OVERLAY_SETTINGS, setOf(settings))
        val reduction =
            LockEngineReducer.reduce(requested, EngineEvent.PolicyStateChanged(PolicyState.Failed("boom")))
        assertEquals(HoldPhase.RECOVERY, reduction.state.readinessHold!!.phase) // escalated now, not at T_ready
        assertEquals(checking.generation, reduction.state.generation) // generation unchanged: token stays valid
        assertEquals(requested.pendingSafeDismiss, reduction.state.pendingSafeDismiss) // escape preserved
        assertTrue(reduction.effects.contains(Effect.CancelTimer(timer))) // its T_ready timer is cancelled
        assertTrue(reduction.effects.contains(Effect.Present(Surface.Recovery("com.a"))))
        // The escape token is still valid, so the settings arrival still tears the surface down.
        val arrival = reduction.state.observe(other(settings))
        assertNull(arrival.state.readinessHold)
    }

    // ---- Round-10 review fixes ----------------------------------------------------------------

    @Test
    fun `a failed navigation reverts the escape to its guard so the control can be retried`() {
        val shielded = failed().observe(other("com.a")).state // Recovery(com.a)
        val token = ReadinessToken(epoch, shielded.generation)
        val requested = shielded.requestSafeDismiss(token, SafeDestination.OVERLAY_SETTINGS, setOf(settings))
        // The interpreter could not launch the destination: navigation-failed reverts LeavingFor -> Guarding,
        // reconciled with the still-Failed policy (the Recovery shield is re-presented, never dismissed).
        val failedNav = requested.navFailed(requested.attempt)
        assertNull(failedNav.state.pendingSafeDismiss) // no longer in flight
        assertEquals(HoldPhase.RECOVERY, failedNav.state.readinessHold!!.phase) // back to a plain guard
        assertEquals("com.a", failedNav.state.readinessHold!!.target)
        assertTrue(failedNav.effects.none { it is Effect.DismissSurface }) // the surface never came down
        // A retry from the same still-live surface now succeeds (it was dropped while LeavingFor stood), and
        // it mints a fresh attempt id so an old attempt's follow-up cannot act on it.
        val retry = LockEngineReducer.reduce(
            failedNav.state,
            EngineEvent.SafeDismissRequested(token, SafeDestination.OVERLAY_SETTINGS, setOf(settings)),
        )
        val retryAttempt = retry.state.attempt
        assertEquals(SafeDestination.OVERLAY_SETTINGS, (retry.state.guardState as GuardState.LeavingFor).destination)
        assertEquals(AttemptId(1), retryAttempt.id) // distinct from attempt 0
        assertEquals(listOf(Effect.NavigateSafely(SafeDestination.OVERLAY_SETTINGS, retryAttempt)), retry.effects)
    }

    @Test
    fun `a navigation-failed follow-up whose token differs from the recorded escape is dropped`() {
        val shielded = failed().observe(other("com.a")).state
        val token = ReadinessToken(epoch, shielded.generation)
        val requested = shielded.requestSafeDismiss(token, SafeDestination.OVERLAY_SETTINGS, setOf(settings))
        val wrong = AttemptToken(epoch, AttemptId(999))
        val reduction = requested.navFailed(wrong)
        assertEquals(requested, reduction.state) // the escape is still in flight, unreverted
        assertTrue(reduction.effects.isEmpty())
    }

    @Test
    fun `a checking escape preserved through Ready does not become Recovery when its stale timer fires`() {
        val checking = loading().observe(other("com.a")).state // Checking(com.a), T_ready scheduled
        val timer = checking.readinessHold!!.timer!!
        val token = ReadinessToken(epoch, checking.generation)
        val requested = checking.requestSafeDismiss(token, SafeDestination.APP_LOCK)
        // Ready preserves the HOME/APP_LOCK escape; the nested Checking surface is frozen for the navigation.
        val afterReady = LockEngineReducer.reduce(
            requested,
            EngineEvent.PolicyStateChanged(PolicyState.Ready(protectedPackages)),
        ).state
        assertEquals(HoldPhase.CHECKING, afterReady.readinessHold!!.phase) // still checking, not escalated
        // The now-stale T_ready timer fires under Ready: it must not escalate the frozen surface to Recovery.
        val fired = LockEngineReducer.reduce(afterReady, EngineEvent.TimerFired(timer))
        assertEquals(afterReady, fired.state) // no state change
        assertTrue(fired.effects.none { it is Effect.Present }) // no Recovery surface presented
        // The App Lock arrival still completes the escape afterwards.
        val arrived = fired.state.appLockArrived(requested.attempt).state
        assertNull(arrived.readinessHold)
        assertNull(arrived.pendingSafeDismiss)
    }

    // ---- Round-11 review fixes ----------------------------------------------------------------

    @Test
    fun `navigation-failed under Ready locks a now-protected restored shield instead of leaving it frozen`() {
        val checking = loading().observe(other("com.a")).state // Checking(com.a) under Loading
        val token = ReadinessToken(epoch, checking.generation)
        val requested = checking.requestSafeDismiss(token, SafeDestination.APP_LOCK)
        // Ready (com.a protected) arrives while the escape is preserved; then the launch fails.
        val afterReady = LockEngineReducer.reduce(
            requested,
            EngineEvent.PolicyStateChanged(PolicyState.Ready(protectedPackages)),
        ).state
        val reduction = afterReady.navFailed(requested.attempt)
        // The restored Checking shield is reconciled with Ready: com.a is protected, so it locks fail-secure
        // rather than standing frozen as a Checking surface (whose timer is inert under Ready).
        val request = reduction.state.activeRequest!!
        assertEquals("com.a", request.target)
        assertNull(reduction.state.readinessHold)
        assertNull(reduction.state.pendingSafeDismiss)
        assertTrue(reduction.effects.contains(Effect.Present(Surface.Lock("com.a", request.id))))
    }

    @Test
    fun `navigation-failed under Ready dismisses a now-unprotected restored shield`() {
        val recovery = failed().observe(other("com.a")).state // Recovery(com.a) under Failed
        val token = ReadinessToken(epoch, recovery.generation)
        val requested = recovery.requestSafeDismiss(token, SafeDestination.HOME)
        // Ready arrives with com.a NOT protected while the escape is preserved; then the launch fails.
        val afterReady = LockEngineReducer.reduce(
            requested,
            EngineEvent.PolicyStateChanged(PolicyState.Ready(setOf("com.other"))),
        ).state
        val reduction = afterReady.navFailed(requested.attempt)
        // com.a is unprotected under Ready, so the restored shield is dismissed, not left frozen.
        assertNull(reduction.state.readinessHold)
        assertNull(reduction.state.activeRequest)
        assertNull(reduction.state.pendingSafeDismiss)
        assertTrue(reduction.effects.contains(Effect.DismissSurface))
    }

    @Test
    fun `after a failed attempt is retried, a late follow-up from the first attempt cannot act on the second`() {
        val shielded = failed().observe(other("com.a")).state // Recovery(com.a)
        val token = ReadinessToken(epoch, shielded.generation)
        val first = shielded.requestSafeDismiss(token, SafeDestination.HOME) // attempt 0
        val attempt1 = first.attempt
        val reverted = first.navFailed(attempt1).state
        // Retry over the same, unchanged surface: same surface token, but a fresh attempt id.
        val second = reverted.requestSafeDismiss(token, SafeDestination.OVERLAY_SETTINGS, setOf(settings)) // attempt 1
        val attempt2 = second.attempt
        assertNotEquals(attempt1, attempt2)
        // A delayed or duplicated success follow-up from attempt 1 must not complete attempt 2.
        val lateIssued = second.navIssued(attempt1)
        assertEquals(second, lateIssued.state) // attempt 2 untouched
        assertTrue(lateIssued.effects.isEmpty())
        // Nor may a delayed failure follow-up from attempt 1 revert attempt 2.
        val lateFailed = second.navFailed(attempt1)
        assertEquals(second, lateFailed.state)
        assertTrue(lateFailed.effects.isEmpty())
        // Attempt 2's own follow-up still completes it (hands off to the arrival exemption).
        val issued = second.navIssued(attempt2).state
        assertEquals(SafeDestination.OVERLAY_SETTINGS, issued.pendingSafeDismiss!!.destination)
    }

    // ---- F1: arrival confirmation, shield-only escapes, and the arrival timeout ----------------

    /** A recovery shield over "com.a" with an in-flight APP_LOCK escape (a ReadinessToken; shield-only). */
    private fun appLockEscape(): EngineState {
        val shielded = failed().observe(other("com.a")).state // Recovery(com.a)
        val token = ReadinessToken(epoch, shielded.generation)
        return shielded.requestSafeDismiss(token, SafeDestination.APP_LOCK)
    }

    @Test
    fun `an app-lock escape completes on the app-lock foreground signal after the issue`() {
        val issued = appLockEscape().let { it.navIssued(it.attempt).state }
        assertEquals(HoldPhase.RECOVERY, issued.readinessHold!!.phase) // still up until App Lock actually arrives
        val arrived = issued.appLockArrived((issued.guardState as GuardState.LeavingFor).attempt)
        assertNull(arrived.state.readinessHold)
        assertNull(arrived.state.pendingSafeDismiss) // APP_LOCK keeps no arrival exemption
        assertNull(arrived.state.lastForeground)
        assertTrue(arrived.effects.contains(Effect.NoteAppLeft("com.a")))
        assertTrue(arrived.effects.contains(Effect.DismissSurface))
    }

    @Test
    fun `an app-lock escape completes on the app-lock foreground signal before the issue (stale queued)`() {
        val requested = appLockEscape() // navigationIssued still false
        val arrived = requested.appLockArrived(requested.attempt) // arrival delivered before the issued echo
        assertNull(arrived.state.readinessHold) // completes in either navigationIssued state
        // The now-stale issued echo matches no live escape and is dropped.
        val late = arrived.state.navIssued(requested.attempt)
        assertEquals(arrived.state, late.state)
        assertTrue(late.effects.isEmpty())
    }

    @Test
    fun `Foreground Own never completes an app-lock escape`() {
        val requested = appLockEscape()
        val afterOwn = requested.observe(Foreground.Own)
        assertEquals(requested, afterOwn.state) // Own is a pure no-op; it cannot be arrival evidence
        assertTrue(afterOwn.effects.isEmpty())
    }

    @Test
    fun `a delayed biometric-host Own across a surface change does not complete a later app-lock escape`() {
        val escape1 = appLockEscape() // shield 1's APP_LOCK escape over Recovery(com.a)
        val shield2 = escape1.observe(other("com.b")).state // com.b supersedes: escape 1 gone, Recovery(com.b)
        assertNull(shield2.pendingSafeDismiss)
        val token2 = ReadinessToken(epoch, shield2.generation)
        val escape2 = shield2.requestSafeDismiss(token2, SafeDestination.APP_LOCK) // shield 2's escape
        // A delayed Foreground.Own drains (App Lock's biometric host from escape 1's era). It must not count as
        // escape 2's arrival. Own never completes an escape, so escape 2 stays in flight.
        val afterOwn = escape2.observe(Foreground.Own)
        assertEquals(escape2.pendingSafeDismiss, afterOwn.state.pendingSafeDismiss) // escape 2 intact
        assertEquals("com.b", afterOwn.state.readinessHold!!.target)
    }

    @Test
    fun `a delayed app-lock foreground signal from a timed-out attempt does not complete a retry`() {
        // attempt 0 -> timeout -> attempt 1. A delayed arrival stamped with attempt 0 must not dismiss attempt 1's
        // shield. Without the attempt key it would, because both are live APP_LOCK escapes over the same shield.
        val shielded = failed().observe(other("com.a")).state // Recovery(com.a)
        val token = ReadinessToken(epoch, shielded.generation)
        val first = shielded.requestSafeDismiss(token, SafeDestination.APP_LOCK) // attempt 0
        val staleAttempt = first.attempt
        val reverted = first.navTimedOut(staleAttempt).state // attempt 0 times out; the shield is restored
        val second = reverted.requestSafeDismiss(token, SafeDestination.APP_LOCK) // attempt 1, a fresh attempt
        assertNotEquals(staleAttempt, second.attempt)
        // The delayed signal from attempt 0 drains now. It carries attempt 0, not attempt 1, so it is dropped.
        val late = second.appLockArrived(staleAttempt)
        assertEquals(second, late.state) // attempt 1's shield stands, not torn down
        assertTrue(late.effects.isEmpty())
        // Attempt 1's own signal still completes it.
        val arrived = second.appLockArrived(second.attempt)
        assertNull(arrived.state.readinessHold)
    }

    @Test
    fun `a delayed app-lock foreground signal across a supersession does not complete a later escape`() {
        val escape1 = appLockEscape() // shield 1 (com.a), attempt 0
        val staleAttempt = escape1.attempt
        val shield2 = escape1.observe(other("com.b")).state // com.b supersedes: escape 1 gone, Recovery(com.b)
        val token2 = ReadinessToken(epoch, shield2.generation)
        val escape2 = shield2.requestSafeDismiss(token2, SafeDestination.APP_LOCK) // shield 2's escape, attempt 1
        // A delayed signal stamped with escape 1's attempt drains: escape 2's attempt differs, so it is dropped.
        val late = escape2.appLockArrived(staleAttempt)
        assertEquals(escape2, late.state) // escape 2 intact
        assertEquals("com.b", late.state.readinessHold!!.target)
    }

    @Test
    fun `an app-lock request from a lock surface is rejected (shield-only)`() {
        val locked = initial().observe(other("com.a")).state // a Lock (RequestToken) under Ready
        val token = RequestToken(epoch, locked.activeRequest!!.id)
        val reduction =
            LockEngineReducer.reduce(locked, EngineEvent.SafeDismissRequested(token, SafeDestination.APP_LOCK))
        assertEquals(locked, reduction.state) // the lock stands; APP_LOCK cannot arm from a lock
        assertTrue(reduction.effects.isEmpty())
    }

    @Test
    fun `an app-lock foreground signal with no escape in flight is dropped`() {
        val recovery = failed().observe(other("com.a")).state // a plain Recovery shield, no escape
        val reduction = recovery.appLockArrived(AttemptToken(epoch, AttemptId(0)))
        assertEquals(recovery, reduction.state) // no live escape: the signal tears nothing down
        assertTrue(reduction.effects.isEmpty())
    }

    @Test
    fun `settings arrival on a different approved package completes the escape`() {
        // This is the normal path. The guarded origin (com.a) is not an approved package, so a different approved
        // package (the resolved settings screen) is an unambiguous arrival.
        val requested = escapeRequested() // approved = {settings}, origin = com.a
        val issued = requested.navIssued(requested.attempt).state
        val arrival = issued.observe(other(settings))
        assertNull(arrival.state.readinessHold)
        assertEquals(settings, arrival.state.pendingSafeDismiss!!.arrived)
        assertTrue(arrival.effects.contains(Effect.DismissSurface))
    }

    @Test
    fun `an aborted overlay-settings launch with origin re-emission after the issue does not complete`() {
        // The launch returned success but silently aborted (a background START_ABORTED never foregrounds). The
        // origin re-emits (com.a, a protected app that is not the approved destination). It re-presents its own
        // shield, and is never counted as the arrival, so the escape stays until the timeout backstops it.
        val requested = escapeRequested() // approved = {settings}, origin = com.a
        val issued = requested.navIssued(requested.attempt).state
        val reappear = issued.observe(other("com.a"))
        assertTrue(reappear.effects.none { it is Effect.DismissSurface }) // origin re-present, not an arrival
        assertEquals(HoldPhase.RECOVERY, reappear.state.readinessHold!!.phase)
        assertEquals("com.a", reappear.state.readinessHold!!.target)
        assertTrue(reappear.state.pendingSafeDismiss!!.navigationIssued) // still in flight
        // The timeout is the backstop. It reverts the escape to its shield.
        val timedOut = reappear.state.navTimedOut(requested.attempt)
        assertNull(timedOut.state.pendingSafeDismiss)
        assertEquals(HoldPhase.RECOVERY, timedOut.state.readinessHold!!.phase)
    }

    @Test
    fun `the arrival timeout reverts an issued escape to its guard, never tearing the surface down`() {
        val issued = escapeRequested().let { it.navIssued(it.attempt).state } // Recovery(com.a), issued
        val timedOut = issued.navTimedOut((issued.guardState as GuardState.LeavingFor).attempt)
        assertNull(timedOut.state.pendingSafeDismiss) // no longer in flight
        assertEquals(HoldPhase.RECOVERY, timedOut.state.readinessHold!!.phase) // back to a plain guard
        assertEquals("com.a", timedOut.state.readinessHold!!.target)
        assertTrue(timedOut.effects.none { it is Effect.DismissSurface }) // the surface never came down
    }

    @Test
    fun `a late arrival timeout for an already-completed escape is dropped`() {
        val requested = escapeRequested()
        val settingsArrived = requested.observe(other(settings)).state // escape completed (exemption armed)
        val late = settingsArrived.navTimedOut(requested.attempt) // a late timeout for the old attempt
        assertEquals(settingsArrived, late.state) // dropped by the attempt token: nothing changes
        assertTrue(late.effects.isEmpty())
    }

    @Test
    fun `a retry to the same destination after an arrival timeout mints a fresh attempt`() {
        val shielded = failed().observe(other("com.a")).state // Recovery(com.a)
        val token = ReadinessToken(epoch, shielded.generation)
        val first = shielded.requestSafeDismiss(token, SafeDestination.OVERLAY_SETTINGS, setOf(settings)) // attempt 0
        val reverted = first.navTimedOut(first.attempt).state // the arrival timeout reverts it to the shield
        assertNull(reverted.pendingSafeDismiss)
        // A retry over the same, still-live surface succeeds and mints a distinct attempt id.
        val retry = reverted.requestSafeDismiss(token, SafeDestination.OVERLAY_SETTINGS, setOf(settings))
        val retryAttempt = (retry.guardState as GuardState.LeavingFor).attempt
        assertEquals(AttemptId(1), retryAttempt.id) // distinct from attempt 0
        // A late timeout from the first attempt cannot revert the second.
        val lateTimeout = retry.navTimedOut(first.attempt)
        assertEquals(retry, lateTimeout.state)
        assertTrue(lateTimeout.effects.isEmpty())
    }

    // ---- Model invariants: the type cannot encode a state the reducer never produces ----------

    @Test(expected = IllegalArgumentException::class)
    fun `an arrival exemption backed by a lock token is rejected by the model`() {
        // An OVERLAY_SETTINGS exemption may originate only from a readiness shield; a RequestToken-backed
        // one (which could bypass a protected app once Ready) must not be representable.
        ArrivalExemption(RequestToken(epoch, RequestId(0)), "com.a", setOf(settings))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an overlay-settings escape with a lock surface token is rejected by the model`() {
        GuardState.LeavingFor(
            BlockingGuard.Lock("com.a", RequestId(0)),
            RequestToken(epoch, RequestId(0)),
            AttemptToken(epoch, AttemptId(0)),
            SafeDestination.OVERLAY_SETTINGS,
            setOf(settings),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an engine state carrying a foreign-epoch escape surface token is rejected`() {
        EngineState(
            epoch = epoch,
            guardState = GuardState.LeavingFor(
                BlockingGuard.Recovery("com.a"),
                ReadinessToken(Epoch(999), Generation(0)),
                AttemptToken(epoch, AttemptId(0)),
                SafeDestination.OVERLAY_SETTINGS,
                setOf(settings),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an engine state carrying a foreign-epoch attempt token is rejected`() {
        EngineState(
            epoch = epoch,
            guardState = GuardState.LeavingFor(
                BlockingGuard.Recovery("com.a"),
                ReadinessToken(epoch, Generation(0)),
                AttemptToken(Epoch(999), AttemptId(0)),
                SafeDestination.OVERLAY_SETTINGS,
                setOf(settings),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an escape whose surface token does not identify its guarding surface is rejected`() {
        GuardState.LeavingFor(
            BlockingGuard.Lock("com.a", RequestId(0)),
            RequestToken(epoch, RequestId(99)), // wrong id: does not identify the guarding lock
            AttemptToken(epoch, AttemptId(0)),
            SafeDestination.HOME,
            emptySet(),
        )
    }
}
