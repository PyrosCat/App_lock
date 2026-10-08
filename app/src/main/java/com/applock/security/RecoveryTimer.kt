package com.applock.security

import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Runs a task once after a delay, on a thread that is not the lockout writer. [RecoveryScheduler] uses it for the delay
 * before each retry, so a delay never occupies the writer.
 */
interface RecoveryTimer {
    /** A planned task. [cancel] removes it; cancelling a task that already ran does nothing. */
    fun interface Handle {
        fun cancel()
    }

    /** Plans [task] to run once after [delayMs]. The call must not block, because the manager lock is held. */
    fun schedule(delayMs: Long, task: () -> Unit): Handle

    /** Cancels every planned task and refuses new ones. */
    fun stop()
}

/**
 * The production [RecoveryTimer]: one daemon thread named [THREAD_NAME] behind a scheduled executor that drops
 * cancelled tasks. The thread starts at the first [schedule] call, so a manager that never retries never starts it.
 * The executor measures delays on the monotonic clock of the JVM, which pauses during deep sleep on Android, so a task
 * can run later than its delay.
 */
class ScheduledRecoveryTimer : RecoveryTimer {
    private var executor: ScheduledThreadPoolExecutor? = null
    private var stopped = false

    /** Whether the timer has started its thread. */
    val started: Boolean
        @Synchronized get() = executor != null

    @Synchronized
    override fun schedule(delayMs: Long, task: () -> Unit): RecoveryTimer.Handle {
        if (stopped) return RecoveryTimer.Handle { }
        val future = executorOrStart().schedule(Runnable { task() }, delayMs, TimeUnit.MILLISECONDS)
        return RecoveryTimer.Handle { future.cancel(false) }
    }

    @Synchronized
    override fun stop() {
        stopped = true
        executor?.shutdownNow()
    }

    private fun executorOrStart(): ScheduledThreadPoolExecutor =
        executor ?: ScheduledThreadPoolExecutor(1) { runnable ->
            Thread(runnable, THREAD_NAME).apply { isDaemon = true }
        }.apply { removeOnCancelPolicy = true }.also { executor = it }

    companion object {
        const val THREAD_NAME = "lockout-retry"
    }
}
