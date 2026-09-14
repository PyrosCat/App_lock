package com.applock.platform.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the caching / resilience policy of [PackageManagerHomeResolver] (M7 WP2 change E), driving
 * the resolve and clock seams directly. They prove the two D contracts: the resolver keeps a last-known-good
 * launcher and never throws through a `PackageManager` outage, and it re-resolves once the cache is stale
 * (past the TTL) so a default-launcher change is picked up, including the fail-dangerous direction, where a
 * FORMER launcher must not stay classified `Home` after the default changed.
 */
class PackageManagerHomeResolverTest {

    private val ttl = 100L

    @Test
    fun `resolves and recognizes the launcher, treating other packages as not home`() {
        val resolver = PackageManagerHomeResolver(
            resolveLauncher = { "com.launcher" },
            nowMs = { 0L },
            ttlMs = ttl,
        )
        assertTrue(resolver.isHome("com.launcher"))
        assertFalse(resolver.isHome("com.other"))
    }

    @Test
    fun `a resolve failure keeps the last-known-good launcher and never throws`() {
        var now = 0L
        var fail = false
        val resolver = PackageManagerHomeResolver(
            resolveLauncher = { if (fail) error("package manager down") else "com.launcher" },
            nowMs = { now },
            ttlMs = ttl,
        )
        assertTrue(resolver.isHome("com.launcher")) // caches the launcher

        // The package manager starts failing; advance past the TTL so an observed non-launcher package
        // forces a re-resolve. The throw is swallowed and the cache is preserved (no exception escapes).
        fail = true
        now = 1_000L
        assertFalse(resolver.isHome("com.other"))
        // The launcher is still recognized through the outage (last-known-good).
        assertTrue(resolver.isHome("com.launcher"))
    }

    @Test
    fun `a failed resolve is bounded by the TTL and not re-attempted on every observation`() {
        var now = 0L
        var fail = false
        var attempts = 0
        val resolver = PackageManagerHomeResolver(
            resolveLauncher = {
                attempts++
                if (fail) error("package manager down") else "com.launcher"
            },
            nowMs = { now },
            ttlMs = ttl,
        )
        assertTrue(resolver.isHome("com.launcher")) // first attempt caches the launcher
        assertEquals(1, attempts)

        // The package manager starts failing. A differing package past the TTL forces ONE resolve, which
        // advances the clock even though it failed; further observations inside the new TTL do not re-attempt.
        fail = true
        now = 1_000L
        resolver.isHome("com.other")
        resolver.isHome("com.other")
        resolver.isHome("com.other")
        assertEquals("a failed resolve must not be re-attempted on every observation", 2, attempts)

        now = 1_150L // past the TTL again: a fresh attempt is allowed
        resolver.isHome("com.other")
        assertEquals(3, attempts)
    }

    @Test
    fun `a cold-start resolve failure is bounded by the TTL, then recovers`() {
        var now = 0L
        var fail = true
        var attempts = 0
        val resolver = PackageManagerHomeResolver(
            resolveLauncher = {
                attempts++
                if (fail) error("package manager down") else "com.launcher"
            },
            nowMs = { now },
            ttlMs = ttl,
        )
        // No launcher cached yet and resolution keeps failing: the first observation attempts once, and
        // further observations inside the TTL do NOT re-attempt, so a cold-start outage is bounded.
        assertFalse(resolver.isHome("com.launcher"))
        assertFalse(resolver.isHome("com.launcher"))
        assertFalse(resolver.isHome("com.other"))
        assertEquals("a cold-start failure must be TTL-bounded", 1, attempts)

        // The package manager recovers and the TTL lapses: the next observation resolves and caches.
        fail = false
        now = 150L
        assertTrue(resolver.isHome("com.launcher"))
        assertEquals(2, attempts)
    }

    @Test
    fun `a cold-start null resolution is bounded by the TTL`() {
        var now = 0L
        var attempts = 0
        val resolver = PackageManagerHomeResolver(
            resolveLauncher = {
                attempts++
                null // nothing resolves
            },
            nowMs = { now },
            ttlMs = ttl,
        )
        assertFalse(resolver.isHome("com.launcher"))
        assertFalse(resolver.isHome("com.launcher"))
        now = 50L // still within the TTL
        assertFalse(resolver.isHome("com.other"))
        assertEquals("a cold-start null resolution must be TTL-bounded", 1, attempts)
    }

    @Test
    fun `re-resolves when the default launcher changes, bounded by the TTL`() {
        var now = 0L
        var launcher = "com.launcher1"
        val resolver = PackageManagerHomeResolver(
            resolveLauncher = { launcher },
            nowMs = { now },
            ttlMs = ttl,
        )
        assertTrue(resolver.isHome("com.launcher1"))

        launcher = "com.launcher2" // the user picks a new default launcher
        // Within the TTL the cache is trusted, so the new launcher is not yet recognized. This TTL-lag is a
        // KNOWN DEVIATION from the re-resolve-whenever-differs rule, deferred to F (see M7_PLAN change E,
        // HomeResolver); F revisits this adapter and this assertion.
        now = 50L
        assertFalse(resolver.isHome("com.launcher2"))

        now = 200L // past the TTL: a stale cache triggers a re-resolve and adopts the new launcher
        assertTrue(resolver.isHome("com.launcher2"))

        now = 400L
        assertFalse(resolver.isHome("com.launcher1")) // the old launcher is no longer home
    }

    @Test
    fun `the former launcher is not classified home after the default changes, even if only it is observed`() {
        // Fail-dangerous regression (M7 WP2 change E, round 8): once launcher1 is cached, the ONLY package the
        // process observes is launcher1 (the user demoted it and then opened it as a protected app). The stale
        // cache must still force a re-resolve past the TTL. Trusting a matching package forever would keep the
        // former launcher classified Home and bypass the lock. F owns the within-TTL lag and the immediate probe.
        var now = 0L
        var launcher = "com.launcher1"
        val resolver = PackageManagerHomeResolver(
            resolveLauncher = { launcher },
            nowMs = { now },
            ttlMs = ttl,
        )
        assertTrue(resolver.isHome("com.launcher1")) // launcher1 cached as home

        launcher = "com.launcher2" // the default launcher changes; launcher2 is NEVER observed as a foreground
        // KNOWN bounded residual (deferred to F): WITHIN the TTL the cache is trusted, so the former launcher
        // is still classified home for up to the TTL. F adds cache-match revalidation on a foreground
        // transition to close this window; the fix here removes only the INDEFINITE bypass.
        now = 50L
        assertTrue(
            "within the TTL the former launcher is still home (bounded residual)",
            resolver.isHome("com.launcher1"),
        )

        now = 200L // past the TTL: observing the FORMER launcher must re-resolve (not trust the equal cache)
        assertFalse(
            "a demoted former launcher must not stay classified home past the TTL",
            resolver.isHome("com.launcher1"),
        )
    }
}
