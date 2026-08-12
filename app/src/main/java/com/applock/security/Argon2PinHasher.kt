package com.applock.security

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom

/**
 * Argon2id PIN hashing (TAS §7.1, FR-011) via BouncyCastle's pure-Java
 * implementation — no native libs, unit-testable on the JVM.
 *
 * Default parameters follow the OWASP password-storage recommendation
 * (19 MiB memory, 2 iterations, 1 lane); well under FR-023's 1-second budget.
 */
object Argon2PinHasher {

    const val MEMORY_KIB = 19_456
    const val ITERATIONS = 2
    const val PARALLELISM = 1
    private const val HASH_LENGTH_BYTES = 32
    private const val SALT_BYTES = 16

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    fun hash(
        pin: CharArray,
        salt: ByteArray,
        memoryKib: Int = MEMORY_KIB,
        iterations: Int = ITERATIONS,
        parallelism: Int = PARALLELISM,
    ): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(memoryKib)
            .withIterations(iterations)
            .withParallelism(parallelism)
            .withSalt(salt)
            .build()
        val generator = Argon2BytesGenerator()
        generator.init(params)
        val out = ByteArray(HASH_LENGTH_BYTES)
        // The char[] overload converts via the parameters' UTF-8 converter and
        // wipes its internal byte copy when done.
        generator.generateBytes(pin, out)
        return out
    }

    /** Constant-time comparison to avoid timing side channels. */
    fun verify(
        pin: CharArray,
        salt: ByteArray,
        memoryKib: Int,
        iterations: Int,
        parallelism: Int,
        expected: ByteArray,
    ): Boolean {
        val actual = hash(pin, salt, memoryKib, iterations, parallelism)
        if (actual.size != expected.size) return false
        var diff = 0
        for (i in actual.indices) diff = diff or (actual[i].toInt() xor expected[i].toInt())
        return diff == 0
    }
}
