package com.applock.security.harness

import com.applock.security.LockoutManager
import com.applock.security.LockoutState

/**
 * A manager-level stand-in for a PIN caller. Before each attempt it reads the lockout state, as the live callers do,
 * and it enters PIN verification only when the state is Available. This class is not the caller code: it shows what a
 * caller that follows this rule can do against the manager.
 *
 * The attempts go through the harness, so the model checks each step. The counters cover all processes of the harness,
 * so a test can count the verifier entries (V in the test plan) over several restarts.
 */
class GatedCaller(private val harness: LockoutHarness) {
    var verifierEntries = 0
        private set
    var blocked = 0
        private set

    /** The pending result of each wrong PIN that reached the manager, in order. */
    val failures = ArrayList<LockoutManager.Pending<LockoutManager.FailureOutcome>>()

    /** Tries a wrong PIN. Returns true when the gate let it reach verification and the failure was admitted. */
    fun wrongPin(): Boolean {
        if (!gateOpen()) return false
        failures += harness.fail()
        return true
    }

    /** Tries the correct PIN. Returns true when the gate let it reach verification and the reset was admitted. */
    fun correctPin(): Boolean {
        if (!gateOpen()) return false
        harness.succeed()
        return true
    }

    /**
     * Tries wrong PINs until the gate blocks one, or until [limit] of them reached verification. Returns the number
     * that reached verification.
     */
    fun wrongPinsUntilBlocked(limit: Int = DEFAULT_LIMIT): Int {
        var entries = 0
        while (entries < limit && wrongPin()) entries++
        return entries
    }

    private fun gateOpen(): Boolean {
        if (harness.poll() !is LockoutState.Available) {
            blocked++
            return false
        }
        verifierEntries++
        return true
    }

    private companion object {
        const val DEFAULT_LIMIT = 100
    }
}
