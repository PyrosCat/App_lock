package com.applock.service.engine

import com.applock.security.LockoutState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Change A (M7 WP2 Phase 1): the request-identity / lock lifecycle under Ready policy. Readiness
 * (Loading/Failed holds, the T_ready timer, PolicyStateChanged) is exercised by change B's suite.
 */
class LockEngineReducerTest {

    private val epoch = Epoch(1)
    private fun initial() = EngineState(epoch = epoch)

    private fun EngineState.observe(foreground: Foreground) =
        LockEngineReducer.reduce(this, EngineEvent.ForegroundObserved(foreground, elapsedRealtimeMs = 0))

    private fun other(packageName: String, protected: Boolean = true, session: Boolean = false) =
        Foreground.Other(packageName, protected = protected, hasValidSession = session)

    // ---- Own / Transient are true no-ops -------------------------------------------------------

    @Test
    fun `Own is a no-op even while a target is locked`() {
        val locked = initial().observe(other("com.a")).state
        val r = locked.observe(Foreground.Own)
        assertEquals(locked, r.state)
        assertTrue(r.effects.isEmpty())
    }

    @Test
    fun `Transient is a no-op even while a target is locked`() {
        val locked = initial().observe(other("com.a")).state
        val r = locked.observe(Foreground.Transient)
        assertEquals(locked, r.state)
        assertTrue(r.effects.isEmpty())
    }

    @Test
    fun `Own between two observations of the same app does not relock it`() {
        var s = initial().observe(other("com.a", protected = false)).state // allowed; advances lastForeground
        s = s.observe(Foreground.Own).state // no-op; lastForeground preserved
        val r = s.observe(other("com.a", protected = false)) // same app returns
        assertTrue(r.effects.none { it is Effect.NoteAppLeft })
    }

    // ---- Locking a protected app ----------------------------------------------------------------

    @Test
    fun `protected app with no session mints a request, logs once, and presents Lock`() {
        val r = initial().observe(other("com.a"))
        val req = r.state.activeRequest!!
        assertEquals("com.a", req.target)
        assertEquals(RequestId(0), req.id)
        assertEquals(
            listOf(
                Effect.Log(AuditEvent.LOCK_TRIGGERED, "com.a"),
                Effect.Present(Surface.Lock("com.a", RequestId(0))),
            ),
            r.effects,
        )
    }

    @Test
    fun `protected app with a valid session is allowed, no lock`() {
        val r = initial().observe(other("com.a", session = true))
        assertNull(r.state.activeRequest)
        assertTrue(r.effects.isEmpty())
    }

    @Test
    fun `unprotected app is allowed, no lock`() {
        val r = initial().observe(other("com.a", protected = false))
        assertNull(r.state.activeRequest)
        assertTrue(r.effects.isEmpty())
    }

    @Test
    fun `re-observing the same locked target re-presents idempotently without a new id or log`() {
        val first = initial().observe(other("com.a"))
        val second = first.state.observe(other("com.a"))
        assertEquals(first.state.activeRequest, second.state.activeRequest) // same id
        assertEquals(1L, second.state.nextRequestId) // exactly one id ever minted
        assertEquals(listOf(Effect.Present(Surface.Lock("com.a", RequestId(0)))), second.effects)
        assertTrue(second.effects.none { it is Effect.Log })
    }

    // ---- Supersession ---------------------------------------------------------------------------

    @Test
    fun `a different protected target supersedes the active request`() {
        val a = initial().observe(other("com.a"))
        val b = a.state.observe(other("com.b"))
        assertEquals(RequestId(1), b.state.activeRequest!!.id)
        assertEquals("com.b", b.state.activeRequest!!.target)
        assertEquals(1L, b.state.supersedeCount)
        assertTrue(b.effects.contains(Effect.NoteAppLeft("com.a"))) // genuine switch relocks A
        assertTrue(b.effects.contains(Effect.Log(AuditEvent.LOCK_TRIGGERED, "com.b")))
        assertTrue(b.effects.contains(Effect.Present(Surface.Lock("com.b", RequestId(1)))))
    }

    // ---- Home ----------------------------------------------------------------------------------

    @Test
    fun `Home dismisses a live lock and relocks the previous app exactly once`() {
        val locked = initial().observe(other("com.a")).state
        val r = locked.observe(Foreground.Home)
        assertNull(r.state.activeRequest)
        assertEquals(RealForeground.Home, r.state.lastForeground)
        assertEquals(1, r.effects.count { it == Effect.NoteAppLeft("com.a") })
        assertTrue(r.effects.contains(Effect.DismissSurface))
    }

