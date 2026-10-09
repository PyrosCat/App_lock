package com.applock.security.harness

import com.applock.security.LockoutSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the B3 rules of the reference model ([DeadlineRetryRules]) against the worked examples and the conversion
 * properties of the B3 specification. The rules are pure functions, so these tests need no manager and no write
 * retry.
 */
class DeadlineRetryRulesTest {

    // B3 spec: D1, D11.
    @Test
    fun `the gap is the remaining fallback until the fallback ends`() {
        assertEquals(T - 1_000L, gapAt(FAILED_FIRST_WRITE, 1_000L))
        assertEquals(1L, gapAt(FAILED_FIRST_WRITE, T - 1))
        assertEquals("no gap when the fallback ends", 0L, gapAt(FAILED_FIRST_WRITE, T))
        assertEquals("no gap after the fallback", 0L, gapAt(FAILED_FIRST_WRITE, T + 1))
    }

    // B3 spec: D4, D10.
    @Test
    fun `retries store the original fallback deadline under stable clocks`() {
        // The retry times of R2_4. A repeated write after a false acknowledgement (R3_4) also stores the same pair.
        listOf(1_000L, 3_000L, 7_000L, 15_000L, T - 1).forEach { atMs ->
            listOf(false, true).forEach { deadlineChain ->
                assertEquals(
                    "the retry at $atMs ms stores the original deadline (deadline chain: $deadlineChain)",
                    LockoutSnapshot(1, W0 + T),
                    payloadAt(FIRST_PAYLOAD, deadlineChain, FAILED_FIRST_WRITE, atMs),
                )
            }
        }
    }

    // B3 spec: D5, D6, D11.
    @Test
    fun `after the fallback ends only a B2 retry writes, with its target payload`() {
        listOf(T, T + 1, 31_000L, TEN_MIN_MS).forEach { atMs ->
            val b2Payload = payloadAt(FIRST_PAYLOAD, deadlineChain = false, FAILED_FIRST_WRITE, atMs)
            val deadlinePayload = payloadAt(FIRST_PAYLOAD, deadlineChain = true, FAILED_FIRST_WRITE, atMs)
            assertEquals("a B2 retry at $atMs ms writes its target payload", FIRST_PAYLOAD, b2Payload)
            assertNull("a deadline retry at $atMs ms writes nothing", deadlinePayload)
        }
    }

    // B3 spec: D5, D6.
    @Test
    fun `a recorded lockout that covers the fallback leaves no gap`() {
        // X03: a stored deadline 10 min ahead, then a failed below-threshold write.
        val longerRecorded = DeadlineMemory(4, W0 + TEN_MIN_MS, NONE, E0 + T)
        val longerTarget = LockoutSnapshot(4, W0 + TEN_MIN_MS)
        assertEquals(0L, gapAt(longerRecorded, 1_000L))
        assertEquals(longerTarget, payloadAt(longerTarget, deadlineChain = false, longerRecorded, 1_000L))
        assertNull(payloadAt(longerTarget, deadlineChain = true, longerRecorded, 1_000L))

        // H06: a committed threshold write records the window of its own fallback.
        val committedThreshold = DeadlineMemory(6, W0 + 60_000L, E0 + 60_000L, E0 + 60_000L)
        assertEquals(0L, gapAt(committedThreshold, 0L))

        // After a forward wall change, the elapsed mirror alone covers the fallback.
        val recorded = DeadlineMemory(1, W0 + T, E0 + T, E0 + T)
        assertEquals(0L, DeadlineRetryRules.gapMs(recorded, W0 + HOUR_MS + 1_000L, E0 + 1_000L))
    }

    // B3 spec: D4, D7, D8.
    @Test
    fun `a converted deadline is never earlier than the target deadline`() {
        // R2_3: F4 failed 40 s late, so its fallback outlasts the committed threshold deadline of F5.
        val lateFallback = DeadlineMemory(5, W0 + T, E0 + T, E0 + 70_000L)
        val fifthPayload = LockoutSnapshot(5, W0 + T)
        val converted = checkNotNull(payloadAt(fifthPayload, deadlineChain = true, lateFallback, 41_000L))
        assertEquals(LockoutSnapshot(5, W0 + 70_000L), converted)
        assertTrue(DeadlineRetryRules.isConverted(fifthPayload, converted))

        // X12: a threshold write that fails at once arms a fallback that ends at its own threshold deadline.
        val failedThreshold = DeadlineMemory(5, NONE, NONE, E0 + T)
        val equal = checkNotNull(payloadAt(fifthPayload, deadlineChain = false, failedThreshold, 1_000L))
        assertEquals("the conversion equals the target payload", fifthPayload, equal)
        assertFalse("the B2 rule records this commit", DeadlineRetryRules.isConverted(fifthPayload, equal))
    }

    // B3 spec: D4.
    @Test
    fun `a converted deadline is at most the cap after the prepare time`() {
        // No window is longer than the cap, so this fallback only exercises the clamp.
        val beyondCap = DeadlineMemory(1, NONE, NONE, E0 + 2 * MAX)
        assertEquals(MAX, gapAt(beyondCap, 0L))
        assertEquals(LockoutSnapshot(1, W0 + MAX), payloadAt(FIRST_PAYLOAD, deadlineChain = false, beyondCap, 0L))
    }

