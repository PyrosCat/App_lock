package com.applock.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProtectedAppDao {

    @Query("SELECT * FROM protected_apps")
    fun observeAll(): Flow<List<ProtectedAppEntity>>

    @Query("SELECT packageName FROM protected_apps WHERE enabled = 1")
    fun observeEnabledPackages(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(app: ProtectedAppEntity)

    @Query("DELETE FROM protected_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}

@Dao
interface SecurityEventDao {

    @Insert
    suspend fun insert(event: SecurityEventEntity)

    @Query("SELECT * FROM security_events ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<SecurityEventEntity>>

    @Query("DELETE FROM security_events WHERE timestamp < :olderThan")
    suspend fun prune(olderThan: Long)
}
