package com.applock.applocker.policy

import com.applock.core.database.ProtectedAppDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Decides whether a package requires locking (TAS §4.1 LockPolicyManager).
 *
 * Keeps an in-memory snapshot of protected packages so the accessibility
 * service can get a synchronous answer on every window-state event without
 * touching the database on the main thread.
 */
class LockPolicyManager(
    private val dao: ProtectedAppDao,
    private val scope: CoroutineScope,
) {

    private val _protectedPackages = MutableStateFlow<Set<String>>(emptySet())
    val protectedPackages: StateFlow<Set<String>> = _protectedPackages

    fun startCaching() {
        scope.launch {
            dao.observeEnabledPackages().collect { packages ->
                _protectedPackages.value = packages.toSet()
            }
        }
    }

    /** Fast, main-thread-safe check used by the lock engine. */
    fun isProtected(packageName: String): Boolean =
        packageName in _protectedPackages.value

    fun evaluate(packageName: String, hasValidSession: Boolean): LockDecision = when {
        !isProtected(packageName) -> LockDecision(false, "not protected")
        hasValidSession -> LockDecision(false, "valid unlock session")
        else -> LockDecision(true, "protected app, no session")
    }
}

data class LockDecision(
    val requiresAuthentication: Boolean,
    val reason: String,
)
