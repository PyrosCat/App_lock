package com.applock.presentation.authentication

import android.os.SystemClock
import com.applock.service.engine.RequestToken
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * A handle to one single-flight biometric lease (M7 WP2 change E). [claim] creates the handle and gives it to
 * the caller. The host acknowledges the exact (token, leaseId) with [BiometricLaunchGate.acknowledge], which
 * completes this handle's one-shot latch. The latch stays on the handle, not in the gate state. Thus an
 * awaiter still sees the acknowledgement after the host releases the gate (no acknowledged-then-released race).
 * [awaitAck] suspends until acknowledgement; [isAcknowledged] reads the state without suspension.
 */
class LeaseHandle internal constructor(
    val token: RequestToken,
    val leaseId: Long,
) {
    private val ack = CompletableDeferred<Unit>()

    /** Completes the one-shot latch. Idempotent (a second call is a no-op). Called by the gate on acknowledge. */
    internal fun completeAck() {
        ack.complete(Unit)
    }

    val isAcknowledged: Boolean get() = ack.isCompleted

    /** Suspends (cancellably) until this lease is acknowledged. Returns immediately if already acknowledged. */
    internal suspend fun awaitAck() {
        ack.await()
    }
}

/**
 * A process-scoped, request-token-keyed single-flight gate for the overlay's biometric launch (M7 WP2
 * change E). The overlay ([com.applock.platform.lock.OverlayLockPresenter]) and the transparent
 * [BiometricHostActivity] are separate windows, so the legacy `LockScreenActivity` in-instance
 * `biometricInFlight` guard cannot cross the launch boundary.
 *
 * ## Token, lease, and the acknowledgement latch
 * Each successful [claim] creates a [LeaseHandle]: a unique lease id and a one-shot latch. The presenter
 * claims before the background-activity-launch and passes the lease id to the host. The host [acknowledge]s
 * (token + lease) in `onCreate`, which completes the latch. Both [release] (token + lease) when done.
 *
 * The gate matches on the token AND the lease id, and it consumes the auto-prompt on the LATCH, not on
 * "startActivity returned". This tells a launch that was only REQUESTED apart from a host that actually
 * STARTED: `Activity.startActivity` does not throw when the system aborts a background launch silently
 * (`START_ABORTED` is not fatal). An unacknowledged lease EXPIRES after [LEASE_TIMEOUT_MS]. After it expires,
 * the same token or a different token can [claim] again and gets a FRESH lease, so a late old host cannot
 * [acknowledge] or [release] the new claim. An acknowledged lease does not expire (a real prompt can stay
 * up). Only a matching [release] frees it (the host's terminal completion, with `onDestroy` as an idempotent
 * fallback), or process death, which resets the object.
 *
 * ## Why the token AND the lease
 * A token-only guard let a stale, restored host free a newer host's claim. In the aborted-launch case it also
 * left the gate owned forever by a token whose host never existed, so every later request read BUSY and only
 * the PIN pad worked. The lease closes both cases: a stale host's (token, oldLease) matches nothing, and an
 * abandoned lease is reclaimable.
 */
object BiometricLaunchGate {

    /** The result of a [claim]: whether, and how, the caller's token owns the launch. */
    sealed interface ClaimOutcome {
        /** The gate was free or held an abandoned lease. The caller now owns this fresh [lease]. */
        data class Claimed(val lease: LeaseHandle) : ClaimOutcome

        /** The caller's token already owns this live [lease] (for example, a manual tap launched a host). */
        data class OwnedByToken(val lease: LeaseHandle) : ClaimOutcome

        /** A different token owns a live lease. The caller must not launch or authenticate (a stale host). */
        data object BusyOther : ClaimOutcome
    }

    /** The gate state: the owning lease handle, whether a host has acknowledged, and when it was claimed. */
    private data class Claim(
        val handle: LeaseHandle,
        val acknowledged: Boolean,
        val claimedAtMs: Long,
    )

    private val state = AtomicReference<Claim?>(null)
    private val leases = AtomicLong(0L)

    // The production clock uses monotonic elapsed-realtime, which a wall-clock change does not affect. It is
    // declared before `clock` because the object initializes top to bottom.
    private val defaultClock: () -> Long = { SystemClock.elapsedRealtime() }

    // Injectable, so the JVM tests control lease expiry. resetForTest restores the default.
    @Volatile
    private var clock: () -> Long = defaultClock

