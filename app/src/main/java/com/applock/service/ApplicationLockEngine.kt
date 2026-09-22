package com.applock.service

import android.content.Context
import android.content.Intent
import android.util.Log
import com.applock.data.SecurityEventDao
import com.applock.data.SecurityEventEntity
import com.applock.data.SecurityEventType
import com.applock.domain.LockPolicyManager
import com.applock.domain.LockSessionManager
import com.applock.presentation.authentication.LockScreenActivity
import com.applock.security.LockoutManager
import com.applock.security.LockoutState
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
    private val intruderCapture: IntruderCaptureManager,
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
        lastForegroundPackage = packageName

        // A genuine app switch (not a repeat window event from the same app)
        // means the user left the previous app — apply its relock policy.
        if (previous != packageName) {
            previous?.let { sessionManager.onAppLeft(it) }
        }

        val decision = policyManager.evaluate(
            packageName = packageName,
            hasValidSession = sessionManager.hasValidSession(packageName),
        )
        Log.d(TAG, "$packageName -> $decision")

        if (decision.requiresAuthentication) {
            // Re-lock on EVERY foreground event for a protected app that has no
            // session — including a repeat of the same package. Rapidly
            // relaunching a protected app slides its window over our lock screen,
            // which then self-finishes (noHistory); deduping same-package events
            // here would leave the app exposed (fast-switch bypass). Launching is
            // idempotent (singleTop), and only the first transition is
            // audit-logged so the security log doesn't fill with repeats.
            val newlyLocked = lockScreenTarget != packageName
            lockScreenTarget = packageName
            if (newlyLocked) logEvent(SecurityEventType.LOCK_TRIGGERED, packageName)
            launchLockScreen(packageName)
        } else if (previous != packageName) {
            lockScreenTarget = null
        }
    }

    fun onUnlockSuccess(packageName: String, method: UnlockMethod = UnlockMethod.PIN) {
        sessionManager.markUnlocked(packageName)
        // Admission clears the counters in memory immediately; the durable write resolves off the caller thread. A
        // failed durable clear does not delay anything here or undo the in-memory reset (the user stays unlocked); it
        // is logged so it is not silently discarded on this live legacy path. The durable diagnostic sink
        // (RuntimeDiagnostics "lockout_reset") arrives with LockEngineRuntime at F6, which replaces this engine.
        lockoutManager.submitSuccess { committed ->
            if (!committed) {
                Log.w(TAG, "lockout reset write failed for $packageName; storage keeps the pre-reset deadline")
            }
        }
        lockScreenTarget = null
        logEvent(
            when (method) {
                UnlockMethod.PIN -> SecurityEventType.UNLOCK_SUCCESS
                UnlockMethod.BIOMETRIC -> SecurityEventType.BIOMETRIC_UNLOCK_SUCCESS
            },
            packageName,
        )
    }

    /**
     * Returns the immediate lockout state after counting this failure (FR-174), so the caller enforces at once. This
     * engine is synchronous and returns before persistence finishes, so the conditional `LOCKOUT_TRIGGERED` audit and
     * the intruder capture run when persistence completes (a completion callback that carries the actual count and
     * durability). `LOCKOUT_TRIGGERED` fires only for a recorded (durable) lockout, never a degraded one; the capture
     * fires on every failure with the real count, even when the durable write later fails (R-007).
     */
    fun onUnlockFailure(
        packageName: String,
        method: UnlockMethod = UnlockMethod.PIN,
    ): LockoutState {
        logEvent(SecurityEventType.UNLOCK_FAILURE, packageName)
        return lockoutManager.submitFailure { outcome ->
            val state = outcome.state
            if (state is LockoutState.LockedOut && !state.degraded) {
                logEvent(SecurityEventType.LOCKOUT_TRIGGERED, packageName)
            }
            // FR-081: the capture manager decides (policy + settings) whether this
            // particular failure crosses the intruder threshold.
            intruderCapture.onAuthFailure(packageName, method.name, outcome.count)
        }.immediate
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
