package com.applock.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun `correct pin verifies against its hash`() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("1234".toCharArray(), salt)
        assertTrue(PinHasher.verify("1234".toCharArray(), salt, PinHasher.ITERATIONS, hash))
    }

    @Test
    fun `wrong pin fails verification`() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("1234".toCharArray(), salt)
        assertFalse(PinHasher.verify("4321".toCharArray(), salt, PinHasher.ITERATIONS, hash))
    }

    @Test
    fun `same pin with different salts produces different hashes`() {
        val pin = "1234"
        val hash1 = PinHasher.hash(pin.toCharArray(), PinHasher.newSalt())
        val hash2 = PinHasher.hash(pin.toCharArray(), PinHasher.newSalt())
        assertFalse(hash1.contentEquals(hash2))
    }

    @Test
    fun `hash is deterministic for same pin and salt`() {
        val salt = PinHasher.newSalt()
        val hash1 = PinHasher.hash("9876".toCharArray(), salt)
        val hash2 = PinHasher.hash("9876".toCharArray(), salt)
        assertTrue(hash1.contentEquals(hash2))
    }

    @Test
    fun `verification with wrong iteration count fails`() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("1234".toCharArray(), salt)
        assertFalse(PinHasher.verify("1234".toCharArray(), salt, PinHasher.ITERATIONS - 1, hash))
    }

    @Test
    fun `salts are unique and correct length`() {
        val s1 = PinHasher.newSalt()
        val s2 = PinHasher.newSalt()
        assertEquals(16, s1.size)
        assertNotEquals(s1.toList(), s2.toList())
    }
}
