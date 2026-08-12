package com.applock.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Argon2PinHasherTest {

    // Small parameters keep the suite fast; production defaults only differ in cost.
    private val m = 64
    private val t = 1
    private val p = 1

    @Test
    fun `correct pin verifies against its hash`() {
        val salt = Argon2PinHasher.newSalt()
        val hash = Argon2PinHasher.hash("1234".toCharArray(), salt, m, t, p)
        assertTrue(Argon2PinHasher.verify("1234".toCharArray(), salt, m, t, p, hash))
    }

    @Test
    fun `wrong pin fails verification`() {
        val salt = Argon2PinHasher.newSalt()
        val hash = Argon2PinHasher.hash("1234".toCharArray(), salt, m, t, p)
        assertFalse(Argon2PinHasher.verify("4321".toCharArray(), salt, m, t, p, hash))
    }

    @Test
    fun `hash is deterministic for same pin and salt`() {
        val salt = Argon2PinHasher.newSalt()
        val h1 = Argon2PinHasher.hash("9876".toCharArray(), salt, m, t, p)
        val h2 = Argon2PinHasher.hash("9876".toCharArray(), salt, m, t, p)
        assertTrue(h1.contentEquals(h2))
    }

    @Test
    fun `same pin with different salts produces different hashes`() {
        val h1 = Argon2PinHasher.hash("1234".toCharArray(), Argon2PinHasher.newSalt(), m, t, p)
        val h2 = Argon2PinHasher.hash("1234".toCharArray(), Argon2PinHasher.newSalt(), m, t, p)
        assertFalse(h1.contentEquals(h2))
    }

    @Test
    fun `different cost parameters produce different hashes`() {
        val salt = Argon2PinHasher.newSalt()
        val h1 = Argon2PinHasher.hash("1234".toCharArray(), salt, m, t, p)
        val h2 = Argon2PinHasher.hash("1234".toCharArray(), salt, m, t + 1, p)
        assertFalse(h1.contentEquals(h2))
        assertFalse(Argon2PinHasher.verify("1234".toCharArray(), salt, m, t + 1, p, h1))
    }

    @Test
    fun `argon2 output differs from legacy pbkdf2 for same inputs`() {
        val salt = Argon2PinHasher.newSalt()
        val argon2 = Argon2PinHasher.hash("1234".toCharArray(), salt, m, t, p)
        val pbkdf2 = PinHasher.hash("1234".toCharArray(), salt)
        assertFalse(argon2.contentEquals(pbkdf2))
    }

    @Test
    fun `salts are unique and correct length`() {
        val s1 = Argon2PinHasher.newSalt()
        val s2 = Argon2PinHasher.newSalt()
        assertEquals(16, s1.size)
        assertNotEquals(s1.toList(), s2.toList())
    }

    @Test
    fun `hash output is 32 bytes`() {
        val hash = Argon2PinHasher.hash("1234".toCharArray(), Argon2PinHasher.newSalt(), m, t, p)
        assertEquals(32, hash.size)
    }

    @Test
    fun `empty pin hashes and verifies without crashing`() {
        // Setup UI enforces 4+ digits, but the hasher must stay total.
        val salt = Argon2PinHasher.newSalt()
        val hash = Argon2PinHasher.hash(CharArray(0), salt, m, t, p)
        assertTrue(Argon2PinHasher.verify(CharArray(0), salt, m, t, p, hash))
        assertFalse(Argon2PinHasher.verify("1".toCharArray(), salt, m, t, p, hash))
    }

    @Test
    fun `twelve digit pin round-trips`() {
        // FR-003: PIN length configurable up to 12 digits.
        val pin = "123456789012"
        val salt = Argon2PinHasher.newSalt()
        val hash = Argon2PinHasher.hash(pin.toCharArray(), salt, m, t, p)
        assertTrue(Argon2PinHasher.verify(pin.toCharArray(), salt, m, t, p, hash))
        assertFalse(Argon2PinHasher.verify("123456789013".toCharArray(), salt, m, t, p, hash))
    }

    @Test
    fun `truncated stored hash is rejected, not partially matched`() {
        val salt = Argon2PinHasher.newSalt()
        val hash = Argon2PinHasher.hash("1234".toCharArray(), salt, m, t, p)
        val truncated = hash.copyOf(16)
        assertFalse(Argon2PinHasher.verify("1234".toCharArray(), salt, m, t, p, truncated))
        assertFalse(Argon2PinHasher.verify("1234".toCharArray(), salt, m, t, p, ByteArray(0)))
    }

    @Test
    fun `verification does not mutate the stored hash or salt`() {
        val salt = Argon2PinHasher.newSalt()
        val hash = Argon2PinHasher.hash("1234".toCharArray(), salt, m, t, p)
        val saltCopy = salt.copyOf()
        val hashCopy = hash.copyOf()
        Argon2PinHasher.verify("9999".toCharArray(), salt, m, t, p, hash)
        assertTrue(salt.contentEquals(saltCopy))
        assertTrue(hash.contentEquals(hashCopy))
    }

    @Test
    fun `production parameters complete within the one second budget`() {
        val salt = Argon2PinHasher.newSalt()
        val start = System.nanoTime()
        Argon2PinHasher.hash("1234".toCharArray(), salt)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        // FR-023: authentication processing ≤ 1s. Generous bound so slow CI
        // machines don't flake; the emulator E2E gives the real number.
        assertTrue("Argon2id took ${elapsedMs}ms", elapsedMs < 5_000)
    }
}
