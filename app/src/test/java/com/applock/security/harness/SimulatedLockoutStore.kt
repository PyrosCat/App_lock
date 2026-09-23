package com.applock.security.harness

import com.applock.security.LockoutSnapshot
import com.applock.security.LockoutStorage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** An injected storage fault. The manager contains it like a real read or write fault. */
class StorageFaultException(message: String) : IllegalStateException(message)

/** Thrown to an operation whose process the harness killed. A live caller never receives it. */
class ProcessKilledException(message: String) : IllegalStateException(message)

/**
 * A lockout store for JVM tests that separates the durable state from the cache of each process, as Android
 * `SharedPreferences` does. A fresh process loads its cache from the durable state. A write that fails on disk still
 * changes the cache of its process (see [WriteScript.ReturnFalse]), so a same-process read is not evidence of
 * durability; only the durable state survives a restart.
 *
 * Each process is a generation. [open] gives a generation its storage view. [fence] marks a generation dead under the
 * store lock, so an operation of a dead process can make no further change, even if its thread still runs. This
 * fence, not `LockoutManager.shutdown()`, is the crash simulation.
 *
 * The [FaultPlan] scripts each operation by generation and index. The [Ledger] records every phase of every
 * operation. The store also records the largest number of writes that were in flight at the same time.
 */