    @Test
    fun `Home with nothing active emits nothing to dismiss and no relock`() {
        val r = initial().observe(Foreground.Home)
        assertNull(r.state.activeRequest)
        assertTrue(r.effects.none { it is Effect.DismissSurface || it is Effect.NoteAppLeft })
    }

    // ---- Completions: stale-result rejection ----------------------------------------------------

    @Test
    fun `unlock success clears the request and emits session, lockout, audit, dismiss`() {
        val locked = initial().observe(other("com.a")).state
        val token = RequestToken(epoch, locked.activeRequest!!.id)
        val r = LockEngineReducer.reduce(locked, EngineEvent.UnlockSucceeded(token, UnlockMethod.PIN))
        assertNull(r.state.activeRequest)
        assertEquals(
            listOf(
                Effect.MarkUnlocked("com.a"),
                Effect.RecordUnlockSuccess("com.a", UnlockMethod.PIN),
                Effect.Log(AuditEvent.UNLOCK_SUCCESS, "com.a"),
                Effect.DismissSurface,
            ),
            r.effects,
        )
    }

    @Test
    fun `biometric unlock success logs the biometric audit event`() {
        val locked = initial().observe(other("com.a")).state
        val token = RequestToken(epoch, locked.activeRequest!!.id)
        val r = LockEngineReducer.reduce(locked, EngineEvent.UnlockSucceeded(token, UnlockMethod.BIOMETRIC))
        assertTrue(r.effects.contains(Effect.Log(AuditEvent.BIOMETRIC_UNLOCK_SUCCESS, "com.a")))
    }

    @Test
    fun `a stale unlock success from a superseded request is rejected`() {
        val a = initial().observe(other("com.a"))
        val staleToken = RequestToken(epoch, a.state.activeRequest!!.id) // id 0
        val b = a.state.observe(other("com.b")).state // now id 1 for com.b
        val r = LockEngineReducer.reduce(b, EngineEvent.UnlockSucceeded(staleToken, UnlockMethod.PIN))
        assertEquals(b, r.state)
        assertTrue(r.effects.isEmpty())
    }

    @Test
    fun `an unlock success with a foreign process epoch is rejected`() {
        val locked = initial().observe(other("com.a")).state
        val ghost = RequestToken(Epoch(999), locked.activeRequest!!.id)
        val r = LockEngineReducer.reduce(locked, EngineEvent.UnlockSucceeded(ghost, UnlockMethod.PIN))
        assertEquals(locked, r.state)
        assertTrue(r.effects.isEmpty())
    }

    @Test
    fun `unlock failure keeps the request locked, holds a pending failure, and records it`() {
        val locked = initial().observe(other("com.a")).state
        val r = LockEngineReducer.reduce(
            locked,
            EngineEvent.UnlockFailed(RequestToken(epoch, locked.activeRequest!!.id), UnlockMethod.PIN),
        )
        assertEquals(locked.activeRequest, r.state.activeRequest) // still locked
        assertEquals(
            mapOf(FailureId(0) to PendingFailure("com.a", UnlockMethod.PIN, streak = 0)),
            r.state.pendingFailures,
        )
        assertEquals(
            listOf(
                Effect.Log(AuditEvent.UNLOCK_FAILURE, "com.a"),
                Effect.RecordUnlockFailure(FailureToken(epoch, FailureId(0)), "com.a", UnlockMethod.PIN),
            ),
            r.effects,
        )
    }

    // ---- Lockout audit + intruder capture (causally tied to a failure, consumed once) ----------

    /** Fails the active request, returning the post-failure state and the minted per-failure token. */
    private fun EngineState.failActive(
        method: UnlockMethod = UnlockMethod.PIN,
    ): Pair<EngineState, FailureToken> {
        val failed = EngineEvent.UnlockFailed(RequestToken(epoch, activeRequest!!.id), method)
        val r = LockEngineReducer.reduce(this, failed)
        val ft = r.effects.filterIsInstance<Effect.RecordUnlockFailure>().single().token
        return r.state to ft
    }

    @Test
    fun `a lockout-triggering failure logs LOCKOUT_TRIGGERED then captures the intruder`() {
        val (failed, ft) = initial().observe(other("com.a")).state.failActive(UnlockMethod.PIN)
        val r = LockEngineReducer.reduce(
            failed,
            EngineEvent.LockoutRecorded(ft, LockoutState.LockedOut(30_000L), failureCount = 5),
        )
        assertEquals(failed.activeRequest, r.state.activeRequest) // lockout does not clear the request
        assertTrue(r.state.pendingFailures.isEmpty()) // consumed
        assertEquals(LockoutState.LockedOut(30_000L), r.state.lockout) // exposed for the surface
        assertEquals(
            listOf(
                Effect.Log(AuditEvent.LOCKOUT_TRIGGERED, "com.a"),
                Effect.CaptureIntruder("com.a", UnlockMethod.PIN, 5),
            ),
            r.effects,
        )
    }

