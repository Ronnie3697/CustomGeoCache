package com.customgeocache.app.data.repo

import com.customgeocache.app.data.api.GcDetailApi
import com.customgeocache.app.data.api.GcLogApi
import com.customgeocache.app.data.api.GcSearchApi
import com.customgeocache.app.data.db.CacheDao
import com.customgeocache.app.data.db.entities.CacheEntity
import com.customgeocache.app.data.db.entities.LogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Single source of truth pro keše. Read-side čte přes Room (Flow), write-side
 * volá GC APIs a ukládá do DB.
 */
class CacheRepository(
    private val dao: CacheDao,
    private val searchApi: GcSearchApi,
    private val detailApi: GcDetailApi,
    private val logApi: GcLogApi
) {

    fun observeAll(): Flow<List<CacheEntity>> = dao.observeAll()

    fun observeByGcCode(gccode: String): Flow<CacheEntity?> = dao.observeByGcCode(gccode)

    fun observeInBounds(
        minLat: Double, maxLat: Double, minLon: Double, maxLon: Double
    ): Flow<List<CacheEntity>> = dao.observeInBounds(minLat, maxLat, minLon, maxLon)

    fun observeLogs(gccode: String, limit: Int = 25): Flow<List<LogEntity>> =
        dao.observeLogs(gccode, limit)

    suspend fun saveCache(cache: CacheEntity) = withContext(Dispatchers.IO) { dao.upsert(cache) }
    suspend fun saveCaches(caches: List<CacheEntity>) = withContext(Dispatchers.IO) { dao.upsertAll(caches) }
    suspend fun deleteCache(gccode: String) = withContext(Dispatchers.IO) { dao.deleteByGcCode(gccode) }

    /** Stáhne keše v bounding boxu, uloží je do DB a vrátí výsledek volání. */
    suspend fun searchInBounds(
        south: Double, west: Double, north: Double, east: Double
    ): GcSearchApi.Result {
        val result = searchApi.searchBox(south, west, north, east)
        if (result is GcSearchApi.Result.Success) {
            saveCaches(result.caches)
        }
        return result
    }

    /** Stáhne detail z webu, mergne s případným záznamem v DB, uloží. */
    suspend fun fetchDetail(gccode: String): CacheEntity? = withContext(Dispatchers.IO) {
        val base = dao.getByGcCode(gccode)
        val fresh = detailApi.fetchDetail(gccode, base) ?: return@withContext base
        dao.upsert(fresh)
        fresh
    }

    /** Vrátí detail + obrázky + uloží detail do DB + spustí refresh logbook s userTokenem. */
    suspend fun fetchDetailFull(gccode: String): DetailResult = withContext(Dispatchers.IO) {
        val base = dao.getByGcCode(gccode)
        val full = detailApi.fetchFull(gccode, base)
        val cache = full.cache ?: return@withContext DetailResult(base, emptyList(), 0)
        dao.upsert(cache)

        val logsCount = if (full.userToken != null) {
            val logs = detailApi.fetchLogs(gccode, full.userToken)
            if (logs.isNotEmpty()) {
                dao.deleteLogsForCache(gccode)
                dao.insertLogs(logs)
            }
            logs.size
        } else 0

        DetailResult(cache, full.imageUrls, logsCount)
    }

    data class DetailResult(
        val cache: CacheEntity?,
        val imageUrls: List<String>,
        val logsCount: Int
    )

    /** Stáhne nejnovější logy a uloží je do DB. */
    suspend fun refreshLogs(gccode: String, userToken: String): Int = withContext(Dispatchers.IO) {
        val logs = detailApi.fetchLogs(gccode, userToken)
        if (logs.isNotEmpty()) {
            dao.deleteLogsForCache(gccode)
            dao.insertLogs(logs)
        }
        logs.size
    }

    suspend fun postLog(
        gccode: String,
        type: GcLogApi.LogType,
        text: String,
        date: Date = Date(),
        usedFavoritePoint: Boolean = false
    ): GcLogApi.Result = logApi.postLog(gccode, type, text, date, usedFavoritePoint)

    suspend fun fetchDetailHtml(gccode: String): String? = withContext(Dispatchers.IO) {
        // Pomocná metoda — vrací RAW HTML pro extrakci userToken (pro fetchLogs).
        // Použijeme jednoduše stejný request jako fetchDetail.
        val req = okhttp3.Request.Builder()
            .url("https://www.geocaching.com/geocache/$gccode?decrypt=y")
            .get()
            .build()
        try {
            // Reuse client cez detailApi by bylo čistší, ale tady jen rychlá pomocná cesta
            null  // implementace ne-kritická pro MVP, logy si stáhneme z hlavního flow
        } catch (_: Throwable) { null }
    }
}
