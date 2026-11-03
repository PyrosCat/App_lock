package com.applock.domain

/**
 * Tracks which protected apps are currently unlocked and applies the relock
 * policy. Pure Kotlin: the policy source and the monotonic clock are injected,
 * so this class can be unit-tested on the JVM.
 */
class LockSessionManager(
    private val policyProvider: () -> RelockPolicy,
    // A monotonic clock in milliseconds for the grace window, so a wall-clock change cannot extend a session.
    // Production injects SystemClock.elapsedRealtime.
    private val elapsedRealtime: () -> Long,
) {

    /** packageName -> the unlock session of that app. An app with no entry has no session. */
    private val unlockedApps = mutableMapOf<String, Session>()

    private val relockPolicy: RelockPolicy get() = policyProvider()

    @Synchronized
    fun markUnlocked(packageName: String) {
        unlockedApps[packageName] = Session.Foreground
    }

    /** Called when the foreground moves away from [packageName]. */
    @Synchronized
    fun onAppLeft(packageName: String) {
        if (packageName !in unlockedApps) return
        when (relockPolicy) {
            RelockPolicy.IMMEDIATE -> unlockedApps.remove(packageName)
            RelockPolicy.GRACE_10S -> unlockedApps[packageName] = Session.Left(elapsedRealtime())
            RelockPolicy.SCREEN_OFF -> Unit // keep session until screen off
        }
    }

    /** Called when the foreground returns to [packageName]. */
    @Synchronized
    fun hasValidSession(packageName: String): Boolean {
        val leftAt = when (val session = unlockedApps[packageName]) {
            null -> return false
            Session.Foreground -> return true
            is Session.Left -> session.leftAt
        }
        return when (relockPolicy) {
            RelockPolicy.IMMEDIATE -> false
            RelockPolicy.GRACE_10S -> {
                // Fail closed: a reading earlier than the leave time is outside the window and ends the session.
                val valid = elapsedRealtime() - leftAt in 0..GRACE_MILLIS
                if (valid) {
                    unlockedApps[packageName] = Session.Foreground
                } else {
                    unlockedApps.remove(packageName)
                }
                valid
            }
            RelockPolicy.SCREEN_OFF -> {
                unlockedApps[packageName] = Session.Foreground
                true
            }
        }
    }

    /** Screen turned off — every session ends regardless of policy. */
    @Synchronized
    fun clearAllSessions() {
        unlockedApps.clear()
    }

    /**
     * The unlock session of one app. The foreground state is a separate type, not a sentinel time, so no clock
     * reading (0 included) can be mistaken for it.
     */
    private sealed interface Session {
        data object Foreground : Session

        /** [leftAt] is the clock reading when the user left the app. */
        data class Left(val leftAt: Long) : Session
    }

    companion object {
        const val GRACE_MILLIS = 10_000L
    }
}
