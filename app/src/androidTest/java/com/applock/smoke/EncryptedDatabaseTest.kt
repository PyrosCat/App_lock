package com.applock.smoke

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.applock.data.AppLockDatabase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * WP8 (M1) smoke: the app database is SQLCipher-encrypted at rest (FR-162 / FR-164).
 *
 * Only a device has the sqlcipher `.so` and the Keystore-backed passphrase, so this can only run
 * on-device. It builds the database through the production [AppLockDatabase.build] path (which runs
 * the fail-safe open + `PRAGMA quick_check`), forces the file onto disk, then reads the raw header.
 * A plaintext SQLite file begins with the 16-byte magic "SQLite format 3" + NUL; an encrypted one
 * does not.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {

    @Test
    fun `database file header is not the plaintext SQLite magic`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val dbFile = context.getDatabasePath("applock.db")

        val db = AppLockDatabase.build(context)
        try {
            // Touch the writable connection so the encrypted header is written to disk.
            db.openHelper.writableDatabase.query("PRAGMA user_version").use { it.moveToFirst() }
        } finally {
            db.close()
        }

        assertTrue("expected $dbFile to exist after build()", dbFile.exists())
        val header = dbFile.readBytes().copyOf(SQLITE_MAGIC.size)
        assertFalse(
            "database header is the plaintext SQLite magic — the store is not encrypted at rest",
            header.contentEquals(SQLITE_MAGIC),
        )
    }

    private companion object {
        // The 16-byte SQLite plaintext header: the ASCII "SQLite format 3" then a NUL terminator.
        // Spelled out as bytes so no NUL ever lives inside a source string literal.
        val SQLITE_MAGIC = byteArrayOf(
            0x53, 0x51, 0x4C, 0x69, 0x74, 0x65, 0x20, 0x66,
            0x6F, 0x72, 0x6D, 0x61, 0x74, 0x20, 0x33, 0x00,
        )
    }
}
