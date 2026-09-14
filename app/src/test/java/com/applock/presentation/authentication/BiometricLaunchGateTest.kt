package com.applock.presentation.authentication

import com.applock.presentation.authentication.BiometricLaunchGate.ClaimOutcome
import com.applock.service.engine.Epoch
import com.applock.service.engine.RequestId
import com.applock.service.engine.RequestToken
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM tests for [BiometricLaunchGate] (M7 WP2 change E), the overlay's token+lease-keyed biometric
 * single-flight gate. They drive an injected clock so lease expiry is deterministic, and cover: a free gate
 * is claimed; a live same-token re-claim is owned-by-token; a foreign token is busy; a normal acknowledged
 * host never expires and is freed only by a matching release; an ABORTED launch (a claim whose host never
 * acknowledged) expires and is reclaimable by the same OR a different token with a FRESH lease; a delayed,
 * stale acknowledgement / release from a reclaimed lease is ignored; release matches by value (token + lease),
 * not identity; and acknowledge accepts ONLY an exact, unexpired lease. A free gate (process death), an
 * expired (abandoned) exact lease, and a late host over a free gate are all rejected (ADR-020: process death
 * does not restore the old request).
 */
class BiometricLaunchGateTest {

    private val a = RequestToken(Epoch(1), RequestId(1))
    private val b = RequestToken(Epoch(1), RequestId(2))
    private val lease = BiometricLaunchGate.LEASE_TIMEOUT_MS
    private var now = 0L

    @Before
    fun setUp() {
        BiometricLaunchGate.resetForTest()
        BiometricLaunchGate.setClockForTest { now }
    }

    @After
    fun tearDown() = BiometricLaunchGate.resetForTest() // process singleton; reset (and restore the clock)

    private fun claimHandle(token: RequestToken): LeaseHandle {
        val outcome = BiometricLaunchGate.claim(token)
        assertTrue("expected a fresh CLAIMED lease", outcome is ClaimOutcome.Claimed)
        return (outcome as ClaimOutcome.Claimed).lease
    }

    private fun claimLease(token: RequestToken): Long = claimHandle(token).leaseId

    @Test
    fun `a free gate is claimed`() {
        assertTrue(BiometricLaunchGate.claim(a) is ClaimOutcome.Claimed)
        assertEquals(a, BiometricLaunchGate.claimedBy)
    }

    @Test
    fun `the same token re-claiming a live lease is owned-by-token`() {
        BiometricLaunchGate.claim(a)
        assertTrue(BiometricLaunchGate.claim(a) is ClaimOutcome.OwnedByToken)
        assertEquals(a, BiometricLaunchGate.claimedBy)
    }

    @Test
    fun `a foreign token is busy while a live lease is in flight`() {
        BiometricLaunchGate.claim(a)
        assertEquals(ClaimOutcome.BusyOther, BiometricLaunchGate.claim(b))
        assertEquals(a, BiometricLaunchGate.claimedBy)
    }

    @Test
    fun `an acknowledged host never expires and is freed only by a matching release`() {
        val leaseA = claimLease(a)
        assertTrue("the host acknowledges its own lease", BiometricLaunchGate.acknowledge(a, leaseA))

        now += lease + 1 // long past the lease timeout: an ACKNOWLEDGED lease must not become stealable
        assertEquals(ClaimOutcome.BusyOther, BiometricLaunchGate.claim(b))
        assertEquals(a, BiometricLaunchGate.claimedBy)

        BiometricLaunchGate.release(a, leaseA)
        assertNull(BiometricLaunchGate.claimedBy)
    }

    @Test
    fun `an aborted launch's unacknowledged lease expires and the same token reclaims with a fresh lease`() {
        val leaseA = claimLease(a) // the presenter claimed; startActivity was aborted; no host ever acknowledged

        now += lease + 1 // the unacknowledged lease expires
        val outcome = BiometricLaunchGate.claim(a) // the SAME request retries (a manual tap)
        assertTrue("an expired unacknowledged lease must be reclaimable", outcome is ClaimOutcome.Claimed)
        assertNotEquals(
            "the reclaim must mint a FRESH lease, so a late old host cannot ack/release it",
            leaseA,
            (outcome as ClaimOutcome.Claimed).lease.leaseId,
        )
        assertEquals(a, BiometricLaunchGate.claimedBy)
    }

    @Test
    fun `an aborted launch's expired lease can be reclaimed by a different token`() {
        claimLease(a)
        now += lease + 1
        assertTrue(
            "a different request must reclaim an abandoned lease",
            BiometricLaunchGate.claim(b) is ClaimOutcome.Claimed,
        )
        assertEquals(b, BiometricLaunchGate.claimedBy)
    }

    @Test
    fun `a delayed acknowledgement from a reclaimed lease is ignored and does not disturb the new owner`() {
        val leaseA = claimLease(a) // aborted launch: host never started
        now += lease + 1

        // A new request reclaims the abandoned lease and its host comes up (acknowledges).
        val leaseB = claimLease(b)
        assertTrue(BiometricLaunchGate.acknowledge(b, leaseB))

        // The ORIGINAL host finally runs onCreate and tries to acknowledge / release its stale lease: both are
        // no-ops, so the live owner (b) keeps the gate.
        assertFalse(
            "a stale/reclaimed lease must not acknowledge over the live owner",
            BiometricLaunchGate.acknowledge(a, leaseA),
        )
        assertEquals(b, BiometricLaunchGate.claimedBy)
        BiometricLaunchGate.release(a, leaseA)
        assertEquals(b, BiometricLaunchGate.claimedBy)
    }

