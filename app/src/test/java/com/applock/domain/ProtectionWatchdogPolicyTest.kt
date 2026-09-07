package com.applock.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM coverage of the watchdog stand-down rule (M7 WP2 Phase 0). The `Service` lifecycle that
 * consumes this stays an instrumentation test.
 */
class ProtectionWatchdogPolicyTest {

    @Test
    fun `stands down when no PIN is set, regardless of policy state`() {
        assertTrue(shouldStandDown(pinSet = false, policyState = PolicyState.Loading))
        assertTrue(shouldStandDown(pinSet = false, policyState = PolicyState.Ready(setOf("com.a"))))
        assertTrue(shouldStandDown(pinSet = false, policyState = PolicyState.Failed("boom")))
    }

    @Test
    fun `stands down when policy is Ready but empty`() {
        assertTrue(shouldStandDown(pinSet = true, policyState = PolicyState.Ready(emptySet())))
    }

    @Test
    fun `stays up when protecting a Ready non-empty set`() {
        assertFalse(shouldStandDown(pinSet = true, policyState = PolicyState.Ready(setOf("com.a"))))
    }

    @Test
    fun `stays up while policy is still Loading (R-005 cold-start fail-secure)`() {
        assertFalse(shouldStandDown(pinSet = true, policyState = PolicyState.Loading))
    }

    @Test
    fun `stays up while policy has Failed (readiness unknown)`() {
        assertFalse(shouldStandDown(pinSet = true, policyState = PolicyState.Failed("boom")))
    }
}
