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
            // ?decrypt=y vrátí čitelný hint (UI ho v HintCard zase zašifruje pro display).
            // Bez decrypt=y stránka v některých případech neobsahuje JS userToken,
            // bez kterého nelze stáhnout logbook. Verifikováno proti c:geo (GCParser.requestHtmlPage).
            val req = Request.Builder()
                .url("https://www.geocaching.com/geocache/$gccode?decrypt=y")
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

    sealed class LogsResult {
        data class Success(val logs: List<LogEntity>) : LogsResult()
        data class Failure(val message: String) : LogsResult()
    }

    /** Čte JSON logbook endpoint. Vyžaduje user token z detail page (`userToken=`).
     *  V c:geo je to GET s query params (ne POST), takže to děláme stejně. */
    suspend fun fetchLogsResult(gccode: String, userToken: String, count: Int = 25): LogsResult =
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
                .header("Referer", "https://www.geocaching.com/geocache/$gccode")
                .build()
            try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "fetchLogs $gccode HTTP ${resp.code}")
                        return@withContext LogsResult.Failure("Server vrátil HTTP ${resp.code}")
                    }
                    val raw = resp.body?.string()
                        ?: return@withContext LogsResult.Failure("Prázdná odpověď serveru")
                    Log.i(TAG, "fetchLogs $gccode raw[${raw.length}B]: ${raw.take(300)}")
                    val logs = parseLogs(gccode, raw)
                    Log.i(TAG, "fetchLogs $gccode -> ${logs.size} logs parsed")
                    if (logs.isEmpty() && raw.isNotBlank()) {
                        return@withContext LogsResult.Failure(
                            "Server vrátil ${raw.length}B, ale žádné logy se nepodařilo naparsovat. Začátek: ${raw.take(80)}"
                        )
                    }
                    LogsResult.Success(logs)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "fetchLogs failed", t)
                LogsResult.Failure("Síťová chyba: ${t.message ?: t.javaClass.simpleName}")
            }
        }

    /** Zachováno pro kompatibilitu — vrátí jen list (prázdný při chybě). */
    suspend fun fetchLogs(gccode: String, userToken: String, count: Int = 25): List<LogEntity> =
        when (val r = fetchLogsResult(gccode, userToken, count)) {
            is LogsResult.Success -> r.logs
            is LogsResult.Failure -> emptyList()
        }

    private fun parseLogs(gccode: String, json: String): List<LogEntity> = runCatching {
        // Plain JSON parsing přes org.json — vyhneme se Moshi reflection issues s PascalCase
        // a nečekanými typy v jednotlivých polích logu (Images, AccountGuid atd.)
        val root = org.json.JSONObject(json)
        val status = root.optString("status")
        if (status != "success") {
            android.util.Log.w(TAG, "parseLogs: status=$status")
            return@runCatching emptyList<LogEntity>()
        }
        val data = root.optJSONArray("data") ?: return@runCatching emptyList<LogEntity>()
        val out = ArrayList<LogEntity>(data.length())
        for (i in 0 until data.length()) {
            val o = data.optJSONObject(i) ?: continue
            out += LogEntity(
                gccode = gccode,
                type = o.optString("LogType", ""),
                author = o.optString("UserName", ""),
                dateMillis = parseLogDate(o.optString("Visited", "")),
                text = o.optString("LogText", "")
            )
        }
        out
    }.getOrElse { t ->
        android.util.Log.w(TAG, "parseLogs threw", t)
        emptyList()
    }

    private fun parseLogDate(visited: String?): Long {
        if (visited.isNullOrBlank()) return System.currentTimeMillis()
        // gc.com posílá různé formáty: "M/d/yyyy" (US), "yyyy-MM-dd...", "d.M.yyyy". Zkusíme po pořadě.
        val formats = listOf(
            "M/d/yyyy",
            "MM/dd/yyyy",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd",
            "d.M.yyyy"
        )
        for (fmt in formats) {
            try {
                return java.text.SimpleDateFormat(fmt, java.util.Locale.US).parse(visited)?.time
                    ?: continue
            } catch (_: Throwable) { /* try next */ }
        }
        return System.currentTimeMillis()
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
