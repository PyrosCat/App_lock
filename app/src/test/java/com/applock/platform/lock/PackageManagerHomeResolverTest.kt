package com.applock.platform.lock

import com.applock.platform.lock.PackageManagerHomeResolver.Resolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the resolve policy of [PackageManagerHomeResolver] (M7 WP2 changes E and F3). They drive the resolve
 * and clock seams directly. They cover failure handling (last-known-good launcher, no throw, TTL-limited retries),
 * the unconditional resolve after the TTL, the resolve on each new foreground episode, and the cached answer for a
 * repeat within one episode.
 */
class PackageManagerHomeResolverTest {

    private val ttl = 100L
    private var now = 0L

    /** A controllable resolve seam. It returns [answer] (or throws while [fail]) and counts every attempt. */
    private class Seam(var answer: Resolution = Resolution.Resolved(LAUNCHER1)) {
        var fail = false
        var attempts = 0

        fun resolve(): Resolution {
            attempts++
            if (fail) error("package manager down")
            return answer
        }

        fun defaultIs(packageName: String) {
            answer = Resolution.Resolved(packageName)
        }
    }

    private fun resolver(seam: Seam) = PackageManagerHomeResolver(
        resolveLauncher = seam::resolve,
        nowMs = { now },
        ttlMs = ttl,
    )

    /** An observation that starts a new foreground episode (the package differs from the previous raw one). */
    private fun PackageManagerHomeResolver.transition(packageName: String) = isHome(packageName, true)

    /** A repeated observation of the same package within one foreground episode. */
    private fun PackageManagerHomeResolver.sameEpisode(packageName: String) = isHome(packageName, false)

    // ---- Failure handling and the TTL ----------------------------------------------------------

    @Test
    fun `resolves and recognizes the launcher, treating other packages as not home`() {
        val r = resolver(Seam())
        assertTrue(r.transition(LAUNCHER1))
        assertFalse(r.transition(OTHER))
    }

    @Test
    fun `a resolve that throws keeps the last-known-good launcher and never throws`() {
        val seam = Seam()
        val r = resolver(seam)
        assertTrue(r.transition(LAUNCHER1)) // caches the launcher

        // The package manager starts to fail. After the TTL a resolve runs and throws. The resolver contains the
        // throw and keeps the cached launcher.
        seam.fail = true
        now = 1_000L
        assertFalse(r.transition(OTHER))
        assertTrue("the launcher is still recognized through the outage", r.transition(LAUNCHER1))
    }

    @Test
    fun `a Failure keeps the last-known-good launcher, a NoDefault answer drops it`() {
        val seam = Seam()
        val r = resolver(seam)
        assertTrue(r.transition(LAUNCHER1))

        seam.answer = Resolution.Failure // the answer is unknown: keep the launcher
        now = 200L
        assertTrue("a Failure keeps the last-known-good launcher", r.sameEpisode(LAUNCHER1))

        seam.answer = Resolution.NoDefault // the platform reports no default: do not keep the old launcher
        now = 400L
        assertFalse("NoDefault must not keep a stale launcher as home", r.sameEpisode(LAUNCHER1))
    }

    @Test
    fun `a failed resolve suppresses every attempt until the TTL expires`() {
        val seam = Seam()
        val r = resolver(seam)
        assertTrue(r.transition(LAUNCHER1)) // the first attempt caches the launcher
        assertEquals(1, seam.attempts)

        // The package manager starts to fail. After the TTL one resolve runs and fails. Within the next TTL no
        // observation makes an attempt, a new episode included.
        seam.fail = true
        now = 1_000L
        r.transition(OTHER)
        r.sameEpisode(OTHER)
        r.transition(SECOND_OTHER)
        assertTrue(r.transition(LAUNCHER1)) // still home (last-known-good), with no revalidation attempt
        assertEquals("a failed resolve must not be re-attempted within the TTL", 2, seam.attempts)

        now = 1_150L // after the TTL again: a new attempt is allowed
        r.transition(OTHER)
        assertEquals(3, seam.attempts)
    }

    @Test
    fun `a cold-start resolve failure is bounded by the TTL, then recovers`() {
        val seam = Seam().apply { fail = true }
        val r = resolver(seam)
        // No launcher is cached and every resolve fails. The first observation makes one attempt. Later
        // observations within the TTL make none.
        assertFalse(r.transition(LAUNCHER1))
        assertFalse(r.sameEpisode(LAUNCHER1))
        assertFalse(r.transition(OTHER))
        assertEquals("a cold-start failure must be TTL-bounded", 1, seam.attempts)

        // The package manager recovers and the TTL expires: the next observation resolves and caches.
        seam.fail = false
        now = 150L
        assertTrue(r.transition(LAUNCHER1))
        assertEquals(2, seam.attempts)
    }

