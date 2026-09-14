package com.applock.platform.lock

import android.os.Handler
import android.os.HandlerThread
import com.applock.service.engine.TimerScheduler
import com.applock.service.engine.TimerToken

/**
 * The real [TimerScheduler] (M7 WP2 change E). It posts the `T_ready` readiness timer on a dedicated
 * [HandlerThread], and feeds a fired timer back to the interpreter through the caller's `onFire`.
 *
 * It honours the D contract for the TERMINAL [shutdown]: [shutdown] cancels every armed callback AND
 * permanently rejects later [schedule] calls (they return false), and it is **atomic with [schedule]**.
 * Every mutation runs under one monitor, so a [schedule] that races [shutdown] cannot leave an armed
 * timer: the pair either arms then is cancelled by the shutdown that follows on the monitor, or the
 * schedule sees the terminal flag and is rejected. A fired callback re-checks, under the same monitor,
 * that it is still the armed callback for its token, so a fire that was already dequeued when [shutdown]
 * cleared the table does not deliver.
 *
 * The Android [Handler] operations are behind the [TimerOps] seam, so the atomicity state machine is pure
 * Kotlin and its race is JVM-testable; [create] wires the seam to a real [HandlerThread].
 */
class HandlerTimerScheduler(private val ops: TimerOps) : TimerScheduler {

    /** The Android side of the scheduler. The real one is a [HandlerThread]; a test supplies a fake. */
    interface TimerOps {
        /** Posts [run] after [delayMs]. Returns false if the post was rejected (a dying looper). */
        fun postDelayed(run: Runnable, delayMs: Long): Boolean
        fun removeCallbacks(run: Runnable)
        fun removeAll()

        /** Releases the backing thread. Called once, from [shutdown]. */
        fun release()
    }

    private val lock = Any()
    private var terminated = false
    private val armed = HashMap<TimerToken, Runnable>()

    override fun schedule(token: TimerToken, delayMs: Long, onFire: (TimerToken) -> Unit): Boolean =
        synchronized(lock) {
            if (terminated) return@synchronized false
            // Defensive: a re-arm for the same token drops the previous callback first.
            armed[token]?.let { ops.removeCallbacks(it) }
            lateinit var runnable: Runnable
            runnable = Runnable {
                // Deliver only if this is still the armed callback for the token: a cancel, a re-arm, or a
                // shutdown that cleared the table between dequeue and here means this fire is stale.
                val fire = synchronized(lock) {
                    if (armed[token] === runnable) {
                        armed.remove(token)
                        true
                    } else {
                        false
                    }
                }
                if (fire) onFire(token)
            }
            armed[token] = runnable
            if (ops.postDelayed(runnable, delayMs)) {
                true
            } else {
                armed.remove(token) // a dying looper armed nothing
                false
            }
        }

    override fun cancel(token: TimerToken): Unit = synchronized(lock) {
        val runnable = armed.remove(token)
        if (runnable != null) ops.removeCallbacks(runnable)
    }

    override fun shutdown(): Unit = synchronized(lock) {
        if (terminated) return@synchronized // idempotent (port contract): removeAll + release run exactly once
        terminated = true
        armed.clear()
        // release() runs even if removeAll() throws: terminated is already set, so a later shutdown() would
        // early-return and never reach release, permanently leaking the HandlerThread. The finally still lets
        // the original removeAll failure propagate to the runtime's diagnostics boundary.
        try {
            ops.removeAll()
        } finally {
            ops.release()
        }
    }

    companion object {
        /** Wires the seam to a real [HandlerThread]. F uses this; the JVM race test uses a fake [TimerOps]. */
        fun create(threadName: String = "applock-lock-timer"): HandlerTimerScheduler =
            HandlerTimerScheduler(HandlerTimerOps(threadName))
    }
}

/** The real [HandlerTimerScheduler.TimerOps]: a private [HandlerThread] and its [Handler]. */
private class HandlerTimerOps(threadName: String) : HandlerTimerScheduler.TimerOps {

    private val thread = HandlerThread(threadName).apply { start() }
    private val handler = Handler(thread.looper)

    override fun postDelayed(run: Runnable, delayMs: Long): Boolean = handler.postDelayed(run, delayMs)

    override fun removeCallbacks(run: Runnable) = handler.removeCallbacks(run)

    override fun removeAll() = handler.removeCallbacksAndMessages(null)

    override fun release() {
        thread.quitSafely()
    }
}
