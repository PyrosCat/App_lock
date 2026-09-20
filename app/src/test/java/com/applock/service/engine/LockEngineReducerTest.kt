package com.applock.service.engine

import com.applock.domain.PolicyState
import com.applock.security.LockoutState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7 WP2 Phase 1 reducer tests. The identity / lock lifecycle (change A) runs under a Ready policy;
 * the readiness holds, T_ready timer, and PolicyStateChanged transitions (change B) have their own
 * section. "com.a" / "com.b" are protected; "com.free" is not.
 */
class LockEngineReducerTest {

    private val epoch = Epoch(1)
    private val protectedPackages = setOf("com.a", "com.b")

    /** Ready policy over the protected set, so a protected foreground with no session locks. */
    private fun initial() = EngineState(epoch = epoch, policy = PolicyState.Ready(protectedPackages))

    private fun EngineState.observe(foreground: Foreground) =
        LockEngineReducer.reduce(this, EngineEvent.ForegroundObserved(foreground, elapsedRealtimeMs = 0))

    private fun other(packageName: String, session: Boolean = false) =
        Foreground.Other(packageName, hasValidSession = session)

    // ---- State invariant -----------------------------------------------------------------------

    // A lock and a shield can no longer coexist by construction (GuardState is one slot), so that is a
    // compile-time guarantee rather than a runtime test. What remains runtime-checked:
    @Test(expected = IllegalArgumentException::class)
    fun `EngineState rejects an in-flight escape coexisting with an arrival exemption`() {
        EngineState(
            epoch = epoch,
            guardState = GuardState.LeavingFor(
                BlockingGuard.Lock("com.a", RequestId(0)),
                RequestToken(epoch, RequestId(0)),
                AttemptToken(epoch, AttemptId(0)),
                SafeDestination.HOME,
                emptySet(),
            ),
            exemption = ArrivalExemption(
                ReadinessToken(epoch, Generation(0)),
                "com.b",
                setOf("com.android.settings"),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a checking hold with no timer is rejected`() {
        ReadinessHold("com.a", HoldPhase.CHECKING, null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a recovery hold with a timer is rejected`() {
        ReadinessHold("com.a", HoldPhase.RECOVERY, TimerToken.ReadinessTimer(epoch, Generation(1)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `EngineState rejects a live checking timer from a stale generation`() {
        EngineState(
            epoch = epoch,
            generation = Generation(2),
            guardState = GuardState.Guarding(
                BlockingGuard.Checking("com.a", TimerToken.ReadinessTimer(epoch, Generation(1))),
            ),
        )
    }

    // ---- Own / Transient are true no-ops -------------------------------------------------------

    @Test
    fun `Own is a no-op even while a target is locked`() {
        val locked = initial().observe(other("com.a")).state
        val reduction = locked.observe(Foreground.Own)
        assertEquals(locked, reduction.state)
        assertTrue(reduction.effects.isEmpty())
    }

    @Test
    fun `Transient is a no-op even while a target is locked`() {
        val locked = initial().observe(other("com.a")).state
        val reduction = locked.observe(Foreground.Transient)
        assertEquals(locked, reduction.state)
        assertTrue(reduction.effects.isEmpty())
    }

    @Test
    fun `Own between two observations of the same app does not relock it`() {
        val afterAllow = initial().observe(other("com.free")).state // allowed; advances lastForeground
        val afterOwn = afterAllow.observe(Foreground.Own).state // no-op; lastForeground preserved
        val reduction = afterOwn.observe(other("com.free")) // same app returns
        assertTrue(reduction.effects.none { it is Effect.NoteAppLeft })
    }

    // ---- Locking a protected app ----------------------------------------------------------------

    @Test
    fun `protected app with no session mints a request, logs once, and presents Lock`() {
        val reduction = initial().observe(other("com.a"))
        val request = reduction.state.activeRequest!!
        assertEquals("com.a", request.target)
        assertEquals(RequestId(0), request.id)
        assertEquals(
            listOf(
                Effect.Log(AuditEvent.LOCK_TRIGGERED, "com.a"),
                Effect.Present(Surface.Lock("com.a", RequestId(0))),
            ),
            reduction.effects,
        )
    }

    @Test
    fun `protected app with a valid session is allowed, no lock`() {
        val reduction = initial().observe(other("com.a", session = true))
        assertNull(reduction.state.activeRequest)
        assertTrue(reduction.effects.isEmpty())
    }

    @Test
    fun `unprotected app is allowed, no lock`() {
        val reduction = initial().observe(other("com.free"))
        assertNull(reduction.state.activeRequest)
        assertTrue(reduction.effects.isEmpty())
    }

    @Test
    fun `re-observing the same locked target re-presents idempotently without a new id or log`() {
        val firstLock = initial().observe(other("com.a"))
        val secondLock = firstLock.state.observe(other("com.a"))
        assertEquals(firstLock.state.activeRequest, secondLock.state.activeRequest) // same id
        assertEquals(1L, secondLock.state.nextRequestId) // exactly one id ever minted
        assertEquals(listOf(Effect.Present(Surface.Lock("com.a", RequestId(0)))), secondLock.effects)
        assertTrue(secondLock.effects.none { it is Effect.Log })
    }

    // ---- Supersession ---------------------------------------------------------------------------

    @Test
    fun `a different protected target supersedes the active request`() {
        val lockA = initial().observe(other("com.a"))
        val lockB = lockA.state.observe(other("com.b"))
        assertEquals(RequestId(1), lockB.state.activeRequest!!.id)
        assertEquals("com.b", lockB.state.activeRequest!!.target)
        assertEquals(1L, lockB.state.supersedeCount)
        assertTrue(lockB.effects.contains(Effect.NoteAppLeft("com.a"))) // genuine switch relocks A
        assertTrue(lockB.effects.contains(Effect.Log(AuditEvent.LOCK_TRIGGERED, "com.b")))
        assertTrue(lockB.effects.contains(Effect.Present(Surface.Lock("com.b", RequestId(1)))))
    }

    // ---- Home ----------------------------------------------------------------------------------

    @Test
    fun `Home dismisses a live lock and relocks the previous app exactly once`() {
        val locked = initial().observe(other("com.a")).state
        val reduction = locked.observe(Foreground.Home)
        assertNull(reduction.state.activeRequest)
        assertEquals(RealForeground.Home, reduction.state.lastForeground)
        assertEquals(1, reduction.effects.count { it == Effect.NoteAppLeft("com.a") })
        assertTrue(reduction.effects.contains(Effect.DismissSurface))
    }

    @Test
    fun `Home with nothing active emits nothing to dismiss and no relock`() {
        val reduction = initial().observe(Foreground.Home)
        assertNull(reduction.state.activeRequest)
        assertTrue(reduction.effects.none { it is Effect.DismissSurface || it is Effect.NoteAppLeft })
    }

    // ---- Completions: stale-result rejection ----------------------------------------------------

    @Test
    fun `unlock success clears the request and emits session, lockout, audit, dismiss`() {
        val locked = initial().observe(other("com.a")).state
        val token = RequestToken(epoch, locked.activeRequest!!.id)
        val reduction = LockEngineReducer.reduce(locked, EngineEvent.UnlockSucceeded(token, UnlockMethod.PIN))
        assertNull(reduction.state.activeRequest)
        assertEquals(
            listOf(
                Effect.MarkUnlocked("com.a"),
                Effect.RecordUnlockSuccess("com.a", UnlockMethod.PIN),
                Effect.Log(AuditEvent.UNLOCK_SUCCESS, "com.a"),
                Effect.DismissSurface,
            ),
            reduction.effects,
        )
    }

    @Test
    fun `biometric unlock success logs the biometric audit event`() {
        val locked = initial().observe(other("com.a")).state
        val token = RequestToken(epoch, locked.activeRequest!!.id)
        val reduction = LockEngineReducer.reduce(locked, EngineEvent.UnlockSucceeded(token, UnlockMethod.BIOMETRIC))
        assertTrue(reduction.effects.contains(Effect.Log(AuditEvent.BIOMETRIC_UNLOCK_SUCCESS, "com.a")))
    }

    @Test
    fun `a stale unlock success from a superseded request is rejected`() {
        val lockA = initial().observe(other("com.a"))
        val staleToken = RequestToken(epoch, lockA.state.activeRequest!!.id) // id 0
        val lockedB = lockA.state.observe(other("com.b")).state // now id 1 for com.b
        val reduction = LockEngineReducer.reduce(lockedB, EngineEvent.UnlockSucceeded(staleToken, UnlockMethod.PIN))
        assertEquals(lockedB, reduction.state)
        assertTrue(reduction.effects.isEmpty())
    }

    @Test
    fun `an unlock success with a foreign process epoch is rejected`() {
        val locked = initial().observe(other("com.a")).state
        val ghost = RequestToken(Epoch(999), locked.activeRequest!!.id)
        val reduction = LockEngineReducer.reduce(locked, EngineEvent.UnlockSucceeded(ghost, UnlockMethod.PIN))
        assertEquals(locked, reduction.state)
        assertTrue(reduction.effects.isEmpty())
    }

    @Test
    fun `unlock failure keeps the request locked, holds a pending failure, and records it`() {
        val locked = initial().observe(other("com.a")).state
        val reduction = LockEngineReducer.reduce(
            locked,
            EngineEvent.UnlockFailed(RequestToken(epoch, locked.activeRequest!!.id), UnlockMethod.PIN),
        )
        assertEquals(locked.activeRequest, reduction.state.activeRequest) // still locked
        assertEquals(
            mapOf(FailureId(0) to PendingFailure("com.a", UnlockMethod.PIN, streak = 0)),
            reduction.state.pendingFailures,
        )
        assertEquals(
            listOf(
                Effect.Log(AuditEvent.UNLOCK_FAILURE, "com.a"),
                Effect.RecordUnlockFailure(FailureToken(epoch, FailureId(0)), "com.a", UnlockMethod.PIN),
            ),
            reduction.effects,
        )
    }

    // ---- Lockout audit + intruder capture (causally tied to a failure, consumed once) ----------

    /** Fails the active request, returning the post-failure state and the minted per-failure token. */
    private fun EngineState.failActive(
        method: UnlockMethod = UnlockMethod.PIN,
    ): Pair<EngineState, FailureToken> {
        val failure = EngineEvent.UnlockFailed(RequestToken(epoch, activeRequest!!.id), method)
        val reduction = LockEngineReducer.reduce(this, failure)
        val failureToken = reduction.effects.filterIsInstance<Effect.RecordUnlockFailure>().single().token
        return reduction.state to failureToken
    }

    @Test
    fun `a lockout-triggering failure logs LOCKOUT_TRIGGERED then captures the intruder`() {
        val (afterFailure, failureToken) = initial().observe(other("com.a")).state.failActive(UnlockMethod.PIN)
        val reduction = LockEngineReducer.reduce(
            afterFailure,
            EngineEvent.LockoutRecorded(failureToken, LockoutState.LockedOut(30_000L), failureCount = 5),
        )
        assertEquals(afterFailure.activeRequest, reduction.state.activeRequest) // lockout keeps the request
        assertTrue(reduction.state.pendingFailures.isEmpty()) // consumed
        assertEquals(LockoutState.LockedOut(30_000L), reduction.state.lockout) // exposed for the surface
        assertEquals(
            listOf(
                Effect.Log(AuditEvent.LOCKOUT_TRIGGERED, "com.a"),
                Effect.CaptureIntruder("com.a", UnlockMethod.PIN, 5),
            ),
            reduction.effects,
        )
    }

    @Test
    fun `a non-lockout failure captures the intruder without a lockout audit`() {
        val (afterFailure, failureToken) = initial().observe(other("com.a")).state.failActive(UnlockMethod.BIOMETRIC)
        val reduction = LockEngineReducer.reduce(
            afterFailure,
            EngineEvent.LockoutRecorded(failureToken, LockoutState.Available, failureCount = 2),
        )
        assertEquals(listOf(Effect.CaptureIntruder("com.a", UnlockMethod.BIOMETRIC, 2)), reduction.effects)
        assertEquals(LockoutState.Available, reduction.state.lockout)
    }

    @Test
    fun `an unsolicited lockout-recorded with no preceding failure is rejected`() {
        val locked = initial().observe(other("com.a")).state // no UnlockFailed -> nothing pending
        val reduction = LockEngineReducer.reduce(
            locked,
            EngineEvent.LockoutRecorded(FailureToken(epoch, FailureId(0)), LockoutState.LockedOut(30_000L), 5),
        )
        assertEquals(locked, reduction.state)
        assertTrue(reduction.effects.isEmpty())
    }

    @Test
    fun `a duplicate lockout-recorded is rejected (consumed exactly once)`() {
        val (afterFailure, failureToken) = initial().observe(other("com.a")).state.failActive()
        val recorded = EngineEvent.LockoutRecorded(failureToken, LockoutState.LockedOut(30_000L), 5)
        val firstRecord = LockEngineReducer.reduce(afterFailure, recorded)
        val secondRecord = LockEngineReducer.reduce(firstRecord.state, recorded) // same token, already consumed
        assertEquals(firstRecord.state, secondRecord.state)
        assertTrue(secondRecord.effects.isEmpty())
    }

    @Test
    fun `two failures before either follow-up each keep their own outcome`() {
        val (afterFailureA, failureTokenA) = initial().observe(other("com.a")).state.failActive(UnlockMethod.PIN)
        val (afterFailureB, failureTokenB) = afterFailureA.failActive(UnlockMethod.BIOMETRIC)
        assertEquals(2, afterFailureB.pendingFailures.size)

        val recordA = LockEngineReducer.reduce(
            afterFailureB,
            EngineEvent.LockoutRecorded(failureTokenA, LockoutState.Available, 1),
        )
        assertEquals(listOf(Effect.CaptureIntruder("com.a", UnlockMethod.PIN, 1)), recordA.effects)

        val recordB = LockEngineReducer.reduce(
            recordA.state,
            EngineEvent.LockoutRecorded(failureTokenB, LockoutState.LockedOut(30_000L), 2),
        )
        assertEquals(
            listOf(
                Effect.Log(AuditEvent.LOCKOUT_TRIGGERED, "com.a"),
                Effect.CaptureIntruder("com.a", UnlockMethod.BIOMETRIC, 2),
            ),
            recordB.effects,
        )
        assertTrue(recordB.state.pendingFailures.isEmpty()) // both consumed, neither lost
    }

    @Test
    fun `a success does not drop an already-recorded failure's pending outcome`() {
        val (afterFailure, failureToken) = initial().observe(other("com.a")).state.failActive(UnlockMethod.PIN)
        val afterSuccess = LockEngineReducer.reduce(
            afterFailure,
            EngineEvent.UnlockSucceeded(RequestToken(epoch, afterFailure.activeRequest!!.id), UnlockMethod.PIN),
        )
        assertNull(afterSuccess.state.activeRequest) // request resolved
        assertEquals(1, afterSuccess.state.pendingFailures.size) // the failure's outcome is still pending

        val reduction = LockEngineReducer.reduce(
            afterSuccess.state,
            EngineEvent.LockoutRecorded(failureToken, LockoutState.Available, 1),
        )
        assertEquals(listOf(Effect.CaptureIntruder("com.a", UnlockMethod.PIN, 1)), reduction.effects)
        assertTrue(reduction.state.pendingFailures.isEmpty())
    }

    @Test
    fun `a delayed pre-success follow-up captures but does not re-lock after success`() {
        val (afterFailure, failureToken) = initial().observe(other("com.a")).state.failActive(UnlockMethod.PIN)
        val afterSuccess = LockEngineReducer.reduce(
            afterFailure,
            EngineEvent.UnlockSucceeded(RequestToken(epoch, afterFailure.activeRequest!!.id), UnlockMethod.PIN),
        )
        assertEquals(LockoutState.Available, afterSuccess.state.lockout)
        // The pre-success failure's follow-up arrives late carrying LockedOut (the manager was already
        // reset by the success). It must still capture/audit, but must not re-lock the projection.
        val reduction = LockEngineReducer.reduce(
            afterSuccess.state,
            EngineEvent.LockoutRecorded(failureToken, LockoutState.LockedOut(30_000L), 5),
        )
        assertEquals(
            listOf(
                Effect.Log(AuditEvent.LOCKOUT_TRIGGERED, "com.a"),
                Effect.CaptureIntruder("com.a", UnlockMethod.PIN, 5),
            ),
            reduction.effects,
        )
        assertEquals(LockoutState.Available, reduction.state.lockout) // not re-locked after success
        assertTrue(reduction.state.pendingFailures.isEmpty())
    }

    @Test
    fun `an out-of-order follow-up does not regress the projected lockout`() {
        val (afterFailureA, failureTokenA) = initial().observe(other("com.a")).state.failActive(UnlockMethod.PIN)
        val (afterFailureB, failureTokenB) = afterFailureA.failActive(UnlockMethod.PIN)
        // Follow-ups arrive newest-first: count 2 (LockedOut) before the stale count 1 (Available).
        val recordB = LockEngineReducer.reduce(
            afterFailureB,
            EngineEvent.LockoutRecorded(failureTokenB, LockoutState.LockedOut(30_000L), 2),
        )
        assertEquals(LockoutState.LockedOut(30_000L), recordB.state.lockout)
        val recordA = LockEngineReducer.reduce(
            recordB.state,
            EngineEvent.LockoutRecorded(failureTokenA, LockoutState.Available, 1),
        )
        assertEquals(LockoutState.LockedOut(30_000L), recordA.state.lockout) // not regressed to Available
        assertEquals(listOf(Effect.CaptureIntruder("com.a", UnlockMethod.PIN, 1)), recordA.effects) // capture kept
        assertTrue(recordA.state.pendingFailures.isEmpty())
    }

    // ---- Biometric cancel: token-keyed no-op on request state -----------------------------------

    @Test
    fun `biometric cancel keeps the request and re-asserts the lock surface`() {
        val locked = initial().observe(other("com.a")).state
        val token = RequestToken(epoch, locked.activeRequest!!.id)
        val reduction = LockEngineReducer.reduce(locked, EngineEvent.BiometricCancelled(token))
        assertEquals(locked.activeRequest, reduction.state.activeRequest) // not cleared (contrast with dismiss)
        assertEquals(listOf(Effect.Present(Surface.Lock("com.a", locked.activeRequest!!.id))), reduction.effects)
    }

    @Test
    fun `a stale biometric cancel is ignored`() {
        val locked = initial().observe(other("com.a")).state
        val cancel = EngineEvent.BiometricCancelled(RequestToken(epoch, RequestId(99)))
        val reduction = LockEngineReducer.reduce(locked, cancel)
        assertEquals(locked, reduction.state)
        assertTrue(reduction.effects.isEmpty())
    }

    // ---- Rotation / recreation (state-observing presenter) + process death ----------------------

    @Test
    fun `after recreation the presenter reconstructs the same request from state surface`() {
        val locked = initial().observe(other("com.a")).state
        // Surface recreation changes no engine state; the presenter re-reads state.surface.
        val surface = locked.surface as Surface.Lock
        assertEquals("com.a", surface.target)
        assertEquals(locked.activeRequest!!.id, surface.id)
        // A completion reconstructed from the surface resolves the same request (not a new/lost one).
        val reduction = LockEngineReducer.reduce(
            locked,
            EngineEvent.UnlockSucceeded(RequestToken(locked.epoch, surface.id), UnlockMethod.PIN),
        )
        assertNull(reduction.state.activeRequest)
    }

    @Test
    fun `process death leaves no ghost request and rejects pre-death completions`() {
        val reborn = EngineState(epoch = Epoch(2)) // fresh process: new epoch, no active request
        val preDeathToken = RequestToken(Epoch(1), RequestId(0))
        val reduction = LockEngineReducer.reduce(reborn, EngineEvent.UnlockSucceeded(preDeathToken, UnlockMethod.PIN))
        assertNull(reduction.state.activeRequest)
        assertEquals(reborn, reduction.state)
        assertTrue(reduction.effects.isEmpty())
    }

    // ---- Screen off -----------------------------------------------------------------------------

    @Test
    fun `screen off clears sessions, tears down a live surface, and resets foreground`() {
        val locked = initial().observe(other("com.a")).state
        val reduction = LockEngineReducer.reduce(locked, EngineEvent.ScreenOff)
        assertNull(reduction.state.activeRequest)
        assertNull(reduction.state.lastForeground)
        assertTrue(reduction.effects.contains(Effect.ClearSessions))
        assertTrue(reduction.effects.contains(Effect.DismissSurface))
    }

    @Test
    fun `screen off from a surface-free state still advances the generation`() {
        val allowed = initial().observe(other("com.free")).state // allowed: no surface
        assertEquals(Generation(0), allowed.generation)
        val reduction = LockEngineReducer.reduce(allowed, EngineEvent.ScreenOff)
        assertEquals(Generation(1), reduction.state.generation) // reset boundary advances unconditionally
        assertNull(reduction.state.lastForeground)
        assertEquals(listOf(Effect.ClearSessions), reduction.effects) // no live surface: no cancel, no dismiss
    }

    // ---- Readiness (change B): Loading / Failed holds, T_ready, PolicyStateChanged ---------------

    private fun loading() = EngineState(epoch = epoch, policy = PolicyState.Loading)
    private fun failed() = EngineState(epoch = epoch, policy = PolicyState.Failed("boom"))

    @Test
    fun `Loading holds an Other behind a checking shield and schedules T_ready`() {
        val reduction = loading().observe(other("com.a"))
        val hold = reduction.state.readinessHold!!
        assertEquals("com.a", hold.target)
        assertEquals(HoldPhase.CHECKING, hold.phase)
        assertNull(reduction.state.activeRequest)
        val token = TimerToken.ReadinessTimer(epoch, Generation(1))
        assertEquals(token, hold.timer)
        assertEquals(
            listOf(
                Effect.ScheduleTimer(token, LockEngineReducer.T_READY_MS),
                Effect.Present(Surface.Checking("com.a")),
            ),
            reduction.effects,
        )
    }

    @Test
    fun `Loading holds an Other even when it would have a valid session`() {
        val reduction = loading().observe(other("com.a", session = true))
        // Nothing can be proven unprotected while loading, so the session does not short-circuit the hold.
        assertEquals(HoldPhase.CHECKING, reduction.state.readinessHold!!.phase)
    }

    @Test
    fun `Loading to Ready locks a held target that is protected`() {
        val held = loading().observe(other("com.a")).state
        val checkingTimer = held.readinessHold!!.timer!!
        val reduction = LockEngineReducer.reduce(
            held,
            EngineEvent.PolicyStateChanged(PolicyState.Ready(setOf("com.a"))),
        )
        assertEquals("com.a", reduction.state.activeRequest!!.target)
        assertNull(reduction.state.readinessHold)
        assertTrue(reduction.effects.contains(Effect.CancelTimer(checkingTimer)))
        assertTrue(reduction.effects.contains(Effect.Log(AuditEvent.LOCK_TRIGGERED, "com.a")))
        assertTrue(reduction.effects.contains(Effect.Present(Surface.Lock("com.a", RequestId(0)))))
    }

    @Test
    fun `Loading to Ready locks a protected target regardless of a prior session (fail-secure)`() {
        val held = loading().observe(other("com.a", session = true)).state
        val reduction = LockEngineReducer.reduce(
            held,
            EngineEvent.PolicyStateChanged(PolicyState.Ready(setOf("com.a"))),
        )
        assertEquals("com.a", reduction.state.activeRequest!!.target) // locked, not allowed on the stale session
    }

    @Test
    fun `Loading to Ready dismisses a held target that is not protected (empty first emission)`() {
        val held = loading().observe(other("com.a")).state
        val checkingTimer = held.readinessHold!!.timer!!
        val reduction = LockEngineReducer.reduce(held, EngineEvent.PolicyStateChanged(PolicyState.Ready(emptySet())))
        assertNull(reduction.state.readinessHold)
        assertNull(reduction.state.activeRequest)
        assertTrue(reduction.effects.contains(Effect.CancelTimer(checkingTimer)))
        assertTrue(reduction.effects.contains(Effect.DismissSurface))
    }

    @Test
    fun `Failed holds an Other behind a recovery shield with no timer, never allowing it`() {
        val reduction = failed().observe(other("com.free")) // held under Failed even though not in any set
        val hold = reduction.state.readinessHold!!
        assertEquals(HoldPhase.RECOVERY, hold.phase)
        assertNull(hold.timer)
        assertNull(reduction.state.activeRequest)
        assertEquals(listOf(Effect.Present(Surface.Recovery("com.free"))), reduction.effects)
        assertTrue(reduction.effects.none { it is Effect.ScheduleTimer })
    }

    @Test
    fun `Failed to Ready re-evaluates a recovery hold and locks a protected target`() {
        val held = failed().observe(other("com.a")).state
        val reduction = LockEngineReducer.reduce(
            held,
            EngineEvent.PolicyStateChanged(PolicyState.Ready(setOf("com.a"))),
        )
        assertEquals("com.a", reduction.state.activeRequest!!.target)
        assertNull(reduction.state.readinessHold)
    }

    @Test
    fun `Ready to Failed raises a recovery shield over a standing allowed foreground`() {
        val allowed = initial().observe(other("com.free")).state // allowed under Ready, current foreground
        assertNull(allowed.readinessHold)
        val reduction = LockEngineReducer.reduce(allowed, EngineEvent.PolicyStateChanged(PolicyState.Failed("boom")))
        assertEquals(HoldPhase.RECOVERY, reduction.state.readinessHold!!.phase)
        assertEquals("com.free", reduction.state.readinessHold!!.target)
        assertTrue(reduction.effects.contains(Effect.Present(Surface.Recovery("com.free"))))
    }

    @Test
    fun `Ready to Failed preserves an active lock`() {
        val locked = initial().observe(other("com.a")).state
        val reduction = LockEngineReducer.reduce(locked, EngineEvent.PolicyStateChanged(PolicyState.Failed("boom")))
        assertEquals(locked.activeRequest, reduction.state.activeRequest) // still locked; Failed does not un-protect
        assertNull(reduction.state.readinessHold)
        assertTrue(reduction.effects.none { it is Effect.Present || it is Effect.DismissSurface })
    }

    @Test
    fun `Ready to Failed converts a checking hold to recovery`() {
        val checking = loading().observe(other("com.a")).state
        val checkingTimer = checking.readinessHold!!.timer!!
        val reduction = LockEngineReducer.reduce(checking, EngineEvent.PolicyStateChanged(PolicyState.Failed("boom")))
        assertEquals(HoldPhase.RECOVERY, reduction.state.readinessHold!!.phase)
        assertNull(reduction.state.readinessHold!!.timer)
        assertTrue(reduction.effects.contains(Effect.CancelTimer(checkingTimer)))
        assertTrue(reduction.effects.contains(Effect.Present(Surface.Recovery("com.a"))))
    }

    @Test
    fun `T_ready expiry escalates a checking hold to recovery`() {
        val checking = loading().observe(other("com.a")).state
        val checkingTimer = checking.readinessHold!!.timer!!
        val reduction = LockEngineReducer.reduce(checking, EngineEvent.TimerFired(checkingTimer))
        assertEquals(HoldPhase.RECOVERY, reduction.state.readinessHold!!.phase)
        assertNull(reduction.state.readinessHold!!.timer)
        assertEquals(listOf(Effect.Present(Surface.Recovery("com.a"))), reduction.effects)
    }

    @Test
    fun `a stale T_ready timer from a superseded hold is rejected`() {
        val holdA = loading().observe(other("com.a")).state
        val staleTimer = holdA.readinessHold!!.timer!! // generation 1
        val holdB = holdA.observe(other("com.b")).state // supersedes to a generation 2 timer for com.b
        val reduction = LockEngineReducer.reduce(holdB, EngineEvent.TimerFired(staleTimer))
        assertEquals(holdB, reduction.state)
        assertTrue(reduction.effects.isEmpty())
    }

    @Test
    fun `a repeated Loading foreground re-presents without restarting the timer`() {
        val firstHold = loading().observe(other("com.a"))
        val secondHold = firstHold.state.observe(other("com.a"))
        assertEquals(firstHold.state.readinessHold, secondHold.state.readinessHold) // same timer and generation
        assertEquals(listOf(Effect.Present(Surface.Checking("com.a"))), secondHold.effects)
        assertTrue(secondHold.effects.none { it is Effect.ScheduleTimer })
    }

    @Test
    fun `a different Loading target supersedes the hold, cancelling and rescheduling the timer`() {
        val holdA = loading().observe(other("com.a")).state
        val timerA = holdA.readinessHold!!.timer!!
        val reduction = holdA.observe(other("com.b"))
        val expectedTimerB = TimerToken.ReadinessTimer(epoch, Generation(2))
        assertEquals("com.b", reduction.state.readinessHold!!.target)
        assertEquals(Generation(2), reduction.state.generation)
        assertEquals(1L, reduction.state.supersedeCount)
        assertTrue(reduction.effects.contains(Effect.NoteAppLeft("com.a")))
        assertTrue(reduction.effects.contains(Effect.CancelTimer(timerA)))
        assertTrue(reduction.effects.contains(Effect.ScheduleTimer(expectedTimerB, LockEngineReducer.T_READY_MS)))
    }

    @Test
    fun `Home dismisses a checking hold and cancels its timer`() {
        val checking = loading().observe(other("com.a")).state
        val checkingTimer = checking.readinessHold!!.timer!!
        val reduction = checking.observe(Foreground.Home)
        assertNull(reduction.state.readinessHold)
        assertTrue(reduction.effects.contains(Effect.CancelTimer(checkingTimer)))
        assertTrue(reduction.effects.contains(Effect.DismissSurface))
        assertTrue(reduction.effects.contains(Effect.NoteAppLeft("com.a")))
    }

    // ---- Generation advances on any invalidating transition -------------------------------------

    @Test
    fun `Home over a checking hold advances the generation`() {
        val checking = loading().observe(other("com.a")).state
        assertEquals(Generation(1), checking.generation)
        val reduction = checking.observe(Foreground.Home) // cancels the checking timer
        assertEquals(Generation(2), reduction.state.generation)
    }

    @Test
    fun `re-evaluating a checking hold to Lock advances the generation`() {
        val held = loading().observe(other("com.a")).state // generation 1
        val reduction = LockEngineReducer.reduce(
            held,
            EngineEvent.PolicyStateChanged(PolicyState.Ready(setOf("com.a"))),
        )
        assertEquals(Generation(2), reduction.state.generation)
    }

    @Test
    fun `superseding an active lock with another target advances the generation`() {
        val lockedA = initial().observe(other("com.a")).state
        assertEquals(Generation(0), lockedA.generation) // a fresh lock invalidates nothing
        val reduction = lockedA.observe(other("com.b")) // supersede A with B (no timer, but a surface replaced)
        assertEquals(Generation(1), reduction.state.generation)
    }

    @Test
    fun `taking an active lock Home advances the generation`() {
        val lockedA = initial().observe(other("com.a")).state
        val reduction = lockedA.observe(Foreground.Home) // tears down the lock
        assertEquals(Generation(1), reduction.state.generation)
    }
}
