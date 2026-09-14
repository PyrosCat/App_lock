package com.applock.service.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM tests for the app-scoped completion seam (M7 WP2 change E). They prove the three properties E owes:
 * the bind is one-time, an unbound bridge drops completions (fail-closed, never queued), and a bound bridge
 * forwards each completion to its delegate and reports ACCEPTED.
 */
class LockCompletionBridgeTest {

    private val epoch = Epoch(1)

    private fun request(id: Long) = RequestToken(epoch, RequestId(id))

    /** Records the completions it receives, so a test can assert both forwarding and its absence. */
    private class RecordingSink : LockCompletionSink {
        val calls = mutableListOf<String>()

        override fun unlockSucceeded(token: RequestToken, method: UnlockMethod): LockCompletionResult {
            calls += "success:${token.id.n}:$method"
            return LockCompletionResult.ACCEPTED
        }

        override fun unlockFailed(token: RequestToken, method: UnlockMethod): LockCompletionResult {
            calls += "fail:${token.id.n}:$method"
            return LockCompletionResult.ACCEPTED
        }

        override fun biometricCancelled(token: RequestToken): LockCompletionResult {
            calls += "cancel:${token.id.n}"
            return LockCompletionResult.ACCEPTED
        }

        override fun safeDismissRequested(
            token: SurfaceToken,
            destination: SafeDestination,
            exemptPackages: Set<String>,
        ): LockCompletionResult {
            calls += "dismiss:$destination:${exemptPackages.size}"
            return LockCompletionResult.ACCEPTED
        }
    }

    @Before
    fun clearBridge() = LockCompletionBridge.resetForTest()

    @After
    fun tearDown() = LockCompletionBridge.resetForTest()

    @Test
    fun `an unbound bridge drops every completion with UNAVAILABLE`() {
        assertFalse(LockCompletionBridge.isBound)
        assertEquals(
            LockCompletionResult.UNAVAILABLE,
            LockCompletionBridge.unlockSucceeded(request(1), UnlockMethod.PIN),
        )
        assertEquals(
            LockCompletionResult.UNAVAILABLE,
            LockCompletionBridge.unlockFailed(request(1), UnlockMethod.PIN),
        )
        assertEquals(
            LockCompletionResult.UNAVAILABLE,
            LockCompletionBridge.biometricCancelled(request(1)),
        )
        assertEquals(
            LockCompletionResult.UNAVAILABLE,
            LockCompletionBridge.safeDismissRequested(request(1), SafeDestination.HOME),
        )
    }

    @Test
    fun `bind is one-time and the second bind is rejected`() {
        val first = RecordingSink()
        val second = RecordingSink()
        assertTrue(LockCompletionBridge.bind(first))
        assertFalse(LockCompletionBridge.bind(second))
        assertTrue(LockCompletionBridge.isBound)

        LockCompletionBridge.unlockSucceeded(request(7), UnlockMethod.PIN)
        assertEquals(listOf("success:7:PIN"), first.calls)
        assertTrue(second.calls.isEmpty())
    }

    @Test
    fun `a bound bridge forwards each completion and returns ACCEPTED`() {
        val sink = RecordingSink()
        LockCompletionBridge.bind(sink)

        assertEquals(
            LockCompletionResult.ACCEPTED,
            LockCompletionBridge.unlockSucceeded(request(1), UnlockMethod.BIOMETRIC),
        )
        LockCompletionBridge.unlockFailed(request(2), UnlockMethod.PIN)
        LockCompletionBridge.biometricCancelled(request(3))
        LockCompletionBridge.safeDismissRequested(
            ReadinessToken(epoch, Generation(4)),
            SafeDestination.OVERLAY_SETTINGS,
            setOf("com.android.settings"),
        )

        assertEquals(
            listOf("success:1:BIOMETRIC", "fail:2:PIN", "cancel:3", "dismiss:OVERLAY_SETTINGS:1"),
            sink.calls,
        )
    }
}
