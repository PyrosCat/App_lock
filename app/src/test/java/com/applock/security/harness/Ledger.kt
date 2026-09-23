package com.applock.security.harness

import com.applock.security.LockoutSnapshot

enum class StorageOp { READ, WRITE }

/**
 * The name of the current thread without the coroutine suffix. In debug mode (on when the JVM runs with assertions,
 * as Gradle tests do), kotlinx.coroutines appends " @coroutine#N" while a coroutine runs, and N is a JVM-wide
 * counter, so the raw name is neither stable nor equal to the executor's thread name.
 */
fun stableThreadName(): String = Thread.currentThread().name.substringBefore(" @")

/**
 * One storage operation: its process generation, its kind, its index inside the generation, and the script it took
 * when it started.
 */
data class OpId(val generation: Int, val op: StorageOp, val index: Int, val script: StorageScript)

enum class Phase {
    /** The operation started. The event names the script it received and, for a write, its payload. */
    BEGIN,

    /** The operation parked at its barrier. */
    HELD,

    /** A write changed the process cache. */
    CACHE_UPDATED,

    /** A write changed the durable state. */
    DURABLE_COMMIT,

    /** The operation returned. A read carries its value; a write carries its result. */
    RETURNED,

    /** The operation threw a storage fault. */
    THREW,

    /** The process died while the operation was parked. The operation returns nothing to a live caller. */
    KILLED_IN_FLIGHT,

    /** An operation from a dead process reached the store and was rejected without a change. */
    REJECTED_DEAD,
}

/** One entry in the harness ledger. [seq] orders all entries of one harness. */
sealed interface LedgerEvent {
    val seq: Int
    val generation: Int

    data class Storage(
        override val seq: Int,
        override val generation: Int,
        val op: StorageOp,
        val index: Int,
        val phase: Phase,
        val script: StorageScript,
        val value: LockoutSnapshot?,
        val result: Boolean?,
        val wallMs: Long,
        val elapsedMs: Long,
        val thread: String,
    ) : LedgerEvent

    /** A harness action: a process start or kill, an admission, a poll, a clock change, a release, a fixture. */
    data class Action(
        override val seq: Int,
        override val generation: Int,
        val name: String,
        val detail: String,
        val wallMs: Long,
        val elapsedMs: Long,
    ) : LedgerEvent
}

/**
 * The harness evidence log: every storage operation phase and every harness action, in one order. It holds no
 * credential data. [trace] renders it as stable text, so two runs of one seed can be compared line by line.
 */
class Ledger {
    private val lock = Any()
    private val events = ArrayList<LedgerEvent>()

    fun storage(
        id: OpId,
        phase: Phase,
        clocks: VirtualClocks,
        value: LockoutSnapshot? = null,
        result: Boolean? = null,
    ) = synchronized(lock) {
        events += LedgerEvent.Storage(
            events.size, id.generation, id.op, id.index, phase, id.script, value, result,
            clocks.wallMs(), clocks.elapsedMs(), stableThreadName(),
        )
    }

    fun action(generation: Int, name: String, detail: String, clocks: VirtualClocks) = synchronized(lock) {
        events += LedgerEvent.Action(events.size, generation, name, detail, clocks.wallMs(), clocks.elapsedMs())
    }

    fun snapshot(): List<LedgerEvent> = synchronized(lock) { events.toList() }

    fun storageEvents(generation: Int, fromSeq: Int = 0): List<LedgerEvent.Storage> =
        snapshot().filterIsInstance<LedgerEvent.Storage>().filter { it.generation == generation && it.seq >= fromSeq }

    fun size(): Int = synchronized(lock) { events.size }

    fun trace(): List<String> = snapshot().map { event ->
        when (event) {
            is LedgerEvent.Storage -> buildString {
                append("g${event.generation} ${event.op}#${event.index} ${event.phase} ${event.script}")
                event.value?.let { append(" (${it.failureCount},${it.lockoutUntil})") }
                event.result?.let { append(" -> $it") }
                append(" w=${event.wallMs} e=${event.elapsedMs} t=${event.thread}")
            }
            is LedgerEvent.Action ->
                "g${event.generation} ${event.name} ${event.detail} w=${event.wallMs} e=${event.elapsedMs}"
        }
    }
}
