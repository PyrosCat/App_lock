package com.applock.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.applock.R
import com.applock.security.DatabaseKeyProvider
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

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
        private const val RECOVERY_CHANNEL_ID = "database_integrity"

        // Notification ids in use: 1/2 = watchdog, 3 = intruder (see those services); 4 = this.
        private const val RECOVERY_NOTIFICATION_ID = 4

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
         * SQLCipher-encrypted Room (FR-162/FR-164). Fresh installs are encrypted from birth; the
         * PIN hash stays in EncryptedSharedPreferences, not here.
         *
         * **Fail-safe open (M1/WP7(a), FR-228/FR-229, R-004).** The database is opened and integrity
         * -checked (`PRAGMA quick_check`) up front. Room's `fallbackToDestructiveMigration()` is
         * deliberately *not* used: a schema version with no registered migration, or an
         * unreadable/corrupt file, no longer silently wipes the encrypted store. Instead the
         * unreadable database is moved aside as a timestamped `.recovery-*.bak` (its bytes preserved
         * for recovery), a fresh encrypted database is created so detection never crash-loops, a
         * persistent notification tells the user, and a `DATABASE_RECOVERED` audit event is written.
         * The encryption boundary is unchanged — both the normal and the recovery database use the
         * same SQLCipher factory, so encryption at rest (FR-164) is unaffected.
         */
        fun build(context: Context): AppLockDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = DatabaseKeyProvider(context).getOrCreateKey()
            val dbFile = context.getDatabasePath(DB_NAME)

            val room = openEncrypted(context, passphrase)
            return try {
                verifyIntegrity(room)
                room
            } catch (e: Exception) {
                Log.e(TAG, "Encrypted database could not be opened or verified — recovering", e)
                runCatching { room.close() }
                recoverAndRebuild(context, passphrase, dbFile)
            }
        }

        private fun openEncrypted(context: Context, passphrase: String): AppLockDatabase =
            Room.databaseBuilder(context, AppLockDatabase::class.java, DB_NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase.toByteArray()))
                .addMigrations(MIGRATION_1_2)
                // No fallbackToDestructiveMigration (R-004): a missing migration, schema mismatch,
                // or corrupt file must surface here so the caller can preserve the data rather than
                // let Room silently drop and recreate every table.
                .build()

        /**
         * Forces the encrypted open (running any registered migration) and verifies integrity with
         * `PRAGMA quick_check` (FR-229 seed). Throws on an open/migration failure or a non-`ok`
         * integrity result — the signal [build] recovers from.
         */
        private fun verifyIntegrity(room: AppLockDatabase) {
            val db = room.openHelper.writableDatabase
            db.query("PRAGMA quick_check").use { cursor ->
                val result = if (cursor.moveToFirst()) cursor.getString(0) else null
                check(DatabaseRecovery.isIntegrityOk(result)) {
                    "database integrity check failed: $result"
                }
            }
        }

        private fun recoverAndRebuild(
            context: Context,
            passphrase: String,
            dbFile: File,
        ): AppLockDatabase {
            val backup = DatabaseRecovery.moveAside(dbFile, System.currentTimeMillis())
            if (backup != null) {
                Log.i(TAG, "Unreadable database preserved as ${backup.name}")
            } else if (dbFile.exists()) {
                // Could not preserve it; last-resort reset so protection never crash-loops on the
                // same file (FR-229 permits a destructive reset when necessary — still surfaced).
                runCatching { dbFile.delete() }
                Log.w(TAG, "Could not preserve the unreadable database; reset as a last resort")
            }

            val fresh = openEncrypted(context, passphrase)
            verifyIntegrity(fresh) // a brand-new encrypted DB must verify; if not, surface it
            recordRecoveryEvent(fresh)
            // Best-effort: notifying must never abort startup (never crash-loop, R-004).
            runCatching { notifyRecovery(context) }
                .onFailure { Log.w(TAG, "Could not post the database-recovery notification", it) }
            return fresh
        }

        /**
         * Records the recovery in the audit log with a direct insert — [build] runs before any
         * coroutine scope exists, so the suspend DAO is unavailable here. Best-effort: a failure to
         * log must not itself abort startup.
         */
        private fun recordRecoveryEvent(room: AppLockDatabase) {
            runCatching {
                room.openHelper.writableDatabase.execSQL(
                    "INSERT INTO security_events (eventType, packageName, timestamp) " +
                        "VALUES (?, ?, ?)",
                    arrayOf<Any?>(
                        SecurityEventType.DATABASE_RECOVERED,
                        null,
                        System.currentTimeMillis(),
                    ),
                )
            }.onFailure { Log.e(TAG, "Could not record database-recovery event", it) }
        }

        private fun notifyRecovery(context: Context) {
            val notifications = NotificationManagerCompat.from(context)
            if (!notifications.areNotificationsEnabled()) return
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    RECOVERY_CHANNEL_ID,
                    context.getString(R.string.db_recovery_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                )
            )
            // Launcher intent resolved by package name — no presentation-layer import, so the data
            // layer stays off the UI layer (Konsist R2).
            val contentIntent = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE) }
            val notification = NotificationCompat.Builder(context, RECOVERY_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(context.getString(R.string.db_recovery_title))
                .setContentText(context.getString(R.string.db_recovery_body))
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            try {
                notifications.notify(RECOVERY_NOTIFICATION_ID, notification)
            } catch (e: SecurityException) {
                // POST_NOTIFICATIONS is revocable at runtime (targetSdk 33+). Explicit catch (not
                // runCatching) so lint sees the SecurityException handled; the areNotificationsEnabled()
                // guard above already skips the ordinary disabled case.
                Log.w(TAG, "POST_NOTIFICATIONS unavailable; recovery notice skipped", e)
            }
        }
    }
}