    /**
     * Claims the gate for [token] and reports the result. [ClaimOutcome.Claimed] (a fresh or reclaimed lease)
     * and [ClaimOutcome.OwnedByToken] both mean [token] owns the launch: proceed, then await the lease's
     * acknowledgement. [ClaimOutcome.BusyOther] means a different request owns a live lease: do not proceed. An
     * abandoned lease (unacknowledged and expired, such as an aborted launch) is reclaimed with a FRESH lease,
     * whatever token held it, so the same request recovers after an aborted launch. Retries the CAS on a race.
     */
    fun claim(token: RequestToken): ClaimOutcome {
        while (true) {
            val current = state.get()
            // A free or abandoned lease gives a fresh claim. This branch comes BEFORE the same-token branch, so
            // an abandoned same-token lease also gets a NEW lease and a late old host cannot ack or release it.
            // A live same-token claim is owned (return its handle to await). A live foreign claim is busy.
            val fresh = when {
                current == null -> newClaim(token)
                current.isAbandoned() -> newClaim(token)
                current.handle.token == token -> return ClaimOutcome.OwnedByToken(current.handle)
                else -> return ClaimOutcome.BusyOther
            }
            if (state.compareAndSet(current, fresh)) return ClaimOutcome.Claimed(fresh.handle)
        }
    }

    /**
     * The host acknowledges that it shows, or is about to show, the prompt for (its [token], [leaseId]).
     * Returns true ONLY when the gate still holds that exact, unexpired lease. It then marks the lease
     * non-expiring and completes the handle's latch, so a waiter is released even if this host releases the
     * gate straight after. Returns false for a free gate, an expired (abandoned) exact lease, or any foreign
     * token or lease, so the caller finishes without authenticating. The gate never adopts an unowned lease.
     * Per ADR-020, full process death does not restore the old request: a restored old-process host finishes
     * silently and the fresh runtime re-derives. Rejecting the free, expired, and foreign cases also stops a
     * stale prompt from blocking a newer request's biometric path.
     */
    fun acknowledge(token: RequestToken, leaseId: Long): Boolean {
        while (true) {
            val current = state.get() ?: return false // free gate (process death or released): never adopt
            // Only our own, still-live lease can be acknowledged. A foreign token or lease, or our own EXPIRED
            // (abandoned) lease, is rejected, so the host finishes silently. A reclaim reassigns the lease id,
            // so a stolen lease is foreign here and a late old host cannot acknowledge over the new owner.
            if (current.handle.token != token || current.handle.leaseId != leaseId || current.isAbandoned()) {
                return false
            }
            // Acknowledge on the CAS, or if already acknowledged (a benign re-ack). completeAck is idempotent,
            // so the already-acked path is a safe no-op. It releases any awaiter, and the latch survives a
            // later release of the gate.
            if (current.acknowledged || state.compareAndSet(current, current.copy(acknowledged = true))) {
                current.handle.completeAck()
                return true
            }
        }
    }

    /**
     * Frees the gate ONLY if the current claim matches BOTH [token] and [leaseId] by value. A stale or foreign
     * host (an old lease id, or a foreign token) is a no-op, so it cannot free a newer claim.
     */
    fun release(token: RequestToken, leaseId: Long) {
        while (true) {
            val current = state.get() ?: return
            if (current.handle.token != token || current.handle.leaseId != leaseId) return // stale/foreign: no-op
            if (state.compareAndSet(current, null)) return
        }
    }

    /** The token that owns the current claim, or null if free. Exposed for diagnostics and tests. */
    val claimedBy: RequestToken? get() = state.get()?.handle?.token

    private fun newClaim(token: RequestToken) =
        Claim(LeaseHandle(token, leases.incrementAndGet()), acknowledged = false, claimedAtMs = clock())

    /** Unacknowledged and older than the lease timeout. The launch returned, but no host started. */
    private fun Claim.isAbandoned(): Boolean = !acknowledged && clock() - claimedAtMs >= LEASE_TIMEOUT_MS

    /** TEST-ONLY: clears the gate and restores the production clock (production release is token+lease-scoped). */
    internal fun resetForTest() {
        state.set(null)
        clock = defaultClock
    }

    /** TEST-ONLY: drives lease expiry deterministically. */
    internal fun setClockForTest(source: () -> Long) {
        clock = source
    }

    /**
     * How long an UNACKNOWLEDGED lease is honoured before it becomes reclaimable. It must comfortably exceed a
     * host's launch-to-`onCreate` latency (so a slow but legitimate launch is not stolen and double-prompted)
     * yet be short enough that an aborted launch does not wedge biometrics for long (the PIN pad remains the
     * whole time). A stolen slow host still self-heals: its [acknowledge] no longer matches, so it finishes.
     * Internal so the presenter's ack-await timeout and the JVM expiry tests key off the exact value.
     */
    internal const val LEASE_TIMEOUT_MS = 4_000L
}
