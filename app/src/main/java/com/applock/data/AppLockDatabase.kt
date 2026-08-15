package com.applock.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.applock.security.DatabaseKeyProvider
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

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
         * SQLCipher-encrypted Room (FR-162/FR-164). Fresh installs are encrypted
         * from birth; the PIN hash stays in EncryptedSharedPreferences, not here.
         *
         * The one-time Phase-1 plaintext -> SQLCipher conversion was removed in
         * M1/WP7(b) (2026-08-14): no 1.0.0 install ever shipped a plaintext
         * database, so there is no field population to convert and the interrupted
         * -import data-loss window it opened is eliminated with it (R-006 closes by
         * elimination — RISK_REGISTER.md; DDS v1.0.0 §16.5). A stray dev-only
         * plaintext `applock.db` is simply not opened as if it were current.
         */
        fun build(context: Context): AppLockDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = DatabaseKeyProvider(context).getOrCreateKey()
            return Room.databaseBuilder(context, AppLockDatabase::class.java, DB_NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase.toByteArray()))
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
