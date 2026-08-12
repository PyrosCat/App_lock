package com.applock.data

import android.content.ContentValues
import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.applock.security.DatabaseKeyProvider
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import android.database.sqlite.SQLiteDatabase as FrameworkSQLiteDatabase

@Database(
    entities = [
        ProtectedAppEntity::class,
        SecurityEventEntity::class,
        IntruderEventEntity::class,
        VaultItemEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppLockDatabase : RoomDatabase() {

    abstract fun protectedAppDao(): ProtectedAppDao
    abstract fun securityEventDao(): SecurityEventDao
    abstract fun intruderEventDao(): IntruderEventDao
    abstract fun vaultItemDao(): VaultItemDao

    companion object {

        private const val TAG = "AppLockDatabase"
        private const val DB_NAME = "applock.db"

        /** Phase 3: intruder events (FR-082) and vault index (FR-106). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `intruder_events` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, " +
                        "`packageName` TEXT, " +
                        "`authMethod` TEXT NOT NULL, " +
                        "`failedAttempts` INTEGER NOT NULL, " +
                        "`batteryPercent` INTEGER NOT NULL, " +
                        "`orientation` TEXT NOT NULL, " +
                        "`photoFileName` TEXT)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vault_items` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`displayName` TEXT NOT NULL, " +
                        "`mimeType` TEXT NOT NULL, " +
                        "`sizeBytes` INTEGER NOT NULL, " +
                        "`importedAt` INTEGER NOT NULL, " +
                        "`fileName` TEXT NOT NULL)"
                )
            }
        }

        /**
         * SQLCipher-encrypted Room (FR-162/FR-164). A database created by
         * Phase 1 is plaintext — its rows are carried into the encrypted
         * database on first open (the schema is two tiny tables, so a
         * read-and-reinsert is simpler and more debuggable than SQLCipher's
         * sqlcipher_export dance). The PIN hash still lives in
         * EncryptedSharedPreferences, not here.
         */
        fun build(context: Context): AppLockDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = DatabaseKeyProvider(context).getOrCreateKey()

            val dbFile = context.getDatabasePath(DB_NAME)
            val legacy = try {
                snapshotAndRemovePlaintext(dbFile)
            } catch (e: Exception) {
                // A failed migration must not crash-loop the app (the
                // accessibility service would keep protection dead). Keep the
                // plaintext file aside for diagnosis and start fresh.
                Log.e(TAG, "Plaintext migration failed — moving old DB aside", e)
                dbFile.renameTo(File(dbFile.parentFile, "$DB_NAME.pre-sqlcipher.bak"))
                File(dbFile.parentFile, "$DB_NAME-wal").delete()
                File(dbFile.parentFile, "$DB_NAME-shm").delete()
                null
            }

            val room = Room.databaseBuilder(context, AppLockDatabase::class.java, DB_NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase.toByteArray()))
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()

            if (legacy != null && (legacy.protectedApps.isNotEmpty() || legacy.events.isNotEmpty())) {
                importLegacyRows(room, legacy)
            }
            return room
        }

        /**
         * Reads all Phase 1 rows with the framework SQLite API (which reads
         * plaintext natively), then deletes the plaintext files. Returns null
         * when there is nothing to migrate (fresh install or already done).
         */
        private fun snapshotAndRemovePlaintext(dbFile: File): LegacySnapshot? {
            if (!dbFile.exists() || !isPlaintextSqlite(dbFile)) return null
            Log.i(TAG, "Migrating Phase 1 plaintext database into SQLCipher")

            val apps = mutableListOf<ContentValues>()
            val events = mutableListOf<ContentValues>()
            val plain = FrameworkSQLiteDatabase.openDatabase(
                dbFile.absolutePath, null, FrameworkSQLiteDatabase.OPEN_READONLY,
            )
            plain.use { db ->
                db.rawQuery("SELECT packageName, enabled, addedAt FROM protected_apps", null)
                    .use { c ->
                        while (c.moveToNext()) {
                            apps += ContentValues().apply {
                                put("packageName", c.getString(0))
                                put("enabled", c.getInt(1))
                                put("addedAt", c.getLong(2))
                            }
                        }
                    }
                db.rawQuery("SELECT eventType, packageName, timestamp FROM security_events", null)
                    .use { c ->
                        while (c.moveToNext()) {
                            events += ContentValues().apply {
                                put("eventType", c.getString(0))
                                put("packageName", c.getStringOrNull(1))
                                put("timestamp", c.getLong(2))
                            }
                        }
                    }
            }

            check(dbFile.delete()) { "could not delete plaintext db" }
            File(dbFile.parentFile, "$DB_NAME-wal").delete()
            File(dbFile.parentFile, "$DB_NAME-shm").delete()
            Log.i(TAG, "Snapshot: ${apps.size} protected apps, ${events.size} events")
            return LegacySnapshot(apps, events)
        }

        private fun importLegacyRows(room: AppLockDatabase, legacy: LegacySnapshot) {
            // openHelper.writableDatabase creates the encrypted schema here.
            val db = room.openHelper.writableDatabase
            db.beginTransaction()
            try {
                legacy.protectedApps.forEach {
                    db.insert("protected_apps", FrameworkSQLiteDatabase.CONFLICT_REPLACE, it)
                }
                legacy.events.forEach {
                    db.insert("security_events", FrameworkSQLiteDatabase.CONFLICT_NONE, it)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            Log.i(TAG, "Legacy rows imported into encrypted database")
        }

        private fun android.database.Cursor.getStringOrNull(index: Int): String? =
            if (isNull(index)) null else getString(index)

        private class LegacySnapshot(
            val protectedApps: List<ContentValues>,
            val events: List<ContentValues>,
        )

        /** Plaintext SQLite files start with the 16-byte magic "SQLite format 3" + NUL. */
        private fun isPlaintextSqlite(file: File): Boolean {
            val magic = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)
            val header = ByteArray(magic.size)
            file.inputStream().use { stream ->
                if (stream.read(header) != header.size) return false
            }
            return header.contentEquals(magic)
        }
    }
}
