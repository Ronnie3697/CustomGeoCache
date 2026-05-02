package com.customgeocache.app.data.auth

import com.customgeocache.app.data.prefs.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * Login na geocaching.com přes HTML scraping. Logika portovaná z c:geo (GCLogin.java).
 *
 * Postup:
 * 1. GET signin stránky → získáme `__RequestVerificationToken` (ASP.NET MVC anti-forgery token)
 *    a session cookies (`__cf_bm`, `gspkauth`, …) přes cookie jar.
 * 2. POST přihlášení s tokenem + credentials → server vrací HTML s embedded JSON
 *    (`"username":"…"`), z toho čteme úspěch + skutečný username.
 * 3. Cookies, které OkHttp dostal, jsou už v jaru → uložíme je přes prefs.
 */
class GCLogin(
    private val client: OkHttpClient,
    private val prefs: AppPreferences
) {

    sealed class Result {
        data class Success(val username: String) : Result()
        object InvalidCredentials : Result()
        object CaptchaRequired : Result()
        object UnvalidatedAccount : Result()
        data class NetworkError(val cause: Throwable? = null) : Result()
        data class Unknown(val responseSnippet: String) : Result()
    }

    suspend fun login(username: String, password: String): Result = withContext(Dispatchers.IO) {
        try {
            val signinPage = getLoginPage() ?: return@withContext Result.NetworkError()

            // Pokud nás server pustí rovnou (zbytkové cookies), kontrola loginu vrátí username.
            extractUsername(signinPage)?.let {
                prefs.setGcUsername(it)
                return@withContext Result.Success(it)
            }

            val token = extractRequestVerificationToken(signinPage)
                ?: return@withContext Result.Unknown(signinPage.take(300))

            val response = postCredentials(username, password, token)
                ?: return@withContext Result.NetworkError()

            when {
                response.contains("<div class=\"g-recaptcha\" data-sitekey=\"") -> Result.CaptchaRequired
                response.contains("id=\"signup-validation-error\"") -> Result.InvalidCredentials
                response.contains("content=\"account/join/success\"") -> Result.UnvalidatedAccount
                else -> {
                    val gcUsername = extractUsername(response)
                    if (gcUsername != null) {
                        prefs.setGcUsername(gcUsername)
                        Result.Success(gcUsername)
                    } else {
                        Result.Unknown(response.take(300))
                    }
                }
            }
        } catch (t: Throwable) {
            Result.NetworkError(t)
        }
    }

    suspend fun logout(): Boolean = withContext(Dispatchers.IO) {
        try {
            client.newCall(
                Request.Builder().url(LOGOUT_URL).get().build()
            ).execute().close()
            prefs.clearGcSession()
            true
        } catch (t: Throwable) {
            prefs.clearGcSession()
            false
        }
    }

    private fun getLoginPage(): String? {
        val req = Request.Builder().url(LOGIN_URL).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return resp.body?.string()
        }
    }

    private fun postCredentials(username: String, password: String, token: String): String? {
        val body = FormBody.Builder()
            .add("UsernameOrEmail", username)
            .add("Password", password)
            .add(REQUEST_VERIFICATION_TOKEN, token)
            .build()
        val req = Request.Builder()
            .url(LOGIN_URL)
            .post(body)
            .header("Referer", LOGIN_URL)
            .build()
        client.newCall(req).execute().use { resp ->
            return resp.body?.string()
        }
    }

    private fun extractRequestVerificationToken(html: String): String? {
        val doc = Jsoup.parse(html)
        val value = doc.select("form > input[name=\"$REQUEST_VERIFICATION_TOKEN\"]").attr("value")
        return value.takeIf { it.isNotEmpty() }
    }

    /** Hledá `"username":"foo"` v JSON kusu, který geocaching.com posílá v <script> tagu. */
    private fun extractUsername(html: String): String? {
        val m1 = PATTERN_LOGIN_NAME_1.find(html)
        if (m1 != null) return m1.groupValues[1].unescapeJson()
        val m2 = PATTERN_LOGIN_NAME_2.find(html)
        if (m2 != null) return m2.groupValues[1].unescapeJson()
        return null
    }

    private fun String.unescapeJson(): String =
        replace("\\\"", "\"").replace("\\\\", "\\").replace("\\u0026", "&")

    companion object {
        const val LOGIN_URL = "https://www.geocaching.com/account/signin?returnUrl=%2Faccount%2Fsettings%2Fhomelocation"
        const val LOGOUT_URL = "https://www.geocaching.com/account/logout"
        const val REQUEST_VERIFICATION_TOKEN = "__RequestVerificationToken"

        private val PATTERN_LOGIN_NAME_1 = Regex(""""referenceCode":"[^"]+","id":\d+,"username":\s*"([^"]+)"""")
        private val PATTERN_LOGIN_NAME_2 = Regex("""window(?:\.|\[')(?:headerSettings|chromeSettings)(?:'\])?\s*=\s*\{[\s\S]*?"username":\s*"([^"]+)"""")

        // Trampolína pro detekci, že GET signin stránky vede k přesměrování na vlastní profil
        // (kdy server v cookies má auth a `account/signin` rovnou redirectne na settings).
        @Suppress("unused") private val LOGIN_URL_HOST = LOGIN_URL.toHttpUrl().host
    }
}
