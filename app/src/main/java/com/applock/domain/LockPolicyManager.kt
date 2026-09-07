package com.applock.domain

import com.applock.data.ProtectedAppDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Decides whether a package requires locking (TAS §4.1 LockPolicyManager).
 *
 * Holds an in-memory [PolicyState] snapshot of the protected packages so the lock engine can answer
 * synchronously on every window-state event without touching the database on the main thread.
 *
 * State is a single atomic value ([state]): readiness and the package set are published together, so
 * a reader can never observe "ready" against a stale set. The cache starts [PolicyState.Loading];
 * the first store emission — including a legitimately empty set — atomically publishes
 * [PolicyState.Ready]. A load/storage error publishes [PolicyState.Failed] and retries with capped
 * backoff; coroutine cancellation is not a failure (R-005 readiness model, M7 WP2).
 *
 * The synchronous facade ([isProtected] / [evaluate]) treats every non-[PolicyState.Ready] state as
 * "not protected", preserving the pre-WP2 cold-start behavior for the untouched lock engine. The
 * fail-secure *hold* on the not-ready window is the engine-owned readiness work in later WP2 phases;
 * R-005 stays Open until WP6.
 */
class LockPolicyManager(
    private val dao: ProtectedAppDao,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow<PolicyState>(PolicyState.Loading)
    val state: StateFlow<PolicyState> = _state

    private var cachingJob: Job? = null

    /**
     * Starts collecting the protected-package set into [state]. Idempotent: a call made while a
     * collector is already active is a no-op (the sole production caller is application init).
     *
     * The broad catch is deliberate: this is a resilience boundary, so *any* load/storage failure
     * (Room can raise several unrelated runtime types) maps to [PolicyState.Failed] + retry.
     * Cancellation is caught and re-thrown first, so it is never swallowed as a failure.
     */
    @Synchronized
    @Suppress("TooGenericExceptionCaught")
    fun startCaching() {
        if (cachingJob?.isActive == true) return
        cachingJob = scope.launch {
            var backoffMs = INITIAL_BACKOFF_MS
            while (isActive) {
                try {
                    dao.observeEnabledPackages().collect { packages ->
                        _state.value = PolicyState.Ready(packages.toSet())
                        backoffMs = INITIAL_BACKOFF_MS
                    }
                    // Upstream completed normally: no error, but no further updates to await.
                    return@launch
                } catch (e: CancellationException) {
                    throw e // scope teardown — never a Failed state
                } catch (e: Exception) {
                    _state.value = PolicyState.Failed(e.message ?: e::class.java.simpleName)
                    delay(backoffMs)
                    backoffMs = (backoffMs * BACKOFF_FACTOR).coerceAtMost(MAX_BACKOFF_MS)
                }
            }
        }
    }

    /** Fast, main-thread-safe check used by the lock engine. */
    fun isProtected(packageName: String): Boolean {
        val current = _state.value
        return current is PolicyState.Ready && packageName in current.packages
    }

    fun evaluate(packageName: String, hasValidSession: Boolean): LockDecision = when {
        !isProtected(packageName) -> LockDecision(false, "not protected")
        hasValidSession -> LockDecision(false, "valid unlock session")
        else -> LockDecision(true, "protected app, no session")
    }

    private companion object {
        const val INITIAL_BACKOFF_MS = 500L
        const val MAX_BACKOFF_MS = 30_000L
        const val BACKOFF_FACTOR = 2L
    }
}

data class LockDecision(
    val requiresAuthentication: Boolean,
    val reason: String,
)
