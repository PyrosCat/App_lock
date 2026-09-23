package com.applock.security.harness

import java.util.concurrent.ConcurrentHashMap

/** The scripted behaviour of one storage operation, captured when the operation starts. */
sealed interface StorageScript

/** The scripted behaviour of one storage read. */
sealed interface ReadScript : StorageScript {
    /** Returns the process cache. */
    data object Normal : ReadScript

    /** Throws a storage fault, as a decryption or keystore failure does. */
    data object Throw : ReadScript

    /** Parks before it reads. After release it reads the cache at that time, or throws when [thenThrow]. */
    data class HoldThenRead(val thenThrow: Boolean = false) : ReadScript

    /** Reads the cache, then parks before it returns, so the caller receives the value from before the park. */
    data object ReadThenHold : ReadScript
}

/** The scripted behaviour of one storage write. */
sealed interface WriteScript : StorageScript {
    /** Updates the cache, commits durably, and returns true. */
    data object Normal : WriteScript

    /**
     * Updates the cache, fails the disk write, and returns false. Android `SharedPreferences.commit()` updates its
     * in-memory map before the disk write and does not roll the map back when the disk write fails. So a later read
     * in the same process returns the new values, while the disk keeps the old ones.
     */
    data object ReturnFalse : WriteScript

    /** Throws before any change, as an encryption failure in the editor does. */
    data object Throw : WriteScript

    /**
     * Parks before any change. After release it continues as [then]. A hold that is never released is a write that
     * never returns.
     */
    data class HoldBeforeCommit(val then: WriteScript = Normal) : WriteScript {
        init {
            require(then is Normal || then is ReturnFalse || then is Throw || then is CommitThenReportFalse) {
                "a hold continues with a script that does not hold again"
            }
        }
    }

    /** Updates the cache and commits durably, then parks before it returns true. */
    data object CommitThenHold : WriteScript

    /**
     * Commits durably, then reports false. This is an injected acknowledgement ambiguity for protocol tests, not a
     * behaviour that Android is known to produce.
     */
    data object CommitThenReportFalse : WriteScript
}

/** True when the script makes a durable change. */
fun WriteScript.commits(): Boolean = when (this) {
    WriteScript.Normal, WriteScript.CommitThenHold, WriteScript.CommitThenReportFalse -> true
    WriteScript.ReturnFalse, WriteScript.Throw -> false
    is WriteScript.HoldBeforeCommit -> then.commits()
}

/** The value that the script makes the write return, or null when it makes the write throw. */
fun WriteScript.returnValue(): Boolean? = when (this) {
    WriteScript.Normal, WriteScript.CommitThenHold -> true
    WriteScript.ReturnFalse, WriteScript.CommitThenReportFalse -> false
    WriteScript.Throw -> null
    is WriteScript.HoldBeforeCommit -> then.returnValue()
}

/** True when the script changes the process cache. A throwing write fails before the editor changes anything. */
fun WriteScript.updatesCache(): Boolean = when (this) {
    WriteScript.Throw -> false
    is WriteScript.HoldBeforeCommit -> then.updatesCache()
    else -> true
}

/**
 * Storage faults, keyed by process generation and by operation index inside the generation (read N, write N), so a
 * fault hits one exact operation. [ALL] matches every generation or every index. The most specific rule applies, in
 * this order: one operation; all indexes of one generation; one index in all generations; everything.
 *
 * The plan belongs to the store, not to a process, so a restart does not reset it. A test can change it at any time.
 * An operation takes its script when it starts and keeps it to the end, and every ledger event of the operation
 * carries that script. So a change affects only operations that start later.
 */
class FaultPlan {
    private data class Key(val generation: Int, val index: Int)

    private val reads = ConcurrentHashMap<Key, ReadScript>()
    private val writes = ConcurrentHashMap<Key, WriteScript>()

    fun read(script: ReadScript, generation: Int = ALL, index: Int = ALL): FaultPlan = apply {
        reads[Key(generation, index)] = script
    }

    fun write(script: WriteScript, generation: Int = ALL, index: Int = ALL): FaultPlan = apply {
        writes[Key(generation, index)] = script
    }

    fun clearReads(): FaultPlan = apply { reads.clear() }

    fun clearWrites(): FaultPlan = apply { writes.clear() }

    fun readFor(generation: Int, index: Int): ReadScript = lookup(reads, generation, index) ?: ReadScript.Normal

    fun writeFor(generation: Int, index: Int): WriteScript = lookup(writes, generation, index) ?: WriteScript.Normal

    private fun <T> lookup(rules: Map<Key, T>, generation: Int, index: Int): T? =
        rules[Key(generation, index)]
            ?: rules[Key(generation, ALL)]
            ?: rules[Key(ALL, index)]
            ?: rules[Key(ALL, ALL)]

    companion object {
        const val ALL = -1
    }
}
