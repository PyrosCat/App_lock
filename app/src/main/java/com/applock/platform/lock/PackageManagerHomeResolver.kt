package com.applock.platform.lock

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import com.applock.service.engine.HomeResolver

/**
 * The real [HomeResolver] (M7 WP2 change E). It classifies a raw foreground package as the current home
 * launcher by resolving `MAIN/HOME` through `PackageManager`. Home is load-bearing: the interpreter lets a
 * `Home` foreground through without evaluation, and a misclassified launcher would otherwise draw a
 * recovery shield, so the resolver honours two hard contracts from D:
 *
 *  - **It keeps a last-known-good launcher and NEVER throws.** A transient `PackageManager` failure returns
 *    the cached launcher, so an intermittent resolve error does not repeatedly shield the launcher (the
 *    launcher stays classified `Home` through the outage). A resolve that throws is swallowed to null.
 *  - **It re-resolves whenever the cached entry is stale (older than a short TTL), whatever the observed
 *    package.** Re-resolving only on a *differing* package would trust a cached launcher forever, so a FORMER
 *    launcher (still cached after the default changed) would stay classified `Home` and bypass the lock (a
 *    fail-dangerous gap). Re-resolving once the cache is stale closes it, and the TTL still bounds the cost,
 *    so a burst of foregrounds inside one window does not re-resolve on every event. This covers a
 *    default-launcher change and a missed broadcast.
 *
 * The Android-bound resolve is behind the [resolveLauncher] seam so the caching and resilience policy is
 * pure Kotlin and JVM-testable; the [Context] constructor wires the seam to the real `PackageManager`.
 */
class PackageManagerHomeResolver(
    private val resolveLauncher: () -> String?,
    private val nowMs: () -> Long,
    private val ttlMs: Long = DEFAULT_TTL_MS,
) : HomeResolver {

    /** Wires the resolve seam and the clock to the real Android APIs. */
    constructor(context: Context) : this(
        resolveLauncher = { resolveHomePackage(context) },
        nowMs = { SystemClock.elapsedRealtime() },
    )

    // Guarded by the monitor: the interpreter classifies on one drain, but a HomeResolver is a small shared
    // seam and DI could hand it to more than one caller, so keep the read-modify-write atomic.
    private var cachedLauncher: String? = null
    private var lastAttemptAt: Long? = null // null = never attempted; set on EVERY attempt (success or not)

    @Synchronized
    override fun isHome(packageName: String): Boolean {
        if (shouldResolve()) {
            // Timestamp EVERY attempt, not only a success, so a failing or empty resolve is bounded by the
            // TTL rather than re-attempted on every observation, whether through a PackageManager outage or
            // a cold start before the first success. A null resolve keeps the last-known-good launcher.
            lastAttemptAt = nowMs()
            resolveSafely()?.let { cachedLauncher = it }
        }
        return packageName == cachedLauncher
    }

    /**
     * Whether to (re)resolve now. It resolves on the first-ever call, then again once the last attempt is
     * older than the TTL, whatever the observed package. It re-resolves even when the package matches the
     * cache, because trusting a match forever would keep a demoted FORMER launcher classified `Home` (a
     * fail-dangerous bypass; see the class comment). The TTL bounds a run of foregrounds and a cold-start
     * failure. F owns the remaining within-TTL residuals and a tri-state resolve result (see the change-E
     * HomeResolver note in M7_PLAN).
     */
    private fun shouldResolve(): Boolean {
        val attemptedAt = lastAttemptAt ?: return true
        // Past the TTL, re-resolve UNCONDITIONALLY (even when the observed package equals the cache): trusting
        // a match forever is the former-launcher bypass. Within the TTL, trust the cache to bound the cost.
        return nowMs() - attemptedAt >= ttlMs
    }

    /** Never throws: a resolve failure degrades to null, and the caller keeps the last-known-good launcher. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // contract: the resolver must never throw
    private fun resolveSafely(): String? =
        try {
            resolveLauncher()
        } catch (e: RuntimeException) {
            null
        }

    private companion object {
        /** Bounds the resolve cost: within this window the resolver trusts the cache, whatever the package. */
        const val DEFAULT_TTL_MS = 2_000L

        /**
         * Resolves the current home launcher's package through `PackageManager`. Returns null when nothing
         * resolves or the multi-launcher chooser (`android`) is returned, so the chooser is never cached as
         * the launcher; the interpreter already treats `android` as a transient package regardless.
         */
        @Suppress("DEPRECATION") // the int-flags overload is used below API 33
        private fun resolveHomePackage(context: Context): String? {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.resolveActivity(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
                )
            } else {
                pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
            return info?.activityInfo?.packageName?.takeUnless { it == "android" }
        }
    }
}