class SimulatedLockoutStore(
    initial: LockoutSnapshot,
    private val clocks: VirtualClocks,
    private val faults: FaultPlan,
    private val ledger: Ledger,
) {
    private data class HoldKey(val generation: Int, val op: StorageOp, val index: Int)

    private class Hold {
        val parked = CountDownLatch(1)
        val release = CountDownLatch(1)

        @Volatile
        var thread: String? = null

        // Set by the releasing thread before the countdown, so a parked operation stops counting as parked at the
        // moment of release, not later when its thread wakes. The harness then waits for the executor instead.
        @Volatile
        var released = false

        @Volatile
        var killed = false

        fun open() {
            released = true
            release.countDown()
        }

        fun isParked(): Boolean = parked.count == 0L && !released
    }

    private val lock = Any()
    private var durable: LockoutSnapshot = initial
    private val caches = HashMap<Int, LockoutSnapshot>()
    private val dead = HashSet<Int>()

    private val readIndexes = ConcurrentHashMap<Int, AtomicInteger>()
    private val writeIndexes = ConcurrentHashMap<Int, AtomicInteger>()
    private val holds = ConcurrentHashMap<HoldKey, Hold>()
    private val activeWrites = AtomicInteger(0)
    private val maxActiveWrites = AtomicInteger(0)

    fun durableState(): LockoutSnapshot = synchronized(lock) { durable }

    fun cacheOf(generation: Int): LockoutSnapshot? = synchronized(lock) { caches[generation] }

    fun readCount(generation: Int): Int = readIndexes[generation]?.get() ?: 0

    fun writeCount(generation: Int): Int = writeIndexes[generation]?.get() ?: 0

    /** The largest number of writes in flight at the same time, over all generations. One ordered writer keeps it 1. */
    fun maxConcurrentWrites(): Int = maxActiveWrites.get()

    /** Opens the storage view of a new process. Its cache starts from the durable state. */
    fun open(generation: Int): LockoutStorage {
        synchronized(lock) {
            require(generation !in caches) { "generation $generation already opened" }
            caches[generation] = durable
        }
        return ProcessView(generation)
    }

    /** Marks [generation] dead. After this, no operation of that generation can change the cache or the store. */
    fun fence(generation: Int) = synchronized(lock) { dead += generation }

    /** Wakes the parked operations of a fenced generation. Each one ends with [ProcessKilledException]. */
    fun wakeKilled(generation: Int) {
        holds.filterKeys { it.generation == generation }.values.forEach { hold ->
            hold.killed = true
            hold.open()
        }
    }

    /** Waits until the named operation parks at its hold. */
    fun awaitHeld(generation: Int, op: StorageOp, index: Int, timeoutMs: Long = HOLD_WAIT_MS) {
        val hold = holds.computeIfAbsent(HoldKey(generation, op, index)) { Hold() }
        check(hold.parked.await(timeoutMs, TimeUnit.MILLISECONDS)) { "g$generation $op#$index never parked" }
    }

    fun release(generation: Int, op: StorageOp, index: Int) {
        holds.computeIfAbsent(HoldKey(generation, op, index)) { Hold() }.open()
    }

    /** True while an operation run by [thread] is parked at a hold. */
    fun isParked(thread: String): Boolean = holds.values.any { it.thread == thread && it.isParked() }

    /** The indexes of the parked operations of [op] in [generation], lowest first. */
    fun parkedIndexes(generation: Int, op: StorageOp): List<Int> =
        holds.entries
            .filter { (key, hold) -> key.generation == generation && key.op == op && hold.isParked() }
            .map { it.key.index }
            .sorted()

    /**
     * Replaces the durable state behind the model. Only the negative controls of the harness use it, to show that the
     * model detects a restart from state it did not expect. The ledger records it.
     */
    fun tamperDurable(generation: Int, snapshot: LockoutSnapshot) {
        synchronized(lock) { durable = snapshot }
        ledger.action(generation, "TAMPER", "durable=(${snapshot.failureCount},${snapshot.lockoutUntil})", clocks)
    }

    private inner class ProcessView(private val generation: Int) : LockoutStorage {

        override fun read(): LockoutSnapshot {
            val index = readIndexes.computeIfAbsent(generation) { AtomicInteger() }.getAndIncrement()
            val script = faults.readFor(generation, index)
            val id = OpId(generation, StorageOp.READ, index, script)
            record(id, Phase.BEGIN)
            rejectIfDead(id)
            return when (script) {
                ReadScript.Normal -> returned(id, cache())
                ReadScript.Throw -> threw(id)
                is ReadScript.HoldThenRead -> {
                    park(id)
                    if (script.thenThrow) threw(id)
                    returned(id, cache())
                }
                ReadScript.ReadThenHold -> {
                    val value = cache()
                    park(id)
                    returned(id, value)
                }
            }
        }

        override fun write(snapshot: LockoutSnapshot): Boolean {
            val index = writeIndexes.computeIfAbsent(generation) { AtomicInteger() }.getAndIncrement()
            val script = faults.writeFor(generation, index)
            val id = OpId(generation, StorageOp.WRITE, index, script)
            record(id, Phase.BEGIN, value = snapshot)
            rejectIfDead(id)
            val active = activeWrites.incrementAndGet()
            maxActiveWrites.accumulateAndGet(active, ::maxOf)
            try {
                val effective = if (script is WriteScript.HoldBeforeCommit) {
                    park(id)
                    script.then
                } else {
                    script
                }
                return when (effective) {
                    WriteScript.Normal -> {
                        commit(id, snapshot)
                        writeReturned(id, true)
                    }
                    WriteScript.ReturnFalse -> {
                        updateCache(id, snapshot)
                        writeReturned(id, false)
                    }
                    WriteScript.Throw -> threw(id)
                    WriteScript.CommitThenHold -> {
                        commit(id, snapshot)
                        park(id)
                        writeReturned(id, true)
                    }
                    WriteScript.CommitThenReportFalse -> {
                        commit(id, snapshot)
                        writeReturned(id, false)
                    }
                    is WriteScript.HoldBeforeCommit -> error("a hold does not continue with another hold")
                }
            } finally {
                activeWrites.decrementAndGet()
            }
        }

        private fun cache(): LockoutSnapshot = synchronized(lock) { caches.getValue(generation) }

        // The cache and the durable state change together under the store lock, after the dead check, so a fenced
        // generation can never commit.
        private fun commit(id: OpId, snapshot: LockoutSnapshot) {
            synchronized(lock) {
                rejectIfDeadLocked(id)
                caches[generation] = snapshot
                durable = snapshot
                record(id, Phase.CACHE_UPDATED, value = snapshot)
                record(id, Phase.DURABLE_COMMIT, value = snapshot)
            }
        }

        private fun updateCache(id: OpId, snapshot: LockoutSnapshot) {
            synchronized(lock) {
                rejectIfDeadLocked(id)
                caches[generation] = snapshot
                record(id, Phase.CACHE_UPDATED, value = snapshot)
            }
        }

        private fun park(id: OpId) {
            val hold = holds.computeIfAbsent(HoldKey(generation, id.op, id.index)) { Hold() }
            hold.thread = stableThreadName()
            record(id, Phase.HELD)
            hold.parked.countDown()
            hold.release.await()
            if (hold.killed) {
                record(id, Phase.KILLED_IN_FLIGHT)
                throw ProcessKilledException("$id: the process died while the operation was parked")
            }
        }

        private fun rejectIfDead(id: OpId) = synchronized(lock) { rejectIfDeadLocked(id) }

        private fun rejectIfDeadLocked(id: OpId) {
            if (generation in dead) {
                record(id, Phase.REJECTED_DEAD)
                throw ProcessKilledException("$id: the process is dead")
            }
        }

        private fun returned(id: OpId, value: LockoutSnapshot): LockoutSnapshot {
            record(id, Phase.RETURNED, value = value)
            return value
        }

        private fun writeReturned(id: OpId, result: Boolean): Boolean {
            record(id, Phase.RETURNED, result = result)
            return result
        }

        private fun threw(id: OpId): Nothing {
            record(id, Phase.THREW)
            throw StorageFaultException("$id: injected storage fault")
        }

        private fun record(id: OpId, phase: Phase, value: LockoutSnapshot? = null, result: Boolean? = null) =
            ledger.storage(id, phase, clocks, value, result)
    }

    private companion object {
        const val HOLD_WAIT_MS = 5_000L
    }
}
