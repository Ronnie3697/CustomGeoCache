package com.customgeocache.app.data.api

import android.util.Log
import com.customgeocache.app.data.db.entities.CacheEntity
import com.customgeocache.app.data.network.GcAuth
import com.customgeocache.app.data.network.dto.SearchResponseDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Hledání kešek v bounding boxu přes geocaching.com web search v2 endpoint.
 *
 *   GET https://www.geocaching.com/api/proxy/web/search/v2?
 *       box=<latMax>,<lonMin>,<latMin>,<lonMax>&rad=16000&take=200&skip=0
 *       &app=cgeo&properties=callernote
 *   Authorization: Bearer <oauth_token>
 *
 * `app=cgeo` je gentlemen's agreement s Groundspeakem — necháme to tak, je to
 * fakticky proxy, na kterou jsou zvyklí, a nejde o impersonation (stejný UA tisíce klientů).
 */
class GcSearchApi(
    private val client: OkHttpClient,
    private val auth: GcAuth
) {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(SearchResponseDto::class.java)

    sealed class Result {
        data class Success(val caches: List<CacheEntity>, val total: Int) : Result()
        object NotAuthenticated : Result()
        data class Error(val message: String) : Result()
    }

    /** Stáhne keše, které našel daný uživatel (`fb=username`). Total v response = počet všech. */
    suspend fun searchFoundBy(
        username: String, take: Int = 200, skip: Int = 0
    ): Result = withContext(Dispatchers.IO) {
        val token = auth.authorizationHeader() ?: return@withContext Result.NotAuthenticated
        val url = "https://www.geocaching.com/api/proxy/web/search/v2".toHttpUrl().newBuilder()
            .addQueryParameter("fb", username)
            .addQueryParameter("hf", "0")
            .addQueryParameter("take", take.toString())
            .addQueryParameter("skip", skip.toString())
            .addQueryParameter("app", "cgeo")
            .addQueryParameter("properties", "callernote")
            .addQueryParameter("sort", "DateLastVisited")
            .addQueryParameter("asc", "false")
            .build()
        val req = Request.Builder()
            .url(url)
            .header("Authorization", token)
            .header("Accept", "application/json")
            .get()
            .build()
        try {
            client.newCall(req).execute().use { resp ->
                if (resp.code == 401) {
                    auth.invalidate()
                    return@withContext Result.NotAuthenticated
                }
                if (!resp.isSuccessful) return@withContext Result.Error("HTTP ${resp.code}")
                val body = resp.body?.string() ?: return@withContext Result.Error("Empty body")
                val dto = adapter.fromJson(body) ?: return@withContext Result.Error("Bad JSON")
                val caches = dto.results.orEmpty().mapNotNull(::toCacheEntity)
                Result.Success(caches = caches, total = dto.total ?: caches.size)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "searchFoundBy failed", t)
            Result.Error(t.message ?: "network error")
        }
    }

    /** lat/lon pole rohů: south, west, north, east */
    suspend fun searchBox(
        south: Double, west: Double, north: Double, east: Double,
        take: Int = 200, skip: Int = 0
    ): Result = withContext(Dispatchers.IO) {
        val token = auth.authorizationHeader() ?: return@withContext Result.NotAuthenticated

        // box order podle cgeo: latMax, lonMin, latMin, lonMax
        val box = "$north,$west,$south,$east"
        val url = "https://www.geocaching.com/api/proxy/web/search/v2".toHttpUrl().newBuilder()
            .addQueryParameter("box", box)
            .addQueryParameter("rad", "16000")
            .addQueryParameter("take", take.toString())
            .addQueryParameter("skip", skip.toString())
            .addQueryParameter("app", "cgeo")
            .addQueryParameter("properties", "callernote")
            .addQueryParameter("sort", "distance")
            .addQueryParameter("asc", "true")
            .build()

        val req = Request.Builder()
            .url(url)
            .header("Authorization", token)
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            client.newCall(req).execute().use { resp ->
                if (resp.code == 401) {
                    auth.invalidate()
                    return@withContext Result.NotAuthenticated
                }
                if (!resp.isSuccessful) {
                    return@withContext Result.Error("HTTP ${resp.code}")
                }
                val body = resp.body?.string() ?: return@withContext Result.Error("Empty body")
                val dto = adapter.fromJson(body) ?: return@withContext Result.Error("Bad JSON")
                val caches = dto.results.orEmpty().mapNotNull(::toCacheEntity)
                Result.Success(caches = caches, total = dto.total ?: caches.size)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "search failed", t)
            Result.Error(t.message ?: "network error")
        }
    }

    private fun toCacheEntity(r: com.customgeocache.app.data.network.dto.CacheResultDto): CacheEntity? {
        val coords = r.userCorrectedCoordinates ?: r.postedCoordinates ?: return null
        return CacheEntity(
            gccode = r.code,
            name = r.name.orEmpty(),
            type = GcMappings.typeName(r.geocacheType),
            lat = coords.latitude,
            lon = coords.longitude,
            difficulty = r.difficulty ?: 0f,
            terrain = r.terrain ?: 0f,
            size = GcMappings.sizeName(r.containerType),
            owner = r.owner?.username,
            isFound = r.userFound == true,
            isFavorite = false
        )
    }

    companion object { private const val TAG = "CGC.Search" }
}
