package com.customgeocache.app.data.api

import android.util.Log
import com.customgeocache.app.data.db.entities.CacheEntity
import com.customgeocache.app.data.db.entities.LogEntity
import com.customgeocache.app.data.parser.GcDetailParser
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Stahuje a parsuje detail stránku jedné keše + nejnovější logy.
 *
 * Detail page: GET https://www.geocaching.com/geocache/{GCxxx}?decrypt=y
 * Logy:        POST https://www.geocaching.com/seek/geocache.logbook (form: tkn, idx, num, decrypt)
 */
class GcDetailApi(private val client: OkHttpClient) {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    suspend fun fetchDetail(gccode: String, base: CacheEntity? = null): CacheEntity? =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("https://www.geocaching.com/geocache/$gccode?decrypt=y")
                .get()
                .build()
            try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "fetchDetail $gccode HTTP ${resp.code}")
                        return@withContext null
                    }
                    val html = resp.body?.string() ?: return@withContext null
                    GcDetailParser.parse(gccode, html, base)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "fetchDetail failed", t)
                null
            }
        }

    /** Čte JSON logbook endpoint. Vyžaduje user token z detail page (`userToken=`). */
    suspend fun fetchLogs(gccode: String, userToken: String, count: Int = 25): List<LogEntity> =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder()
                .add("tkn", userToken)
                .add("idx", "1")
                .add("num", count.toString())
                .add("decrypt", "false")
                .build()
            val req = Request.Builder()
                .url("https://www.geocaching.com/seek/geocache.logbook")
                .post(body)
                .build()
            try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()
                    val raw = resp.body?.string() ?: return@withContext emptyList()
                    parseLogs(gccode, raw)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "fetchLogs failed", t)
                emptyList()
            }
        }

    private fun parseLogs(gccode: String, json: String): List<LogEntity> = runCatching {
        val type = Types.newParameterizedType(LogbookResponse::class.java)
        val adapter = moshi.adapter(LogbookResponse::class.java)
        val parsed = adapter.fromJson(json) ?: return@runCatching emptyList<LogEntity>()
        parsed.data.orEmpty().map { d ->
            LogEntity(
                gccode = gccode,
                type = d.LogType.orEmpty(),
                author = d.UserName.orEmpty(),
                dateMillis = parseLogDate(d.Visited),
                text = d.LogText.orEmpty()
            )
        }
    }.getOrElse { emptyList() }

    private fun parseLogDate(visited: String?): Long {
        if (visited.isNullOrBlank()) return System.currentTimeMillis()
        // visited is ISO-ish, e.g. "9/14/2024" or "2024-09-14T..." — graceful fallback
        return try {
            java.text.SimpleDateFormat("M/d/yyyy", java.util.Locale.US).parse(visited)?.time
                ?: System.currentTimeMillis()
        } catch (_: Throwable) {
            System.currentTimeMillis()
        }
    }

    @JsonClass(generateAdapter = true)
    data class LogbookResponse(val status: String?, val data: List<LogDto>?)

    @JsonClass(generateAdapter = true)
    data class LogDto(
        val LogType: String?,
        val Visited: String?,
        val UserName: String?,
        val LogText: String?,
        val LogID: Long?
    )

    /** Vytáhne userToken z HTML detail stránky (potřebný pro logbook endpoint). */
    fun extractUserToken(html: String): String? {
        val m = Regex("userToken\\s*=\\s*'([^']+)'").find(html) ?: return null
        return m.groupValues[1]
    }

    companion object { private const val TAG = "CGC.Detail" }
}
