package com.applock.platform.lock

import com.applock.service.engine.Epoch
import com.applock.service.engine.Generation
import com.applock.service.engine.TimerToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * JVM tests for the terminal-shutdown / schedule atomicity of [HandlerTimerScheduler] (M7 WP2 change E),
 * driving the [HandlerTimerScheduler.TimerOps] seam with a fake so the concurrency invariant is testable
 * without a real Looper. The mandated case (round-10 F2) is the last test: a `schedule` racing `shutdown`
 * never leaves an armed timer.
 */
class HandlerTimerSchedulerTest {

    private val epoch = Epoch(1)
    private val delay = 100L

    private fun token(generation: Long) = TimerToken.ReadinessTimer(epoch, Generation(generation))

    /**
     * A fake [HandlerTimerScheduler.TimerOps]. `posted` models the callbacks live in a Handler queue.
     * Optional latches let a test park a `postDelayed` call after it has been reached (inside the
     * scheduler's monitor, past the terminal check) so a concurrent `shutdown` must wait for the monitor.
     */
    private class FakeTimerOps : HandlerTimerScheduler.TimerOps {
        private val posted = mutableListOf<Runnable>()
        var releaseCount = 0
        var removeAllCount = 0
        var failRemoveAll = false
        val released: Boolean get() = releaseCount > 0
        var entered: CountDownLatch? = null
        var proceed: CountDownLatch? = null

        override fun postDelayed(run: Runnable, delayMs: Long): Boolean {
            entered?.countDown()
            proceed?.await()
            synchronized(posted) { posted.add(run) }
            return true
        }

        override fun removeCallbacks(run: Runnable) {
            synchronized(posted) { posted.remove(run) }
        }

        override fun removeAll() {
            removeAllCount++
            if (failRemoveAll) error("injected removeAll failure")
            synchronized(posted) { posted.clear() }
        }

        override fun release() {
            releaseCount++
        }

        fun liveCount(): Int = synchronized(posted) { posted.size }

        /** Dispatches every live callback, as the Looper would: a dispatched message leaves the queue. */
        fun fireAll() {
            val dispatched = synchronized(posted) {
                val copy = posted.toList()
                posted.clear()
                copy
            }
            dispatched.forEach { it.run() }
        }
    }

    @Test
    fun `a scheduled timer fires once and is then no longer armed`() {
        val ops = FakeTimerOps()
        val scheduler = HandlerTimerScheduler(ops)
        val fired = mutableListOf<TimerToken>()
        val t = token(1)

        assertTrue(scheduler.schedule(t, delay) { fired.add(it) })
        assertEquals(1, ops.liveCount())

        ops.fireAll()
        assertEquals(listOf(t), fired)
        assertEquals(0, ops.liveCount())
    }

    @Test
    fun `a cancelled timer does not fire`() {
        val ops = FakeTimerOps()
        val scheduler = HandlerTimerScheduler(ops)
        val fired = mutableListOf<TimerToken>()
        val t = token(1)

        scheduler.schedule(t, delay) { fired.add(it) }
        scheduler.cancel(t)
        assertEquals(0, ops.liveCount())

        ops.fireAll()
        assertTrue(fired.isEmpty())
    }

    @Test
    fun `shutdown cancels armed timers, rejects later schedules, releases, and is idempotent`() {
        val ops = FakeTimerOps()
        val scheduler = HandlerTimerScheduler(ops)
        scheduler.schedule(token(1), delay) { }
        assertEquals(1, ops.liveCount())

        scheduler.shutdown()
        assertEquals(0, ops.liveCount())
        assertTrue(ops.released)

        assertFalse(scheduler.schedule(token(2), delay) { }) // permanently rejected
        assertEquals(0, ops.liveCount())

        scheduler.shutdown() // idempotent: no throw, still terminal
        // The port contract is that release + removeAll run exactly once, not once per shutdown() call.
        assertEquals(1, ops.releaseCount)
        assertEquals(1, ops.removeAllCount)
    }

    @Test
    fun `shutdown releases the thread even if removeAll throws, and stays terminal`() {
        val ops = FakeTimerOps()
        val scheduler = HandlerTimerScheduler(ops)
        scheduler.schedule(token(1), delay) { }
        ops.failRemoveAll = true

        // The removeAll failure must reach the runtime's diagnostics boundary, but release() must still run
        // (otherwise terminated is set, every later shutdown early-returns, and the HandlerThread leaks).
        val thrown = runCatching { scheduler.shutdown() }.exceptionOrNull()
        assertNotNull("the removeAll failure must propagate", thrown)
        assertEquals("release must run exactly once despite the removeAll failure", 1, ops.releaseCount)

        // Still terminal: later schedules are rejected and a later shutdown does not re-release.
        assertFalse(scheduler.schedule(token(2), delay) { })
        scheduler.shutdown()
        assertEquals(1, ops.releaseCount)
    }

    @Test
    fun `shutdown is safe with nothing armed`() {
        val ops = FakeTimerOps()
        val scheduler = HandlerTimerScheduler(ops)
        scheduler.shutdown()
        assertTrue(ops.released)
    }

    @Test(timeout = 5_000)
    fun `a schedule racing shutdown never leaves an armed timer`() {
        val ops = FakeTimerOps()
        val scheduler = HandlerTimerScheduler(ops)
        ops.entered = CountDownLatch(1)
        ops.proceed = CountDownLatch(1)

        // The schedule thread enters schedule(), takes the monitor, passes the terminal check, and parks
        // inside postDelayed (still holding the monitor, about to arm).
        val scheduleThread = thread { scheduler.schedule(token(1), delay) { } }
        assertTrue(ops.entered!!.await(2, TimeUnit.SECONDS))

        // shutdown() now blocks on the monitor the schedule thread holds.
        val shutdownThread = thread { scheduler.shutdown() }

        // Let the schedule finish arming and release the monitor; shutdown then runs removeAll + terminate.
        ops.proceed!!.countDown()
        scheduleThread.join(2_000)
        shutdownThread.join(2_000)

        // Whatever the interleaving, no armed timer survives and later schedules are rejected.
        assertEquals(0, ops.liveCount())
        assertFalse(scheduler.schedule(token(2), delay) { })
    }
}
