package com.applock.security

/**
 * Persisted failure counters. Backed by storage that survives process death so
 * lockout cannot be bypassed by force-stopping the app (FR-174).
 */
interface LockoutStorage {
    var failureCount: Int

    /** Epoch millis until which authentication is blocked; 0 = not locked out. */
    var lockoutUntil: Long
}

sealed interface LockoutState {
    data object Available : LockoutState
    data class LockedOut(val remainingMs: Long) : LockoutState
}

/**
 * Brute-force protection (FR-009/FR-010/FR-174). Pure Kotlin — clock and
 * storage are injected so this can be unit-tested on the JVM.
 *
 * Failures below [FAILURE_THRESHOLD] are only counted. From the threshold on,
 * every failure starts a lockout window whose duration doubles per failure
 * ([BASE_LOCKOUT_MS] at the threshold, capped at [MAX_LOCKOUT_MS]). A
 * successful authentication resets everything.
 */
class LockoutManager(
    private val storage: LockoutStorage,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    @Synchronized
    fun currentState(): LockoutState {
        val until = storage.lockoutUntil
        if (until == NOT_LOCKED) return LockoutState.Available
        val now = clock()
        // Guard against device clock moving backwards: never report more
        // time remaining than the longest possible window.
        val remaining = (until - now).coerceAtMost(MAX_LOCKOUT_MS)
        if (remaining <= 0) {
            storage.lockoutUntil = NOT_LOCKED
            return LockoutState.Available
        }
        return LockoutState.LockedOut(remaining)
    }

    /** Records a failed attempt and returns the resulting state. */
    @Synchronized
    fun recordFailure(): LockoutState {
        val count = storage.failureCount + 1
        storage.failureCount = count
        if (count < FAILURE_THRESHOLD) return LockoutState.Available
        val duration = lockoutDurationFor(count)
        storage.lockoutUntil = clock() + duration
        return LockoutState.LockedOut(duration)
    }

    /** The [state] of a recorded failure and the [count] that produced it, read as one observation. */
    data class FailureOutcome(val state: LockoutState, val count: Int)

    /**
     * Records a failure and returns its state and its resulting count under one lock, so the two describe the
     * same operation. If a caller reads [recordFailure] and [failureCount] in two separate calls, another writer
     * (for example, the self-gate) can interleave between the calls, and the caller can then read a mismatched pair.
     */
    @Synchronized
    fun recordFailureAndCount(): FailureOutcome = FailureOutcome(recordFailure(), storage.failureCount)

    @Synchronized
    fun recordSuccess() {
        storage.failureCount = 0
        storage.lockoutUntil = NOT_LOCKED
    }

    /** Consecutive failures since the last success (drives FR-081 capture). */
    @Synchronized
    fun failureCount(): Int = storage.failureCount

    companion object {
        const val FAILURE_THRESHOLD = 5
        const val BASE_LOCKOUT_MS = 30_000L
        const val MAX_LOCKOUT_MS = 30 * 60_000L

        fun lockoutDurationFor(failureCount: Int): Long {
            val doublings = (failureCount - FAILURE_THRESHOLD).coerceIn(0, 20)
            return (BASE_LOCKOUT_MS shl doublings).coerceAtMost(MAX_LOCKOUT_MS)
        }

        private const val NOT_LOCKED = 0L
    }
}
