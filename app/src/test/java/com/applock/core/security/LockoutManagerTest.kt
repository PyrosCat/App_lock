package com.applock.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LockoutManagerTest {

    private class FakeStorage : LockoutStorage {
        override var failureCount: Int = 0
        override var lockoutUntil: Long = 0L
    }

    private var now = 1_000_000L
    private val storage = FakeStorage()
    private val manager = LockoutManager(storage, clock = { now })

    private fun failTimes(n: Int): LockoutState {
        var state: LockoutState = LockoutState.Available
        repeat(n) { state = manager.recordFailure() }
        return state
    }

    @Test
    fun `no lockout below threshold`() {
        val state = failTimes(LockoutManager.FAILURE_THRESHOLD - 1)
        assertEquals(LockoutState.Available, state)
        assertEquals(LockoutState.Available, manager.currentState())
    }

    @Test
    fun `threshold failure triggers base lockout`() {
        val state = failTimes(LockoutManager.FAILURE_THRESHOLD)
        assertEquals(LockoutState.LockedOut(LockoutManager.BASE_LOCKOUT_MS), state)
    }

    @Test
    fun `lockout expires after its duration`() {
        failTimes(LockoutManager.FAILURE_THRESHOLD)
        now += LockoutManager.BASE_LOCKOUT_MS - 1
        assertTrue(manager.currentState() is LockoutState.LockedOut)
        now += 1
        assertEquals(LockoutState.Available, manager.currentState())
    }

    @Test
    fun `delay doubles with each failure past the threshold`() {
        failTimes(LockoutManager.FAILURE_THRESHOLD)
        now += LockoutManager.BASE_LOCKOUT_MS

        val sixth = manager.recordFailure()
        assertEquals(LockoutState.LockedOut(2 * LockoutManager.BASE_LOCKOUT_MS), sixth)

        now += 2 * LockoutManager.BASE_LOCKOUT_MS
        val seventh = manager.recordFailure()
        assertEquals(LockoutState.LockedOut(4 * LockoutManager.BASE_LOCKOUT_MS), seventh)
    }

    @Test
    fun `lockout duration is capped`() {
        val state = failTimes(50)
        assertEquals(LockoutState.LockedOut(LockoutManager.MAX_LOCKOUT_MS), state)
    }

    @Test
    fun `success resets counter and lockout`() {
        failTimes(LockoutManager.FAILURE_THRESHOLD)
        manager.recordSuccess()
        assertEquals(LockoutState.Available, manager.currentState())
        // Next failure run needs the full threshold again.
        val state = failTimes(LockoutManager.FAILURE_THRESHOLD - 1)
        assertEquals(LockoutState.Available, state)
    }

    @Test
    fun `counter survives process restart via storage`() {
        failTimes(LockoutManager.FAILURE_THRESHOLD - 1)
        // Simulate process death: new manager over the same storage.
        val revived = LockoutManager(storage, clock = { now })
        val state = revived.recordFailure()
        assertEquals(LockoutState.LockedOut(LockoutManager.BASE_LOCKOUT_MS), state)
    }

    @Test
    fun `active lockout survives process restart`() {
        failTimes(LockoutManager.FAILURE_THRESHOLD)
        val revived = LockoutManager(storage, clock = { now })
        assertTrue(revived.currentState() is LockoutState.LockedOut)
    }

    @Test
    fun `clock moving backwards cannot extend lockout past the cap`() {
        failTimes(LockoutManager.FAILURE_THRESHOLD)
        now -= 24 * 60 * 60_000L // user set the clock back a day
        val state = manager.currentState()
        assertTrue(state is LockoutState.LockedOut)
        assertTrue((state as LockoutState.LockedOut).remainingMs <= LockoutManager.MAX_LOCKOUT_MS)
    }
}
