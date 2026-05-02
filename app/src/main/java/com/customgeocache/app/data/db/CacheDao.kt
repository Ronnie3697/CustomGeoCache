package com.customgeocache.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.customgeocache.app.data.db.entities.CacheEntity
import com.customgeocache.app.data.db.entities.LogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CacheDao {

    @Query("SELECT * FROM caches ORDER BY lastUpdatedMillis DESC")
    fun observeAll(): Flow<List<CacheEntity>>

    @Query("SELECT * FROM caches WHERE gccode = :gccode")
    suspend fun getByGcCode(gccode: String): CacheEntity?

    @Query("SELECT * FROM caches WHERE gccode = :gccode")
    fun observeByGcCode(gccode: String): Flow<CacheEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cache: CacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(caches: List<CacheEntity>)

    @Query("DELETE FROM caches WHERE gccode = :gccode")
    suspend fun deleteByGcCode(gccode: String)

    @Query("DELETE FROM caches")
    suspend fun deleteAll()

    @Query(
        """
        SELECT * FROM caches
         WHERE lat BETWEEN :minLat AND :maxLat
           AND lon BETWEEN :minLon AND :maxLon
        """
    )
    fun observeInBounds(
        minLat: Double, maxLat: Double, minLon: Double, maxLon: Double
    ): Flow<List<CacheEntity>>

    // ----- Logs -----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<LogEntity>)

    @Query("SELECT * FROM logs WHERE gccode = :gccode ORDER BY dateMillis DESC LIMIT :limit")
    fun observeLogs(gccode: String, limit: Int = 10): Flow<List<LogEntity>>

    @Query("DELETE FROM logs WHERE gccode = :gccode")
    suspend fun deleteLogsForCache(gccode: String)
}
