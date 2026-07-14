package com.applock.applocker.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockSessionManagerTest {

    private var policy = RelockPolicy.IMMEDIATE
    private var now = 0L
    private val manager = LockSessionManager(policyProvider = { policy }, clock = { now })

    private companion object {
        const val PKG = "com.example.app"
    }

    @Test
    fun `unknown app has no session`() {
        assertFalse(manager.hasValidSession(PKG))
    }

    @Test
    fun `unlocked app has session while in foreground`() {
        manager.markUnlocked(PKG)
        assertTrue(manager.hasValidSession(PKG))
    }

    // --- IMMEDIATE ---

    @Test
    fun `immediate policy relocks the moment the app is left`() {
        policy = RelockPolicy.IMMEDIATE
        manager.markUnlocked(PKG)
        manager.onAppLeft(PKG)
        assertFalse(manager.hasValidSession(PKG))
    }

    // --- GRACE_10S ---

    @Test
    fun `grace policy keeps session within the grace window`() {
        policy = RelockPolicy.GRACE_10S
        manager.markUnlocked(PKG)
        now = 100_000; manager.onAppLeft(PKG)
        now = 100_000 + LockSessionManager.GRACE_MILLIS // exactly at the boundary
        assertTrue(manager.hasValidSession(PKG))
    }

    @Test
    fun `grace policy relocks after the grace window`() {
        policy = RelockPolicy.GRACE_10S
        manager.markUnlocked(PKG)
        now = 100_000; manager.onAppLeft(PKG)
        now = 100_000 + LockSessionManager.GRACE_MILLIS + 1
        assertFalse(manager.hasValidSession(PKG))
    }

    @Test
    fun `returning within grace resets the session to foreground`() {
        policy = RelockPolicy.GRACE_10S
        manager.markUnlocked(PKG)
        now = 100_000; manager.onAppLeft(PKG)
        now = 105_000
        assertTrue(manager.hasValidSession(PKG)) // re-enter within grace
        // Much later, still foreground — must remain valid (timestamp was reset).
        now = 500_000
        assertTrue(manager.hasValidSession(PKG))
    }

    @Test
    fun `expired grace session is removed, not just reported invalid`() {
        policy = RelockPolicy.GRACE_10S
        manager.markUnlocked(PKG)
        now = 100_000; manager.onAppLeft(PKG)
        now = 200_000
        assertFalse(manager.hasValidSession(PKG))
        // A second check right away must also fail (entry pruned).
        assertFalse(manager.hasValidSession(PKG))
    }

    // --- SCREEN_OFF ---

    @Test
    fun `screen-off policy keeps session across app switches`() {
        policy = RelockPolicy.SCREEN_OFF
        manager.markUnlocked(PKG)
        manager.onAppLeft(PKG)
        now = 999_999_999
        assertTrue(manager.hasValidSession(PKG))
    }

    @Test
    fun `clearAllSessions ends every session regardless of policy`() {
        policy = RelockPolicy.SCREEN_OFF
        manager.markUnlocked(PKG)
        manager.markUnlocked("com.other.app")
        manager.clearAllSessions()
        assertFalse(manager.hasValidSession(PKG))
        assertFalse(manager.hasValidSession("com.other.app"))
    }

    @Test
    fun `policy change applies to existing sessions`() {
        policy = RelockPolicy.SCREEN_OFF
        manager.markUnlocked(PKG)
        manager.onAppLeft(PKG)
        assertTrue(manager.hasValidSession(PKG))
        // User tightens the policy; next departure must relock immediately.
        policy = RelockPolicy.IMMEDIATE
        manager.onAppLeft(PKG)
        assertFalse(manager.hasValidSession(PKG))
    }
}
