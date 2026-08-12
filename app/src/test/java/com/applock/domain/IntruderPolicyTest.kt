package com.applock.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntruderPolicyTest {

    private var enabled = true
    private var threshold = 5
    private val policy = IntruderPolicy(enabled = { enabled }, threshold = { threshold })

    @Test
    fun `never fires when disabled`() {
        enabled = false
        for (failures in 1..20) {
            assertFalse(policy.shouldCapture(failures))
        }
    }

    @Test
    fun `fires exactly at the threshold`() {
        assertFalse(policy.shouldCapture(4))
        assertTrue(policy.shouldCapture(5))
        assertFalse(policy.shouldCapture(6))
    }

    @Test
    fun `fires again at every multiple of the threshold`() {
        assertTrue(policy.shouldCapture(10))
        assertTrue(policy.shouldCapture(15))
        assertFalse(policy.shouldCapture(11))
        assertFalse(policy.shouldCapture(14))
    }

    @Test
    fun `respects a changed threshold immediately`() {
        threshold = 3
        assertTrue(policy.shouldCapture(3))
        assertFalse(policy.shouldCapture(5))
        assertTrue(policy.shouldCapture(6))
    }

    @Test
    fun `zero and negative counts never fire`() {
        assertFalse(policy.shouldCapture(0))
        assertFalse(policy.shouldCapture(-5))
    }

    @Test
    fun `nonsensical threshold is coerced instead of crashing`() {
        threshold = 0
        // Coerced to 1: every failure is a capture rather than division by zero.
        assertTrue(policy.shouldCapture(1))
        assertTrue(policy.shouldCapture(2))
        threshold = -7
        assertTrue(policy.shouldCapture(3))
    }

    @Test
    fun `default threshold matches FR-081`() {
        assertTrue(IntruderPolicy.DEFAULT_THRESHOLD == 5)
        assertTrue(IntruderPolicy.DEFAULT_THRESHOLD in IntruderPolicy.THRESHOLD_CHOICES)
    }

    @Test
    fun `threshold of one fires on every failure`() {
        threshold = 1
        for (failures in 1..10) {
            assertTrue(policy.shouldCapture(failures))
        }
    }

    @Test
    fun `extreme failure counts behave sanely`() {
        // 2_147_483_645 = Int.MAX_VALUE - 2 is divisible by 5.
        assertTrue(policy.shouldCapture(2_147_483_645))
        assertFalse(policy.shouldCapture(Int.MAX_VALUE)) // % 5 == 2
        assertFalse(policy.shouldCapture(Int.MIN_VALUE))
    }

    @Test
    fun `toggling enabled mid-stream is honored per call`() {
        assertTrue(policy.shouldCapture(5))
        enabled = false
        assertFalse(policy.shouldCapture(5))
        enabled = true
        assertTrue(policy.shouldCapture(5))
    }
}
