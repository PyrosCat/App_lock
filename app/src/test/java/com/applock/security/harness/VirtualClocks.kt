package com.applock.security.harness

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Independent wall and elapsed clocks for the lockout harness. Only the test thread moves them, between quiescent
 * points, so every storage event and every completion sees a stable time. [advance] moves both clocks, as real time
 * does. [jumpWall] moves the wall clock alone, as a user or network time change does. [reboot] starts a new boot: the
 * wall clock moves on by the downtime, and the elapsed clock starts again from [bootElapsedMs].
 */
class VirtualClocks(wallMs: Long = W0, elapsedMs: Long = E0) {
    private val wall = AtomicLong(wallMs)
    private val elapsed = AtomicLong(elapsedMs)
    private val boot = AtomicInteger(1)

    fun wallMs(): Long = wall.get()

    fun elapsedMs(): Long = elapsed.get()

    fun bootCount(): Int = boot.get()

    fun advance(ms: Long) {
        require(ms >= 0) { "real time does not move backwards; use jumpWall for a wall-clock change" }
        wall.addAndGet(ms)
        elapsed.addAndGet(ms)
    }

    fun jumpWall(ms: Long) {
        wall.addAndGet(ms)
    }

    fun reboot(downtimeMs: Long, bootElapsedMs: Long = BOOT_ELAPSED_MS) {
        require(downtimeMs >= 0)
        wall.addAndGet(downtimeMs)
        elapsed.set(bootElapsedMs)
        boot.incrementAndGet()
    }

    companion object {
        /** The test plan's fixture clocks: W0 (wall) and E0 (elapsed). */
        const val W0 = 1_000_000L
        const val E0 = 5_000_000L
        const val BOOT_ELAPSED_MS = 10_000L
    }
}
