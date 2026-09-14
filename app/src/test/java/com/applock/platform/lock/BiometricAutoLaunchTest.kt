package com.applock.platform.lock

import com.applock.presentation.authentication.BiometricLaunchGate
import com.applock.presentation.authentication.BiometricLaunchGate.ClaimOutcome
import com.applock.presentation.authentication.LeaseHandle
import com.applock.service.engine.Epoch
import com.applock.service.engine.RequestId
import com.applock.service.engine.RequestToken
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM tests for [driveAutoLaunch] (M7 WP2 change E), the presenter's once-per-request biometric auto-launch
 * loop, extracted from the Android surface so its acknowledgement-gating, bounded retry, and cancellation are
 * testable without a real window. It must mark a request prompted ONLY after the host ACKNOWLEDGES the lease
 * (not merely because the launch call returned), wait (cancellably) for a foreign owner without spending the
 * retry budget, make at most one bounded retry after an unacknowledged lease expires, and never burn the
 * auto-prompt on a lockout / failed launch / cancellation / exhausted budget.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BiometricAutoLaunchTest {

    private val a = RequestToken(Epoch(1), RequestId(1))
    private val handle = LeaseHandle(a, 1L) // a fake lease for the Requested outcomes; awaitAck is faked per test
    private val poll = 100L
    private val maxRetries = 1
    private var retries = 0 // the single-token retry budget the presenter backs outside Compose

    private fun retriesUsed(request: RequestToken): Int = if (request == a) retries else 0
    private fun recordRetry(request: RequestToken) {
        if (request == a) retries++
    }

    // The real gate is a process singleton; a fixed clock keeps the integration test deterministic (and the
    // default SystemClock clock is not available in a plain JVM test). Harmless for the fake-driven tests.
    @Before
    fun setUp() {
        BiometricLaunchGate.resetForTest()
        BiometricLaunchGate.setClockForTest { 0L }
    }

    @After
    fun tearDown() = BiometricLaunchGate.resetForTest()

    @Test
    fun `an acknowledged launch marks the request prompted, once`() = runTest {
        var marked: RequestToken? = null
        var attempts = 0
        launch {
            driveAutoLaunch(
                request = a,
                alreadyPrompted = { false },
                markPrompted = { marked = it },
                retriesUsed = ::retriesUsed,
                recordRetry = ::recordRetry,
                maxRetries = maxRetries,
                launch = {
                    attempts++
                    LaunchOutcome.Requested(handle)
                },
                awaitAck = { true }, // the host acknowledged (already, or before we observed)
                pollDelayMs = poll,
            )
        }
        testScheduler.advanceUntilIdle()
        assertEquals(a, marked)
        assertEquals(1, attempts)
    }

    @Test
    fun `it does not mark until the host acknowledges, then marks`() = runTest {
        var marked: RequestToken? = null
        val ack = CompletableDeferred<Boolean>()
        launch {
            driveAutoLaunch(
                request = a,
                alreadyPrompted = { false },
                markPrompted = { marked = it },
                retriesUsed = ::retriesUsed,
                recordRetry = ::recordRetry,
                maxRetries = maxRetries,
                launch = { LaunchOutcome.Requested(handle) },
                awaitAck = { ack.await() }, // acknowledgement arrives AFTER the waiter is suspended
                pollDelayMs = poll,
            )
        }
        testScheduler.runCurrent()
        assertNull("must not mark before acknowledgement", marked)
        ack.complete(true)
        testScheduler.advanceUntilIdle()
        assertEquals(a, marked)
    }

    @Test
    fun `an unacknowledged lease is retried once, then gives up without marking`() = runTest {
        var marked: RequestToken? = null
        var attempts = 0
        launch {
            driveAutoLaunch(
                request = a,
                alreadyPrompted = { false },
                markPrompted = { marked = it },
                retriesUsed = ::retriesUsed,
                recordRetry = ::recordRetry,
                maxRetries = maxRetries,
                launch = {
                    attempts++
                    LaunchOutcome.Requested(handle)
                },
                awaitAck = { false }, // the lease expires unacknowledged every time
                pollDelayMs = poll,
            )
        }
        testScheduler.advanceUntilIdle()
        assertNull("an unacknowledged launch must not consume the auto-prompt", marked)
        assertEquals("initial launch + exactly one bounded retry", 2, attempts)
    }

    @Test
    fun `an exhausted retry budget does not re-launch (survives a host rebuild)`() = runTest {
        retries = maxRetries // as if a prior effect run had already spent the budget
        var marked: RequestToken? = null
        var attempts = 0
        launch {
            driveAutoLaunch(
                request = a,
                alreadyPrompted = { false },
                markPrompted = { marked = it },
                retriesUsed = ::retriesUsed,
                recordRetry = ::recordRetry,
                maxRetries = maxRetries,
                launch = {
                    attempts++
                    LaunchOutcome.Requested(handle)
                },
                awaitAck = { false },
                pollDelayMs = poll,
            )
        }
        testScheduler.advanceUntilIdle()
        assertEquals("an exhausted budget must not re-launch on a rebuild", 0, attempts)
        assertNull(marked)
    }

    @Test
    fun `an already-prompted request does not launch again`() = runTest {
        var attempts = 0
        launch {
            driveAutoLaunch(
                request = a,
                alreadyPrompted = { true },
                markPrompted = { },
                retriesUsed = ::retriesUsed,
                recordRetry = ::recordRetry,
                maxRetries = maxRetries,
                launch = {
                    attempts++
                    LaunchOutcome.Requested(handle)
                },
                awaitAck = { true },
                pollDelayMs = poll,
            )
        }
        testScheduler.advanceUntilIdle()
        assertEquals("an already-prompted request must not re-launch", 0, attempts)
    }

    @Test
    fun `a lockout suppresses the auto-prompt without retrying`() = runTest {
        var marked: RequestToken? = null
        var attempts = 0
        launch {
            driveAutoLaunch(
                request = a,
                alreadyPrompted = { false },
                markPrompted = { marked = it },
                retriesUsed = ::retriesUsed,
                recordRetry = ::recordRetry,
                maxRetries = maxRetries,
                launch = {
                    attempts++
                    LaunchOutcome.LockedOut
                },
                awaitAck = { true },
                pollDelayMs = poll,
            )
        }
        testScheduler.advanceUntilIdle()
        assertNull("a locked-out attempt must not mark the request prompted", marked)
        assertEquals("LOCKED_OUT must exit the loop, not retry", 1, attempts)
    }

    @Test
    fun `a failed launch does not consume the auto-prompt`() = runTest {
        var marked: RequestToken? = null
        launch {
            driveAutoLaunch(
                request = a,
                alreadyPrompted = { false },
                markPrompted = { marked = it },
                retriesUsed = ::retriesUsed,
                recordRetry = ::recordRetry,
                maxRetries = maxRetries,
                launch = { LaunchOutcome.LaunchFailed },
                awaitAck = { true },
                pollDelayMs = poll,
            )
        }
        testScheduler.advanceUntilIdle()
        assertNull("a failed launch must not mark the request prompted", marked)
    }

    @Test
    fun `it polls a foreign owner without spending the retry budget, then proceeds`() = runTest {
        var marked: RequestToken? = null
        var attempts = 0
        launch {
            driveAutoLaunch(
                request = a,
                alreadyPrompted = { false },
                markPrompted = { marked = it },
                retriesUsed = ::retriesUsed,
                recordRetry = ::recordRetry,
                maxRetries = maxRetries,
                launch = {
                    attempts++
                    if (attempts < 3) LaunchOutcome.BusyOther else LaunchOutcome.Requested(handle)
                },
                awaitAck = { true },
                pollDelayMs = poll,
            )
        }
        testScheduler.advanceUntilIdle()
        assertEquals("it must poll the foreign owner, then launch", a, marked)
        assertEquals(3, attempts)
        assertEquals("foreign-owner polling must not consume the retry budget", 0, retries)
    }

    @Test
    fun `cancellation during the acknowledgement wait does not consume the auto-prompt`() = runTest {
        var marked: RequestToken? = null
        val neverAck = CompletableDeferred<Boolean>()
        val job = launch {
            driveAutoLaunch(
                request = a,
                alreadyPrompted = { false },
                markPrompted = { marked = it },
                retriesUsed = ::retriesUsed,
                recordRetry = ::recordRetry,
                maxRetries = maxRetries,
                launch = { LaunchOutcome.Requested(handle) },
                awaitAck = { neverAck.await() }, // never acknowledged
                pollDelayMs = poll,
            )
        }
        testScheduler.runCurrent() // suspended awaiting acknowledgement
        job.cancelAndJoin()
        assertNull("a cancelled acknowledgement wait must not consume the once-per-request prompt", marked)
    }

    @Test
    fun `end-to-end - a real lease acknowledged then released immediately marks once with no retry`() = runTest {
        // Integration seam: drive the loop with a REAL claimed LeaseHandle and the REAL awaitAck (a
        // withTimeoutOrNull over the handle's latch), then acknowledge() and release() the gate immediately (as
        // a host does). The latch lives on the handle, so it stays observed after release: one mark, no retry.
        val claimed = BiometricLaunchGate.claim(a) as ClaimOutcome.Claimed
        var marked: RequestToken? = null
        var attempts = 0
        launch {
            driveAutoLaunch(
                request = a,
                alreadyPrompted = { false },
                markPrompted = { marked = it },
                retriesUsed = ::retriesUsed,
                recordRetry = ::recordRetry,
                maxRetries = maxRetries,
                launch = {
                    attempts++
                    LaunchOutcome.Requested(claimed.lease)
                },
                awaitAck = { handle -> withTimeoutOrNull(10_000L) { handle.awaitAck() } != null },
                pollDelayMs = poll,
            )
        }
        testScheduler.runCurrent() // launched once, now suspended on the real latch
        assertNull("must not mark before the real acknowledgement", marked)

        assertTrue(BiometricLaunchGate.acknowledge(a, claimed.lease.leaseId))
        BiometricLaunchGate.release(a, claimed.lease.leaseId) // host frees the gate immediately after
        testScheduler.advanceUntilIdle()

        assertEquals("acknowledgement (surviving the release) marks the request", a, marked)
        assertEquals("a real acknowledgement must not trigger a retry", 1, attempts)
        assertEquals(0, retries)
    }
}