    @Test
    fun `release matches by value not identity - a reconstructed token plus lease frees the gate`() {
        // The presenter claims with one instance; the host reconstructs an epoch+id token (a value-equal but
        // DISTINCT reference) and releases with it. Identity-based CAS would leave the gate stuck.
        val ownerInstance = RequestToken(Epoch(1), RequestId(1))
        val reconstructed = RequestToken(Epoch(1), RequestId(1))
        assertNotSame("the test needs two distinct instances", ownerInstance, reconstructed)

        val leaseId = claimLease(ownerInstance)
        BiometricLaunchGate.release(reconstructed, leaseId)
        assertNull(
            "a value-equal reconstructed token + matching lease must free the gate",
            BiometricLaunchGate.claimedBy,
        )
    }

    @Test
    fun `a wrong token or wrong lease release is a no-op - the matching one frees it`() {
        val leaseA = claimLease(a)
        val foreignLease = leaseA + 100L

        BiometricLaunchGate.release(b, leaseA) // wrong token
        assertEquals(a, BiometricLaunchGate.claimedBy)
        BiometricLaunchGate.release(a, foreignLease) // right token, wrong lease
        assertEquals(a, BiometricLaunchGate.claimedBy)

        BiometricLaunchGate.release(a, leaseA) // correct token + lease
        assertNull(BiometricLaunchGate.claimedBy)
    }

    @Test
    fun `a foreign token can claim after the owner releases (wake after release)`() {
        val leaseA = claimLease(a)
        assertEquals(ClaimOutcome.BusyOther, BiometricLaunchGate.claim(b))

        BiometricLaunchGate.release(a, leaseA)
        assertTrue(BiometricLaunchGate.claim(b) is ClaimOutcome.Claimed)
        assertEquals(b, BiometricLaunchGate.claimedBy)
    }

    @Test
    fun `acknowledge on a free gate is rejected - process death does not restore the old request`() {
        // Full process death empties the singleton gate. A restored old-process host (carrying a stale lease
        // from savedInstanceState) must NOT adopt the free gate and authenticate (ADR-020): it finishes
        // silently, and the fresh runtime re-derives and presents a new request.
        val restoredLease = 7L // carried in the restored host's savedInstanceState from the dead process
        assertFalse("a restored host must not adopt a free gate", BiometricLaunchGate.acknowledge(a, restoredLease))
        assertNull("acknowledge must not create a claim", BiometricLaunchGate.claimedBy)
    }

    @Test
    fun `an exact but expired (abandoned) lease acknowledging is rejected`() {
        val leaseA = claimLease(a) // claimed but never acknowledged in time (e.g. a very slow / aborted launch)
        now += lease + 1 // the exact lease is now abandoned (unacknowledged past the timeout)
        assertFalse(
            "an expired exact lease must not acknowledge - the request may no longer be current",
            BiometricLaunchGate.acknowledge(a, leaseA),
        )
    }

    @Test
    fun `a late host acknowledging after a newer claim has completed is rejected`() {
        val leaseA = claimLease(a) // aborted launch: host never started
        now += lease + 1

        // A newer request reclaims, acknowledges, and completes (releases), so the gate is free again.
        val leaseB = claimLease(b)
        assertTrue(BiometricLaunchGate.acknowledge(b, leaseB))
        BiometricLaunchGate.release(b, leaseB)
        assertNull(BiometricLaunchGate.claimedBy)

        // The original host FINALLY runs onCreate and tries to acknowledge: the gate is free, so it is rejected
        // (it must not adopt), and it finishes silently.
        assertFalse("a late host over a free gate must be rejected", BiometricLaunchGate.acknowledge(a, leaseA))
        assertNull(BiometricLaunchGate.claimedBy)
    }

    @Test
    fun `acknowledge completes the exact lease handle's latch`() {
        val handle = claimHandle(a)
        assertFalse("a fresh lease is not acknowledged", handle.isAcknowledged)
        assertTrue(BiometricLaunchGate.acknowledge(a, handle.leaseId))
        assertTrue("acknowledge must complete the handle latch the awaiter observes", handle.isAcknowledged)
    }

    @Test
    fun `the acknowledgement latch survives a subsequent release (observable after the host frees the gate)`() {
        val handle = claimHandle(a)
        assertTrue(BiometricLaunchGate.acknowledge(a, handle.leaseId))
        BiometricLaunchGate.release(a, handle.leaseId) // host completes and frees the gate immediately after
        assertNull(BiometricLaunchGate.claimedBy)
        assertTrue(
            "a released lease's latch stays completed, so the auto-launch waiter still sees the ack",
            handle.isAcknowledged,
        )
    }

    @Test
    fun `a rejected acknowledge does not complete the latch`() {
        val handle = claimHandle(a)
        now += lease + 1 // the exact lease is abandoned -> acknowledge is rejected
        assertFalse(BiometricLaunchGate.acknowledge(a, handle.leaseId))
        assertFalse("a rejected acknowledge must not complete the latch", handle.isAcknowledged)
    }
}
