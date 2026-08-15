package com.applock.data

import java.io.File

/**
 * Pure fail-safe decision logic for [AppLockDatabase] recovery (M1/WP7(a), R-004 / FR-228 / FR-229).
 *
 * Kept free of Android and SQLCipher types so it is unit-testable on the plain JVM
 * ([com.applock.data.DatabaseRecoveryTest]); the Android-coupled orchestration — opening the
 * encrypted database, running `PRAGMA quick_check`, raising the notification, writing the audit
 * event — stays in [AppLockDatabase].
 */
internal object DatabaseRecovery {

    /**
     * The name a database that could not be opened is moved aside to. Timestamped so a repeated
     * recovery never clobbers an earlier preserved copy — the prior bytes are always retained, never
     * silently overwritten (R-004).
     */
    fun backupFileName(dbName: String, timestampMs: Long): String =
        "$dbName.recovery-$timestampMs.bak"

    /** SQLite `PRAGMA quick_check` reports `ok` on its first row when the database is intact. */
    fun isIntegrityOk(quickCheckFirstRow: String?): Boolean =
        quickCheckFirstRow?.equals("ok", ignoreCase = true) == true

    /**
     * Moves a database that could not be opened or verified aside so its bytes survive for recovery
     * (R-004: preserve, never silently wipe). The main file is renamed to a timestamped
     * `.recovery-*.bak`; the `-wal`/`-shm` sidecars are dropped — they are meaningless without their
     * parent file and would otherwise be adopted by the fresh database.
     *
     * @return the `.bak` file the database was preserved as, or null when there was nothing to move
     *   (a non-existent file, or a rename that failed). The sidecars are cleared either way. A null
     *   return with the source still present is the caller's signal to fall back to a last-resort
     *   reset rather than crash-loop on the same unreadable file.
     */
    fun moveAside(dbFile: File, timestampMs: Long): File? {
        val parent = dbFile.parentFile
        val wal = File(parent, "${dbFile.name}-wal")
        val shm = File(parent, "${dbFile.name}-shm")
        if (!dbFile.exists()) {
            wal.delete()
            shm.delete()
            return null
        }
        val backup = File(parent, backupFileName(dbFile.name, timestampMs))
        val moved = dbFile.renameTo(backup)
        wal.delete()
        shm.delete()
        return if (moved) backup else null
    }
}
