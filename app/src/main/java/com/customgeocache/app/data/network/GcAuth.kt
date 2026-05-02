package com.customgeocache.app.data.network

import android.util.Log
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * OAuth bearer token getter pro geocaching.com API.
 *
 * Geocaching.com vrací token na `GET /account/oauth/token` jakmile má klient
 * platnou session cookie (tj. uživatel je přihlášen přes HTML login). Žádný
 * grant_type / redirect_uri flow — endpoint si bere identitu ze session cookie.
 *
 * Token se cachuje v paměti na 80 % `expires_in` doby.
 */
class GcAuth(private val client: OkHttpClient) {

    private val mutex = Mutex()
    private var cached: CachedToken? = null

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(TokenResponse::class.java)

    /** Vrátí "Bearer xxx" header value, nebo null pokud uživatel není přihlášen. */
    suspend fun authorizationHeader(): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        mutex.withLock {
            val c = cached
            if (c != null && now < c.expiresAt) {
                return@withContext c.headerValue
            }
            val fresh = fetchToken() ?: return@withContext null
            val expires = now + (fresh.expiresIn * 1000L * 80 / 100)
            cached = CachedToken(headerValue = "${fresh.tokenType} ${fresh.accessToken}", expiresAt = expires)
            cached?.headerValue
        }
    }

    fun invalidate() {
        cached = null
    }

    private fun fetchToken(): TokenResponse? {
        val req = Request.Builder()
            .url("https://www.geocaching.com/account/oauth/token")
            .get()
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "OAuth token fetch failed: ${resp.code}")
                    return null
                }
                val body = resp.body?.string() ?: return null
                adapter.fromJson(body)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "OAuth token fetch error", t)
            null
        }
    }

    private data class CachedToken(val headerValue: String, val expiresAt: Long)

    @JsonClass(generateAdapter = true)
    data class TokenResponse(
        val token_type: String,
        val access_token: String,
        val expires_in: Long
    ) {
        val tokenType get() = token_type
        val accessToken get() = access_token
        val expiresIn get() = expires_in
    }

    companion object { private const val TAG = "CGC.Auth" }
}
