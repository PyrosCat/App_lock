package com.applock.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProtectedAppEntity::class, SecurityEventEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppLockDatabase : RoomDatabase() {

    abstract fun protectedAppDao(): ProtectedAppDao
    abstract fun securityEventDao(): SecurityEventDao

    companion object {
        // NOTE (Phase 2): swap the factory for SQLCipher. Phase 1 stores only
        // package names + event log here — the PIN hash lives in
        // EncryptedSharedPreferences, never in this DB.
        fun build(context: Context): AppLockDatabase =
            Room.databaseBuilder(context, AppLockDatabase::class.java, "applock.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
