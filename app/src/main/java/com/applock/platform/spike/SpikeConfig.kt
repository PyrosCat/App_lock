@file:Suppress("MagicNumber")

package com.applock.platform.spike

/**
 * M7 WP0 platform-validation spike — **THROWAWAY**. This whole package (and the `SPIKE`-marked
 * manifest block) is deleted at WP0 close; it exists only to measure the platform behaviours that
 * ADR-020 / ADR-021 depend on — overlay-wins-the-relaunch-race (R-002), biometric-via-BAL, the
 * poll interval, and battery — on the API-36 target. No engine wiring, no persistence, no
 * production quality bar. See `docs/process/M7_PLAN.md` WP0.
 */
object SpikeConfig {
    /** Log tag for all spike output (the grep target for the WP0 evidence report). */
    const val LOG_TAG = "M7Spike"

    /**
     * The overlay window's **stable** title. The WP1 harness probe and the durable OV-4 UIAutomator
     * test both assert on this via `dumpsys window windows`, so it MUST NOT change.
     */
    const val OVERLAY_WINDOW_TITLE = "AppLockSpikeOverlay"

    /** Candidate poll interval P (D1 / ADR-021, ~300-800 ms range); WP0 measurement tunes it. */
    const val DEFAULT_POLL_INTERVAL_MS = 400L

    /** specialUse FGS notification channel + id. */
    const val CHANNEL_ID = "m7_spike_poll"
    const val NOTIFICATION_ID = 4700
}

/**
 * Mutable spike runtime state — **in-memory only** (DDS §1.3: nothing persisted). Written by the
 * launcher, read by the poll service.
 */
object SpikeState {
    /** The package the spike treats as "protected" (drives the overlay). Null = detect-only. */
    @Volatile
    var protectedPackage: String? = null

    /** Active poll interval P, ms — settable per run (extra) so the fleet can sweep intervals. */
    @Volatile
    var pollIntervalMs: Long = SpikeConfig.DEFAULT_POLL_INTERVAL_MS

    /** Last measured detection lag (event time -> poll observation), ms; -1 = none yet. */
    @Volatile
    var lastLatencyMs: Long = -1L
}
