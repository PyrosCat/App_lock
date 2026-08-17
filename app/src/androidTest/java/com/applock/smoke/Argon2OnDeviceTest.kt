package com.applock.smoke

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.applock.security.Argon2PinHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * WP8 (M1) smoke: Argon2id PIN hashing runs on-device at the real cost parameters.
 *
 * The JVM suite already covers correctness with small parameters; this exercises the *production*
 * cost (19 MiB / t=2 / p=1) on a real ART heap — the canary for the constrained-heap OOM seen on
 * the API-29 image (docs/reports/campaigns/2026-08-09_wp5-matrix_nucbox-g5.md; the heap workaround
 * is in docs/testing/WP8_GMD_MATRIX.md).
 */
@RunWith(AndroidJUnit4::class)
class Argon2OnDeviceTest {

    @Test
    fun productionCostHashRoundTripsAndRejectsWrongPin() {
        val salt = Argon2PinHasher.newSalt()
        // No cost overrides → the production defaults (MEMORY_KIB / ITERATIONS / PARALLELISM).
        val hash = Argon2PinHasher.hash(CORRECT.toCharArray(), salt)

        assertEquals(32, hash.size)
        assertTrue(
            "the correct PIN must verify against its own hash",
            verifyAtProductionCost(CORRECT, salt, hash),
        )
        assertFalse(
            "a wrong PIN must not verify",
            verifyAtProductionCost(WRONG, salt, hash),
        )
    }

    private fun verifyAtProductionCost(pin: String, salt: ByteArray, expected: ByteArray): Boolean =
        Argon2PinHasher.verify(
            pin = pin.toCharArray(),
            salt = salt,
            memoryKib = Argon2PinHasher.MEMORY_KIB,
            iterations = Argon2PinHasher.ITERATIONS,
            parallelism = Argon2PinHasher.PARALLELISM,
            expected = expected,
        )

    private companion object {
        const val CORRECT = "2468"
        const val WRONG = "1357"
    }
}
