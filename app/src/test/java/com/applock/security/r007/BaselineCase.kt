package com.applock.security.r007

import com.applock.security.LockoutSnapshot
import com.applock.security.harness.FaultPlan
import com.applock.security.harness.GatedCaller
import com.applock.security.harness.LockoutHarness
import com.applock.security.harness.LockoutModel
import com.applock.security.harness.VirtualClocks
import org.junit.Rule
import org.junit.internal.AssumptionViolatedException
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.MultipleFailureException
import org.junit.runners.model.Statement

/**
 * The base class of the baseline characterization tests (R-007 test plan, phase P2). Each test runs one case of the
 * plan against the production `LockoutManager` through the P1 harness.
 *
 * A test asserts what the baseline does. Where the baseline does something that the plan calls unwanted, the test
 * still asserts the observed behaviour, and the test name gives the case ID. A later candidate that changes the
 * behaviour must change the test on purpose.
 *
 * The [evidence] rule writes one evidence file for each test, also when the test fails. The file holds the verdict,
 * each failure, the source identity of [EvidenceRun], the values that [record] attached, and the ledger trace of each
 * harness of the test. Each test JVM run writes to a new directory, so a rerun never replaces earlier evidence.
 */
abstract class BaselineCase {

    private class CaseHarness(val harness: LockoutHarness) {
        val notes = ArrayList<String>()
    }

    private val harnesses = mutableListOf<CaseHarness>()

    /**
     * Runs the test, closes every harness, and then saves the evidence. The traces are taken before the cleanup, so
     * they end where the test ended. The verdict is PASS only when the test and every cleanup passed. JUnit receives
     * every failure, also a failed cleanup or a failed save.
     */
    @get:Rule
    val evidence = TestRule { base, description ->
        object : Statement() {
            override fun evaluate() {
                val testFailure = runCatching { base.evaluate() }.exceptionOrNull()
                val traces = harnesses.map { it.notes.toList() + it.harness.ledger.trace() }
                val cleanupFailures = harnesses.mapNotNull { runCatching { it.harness.close() }.exceptionOrNull() }
                val saveFailure = runCatching {
                    save(description, traces, testFailure, cleanupFailures)
                }.exceptionOrNull()
                val failures = listOfNotNull(testFailure) + cleanupFailures + listOfNotNull(saveFailure)
                MultipleFailureException.assertEmpty(failures)
            }
        }
    }

    protected fun harness(
        initial: LockoutSnapshot = Z,
        faults: FaultPlan = FaultPlan(),
        clocks: VirtualClocks = VirtualClocks(),
    ): LockoutHarness = LockoutHarness(initial, clocks, faults).also { harnesses += CaseHarness(it) }

    /** Attaches a case label and its measured values to the evidence of this harness. */
    protected fun LockoutHarness.record(case: String, vararg measured: Pair<String, Any?>) {
        val notes = harnesses.first { it.harness === this }.notes
        notes += "# case=$case"
        measured.forEach { (name, value) -> notes += "# $name=$value" }
    }

    /**
     * Runs [cycles] restart cycles: each cycle starts a new process (the first cycle uses the live one) and tries wrong
     * PINs until the gate blocks one. Returns the verifier entries of each cycle.
     */
    protected fun GatedCaller.entriesPerRestartCycle(harness: LockoutHarness, cycles: Int = CYCLES): List<Int> =
        (1..cycles).map { cycle ->
            if (cycle > 1) harness.restart()
            wrongPinsUntilBlocked()
        }

    private fun save(
        description: Description,
        traces: List<List<String>>,
        testFailure: Throwable?,
        cleanupFailures: List<Throwable>,
    ) {
        val verdict = when {
            testFailure == null && cleanupFailures.isEmpty() -> "PASS"
            testFailure is AssumptionViolatedException && cleanupFailures.isEmpty() -> "SKIPPED"
            else -> "FAIL"
        }
        val text = buildString {
            appendLine("# test=${description.testClass.simpleName}.${description.methodName}")
            appendLine("# verdict=$verdict")
            testFailure?.let { appendLine("# failure=${summary(it)}") }
            cleanupFailures.forEach { appendLine("# cleanup_failure=${summary(it)}") }
            EvidenceRun.sourceLines().forEach(::appendLine)
            traces.forEachIndexed { index, lines ->
                appendLine("## harness ${index + 1}")
                lines.forEach(::appendLine)
            }
        }
        EvidenceRun.fileFor(description).writeText(text)
    }

    private fun summary(failure: Throwable): String =
        "${failure.javaClass.name}: ${failure.message.orEmpty().lineSequence().first()}"

    companion object {
        /** The base lockout window T of the plan (30 s). */
        const val T = LockoutModel.BASE_MS
        const val W0 = VirtualClocks.W0
        const val E0 = VirtualClocks.E0
        const val MAX = LockoutModel.MAX_MS

        // The fixtures of test plan §3.1: D=(count, wall deadline).
        val Z = LockoutSnapshot(0, 0L)
        val C4 = LockoutSnapshot(4, 0L)
        val L5 = LockoutSnapshot(5, W0 + T)
        val L8 = LockoutSnapshot(8, W0 + LockoutModel.ladder(8))

        /** The self-gate polls the lockout state every 250 ms. */
        const val POLL_MS = 250L

        /** The stall of the plan's held operations: 40 s, longer than the base window. */
        const val STALL_MS = 40_000L

        /** The deliberate restart cycles of the plan (R2.4, R4.4). */
        const val CYCLES = 20

        /** The poll burst of the plan (R1.1). */
        const val BURST = 100

        const val HOUR_MS = 3_600_000L
    }
}
