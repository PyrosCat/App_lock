package com.applock.security.harness

import com.applock.security.LockoutSnapshot

/**
 * The part of the manager memory M = (c, rw, rm, f, rev, streak) of the test plan that the B3 rules read: the count
 * [count], the recorded wall deadline [recordedWall], its elapsed mirror [recordedElapsed], and the fallback deadline
 * [fallbackElapsed] on the elapsed clock. A deadline of 0 means none.
 */
data class DeadlineMemory(val count: Int, val recordedWall: Long, val recordedElapsed: Long, val fallbackElapsed: Long)

/**
 * The reference rules of P3 candidate B3 (`2026-10-07_R007_F2_HARDENING_P3_B3_SPEC.md`). Under B3, a B2 write retry
 * also stores the remaining in-memory fallback as a wall deadline, so a restart keeps the fallback. The rules are pure
 * functions, so a model with the B2 retry rules can apply them at its own steps.
 */
object DeadlineRetryRules {

    /**
     * The gap: the remaining fallback, when it is longer than the recorded remaining, or else 0. A gap is the condition
     * under which the state reads degraded.
     */
    fun gapMs(memory: DeadlineMemory, wallMs: Long, elapsedMs: Long): Long {
        val fallback = remaining(memory.fallbackElapsed, elapsedMs)
        val recorded = maxOf(remaining(memory.recordedWall, wallMs), remaining(memory.recordedElapsed, elapsedMs))
        return if (fallback > recorded) fallback else 0L
    }

    /**
     * D2 and D3: a committed write of the latest admission starts a deadline chain when its completion leaves a gap.
     * The chain's target is then already durable. [memory] and the clocks are those after the baseline completion. A
     * reset clears the fallback, so its completion starts none.
     */
    fun startsDeadlineChain(
        committed: Boolean,
        latestAdmission: Boolean,
        memory: DeadlineMemory,
        wallMs: Long,
        elapsedMs: Long,
    ): Boolean = committed && latestAdmission && gapMs(memory, wallMs, elapsedMs) > 0L

    /**
     * D4 to D6: what a still-wanted retry writes, with the clocks of its prepare step. While a gap exists, it writes
     * the converted payload (c, max(t, Wb + rem)): t is the deadline of [target], Wb is the wall time of the prepare
     * step, and rem is the gap. Without a gap, a B2 chain writes [target], and a deadline chain writes nothing (null).
     */
    fun retryPayload(
        target: LockoutSnapshot,
        deadlineChain: Boolean,
        memory: DeadlineMemory,
        wallMs: Long,
        elapsedMs: Long,
    ): LockoutSnapshot? {
        val gap = gapMs(memory, wallMs, elapsedMs)
        return when {
            gap > 0L -> LockoutSnapshot(memory.count, maxOf(target.lockoutUntil, wallMs + gap))
            deadlineChain -> null
            else -> target
        }
    }

    /**
     * The conversion only moves the deadline later (max(t, Wb + rem)), and the count is the target's while the retry
     * is still wanted. A different deadline therefore means that the gap set it. An equal deadline is the B2 payload,
     * which the B2 rule records.
     */
    fun isConverted(target: LockoutSnapshot, stored: LockoutSnapshot): Boolean =
        stored.lockoutUntil != target.lockoutUntil

    /**
     * D7: after a still-wanted commit of the converted payload [stored], the stored deadline becomes the recorded wall
     * deadline, and the elapsed mirror rises to the fallback deadline. The fallback keeps its deadline. A commit of a
     * payload that is not converted follows the B2 rule (D8).
     */
    fun recordConverted(stored: LockoutSnapshot, memory: DeadlineMemory): DeadlineMemory =
        memory.copy(
            recordedWall = stored.lockoutUntil,
            recordedElapsed = maxOf(memory.recordedElapsed, memory.fallbackElapsed),
        )

    // The deadline minus now, clamped to 0..max, as in the model.
    private fun remaining(deadline: Long, now: Long): Long =
        if (deadline == NONE) 0L else (deadline - now).coerceIn(0L, LockoutModel.MAX_MS)

    private const val NONE = 0L
}