    // B3 spec: D4, D10.
    @Test
    fun `a wall change between retries keeps the remainder`() {
        val firstRetry = DeadlineRetryRules.retryPayload(
            FIRST_PAYLOAD,
            deadlineChain = false,
            FAILED_FIRST_WRITE,
            wallMs = REALISTIC_WALL_MS + 1_000L,
            elapsedMs = E0 + 1_000L,
        )
        assertEquals(LockoutSnapshot(1, REALISTIC_WALL_MS + T), firstRetry)

        // The wall clock jumps 1 h forward, and the next retry runs 2 s later.
        val secondRetry = DeadlineRetryRules.retryPayload(
            FIRST_PAYLOAD,
            deadlineChain = false,
            FAILED_FIRST_WRITE,
            wallMs = REALISTIC_WALL_MS + HOUR_MS + 3_000L,
            elapsedMs = E0 + 3_000L,
        )
        assertEquals(LockoutSnapshot(1, REALISTIC_WALL_MS + HOUR_MS + T), secondRetry)
    }

    // B3 spec: D2, D3.
    @Test
    fun `only a latest-admission commit that leaves a gap starts a deadline chain`() {
        // R2_2: F2 commits its count while the fallback of the failed F1 runs.
        val countUnderFallback = DeadlineMemory(2, NONE, NONE, E0 + T)
        assertTrue("a count commit under a fallback starts a chain", startsAt(countUnderFallback, 0L))
        // R2_3: F5 commits a threshold deadline that the late fallback of F4 outlasts.
        val expiredThreshold = DeadlineMemory(5, W0 + T, E0 + T, E0 + 70_000L)
        assertTrue("a threshold commit that the fallback outlasts starts a chain", startsAt(expiredThreshold, STALL_MS))

        // A failed write starts a B2 chain (D1), not a deadline chain.
        assertFalse("a failed write starts no deadline chain", startsAt(FAILED_FIRST_WRITE, 0L, committed = false))
        // H06: F5 commits after F6 was admitted, while the fallback of F6 runs.
        val staleThreshold = DeadlineMemory(6, NONE, NONE, E0 + 60_000L)
        assertFalse("a stale threshold commit starts no chain", startsAt(staleThreshold, 0L, latest = false))
        val countWithoutFallback = DeadlineMemory(2, NONE, NONE, NONE)
        assertFalse("a count commit with no fallback starts no chain", startsAt(countWithoutFallback, 0L))
        val coveringThreshold = DeadlineMemory(5, W0 + T, E0 + T, E0 + T)
        assertFalse("a threshold commit that covers its fallback starts no chain", startsAt(coveringThreshold, 0L))
        val reset = DeadlineMemory(0, NONE, NONE, NONE)
        assertFalse("a reset commit starts no chain", startsAt(reset, 0L))
    }

    // B3 spec: D7.
    @Test
    fun `a converted commit records both deadlines and leaves no gap`() {
        // R2_1: the retry at 1 s stores (1, W0 + T).
        val stored = checkNotNull(payloadAt(FIRST_PAYLOAD, deadlineChain = false, FAILED_FIRST_WRITE, 1_000L))
        val recorded = DeadlineRetryRules.recordConverted(stored, FAILED_FIRST_WRITE)
        assertEquals(DeadlineMemory(1, W0 + T, E0 + T, E0 + T), recorded)
        listOf(1_000L, 5_000L, T - 1).forEach { atMs -> assertEquals("no gap at $atMs ms", 0L, gapAt(recorded, atMs)) }

        // R2_3: the converted commit raises both recorded deadlines to the late fallback.
        val lateFallback = DeadlineMemory(5, W0 + T, E0 + T, E0 + 70_000L)
        assertEquals(
            DeadlineMemory(5, W0 + 70_000L, E0 + 70_000L, E0 + 70_000L),
            DeadlineRetryRules.recordConverted(LockoutSnapshot(5, W0 + 70_000L), lateFallback),
        )
    }

    // ---- Helpers -------------------------------------------------------------------------------

    // The gap [atMs] after W0 and E0, with stable clocks.
    private fun gapAt(memory: DeadlineMemory, atMs: Long): Long = DeadlineRetryRules.gapMs(memory, W0 + atMs, E0 + atMs)

    // The payload of a retry prepared [atMs] after W0 and E0, with stable clocks.
    private fun payloadAt(
        target: LockoutSnapshot,
        deadlineChain: Boolean,
        memory: DeadlineMemory,
        atMs: Long,
    ): LockoutSnapshot? = DeadlineRetryRules.retryPayload(target, deadlineChain, memory, W0 + atMs, E0 + atMs)

    // Whether a completion [atMs] after W0 and E0 starts a deadline chain, with stable clocks.
    private fun startsAt(memory: DeadlineMemory, atMs: Long, committed: Boolean = true, latest: Boolean = true) =
        DeadlineRetryRules.startsDeadlineChain(committed, latest, memory, W0 + atMs, E0 + atMs)

    private companion object {
        const val W0 = VirtualClocks.W0
        const val E0 = VirtualClocks.E0
        const val T = LockoutModel.BASE_MS
        const val MAX = LockoutModel.MAX_MS
        const val NONE = 0L
        const val STALL_MS = 40_000L
        const val TEN_MIN_MS = 600_000L
        const val HOUR_MS = 3_600_000L

        // A realistic epoch time, as in the X04 tests.
        const val REALISTIC_WALL_MS = 1_790_000_000_000L

        /** The memory after a failed first write at W0 and E0: count 1, no recorded lockout, and a fallback of T. */
        val FAILED_FIRST_WRITE = DeadlineMemory(1, NONE, NONE, E0 + T)

        /** The payload of a first failure below the threshold: count 1 and no deadline. */
        val FIRST_PAYLOAD = LockoutSnapshot(1, NONE)
    }
}
