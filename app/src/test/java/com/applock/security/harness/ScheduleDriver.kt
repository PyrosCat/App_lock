package com.applock.security.harness

import com.applock.security.LockoutStorage
import kotlin.random.Random

/**
 * Runs a reproducible pseudo-random schedule of harness actions from a seed and checks the model after every step.
 * The same seed gives the same ledger trace, so a failing seed can be replayed exactly.
 *
 * The actions are manager-level: failures, resets, polls, clock changes, releases of held writes, restarts, and
 * reboots, with scripted storage faults. The driver does not apply caller gating, so it also submits failures during
 * a lockout. That is valid for the manager contract, not a claim about a caller.
 *
 * The startup and each step (one action and its check) run inside [reported]. Any failure there, an assertion in the
 * action or the check, a quiescence timeout, or a startup fault, ends the run with the seed, the step, the action,
 * and the end of the trace.
 */
class ScheduleDriver(
    private val seed: Long,
    private val steps: Int = DEFAULT_STEPS,
    private val storageDecorator: (LockoutStorage) -> LockoutStorage = { it },
) {
    private var action = "start"

    fun run(): List<String> {
        val random = Random(seed)
        val faults = FaultPlan()
        val clocks = VirtualClocks(wallMs = DRIVER_WALL_START_MS)
        return LockoutHarness(clocks = clocks, faults = faults, storageDecorator = storageDecorator).use { harness ->
            reported(harness, START_STEP) {
                planProcess(faults, random, harness.generation + 1)
                harness.start()
                harness.check()
            }
            repeat(steps) { step ->
                reported(harness, step) {
                    act(harness, faults, random)
                    harness.check()
                }
            }
            harness.ledger.trace()
        }
    }

    @Suppress("TooGenericExceptionCaught") // adds the reproduction context to any failure, then rethrows it
    private fun reported(harness: LockoutHarness, step: Int, block: () -> Unit) {
        try {
            block()
        } catch (e: AssertionError) {
            throw withContext(harness, step, e)
        } catch (e: Exception) {
            throw withContext(harness, step, e)
        }
    }

    private fun withContext(harness: LockoutHarness, step: Int, cause: Throwable): AssertionError {
        val tail = harness.ledger.trace().takeLast(TRACE_TAIL).joinToString("\n")
        return AssertionError("seed=$seed step=$step action=$action: $cause\n$tail", cause)
    }

    private fun act(harness: LockoutHarness, faults: FaultPlan, random: Random) {
        when (random.nextInt(PERCENT)) {
            in 0 until 30 -> {
                action = "fail"
                faults.write(writeScript(random), harness.generation, harness.nextWriteIndex)
                harness.fail()
            }
            in 30 until 40 -> {
                action = "succeed"
                faults.write(writeScript(random), harness.generation, harness.nextWriteIndex)
                harness.succeed()
            }
            in 40 until 55 -> {
                action = "poll"
                harness.poll()
            }
            in 55 until 70 -> {
                action = "advance"
                harness.advance(random.nextLong(0L, MAX_ADVANCE_MS))
            }
            in 70 until 85 -> {
                action = "release"
                harness.store.parkedIndexes(harness.generation, StorageOp.WRITE).firstOrNull()
                    ?.let { harness.release(StorageOp.WRITE, it) }
            }
            in 85 until 92 -> {
                action = "restart"
                planProcess(faults, random, harness.generation + 1)
                harness.restart()
            }
            in 92 until 96 -> {
                action = "jumpWall"
                harness.jumpWall(random.nextLong(-MAX_WALL_JUMP_MS, MAX_WALL_JUMP_MS))
            }
            else -> {
                action = "reboot"
                planProcess(faults, random, harness.generation + 1)
                harness.reboot(random.nextLong(0L, MAX_DOWNTIME_MS))
            }
        }
    }

    // The construction read of a process fails in about one start of four; each of its re-seed reads fails or
    // succeeds with equal chance.
    private fun planProcess(faults: FaultPlan, random: Random, generation: Int) {
        val seedRead = if (random.nextInt(PERCENT) < 25) ReadScript.Throw else ReadScript.Normal
        val reseedRead = if (random.nextBoolean()) ReadScript.Throw else ReadScript.Normal
        faults.read(seedRead, generation, index = 0)
        faults.read(reseedRead, generation)
    }

    private fun writeScript(random: Random): WriteScript = when (random.nextInt(PERCENT)) {
        in 0 until 55 -> WriteScript.Normal
        in 55 until 67 -> WriteScript.ReturnFalse
        in 67 until 75 -> WriteScript.Throw
        in 75 until 85 -> WriteScript.HoldBeforeCommit(WriteScript.Normal)
        in 85 until 90 -> WriteScript.HoldBeforeCommit(WriteScript.ReturnFalse)
        in 90 until 95 -> WriteScript.CommitThenHold
        else -> WriteScript.CommitThenReportFalse
    }

    companion object {
        const val DEFAULT_STEPS = 40
        private const val START_STEP = -1
        private const val PERCENT = 100
        private const val MAX_ADVANCE_MS = 40_000L
        private const val MAX_WALL_JUMP_MS = 3_600_000L
        private const val MAX_DOWNTIME_MS = 120_000L
        private const val TRACE_TAIL = 30

        // A realistic epoch time, so wall jumps never reach zero, which the persisted format uses for "no lockout".
        private const val DRIVER_WALL_START_MS = 1_790_000_000_000L
    }
}
