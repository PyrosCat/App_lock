package com.applock.security.harness

import com.applock.security.RecoveryTimer

/**
 * A [RecoveryTimer] on the elapsed clock of [VirtualClocks]. A task is due at the elapsed time of its schedule call
 * plus its delay. [fireDue] runs the due tasks in due-time order, on the calling thread. A cancelled task never runs,
 * and after [stop] no task runs. A wall-clock jump moves no due time, so it fires nothing.
 */
class VirtualRecoveryTimer(private val clocks: VirtualClocks) : RecoveryTimer {
    private class Task(val dueMs: Long, val order: Long, val action: () -> Unit) {
        @Volatile
        var cancelled = false
    }

    private val lock = Any()
    private val tasks = ArrayList<Task>()
    private var nextOrder = 0L
    private var stopped = false

    override fun schedule(delayMs: Long, task: () -> Unit): RecoveryTimer.Handle {
        synchronized(lock) {
            if (stopped) return RecoveryTimer.Handle { }
            val entry = Task(clocks.elapsedMs() + delayMs, nextOrder++, task)
            tasks += entry
            return RecoveryTimer.Handle {
                synchronized(lock) {
                    entry.cancelled = true
                    tasks.remove(entry)
                }
            }
        }
    }

    override fun stop() {
        synchronized(lock) {
            stopped = true
            tasks.forEach { it.cancelled = true }
            tasks.clear()
        }
    }

    /** The number of planned tasks that have not run yet. */
    fun pendingCount(): Int = synchronized(lock) { tasks.size }

    /**
     * Runs every task that is due at the current elapsed time, in due-time order, and returns how many ran. The tasks
     * run outside the timer lock, as a real timer thread runs them.
     */
    fun fireDue(): Int {
        val due = synchronized(lock) {
            val now = clocks.elapsedMs()
            tasks.filter { it.dueMs <= now }
                .sortedWith(compareBy<Task>({ it.dueMs }, { it.order }))
                .also { tasks.removeAll(it.toSet()) }
        }
        // An earlier task of this batch can cancel a later one or stop the timer, so both are checked right before
        // each task runs.
        var ran = 0
        due.forEach { task ->
            if (synchronized(lock) { !stopped && !task.cancelled }) {
                task.action()
                ran++
            }
        }
        return ran
    }
}
