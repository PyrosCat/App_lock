package com.applock.privacy

/**
 * Decides when a run of consecutive authentication failures becomes an
 * intruder event (FR-081). Pure logic — settings are injected as providers
 * so this is JVM-testable and always reflects the live configuration.
 *
 * Fires at the configured threshold and at every further multiple of it
 * (5, 10, 15, … for the default of 5): a persistent intruder produces
 * fresh evidence instead of only the very first photo, without capturing
 * on every single failure.
 */
class IntruderPolicy(
    private val enabled: () -> Boolean,
    private val threshold: () -> Int,
) {

    fun shouldCapture(consecutiveFailures: Int): Boolean {
        if (!enabled()) return false
        if (consecutiveFailures <= 0) return false
        val t = threshold().coerceAtLeast(1)
        return consecutiveFailures % t == 0
    }

    companion object {
        const val DEFAULT_THRESHOLD = 5
        val THRESHOLD_CHOICES = listOf(3, 5, 10)
    }
}
