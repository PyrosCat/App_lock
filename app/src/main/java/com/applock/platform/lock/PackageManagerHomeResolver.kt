package com.applock.platform.lock

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import com.applock.service.engine.HomeResolver

/**
 * The real [HomeResolver] (M7 WP2 changes E and F3). It resolves `MAIN/HOME` through `PackageManager` to tell
 * whether a foreground package is the current home launcher. The result is security-relevant: the interpreter does
 * not evaluate a `Home` foreground. A demoted former launcher that is still classified `Home` bypasses the lock. A
 * new launcher that is not recognized gets a recovery shield under a `Failed` policy.
 *
 * Resolve policy:
 *  - The first call resolves.
 *  - A call resolves when the last attempt is older than the TTL, for every package. This includes a repeat of the
 *    cached launcher, so the resolver re-resolves instead of trusting a cached match forever.
 *  - Within the TTL, a call resolves on a new foreground episode. The interpreter sets
 *    `newForegroundEpisode` and counts `Own` and `Transient` observations too. Thus a former launcher and a new
 *    default launcher are both resolved when they come to the front. The cost is one resolve per foreground change.
 *    A repeat within one episode uses the cached answer.
 *  - After a failed attempt, no call resolves until the TTL expires. This limits the retries against a failing
 *    `PackageManager`.
 *
 * Results: [Resolution.Failure], or a throw, keeps the last-known-good launcher. Thus an intermittent error does not
 * shield the launcher again and again. [Resolution.NoDefault] clears the cached launcher, because the platform
 * reports that no default is set. The resolver never throws.
 *
 * Limitation: the resolver runs only when the interpreter classifies an observation, and a classification stays in
 * effect until the next observation. The TTL forces a re-resolve once the last attempt is older than it. It does
 * not bound the age of the answer that is used, nor how long a wrong classification stays in effect. A stale answer
 * is still used in three cases: a default change with no foreground change (for example, through adb or device
 * management), until the first observation after the TTL; a new episode within the TTL after a failed attempt; and
 * a run of resolve failures, which keeps the last-known-good launcher in use until a later resolve succeeds. A
 * strict time limit needs scheduled revalidation and a runtime re-evaluation of the current foreground. These are
 * not implemented.
 *
 * The [resolveLauncher] seam isolates the Android call, so the policy is JVM-testable. The [Context] constructor
 * connects the seam to `PackageManager`.
 */
class PackageManagerHomeResolver(
    private val resolveLauncher: () -> Resolution,
    private val nowMs: () -> Long,
    private val ttlMs: Long = DEFAULT_TTL_MS,
) : HomeResolver {

    /** Connects the resolve seam and the clock to the Android APIs. */
    constructor(context: Context) : this(
        resolveLauncher = { Resolution.of(resolveHomePackage(context)) },
        nowMs = { SystemClock.elapsedRealtime() },
    )

    /** The result of one `MAIN/HOME` resolve. */
    sealed interface Resolution {
        /** [packageName] is the default home launcher. */
        data class Resolved(val packageName: String) : Resolution

        /** No default launcher is set: nothing resolves, or the chooser resolves. */
        data object NoDefault : Resolution

        /** The resolve failed. The answer is unknown. */
        data object Failure : Resolution

        companion object {
            /**
             * Maps the package from `resolveActivity`. Null and the chooser package map to [NoDefault], so the
             * chooser is never cached as the launcher.
             */
            internal fun of(packageName: String?): Resolution = when (packageName) {
                null, CHOOSER_PACKAGE -> NoDefault
                else -> Resolved(packageName)
            }
        }
    }

    // Guarded by this object's monitor. The interpreter calls from one drain, but DI can share the resolver, so
    // each read-modify-write must be atomic.
    private var cachedLauncher: String? = null
    private var lastAttemptAt: Long? = null // null until the first attempt; set on every attempt, failures included
    private var lastAttemptFailed = false

    @Synchronized
    override fun isHome(packageName: String, newForegroundEpisode: Boolean): Boolean {
        val now = nowMs()
        if (shouldResolve(newForegroundEpisode, now)) resolve(now)
        return packageName == cachedLauncher
    }

    /**
     * Applies the resolve policy in the class comment. The order of the checks is part of the policy: the TTL check
     * comes first, so a failed attempt is retried after the TTL. The failure check comes next, so it also blocks a
     * new episode.
     */
    private fun shouldResolve(newForegroundEpisode: Boolean, now: Long): Boolean {
        val attemptedAt = lastAttemptAt ?: return true
        return when {
            now - attemptedAt >= ttlMs -> true
            lastAttemptFailed -> false
            else -> newForegroundEpisode
        }
    }

    /**
     * Makes one attempt and applies the result. It records the time of every attempt, failures included, so the TTL
     * also limits retries.
     */
    private fun resolve(now: Long) {
        lastAttemptAt = now
        when (val resolution = resolveSafely()) {
            is Resolution.Resolved -> {
                cachedLauncher = resolution.packageName
                lastAttemptFailed = false
            }
            Resolution.NoDefault -> {
                cachedLauncher = null // no default is set: do not keep the old launcher
                lastAttemptFailed = false
            }
            Resolution.Failure -> lastAttemptFailed = true // keep the last-known-good launcher
        }
    }

    /** Converts a throw from the seam to [Resolution.Failure]. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // contract: the resolver must never throw
    private fun resolveSafely(): Resolution =
        try {
            resolveLauncher()
        } catch (e: RuntimeException) {
            Resolution.Failure
        }

    private companion object {
        /** Max age of the last attempt before a call re-resolves; also the retry backoff after a failure. */
        const val DEFAULT_TTL_MS = 2_000L

        /** The package of the launcher chooser (`ResolverActivity`). It resolves when no default is set. */
        const val CHOOSER_PACKAGE = "android"

        /** Resolves the current home launcher's package through `PackageManager`, or null when nothing resolves. */
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
            return info?.activityInfo?.packageName
        }
    }
}
