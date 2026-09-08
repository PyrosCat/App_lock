package com.applock.service.engine

import com.applock.domain.PolicyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7 WP2 Phase 2 (change C2): the pure reducer's token-keyed safe-dismiss handshake
 * ([EngineEvent.SafeDismissRequested] -> [Effect.NavigateSafely] -> exactly one attempt-keyed follow-up,
 * [EngineEvent.SafeDismissNavigationIssued] on success or [EngineEvent.SafeDismissNavigationFailed] on a
 * launch failure). Split out from [LockEngineReducerTest] so each suite stays focused. "com.a" / "com.b"
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

    // ---- Step 2: navigation-issued tears the still-live surface down, but only after a real step 1 ---

    @Test
    fun `the navigation-issued follow-up tears down the lock and advances the generation`() {
        val locked = initial().observe(other("com.a")).state
        assertEquals(Generation(0), locked.generation)
        val token = RequestToken(epoch, locked.activeRequest!!.id)
        val requested = locked.requestSafeDismiss(token, SafeDestination.HOME)
        val reduction = requested.navIssued(requested.attempt)
        assertNull(reduction.state.activeRequest)
        assertEquals(Generation(1), reduction.state.generation) // an invalidating teardown
        assertNull(reduction.state.pendingSafeDismiss) // HOME needs no arrival exemption
        // The app being left is relocked before its foreground is erased.
        assertEquals(listOf(Effect.NoteAppLeft("com.a"), Effect.DismissSurface), reduction.effects)
    }

    @Test
    fun `the follow-up over a checking shield cancels its timer, dismisses, and advances the generation`() {
        val checking = loading().observe(other("com.a")).state
        val checkingTimer = checking.readinessHold!!.timer!!
        assertEquals(Generation(1), checking.generation)
        val token = ReadinessToken(epoch, checking.generation)
        val requested = checking.requestSafeDismiss(token, SafeDestination.HOME)
        val reduction = requested.navIssued(requested.attempt)
        assertNull(reduction.state.readinessHold)
        assertEquals(Generation(2), reduction.state.generation)
        assertEquals(
            listOf(Effect.CancelTimer(checkingTimer), Effect.NoteAppLeft("com.a"), Effect.DismissSurface),
            reduction.effects,
        )
    }

    @Test
    fun `a navigation-issued follow-up with no prior request cannot tear the guard down`() {
        val locked = initial().observe(other("com.a")).state // no SafeDismissRequested was ever issued
        val reduction = locked.navIssued(AttemptToken(epoch, AttemptId(0)))
        assertEquals(locked, reduction.state) // guard intact; a bare follow-up cannot dismiss it
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
        // Step 2 tears the shield down but keeps the record so the arrival can still be exempted.
        val issued = requested.navIssued(requested.attempt).state
        assertNull(issued.readinessHold)
        assertEquals(SafeDestination.OVERLAY_SETTINGS, issued.pendingSafeDismiss!!.destination)
        val arrival = issued.observe(other(settings))
        assertNull(arrival.state.readinessHold) // allowed through, no new shield
        assertNull(arrival.state.activeRequest)
        assertEquals(settings, arrival.state.pendingSafeDismiss!!.arrived)
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
        // com.a re-emits its own accessibility event before step 2: not a supersession, so the escape stays.
        val reappear = requested.observe(other("com.a")).state
        assertEquals(requested.pendingSafeDismiss, reappear.pendingSafeDismiss) // escape preserved
        assertEquals(HoldPhase.RECOVERY, reappear.readinessHold!!.phase) // com.a still shielded
        assertEquals(requested.lastForegroundSeq + 1, reappear.lastForegroundSeq) // processed, emits a new state
        // The follow-up therefore still works: the shield comes down and settings then arrives exempt.
        val issued = reappear.navIssued(requested.attempt).state
        assertNull(issued.readinessHold)
        val arrival = issued.observe(other(settings))
        assertNull(arrival.state.readinessHold)
        assertEquals(settings, arrival.state.pendingSafeDismiss!!.arrived)
    }

    @Test
    fun `a same-target re-emission before the follow-up does not cancel an app-lock escape`() {
        val shielded = failed().observe(other("com.a")).state
        val token = ReadinessToken(epoch, shielded.generation)
        val requested = shielded.requestSafeDismiss(token, SafeDestination.APP_LOCK)
        val reappear = requested.observe(other("com.a")).state // com.a re-emits before step 2
        assertEquals(requested.pendingSafeDismiss, reappear.pendingSafeDismiss) // escape not cancelled
        assertEquals(requested.lastForegroundSeq + 1, reappear.lastForegroundSeq) // processed, emits a new state
        // The follow-up still tears the shield down (without the fix it would be rejected, leaving it stuck).
        val issued = reappear.navIssued(requested.attempt).state
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
        val issued = reappear.navIssued(requested.attempt).state
        assertNull(issued.readinessHold)
    }

    @Test
    fun `a same-origin re-emission that now evaluates allow during a lock escape re-presents, not tears down`() {
        val locked = initial().observe(other("com.a")).state // Lock(com.a) under Ready
        val token = RequestToken(epoch, locked.activeRequest!!.id)
        val requested = locked.requestSafeDismiss(token, SafeDestination.HOME)
        // com.a re-emits now reporting a valid session (a fresh eval would ALLOW and dismiss the lock):
        // during the handshake it is an idempotent re-presentation, so the lock and escape are preserved.
        val reappear = requested.observe(other("com.a", session = true)).state
        assertEquals(requested.activeRequest, reappear.activeRequest) // lock preserved
        assertEquals(requested.pendingSafeDismiss, reappear.pendingSafeDismiss) // escape preserved
        assertEquals(requested.lastForegroundSeq + 1, reappear.lastForegroundSeq) // processed, emits a new state
        val issued = reappear.navIssued(requested.attempt)
        assertNull(issued.state.activeRequest)
        assertTrue(issued.effects.contains(Effect.DismissSurface))
    }

    @Test
    fun `the follow-up relocks the app being left before clearing the foreground`() {
        val shielded = failed().observe(other("com.a")).state
        val token = ReadinessToken(epoch, shielded.generation)
        val requested = shielded.requestSafeDismiss(token, SafeDestination.HOME)
        val reduction = requested.navIssued(requested.attempt)
        assertTrue(reduction.effects.contains(Effect.NoteAppLeft("com.a"))) // session ended for the app left
        assertNull(reduction.state.lastForeground)
        // The later Home observation must not emit a duplicate (the foreground was already cleared).
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
        val issued = requested.navIssued(requested.attempt).state
        assertNull(issued.readinessHold)
        // The original protected target re-emits before Settings appears: it is NOT the approved
        // destination, so it must be re-shielded, never allowed through.
        val reappear = issued.observe(other("com.a"))
        assertEquals(HoldPhase.RECOVERY, reappear.state.readinessHold!!.phase)
        assertEquals("com.a", reappear.state.readinessHold!!.target)
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
    fun `a failed policy re-emission mid-escape does not re-raise a shield over the abandoned target`() {
        val requested = escapeRequested()
        val issued = requested.navIssued(requested.attempt).state
        assertNull(issued.readinessHold) // the shield is already down for the escape
        // Policy re-emits Failed before settings arrives; the abandoned target must not be re-shielded.
        val reduction = LockEngineReducer.reduce(issued, EngineEvent.PolicyStateChanged(PolicyState.Failed("again")))
        assertNull(reduction.state.readinessHold)
        assertNull(reduction.state.activeRequest)
        assertTrue(reduction.effects.none { it is Effect.Present }) // zero flash
        assertEquals(SafeDestination.OVERLAY_SETTINGS, reduction.state.pendingSafeDismiss!!.destination)
    }

    @Test
    fun `app-lock escape then Own then a failed re-emission raises no shield over the abandoned target`() {
        val shielded = failed().observe(other("com.a")).state // Recovery(com.a)
        val token = ReadinessToken(epoch, shielded.generation)
        val requested = shielded.requestSafeDismiss(token, SafeDestination.APP_LOCK)
        val issued = requested.navIssued(requested.attempt).state
        assertNull(issued.readinessHold)
        assertNull(issued.pendingSafeDismiss) // APP_LOCK keeps no arrival exemption
        val afterOwn = issued.observe(Foreground.Own).state // App Lock's own UI foregrounds; Own is a no-op
        val reduction = LockEngineReducer.reduce(afterOwn, EngineEvent.PolicyStateChanged(PolicyState.Failed("again")))
        assertNull(reduction.state.readinessHold) // no shield rebuilt over com.a while App Lock is foreground
        assertTrue(reduction.effects.none { it is Effect.Present })
    }

    @Test
    fun `home escape then a failed re-emission before the home observation raises no shield`() {
        val shielded = failed().observe(other("com.a")).state
        val token = ReadinessToken(epoch, shielded.generation)
        val requested = shielded.requestSafeDismiss(token, SafeDestination.HOME)
        val issued = requested.navIssued(requested.attempt).state
        assertNull(issued.readinessHold)
        // Failed arrives before Home is observed; the cleared lastForeground must not resurrect com.a.
        val reduction = LockEngineReducer.reduce(issued, EngineEvent.PolicyStateChanged(PolicyState.Failed("again")))
        assertNull(reduction.state.readinessHold)
        assertTrue(reduction.effects.none { it is Effect.Present })
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
    fun `a ready re-emission during a lock's app-lock escape preserves the handshake`() {
        val locked = initial().observe(other("com.a")).state // Lock(com.a) under Ready
        val token = RequestToken(epoch, locked.activeRequest!!.id)
        val requested = locked.requestSafeDismiss(token, SafeDestination.APP_LOCK)
        // A harmless Ready re-emission must not drop the in-flight handshake over the still-live lock.
        val afterReady = LockEngineReducer.reduce(
            requested,
            EngineEvent.PolicyStateChanged(PolicyState.Ready(protectedPackages)),
        ).state
        assertEquals(requested.activeRequest, afterReady.activeRequest) // lock preserved
        assertEquals(requested.pendingSafeDismiss, afterReady.pendingSafeDismiss) // handshake preserved
        // The navigation-issued follow-up then dismisses the surface as intended (not stranded).
        val issued = afterReady.navIssued(requested.attempt)
        assertNull(issued.state.activeRequest)
        assertTrue(issued.effects.contains(Effect.DismissSurface))
    }

    // ---- Round-9 review fixes -----------------------------------------------------------------

    @Test
    fun `when the guarded origin is itself the approved destination, a same-origin event re-presents not arrives`() {
        // The guarded app IS Settings and the OVERLAY_SETTINGS escape targets the grant screen in that same
        // package, so origin == the sole approved package. A same-origin event before step 2 must re-present
        // the shield, never be mistaken for the destination arriving and tear it down early.
        val shielded = failed().observe(other(settings)).state // Recovery(settings)
        val token = ReadinessToken(epoch, shielded.generation)
        val requested = shielded.requestSafeDismiss(token, SafeDestination.OVERLAY_SETTINGS, setOf(settings))
        val reappearR = requested.observe(other(settings))
        assertTrue(reappearR.effects.none { it is Effect.DismissSurface }) // shield not torn down
        val reappear = reappearR.state
        assertEquals(HoldPhase.RECOVERY, reappear.readinessHold!!.phase) // shield preserved
        assertEquals(settings, reappear.readinessHold!!.target)
        assertEquals(requested.pendingSafeDismiss, reappear.pendingSafeDismiss) // still the in-flight escape
        assertEquals(requested.lastForegroundSeq + 1, reappear.lastForegroundSeq) // processed
        // Only step 2 confirms the navigation; a settings observation after it counts as the arrival.
        val issued = reappear.navIssued(requested.attempt).state
        assertNull(issued.readinessHold)
        val arrival = issued.observe(other(settings))
        assertNull(arrival.state.readinessHold) // now allowed through as the arrival
        assertEquals(settings, arrival.state.pendingSafeDismiss!!.arrived)
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
        // The follow-up then completes the escape instead of being dropped as stale.
        val issued = afterReady.navIssued(requested.attempt).state
        assertNull(issued.readinessHold)
        assertNull(issued.pendingSafeDismiss)
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
        val issued = afterReady.navIssued(requested.attempt).state
        assertNull(issued.readinessHold)
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
        // The escape token is still valid, so the follow-up still tears the surface down.
        val issued = reduction.state.navIssued(requested.attempt).state
        assertNull(issued.readinessHold)
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
        // The navigation follow-up still succeeds afterwards.
        val issued = fired.state.navIssued(requested.attempt).state
        assertNull(issued.readinessHold)
        assertNull(issued.pendingSafeDismiss)
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