    @Test
    fun `a non-lockout failure captures the intruder without a lockout audit`() {
        val (failed, ft) = initial().observe(other("com.a")).state.failActive(UnlockMethod.BIOMETRIC)
        val r = LockEngineReducer.reduce(
            failed,
            EngineEvent.LockoutRecorded(ft, LockoutState.Available, failureCount = 2),
        )
        assertEquals(listOf(Effect.CaptureIntruder("com.a", UnlockMethod.BIOMETRIC, 2)), r.effects)
        assertEquals(LockoutState.Available, r.state.lockout)
    }

    @Test
    fun `an unsolicited lockout-recorded with no preceding failure is rejected`() {
        val locked = initial().observe(other("com.a")).state // no UnlockFailed -> nothing pending
        val r = LockEngineReducer.reduce(
            locked,
            EngineEvent.LockoutRecorded(FailureToken(epoch, FailureId(0)), LockoutState.LockedOut(30_000L), 5),
        )
        assertEquals(locked, r.state)
        assertTrue(r.effects.isEmpty())
    }

    @Test
    fun `a duplicate lockout-recorded is rejected (consumed exactly once)`() {
        val (failed, ft) = initial().observe(other("com.a")).state.failActive()
        val recorded = EngineEvent.LockoutRecorded(ft, LockoutState.LockedOut(30_000L), 5)
        val first = LockEngineReducer.reduce(failed, recorded)
        val second = LockEngineReducer.reduce(first.state, recorded) // same token, already consumed
        assertEquals(first.state, second.state)
        assertTrue(second.effects.isEmpty())
    }

    @Test
    fun `two failures before either follow-up each keep their own outcome`() {
        val (afterA, ftA) = initial().observe(other("com.a")).state.failActive(UnlockMethod.PIN)
        val (afterB, ftB) = afterA.failActive(UnlockMethod.BIOMETRIC)
        assertEquals(2, afterB.pendingFailures.size)

        val rA = LockEngineReducer.reduce(
            afterB,
            EngineEvent.LockoutRecorded(ftA, LockoutState.Available, 1),
        )
        assertEquals(listOf(Effect.CaptureIntruder("com.a", UnlockMethod.PIN, 1)), rA.effects)

        val rB = LockEngineReducer.reduce(
            rA.state,
            EngineEvent.LockoutRecorded(ftB, LockoutState.LockedOut(30_000L), 2),
        )
        assertEquals(
            listOf(
                Effect.Log(AuditEvent.LOCKOUT_TRIGGERED, "com.a"),
                Effect.CaptureIntruder("com.a", UnlockMethod.BIOMETRIC, 2),
            ),
            rB.effects,
        )
        assertTrue(rB.state.pendingFailures.isEmpty()) // both consumed, neither lost
    }

    @Test
    fun `a success does not drop an already-recorded failure's pending outcome`() {
        val (failed, ft) = initial().observe(other("com.a")).state.failActive(UnlockMethod.PIN)
        val afterSuccess = LockEngineReducer.reduce(
            failed,
            EngineEvent.UnlockSucceeded(RequestToken(epoch, failed.activeRequest!!.id), UnlockMethod.PIN),
        )
        assertNull(afterSuccess.state.activeRequest) // request resolved
        assertEquals(1, afterSuccess.state.pendingFailures.size) // the failure's outcome is still pending

        val r = LockEngineReducer.reduce(
            afterSuccess.state,
            EngineEvent.LockoutRecorded(ft, LockoutState.Available, 1),
        )
        assertEquals(listOf(Effect.CaptureIntruder("com.a", UnlockMethod.PIN, 1)), r.effects)
        assertTrue(r.state.pendingFailures.isEmpty())
    }

    @Test
    fun `a delayed pre-success follow-up captures but does not re-lock after success`() {
        val (failed, ft) = initial().observe(other("com.a")).state.failActive(UnlockMethod.PIN)
        val afterSuccess = LockEngineReducer.reduce(
            failed,
            EngineEvent.UnlockSucceeded(RequestToken(epoch, failed.activeRequest!!.id), UnlockMethod.PIN),
        )
        assertEquals(LockoutState.Available, afterSuccess.state.lockout)
        // The pre-success failure's follow-up arrives late carrying LockedOut (the manager was already
        // reset by the success). It must still capture/audit, but must not re-lock the projection.
        val r = LockEngineReducer.reduce(
            afterSuccess.state,
            EngineEvent.LockoutRecorded(ft, LockoutState.LockedOut(30_000L), 5),
        )
        assertEquals(
            listOf(
                Effect.Log(AuditEvent.LOCKOUT_TRIGGERED, "com.a"),
                Effect.CaptureIntruder("com.a", UnlockMethod.PIN, 5),
            ),
            r.effects,
        )
        assertEquals(LockoutState.Available, r.state.lockout) // not re-locked after success
        assertTrue(r.state.pendingFailures.isEmpty())
    }

