package com.applock.applocker.engine

import android.content.Context
import android.content.Intent
import android.util.Log
import com.applock.applocker.policy.LockPolicyManager
import com.applock.applocker.session.LockSessionManager
import com.applock.authentication.ui.LockScreenActivity
import com.applock.core.database.SecurityEventDao
import com.applock.core.database.SecurityEventEntity
import com.applock.core.database.SecurityEventType
import com.applock.core.security.LockoutManager
import com.applock.core.security.LockoutState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Central security component (TAS §4.1). Receives foreground-app changes from
 * [com.applock.applocker.service.AppDetectionService], evaluates policy, and
 * launches the lock screen when authentication is required.
 */
class ApplicationLockEngine(
    private val context: Context,
    private val policyManager: LockPolicyManager,
    private val sessionManager: LockSessionManager,
    private val lockoutManager: LockoutManager,
    private val securityEventDao: SecurityEventDao,
    private val scope: CoroutineScope,
) {

    private var lastForegroundPackage: String? = null

    /** Package currently showing our lock screen, if any. */
    @Volatile
    private var lockScreenTarget: String? = null

    fun onAppForegrounded(packageName: String) {
        if (packageName == context.packageName) return
        // Ignore transient system windows (keyboard, system UI) so they don't
        // count as "leaving" the protected app.
        if (packageName in IGNORED_PACKAGES) return

        val previous = lastForegroundPackage
        if (previous == packageName) return
        lastForegroundPackage = packageName

        // The user left the previous app — apply its relock policy.
        previous?.let { sessionManager.onAppLeft(it) }

        val decision = policyManager.evaluate(
            packageName = packageName,
            hasValidSession = sessionManager.hasValidSession(packageName),
        )
        Log.d(TAG, "$packageName -> $decision")

        if (decision.requiresAuthentication) {
            lockScreenTarget = packageName
            logEvent(SecurityEventType.LOCK_TRIGGERED, packageName)
            launchLockScreen(packageName)
        }
    }

    fun onUnlockSuccess(packageName: String, method: UnlockMethod = UnlockMethod.PIN) {
        sessionManager.markUnlocked(packageName)
        lockoutManager.recordSuccess()
        lockScreenTarget = null
        logEvent(
            when (method) {
                UnlockMethod.PIN -> SecurityEventType.UNLOCK_SUCCESS
                UnlockMethod.BIOMETRIC -> SecurityEventType.BIOMETRIC_UNLOCK_SUCCESS
            },
            packageName,
        )
    }

    /** Returns the lockout state after counting this failure (FR-174). */
    fun onUnlockFailure(packageName: String): LockoutState {
        logEvent(SecurityEventType.UNLOCK_FAILURE, packageName)
        val state = lockoutManager.recordFailure()
        if (state is LockoutState.LockedOut) {
            logEvent(SecurityEventType.LOCKOUT_TRIGGERED, packageName)
        }
        return state
        // Phase 3 hook: intruder selfie on lockout.
    }

    /** User backed out of the lock screen without authenticating. */
    fun onLockScreenDismissed(packageName: String) {
        lockScreenTarget = null
        // Kick the user to the home screen so the protected app
        // isn't left visible underneath.
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(home)
    }

    fun onScreenOff() {
        sessionManager.clearAllSessions()
        lastForegroundPackage = null
    }

    private fun launchLockScreen(packageName: String) {
        val intent = LockScreenActivity.createIntent(context, packageName).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }

    private fun logEvent(type: String, packageName: String?) {
        scope.launch {
            securityEventDao.insert(SecurityEventEntity(eventType = type, packageName = packageName))
        }
    }

    enum class UnlockMethod { PIN, BIOMETRIC }

    private companion object {
        const val TAG = "AppLockEngine"
        val IGNORED_PACKAGES = setOf(
            "com.android.systemui",
            "android",
        )
    }
}
