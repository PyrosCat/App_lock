package com.applock.security.harness

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

/**
 * The persistence executor of one simulated process: one named worker thread that runs tasks in submission order,
 * like the manager's default single-thread executor.
 *
 * The harness can [pause] it between tasks, so work that an action enqueues starts only after the harness has logged
 * that action. This keeps the ledger order stable without changing the task order. [stop] drops every queued task,
 * as process death does; a task that is running (for example, parked at a storage hold) ends when the store wakes it.
 * A task submitted after [stop] is dropped silently: a rejection would make kotlinx.coroutines run it on another
 * dispatcher, which a dead process cannot do.
 */
class ControlledIoExecutor(val threadName: String) : Executor {
    private val lock = Object()
    private val queue = ArrayDeque<Runnable>()
    private var running = false
    private var paused = false
    private var stopped = false

    /** Throwables that escaped a task. A coroutine task contains its own failures, so the harness expects none. */
    val escaped = CopyOnWriteArrayList<Throwable>()

    private val worker = Thread(::loop, threadName).apply {
        isDaemon = true
        start()
    }

    override fun execute(command: Runnable) {
        synchronized(lock) {
            if (!stopped) {
                queue.addLast(command)
                lock.notifyAll()
            }
        }
    }

    @Suppress("TooGenericExceptionCaught") // record anything that escapes a task, so the harness can fail on it
    private fun loop() {
        while (true) {
            val task = synchronized(lock) {
                while (!stopped && (paused || queue.isEmpty())) lock.wait()
                if (stopped) return
                running = true
                queue.removeFirst()
            }
            try {
                task.run()
            } catch (e: Throwable) {
                escaped += e
            } finally {
                synchronized(lock) {
                    running = false
                    lock.notifyAll()
                }
            }
        }
    }

    fun pause() = synchronized(lock) { paused = true }

    fun resume() = synchronized(lock) {
        paused = false
        lock.notifyAll()
    }

    /** True when no task runs and none is queued. */
    fun isIdle(): Boolean = synchronized(lock) { !running && queue.isEmpty() }

    /** Waits up to [timeoutMs] for a change of executor state. A spurious or timed-out wake is harmless to callers. */
    fun awaitChange(timeoutMs: Long) = synchronized(lock) { lock.wait(timeoutMs) }

    /**
     * Drops the queued tasks but keeps the worker running. Only the harness negative control uses it, to show that a
     * check detects admitted work that never runs. Returns the number of dropped tasks.
     */
    fun dropQueued(): Int = synchronized(lock) {
        val dropped = queue.size
        queue.clear()
        dropped
    }

    /** Drops the queued tasks and ends the worker after its current task. Returns the number of dropped tasks. */
    fun stop(): Int = synchronized(lock) {
        val dropped = queue.size
        queue.clear()
        stopped = true
        lock.notifyAll()
        dropped
    }

    /** Waits for the worker thread to end after [stop]. */
    fun awaitTermination(timeoutMs: Long): Boolean {
        worker.join(timeoutMs)
        return !worker.isAlive
    }
}
