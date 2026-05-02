package com.customgeocache.app.data.api

import android.util.Log
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Stáhne uživatelský profil z `https://www.geocaching.com/play/serverparameters/params`.
 *
 * Endpoint vrací JS s přiřazením tvaru `var serverParameters = { ... };`.
 * V JS objektu je pole `user.info` s username, userType, publicGuid, avatarUrl, referenceCode.
 *
 * Místo plnokrevného JS parseru extrahujeme JSON podstring pomocí závorky-counting
 * a Moshi pak naparsuje strukturu.
 */
class GcProfileApi(private val client: OkHttpClient) {

    data class Profile(
        val username: String,
        val userType: String?,        // "Premium" / "Basic"
        val publicGuid: String?,
        val avatarUrl: String?,
        val referenceCode: String?,
        val isLoggedIn: Boolean
    )

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(ServerParametersDto::class.java)

    suspend fun fetchProfile(): Profile? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://www.geocaching.com/play/serverparameters/params")
            .get()
            .header("Accept", "*/*")
            .build()
        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "fetchProfile HTTP ${resp.code}")
                    return@withContext null
                }
                val body = resp.body?.string() ?: return@withContext null
                val json = extractJsonObject(body) ?: run {
                    Log.w(TAG, "fetchProfile: cannot extract JSON from JS body")
                    return@withContext null
                }
                val dto = adapter.fromJson(json) ?: return@withContext null
                val info = dto.user?.info ?: return@withContext null
                Profile(
                    username = info.username.orEmpty(),
                    userType = info.userType,
                    publicGuid = info.publicGuid,
                    avatarUrl = info.avatarUrl,
                    referenceCode = info.referenceCode,
                    isLoggedIn = info.isLoggedIn ?: false
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "fetchProfile failed", t)
            null
        }
    }

    /**
     * Extrahuje první nejvyšší JSON objekt z JS body. Server posílá něco jako
     *   var serverParameters = { ... };
     * najdeme první `{` a počítáme závorky, dokud nenajdeme matching `}`.
     */
    private fun extractJsonObject(js: String): String? {
        val start = js.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until js.length) {
            val ch = js[i]
            if (escape) { escape = false; continue }
            if (ch == '\\' && inString) { escape = true; continue }
            if (ch == '"') { inString = !inString; continue }
            if (inString) continue
            if (ch == '{') depth++
            else if (ch == '}') {
                depth--
                if (depth == 0) return js.substring(start, i + 1)
            }
        }
        return null
    }

    @JsonClass(generateAdapter = true)
    data class ServerParametersDto(val user: UserNode?)

    @JsonClass(generateAdapter = true)
    data class UserNode(val info: InfoNode?)

    @JsonClass(generateAdapter = true)
    data class InfoNode(
        val username: String?,
        val userType: String?,
        val publicGuid: String?,
        val avatarUrl: String?,
        val referenceCode: String?,
        val isLoggedIn: Boolean?
    )

    companion object { private const val TAG = "CGC.Profile" }
}
