package com.customgeocache.app.data.api

import android.util.Log
import com.customgeocache.app.data.network.GcAuth
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Posílá log na geocaching.com.
 *
 *   POST https://www.geocaching.com/api/live/v1/logs/{GEOCODE}/geocacheLog
 *   Authorization: Bearer <oauth>
 *   CSRF-Token: <csrf>
 *   Content-Type: application/json
 *
 *   { "logType": 2, "logText": "...", "logDate": "2026-05-02T...Z",
 *     "usedFavoritePoint": false, "images": [], "trackables": [] }
 */
class GcLogApi(
    private val client: OkHttpClient,
    private val auth: GcAuth
) {

    enum class LogType(val id: Int, val display: String, val isPositive: Boolean = false) {
        FOUND(2, "Found it", true),
        DNF(3, "Did Not Find"),
        NOTE(4, "Write note"),
        NEEDS_MAINTENANCE(45, "Needs maintenance"),
        NEEDS_ARCHIVED(7, "Needs archived")
    }

    sealed class Result {
        data class Success(val logCode: String) : Result()
        object NotAuthenticated : Result()
        data class Error(val message: String) : Result()
    }

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val reqAdapter = moshi.adapter(LogRequest::class.java)
    private val respAdapter = moshi.adapter(LogResponse::class.java)
    private val csrfAdapter = moshi.adapter(CsrfResponse::class.java)

    suspend fun postLog(
        gccode: String,
        type: LogType,
        text: String,
        date: Date = Date(),
        usedFavoritePoint: Boolean = false
    ): Result = withContext(Dispatchers.IO) {
        val token = auth.authorizationHeader() ?: return@withContext Result.NotAuthenticated
        val csrf = fetchCsrf() ?: return@withContext Result.Error("CSRF token fetch failed")

        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(date)

        val payload = LogRequest(
            logType = type.id,
            logText = text,
            logDate = iso,
            usedFavoritePoint = usedFavoritePoint,
            images = emptyList(),
            trackables = emptyList()
        )
        val body = reqAdapter.toJson(payload).toRequestBody(JSON)

        val req = Request.Builder()
            .url("https://www.geocaching.com/api/live/v1/logs/$gccode/geocacheLog")
            .header("Authorization", token)
            .header("CSRF-Token", csrf)
            .header("Accept", "application/json")
            .post(body)
            .build()

        try {
            client.newCall(req).execute().use { resp ->
                val respBody = resp.body?.string().orEmpty()
                when {
                    resp.code == 401 || resp.code == 403 -> {
                        auth.invalidate()
                        Result.NotAuthenticated
                    }
                    !resp.isSuccessful -> Result.Error("HTTP ${resp.code}: ${respBody.take(200)}")
                    else -> {
                        val parsed = runCatching { respAdapter.fromJson(respBody) }.getOrNull()
                        Result.Success(logCode = parsed?.logReferenceCode.orEmpty())
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "postLog failed", t)
            Result.Error(t.message ?: "network error")
        }
    }

    private fun fetchCsrf(): String? {
        val req = Request.Builder()
            .url("https://www.geocaching.com/api/auth/csrf")
            .get()
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                csrfAdapter.fromJson(body)?.csrfToken
            }
        } catch (_: Throwable) { null }
    }

    @JsonClass(generateAdapter = true)
    data class LogRequest(
        val logType: Int,
        val logText: String,
        val logDate: String,
        val usedFavoritePoint: Boolean,
        val images: List<String>,
        val trackables: List<Any>
    )

    @JsonClass(generateAdapter = true)
    data class LogResponse(
        val logReferenceCode: String?,
        val logDate: String?,
        val logType: Int?
    )

    @JsonClass(generateAdapter = true)
    data class CsrfResponse(val csrfToken: String?)

    companion object {
        private const val TAG = "CGC.LogApi"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