    @Test
    fun `past the TTL a repeated cache match re-resolves unconditionally`() {
        // Only the cached launcher is observed, as repeats within one episode, so no new episode triggers a
        // resolve. After the TTL the resolver must resolve although the package matches the cache. Otherwise a
        // demoted former launcher stays classified home indefinitely.
        val seam = Seam()
        val r = resolver(seam)
        assertTrue(r.transition(LAUNCHER1))

        seam.defaultIs(LAUNCHER2) // the default changes; launcher2 is not observed
        now = 50L
        assertTrue(r.sameEpisode(LAUNCHER1))
        assertTrue(r.sameEpisode(LAUNCHER1))
        assertEquals("a same-episode match is trusted within the TTL", 1, seam.attempts)

        now = 200L
        assertFalse("a demoted former launcher must not stay home past the TTL", r.sameEpisode(LAUNCHER1))
        assertEquals(2, seam.attempts)
    }

    // ---- New foreground episodes ---------------------------------------------------------------

    @Test
    fun `a cache match on a new foreground episode is revalidated within the TTL`() {
        // The interpreter flags A -> Own -> A as a new episode, so the resolver resolves the cached launcher again.
        // A former launcher demoted while App Lock was in front is detected before the TTL expires.
        val seam = Seam()
        val r = resolver(seam)
        assertTrue(r.transition(LAUNCHER1))

        seam.defaultIs(LAUNCHER2)
        now = 10L // well within the TTL
        assertFalse("a demoted former launcher must not be home on a new episode", r.transition(LAUNCHER1))
        assertEquals(2, seam.attempts)
    }

    @Test
    fun `a revalidated cache match that still resolves stays home`() {
        val seam = Seam()
        val r = resolver(seam)
        assertTrue(r.transition(LAUNCHER1))
        now = 10L
        assertTrue(r.transition(LAUNCHER1)) // revalidated on the transition, and still the default
        assertEquals(2, seam.attempts)
    }

    @Test
    fun `a new default launcher is home on its first episode within the TTL`() {
        val seam = Seam()
        val r = resolver(seam)
        assertTrue(r.transition(LAUNCHER1))

        seam.defaultIs(LAUNCHER2) // the user picks a new default launcher
        now = 10L // within the TTL
        assertTrue("a new default launcher must be home on its first observation", r.transition(LAUNCHER2))
        assertEquals(2, seam.attempts)
        assertFalse(r.transition(LAUNCHER1)) // the old launcher is no longer home
    }

    @Test
    fun `a package answered earlier as a plain app is re-resolved on its next episode`() {
        // B is first observed while A is home, and the answer is "not home". App Lock comes to the front, the
        // default changes to B, and B returns within the TTL. The return is a new episode, so the earlier answer
        // must not be used.
        val seam = Seam()
        val r = resolver(seam)
        assertTrue(r.transition(LAUNCHER1))
        now = 10L
        assertFalse(r.transition(LAUNCHER2)) // a plain app while launcher1 is the default

        seam.defaultIs(LAUNCHER2) // changed while App Lock was in front; the runtime flags B's return as new
        now = 20L
        assertTrue("a new episode must be resolved again", r.transition(LAUNCHER2))
        assertEquals(3, seam.attempts)
    }

    @Test
    fun `a repeat within one episode reuses the cached answer until the TTL`() {
        // Documented limitation: a default change with no foreground change (for example, through adb or device
        // management) is not detected by a repeat until the TTL expires.
        val seam = Seam()
        val r = resolver(seam)
        assertTrue(r.transition(LAUNCHER1))
        now = 10L
        assertFalse(r.transition(OTHER)) // resolved at t=10
        assertEquals(2, seam.attempts)

        seam.defaultIs(OTHER)
        now = 50L
        assertFalse("a same-episode repeat reuses the cached answer", r.sameEpisode(OTHER))
        assertEquals(2, seam.attempts)

        now = 110L // the TTL since the attempt at t=10 has expired
        assertTrue("after the TTL the repeat is resolved again", r.sameEpisode(OTHER))
        assertEquals(3, seam.attempts)
    }

    @Test
    fun `a no-default answer is never home and a new episode picks up a just-chosen default`() {
        val seam = Seam(answer = Resolution.NoDefault) // several launchers, none chosen as the default
        val r = resolver(seam)
        assertFalse(r.transition(LAUNCHER1))
        assertFalse(r.sameEpisode(LAUNCHER1))
        assertEquals(1, seam.attempts)

        seam.defaultIs(LAUNCHER2) // the user picks "Always" for launcher2
        now = 10L
        assertTrue(r.transition(LAUNCHER2))
        assertEquals(2, seam.attempts)
    }

    @Test
    fun `the raw resolve maps null and the chooser to NoDefault`() {
        assertEquals(Resolution.NoDefault, Resolution.of(null))
        assertEquals(Resolution.NoDefault, Resolution.of("android"))
        assertEquals(Resolution.Resolved(LAUNCHER1), Resolution.of(LAUNCHER1))
    }

    private companion object {
        const val LAUNCHER1 = "com.launcher1"
        const val LAUNCHER2 = "com.launcher2"
        const val OTHER = "com.other"
        const val SECOND_OTHER = "com.other2"
    }
}
