package com.customgeocache.app.data.repo

import com.customgeocache.app.data.db.CacheDao
import com.customgeocache.app.data.db.entities.CacheEntity
import com.customgeocache.app.data.db.entities.LogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Cache repository — agreguje DB + síťové volání geocaching.com.
 *
 * MVP iterace: implementováno čtení/zápis DB, observe všech kešek + observe v bounding boxu.
 * Real fetch z geocaching.com (search nearby + parse cache detail) přijde v iteraci 2 —
 * vyžaduje to interakci s GC search API endpointem který chce platný anti-forgery token,
 * a HTML parsing pro detail. Tady to mám připravené v signaturách.
 */
class CacheRepository(
    private val dao: CacheDao,
    @Suppress("unused") private val client: OkHttpClient
) {

    fun observeAll(): Flow<List<CacheEntity>> = dao.observeAll()

    fun observeByGcCode(gccode: String): Flow<CacheEntity?> = dao.observeByGcCode(gccode)

    fun observeInBounds(
        minLat: Double, maxLat: Double, minLon: Double, maxLon: Double
    ): Flow<List<CacheEntity>> = dao.observeInBounds(minLat, maxLat, minLon, maxLon)

    fun observeLogs(gccode: String, limit: Int = 10): Flow<List<LogEntity>> =
        dao.observeLogs(gccode, limit)

    suspend fun saveCache(cache: CacheEntity) = withContext(Dispatchers.IO) { dao.upsert(cache) }

    suspend fun saveCaches(caches: List<CacheEntity>) = withContext(Dispatchers.IO) {
        dao.upsertAll(caches)
    }

    suspend fun deleteCache(gccode: String) = withContext(Dispatchers.IO) {
        dao.deleteByGcCode(gccode)
    }

    /**
     * TODO iter 2: pošli search request na geocaching.com pro keše v okolí (lat, lon, radius).
     * Pro teď vrací prázdný seznam.
     */
    suspend fun searchAround(@Suppress("unused_parameter") lat: Double,
                             @Suppress("unused_parameter") lon: Double,
                             @Suppress("unused_parameter") radiusKm: Double = 5.0): List<CacheEntity> =
        withContext(Dispatchers.IO) { emptyList() }

    /**
     * TODO iter 2: stáhni a naparsuj detail kešky z geocaching.com (description, hint, attributy, logy).
     * Pro teď vrací cached verzi z DB.
     */
    suspend fun fetchDetail(gccode: String): CacheEntity? = withContext(Dispatchers.IO) {
        dao.getByGcCode(gccode)
    }
}
