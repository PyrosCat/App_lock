package com.applock.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * JVM coverage for the pure fail-safe decision logic (M1/WP7(a), R-004). The Android/SQLCipher
 * orchestration in [AppLockDatabase] is exercised by the on-device WP7 deliberate-failure drill.
 */
class DatabaseRecoveryTest {

    @Test
    fun `backup file name embeds the db name and timestamp`() {
        assertEquals(
            "applock.db.recovery-1699999999999.bak",
            DatabaseRecovery.backupFileName("applock.db", 1_699_999_999_999L),
        )
    }

    @Test
    fun `quick_check ok is integrity ok, case-insensitively`() {
        assertTrue(DatabaseRecovery.isIntegrityOk("ok"))
        assertTrue(DatabaseRecovery.isIntegrityOk("OK"))
    }

    @Test
    fun `quick_check failure, empty, or null is not integrity ok`() {
        assertFalse(DatabaseRecovery.isIntegrityOk(null))
        assertFalse(DatabaseRecovery.isIntegrityOk(""))
        assertFalse(DatabaseRecovery.isIntegrityOk("*** in database main ***"))
    }

    @Test
    fun `moveAside preserves the db as a timestamped bak and clears the sidecars`() {
        val dir = Files.createTempDirectory("dbrec").toFile()
        val db = File(dir, "applock.db").apply { writeText("payload") }
        File(dir, "applock.db-wal").apply { writeText("wal") }
        File(dir, "applock.db-shm").apply { writeText("shm") }

        val backup = DatabaseRecovery.moveAside(db, 12_345L)

        assertEquals(File(dir, "applock.db.recovery-12345.bak"), backup)
        assertTrue(backup!!.exists())
        assertEquals("payload", backup.readText()) // bytes preserved, never wiped
        assertFalse("original moved, not left in place", db.exists())
        assertFalse(File(dir, "applock.db-wal").exists())
        assertFalse(File(dir, "applock.db-shm").exists())
    }

    @Test
    fun `moveAside returns null and still clears sidecars when there is no db to move`() {
        val dir = Files.createTempDirectory("dbrec-empty").toFile()
        val staleWal = File(dir, "applock.db-wal").apply { writeText("stale") }

        val backup = DatabaseRecovery.moveAside(File(dir, "applock.db"), 1L)

        assertNull(backup)
        assertFalse("stale sidecar cleared", staleWal.exists())
    }
}