    @Test
    fun `an out-of-order follow-up does not regress the projected lockout`() {
        val (afterA, ftA) = initial().observe(other("com.a")).state.failActive(UnlockMethod.PIN)
        val (afterB, ftB) = afterA.failActive(UnlockMethod.PIN)
        // Follow-ups arrive newest-first: count 2 (LockedOut) before the stale count 1 (Available).
        val rB = LockEngineReducer.reduce(
            afterB,
            EngineEvent.LockoutRecorded(ftB, LockoutState.LockedOut(30_000L), 2),
        )
        assertEquals(LockoutState.LockedOut(30_000L), rB.state.lockout)
        val rA = LockEngineReducer.reduce(
            rB.state,
            EngineEvent.LockoutRecorded(ftA, LockoutState.Available, 1),
        )
        assertEquals(LockoutState.LockedOut(30_000L), rA.state.lockout) // not regressed to Available
        assertEquals(listOf(Effect.CaptureIntruder("com.a", UnlockMethod.PIN, 1)), rA.effects) // capture kept
        assertTrue(rA.state.pendingFailures.isEmpty())
    }

    // ---- Biometric cancel: token-keyed no-op on request state -----------------------------------

    @Test
    fun `biometric cancel keeps the request and re-asserts the lock surface`() {
        val locked = initial().observe(other("com.a")).state
        val token = RequestToken(epoch, locked.activeRequest!!.id)
        val r = LockEngineReducer.reduce(locked, EngineEvent.BiometricCancelled(token))
        assertEquals(locked.activeRequest, r.state.activeRequest) // not cleared (contrast with dismiss)
        assertEquals(listOf(Effect.Present(Surface.Lock("com.a", locked.activeRequest!!.id))), r.effects)
    }

    @Test
    fun `a stale biometric cancel is ignored`() {
        val locked = initial().observe(other("com.a")).state
        val r = LockEngineReducer.reduce(locked, EngineEvent.BiometricCancelled(RequestToken(epoch, RequestId(99))))
        assertEquals(locked, r.state)
        assertTrue(r.effects.isEmpty())
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
        val r = LockEngineReducer.reduce(
            locked,
            EngineEvent.UnlockSucceeded(RequestToken(locked.epoch, surface.id), UnlockMethod.PIN),
        )
        assertNull(r.state.activeRequest)
    }

    @Test
    fun `process death leaves no ghost request and rejects pre-death completions`() {
        val reborn = EngineState(epoch = Epoch(2)) // fresh process: new epoch, no active request
        val preDeath = RequestToken(Epoch(1), RequestId(0))
        val r = LockEngineReducer.reduce(reborn, EngineEvent.UnlockSucceeded(preDeath, UnlockMethod.PIN))
        assertNull(r.state.activeRequest)
        assertEquals(reborn, r.state)
        assertTrue(r.effects.isEmpty())
    }

    @Test
    fun `dismiss clears the request and sends the user home`() {
        val locked = initial().observe(other("com.a")).state
        val token = RequestToken(epoch, locked.activeRequest!!.id)
        val r = LockEngineReducer.reduce(locked, EngineEvent.Dismissed(token))
        assertNull(r.state.activeRequest)
        assertEquals(listOf(Effect.GoHome, Effect.DismissSurface), r.effects)
    }

    @Test
    fun `a stale dismiss is rejected`() {
        val locked = initial().observe(other("com.a")).state
        val stale = RequestToken(epoch, RequestId(99))
        val r = LockEngineReducer.reduce(locked, EngineEvent.Dismissed(stale))
        assertEquals(locked, r.state)
        assertTrue(r.effects.isEmpty())
    }

    // ---- Screen off -----------------------------------------------------------------------------

    @Test
    fun `screen off clears sessions, tears down a live surface, and resets foreground`() {
        val locked = initial().observe(other("com.a")).state
        val r = LockEngineReducer.reduce(locked, EngineEvent.ScreenOff)
        assertNull(r.state.activeRequest)
        assertNull(r.state.lastForeground)
        assertTrue(r.effects.contains(Effect.ClearSessions))
        assertTrue(r.effects.contains(Effect.DismissSurface))
    }
}
