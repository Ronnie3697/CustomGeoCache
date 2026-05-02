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
import okhttp3.HttpUrl.Companion.toHttpUrl
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

    /**
     * Stáhne detail page + parse cache + extract userToken pro logbook + extract image URLs.
     *
     * URL **bez `decrypt=y`** parametru — server vrací hint v ROT13 (uživatel ho odhalí
     * klepnutím v UI). Decrypt=y by hint poslal čitelný a UI logika by ho zase šifrovala.
     */
    data class FullDetail(
        val cache: CacheEntity?,
        val userToken: String?,
        val imageUrls: List<String>
    )

    suspend fun fetchFull(gccode: String, base: CacheEntity? = null): FullDetail =
        withContext(Dispatchers.IO) {
            Log.i(TAG, "fetchFull start: $gccode")
            val req = Request.Builder()
                .url("https://www.geocaching.com/geocache/$gccode")
                .get()
                .build()
            try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "fetchFull $gccode HTTP ${resp.code}")
                        return@withContext FullDetail(null, null, emptyList())
                    }
                    val html = resp.body?.string()
                        ?: return@withContext FullDetail(null, null, emptyList())
                    val token = extractUserToken(html)
                    val images = GcDetailParser.extractImages(html)
                    Log.i(TAG, "fetchFull $gccode ok: html=${html.length}B userToken=${token != null} images=${images.size}")
                    FullDetail(
                        cache = GcDetailParser.parse(gccode, html, base),
                        userToken = token,
                        imageUrls = images
                    )
                }
            } catch (t: Throwable) {
                Log.w(TAG, "fetchFull failed", t)
                FullDetail(null, null, emptyList())
            }
        }

    suspend fun fetchDetail(gccode: String, base: CacheEntity? = null): CacheEntity? =
        fetchFull(gccode, base).cache

    /** Čte JSON logbook endpoint. Vyžaduje user token z detail page (`userToken=`).
     *  V c:geo je to GET s query params (ne POST), takže to děláme stejně. */
    suspend fun fetchLogs(gccode: String, userToken: String, count: Int = 25): List<LogEntity> =
        withContext(Dispatchers.IO) {
            val url = "https://www.geocaching.com/seek/geocache.logbook".toHttpUrl().newBuilder()
                .addQueryParameter("tkn", userToken)
                .addQueryParameter("idx", "1")
                .addQueryParameter("num", count.toString())
                .addQueryParameter("decrypt", "false")
                .build()
            val req = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .build()
            try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "fetchLogs $gccode HTTP ${resp.code}")
                        return@withContext emptyList()
                    }
                    val raw = resp.body?.string() ?: return@withContext emptyList()
                    val logs = parseLogs(gccode, raw)
                    Log.i(TAG, "fetchLogs $gccode -> ${logs.size} logs")
                    logs
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

    /** Vytáhne userToken z HTML detail stránky (potřebný pro logbook endpoint).
     *  Geocaching.com ho v JS embedded má jako `userToken = '...'` nebo `"userToken":"..."`. */
    fun extractUserToken(html: String): String? {
        val singleQuote = Regex("userToken\\s*=\\s*'([^']+)'").find(html)
        if (singleQuote != null) return singleQuote.groupValues[1]
        val jsonStyle = Regex("\"userToken\"\\s*:\\s*\"([^\"]+)\"").find(html)
        if (jsonStyle != null) return jsonStyle.groupValues[1]
        return null
    }

    companion object { private const val TAG = "CGC.Detail" }
}
