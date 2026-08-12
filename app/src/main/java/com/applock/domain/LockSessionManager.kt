package com.applock.domain

/**
 * Tracks which protected apps are currently unlocked and applies the relock
 * policy. Pure Kotlin — policy source and clock are injected so this can be
 * unit-tested on the JVM.
 */
class LockSessionManager(
    private val policyProvider: () -> RelockPolicy,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** packageName -> timestamp the user left the app (0 = still foreground). */
    private val unlockedApps = mutableMapOf<String, Long>()

    private val relockPolicy: RelockPolicy get() = policyProvider()

    @Synchronized
    fun markUnlocked(packageName: String) {
        unlockedApps[packageName] = STILL_FOREGROUND
    }

    /** Called when the foreground moves away from [packageName]. */
    @Synchronized
    fun onAppLeft(packageName: String) {
        if (packageName !in unlockedApps) return
        when (relockPolicy) {
            RelockPolicy.IMMEDIATE -> unlockedApps.remove(packageName)
            RelockPolicy.GRACE_10S -> unlockedApps[packageName] = clock()
            RelockPolicy.SCREEN_OFF -> Unit // keep session until screen off
        }
    }

    /** Called when the foreground returns to [packageName]. */
    @Synchronized
    fun hasValidSession(packageName: String): Boolean {
        val leftAt = unlockedApps[packageName] ?: return false
        if (leftAt == STILL_FOREGROUND) return true
        return when (relockPolicy) {
            RelockPolicy.IMMEDIATE -> false
            RelockPolicy.GRACE_10S -> {
                val valid = clock() - leftAt <= GRACE_MILLIS
                if (valid) unlockedApps[packageName] = STILL_FOREGROUND
                else unlockedApps.remove(packageName)
                valid
            }
            RelockPolicy.SCREEN_OFF -> {
                unlockedApps[packageName] = STILL_FOREGROUND
                true
            }
        }
    }

    /** Screen turned off — every session ends regardless of policy. */
    @Synchronized
    fun clearAllSessions() {
        unlockedApps.clear()
    }

    companion object {
        private const val STILL_FOREGROUND = 0L
        const val GRACE_MILLIS = 10_000L
    }
}
