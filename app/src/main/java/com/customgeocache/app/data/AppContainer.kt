package com.customgeocache.app.data

import android.content.Context
import com.customgeocache.app.data.auth.GCLogin
import com.customgeocache.app.data.auth.PersistentCookieJar
import com.customgeocache.app.data.db.AppDatabase
import com.customgeocache.app.data.network.HttpClientFactory
import com.customgeocache.app.data.prefs.AppPreferences
import com.customgeocache.app.data.repo.CacheRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Lehký DI kontejner. Bez Hilt — věci jsou jednoduché, manuální propojení stačí.
 * Singletony se vytvářejí lazy.
 */
class AppContainer(private val context: Context) {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val preferences: AppPreferences by lazy { AppPreferences(context) }

    val cookieJar: PersistentCookieJar by lazy {
        val initial = preferences.loadCookiesBlocking()
        PersistentCookieJar(initialSerialized = initial) { serialized ->
            ioScope.launch { preferences.setGcCookies(serialized) }
        }
    }

    val httpClient by lazy { HttpClientFactory.create(cookieJar) }

    val database: AppDatabase by lazy { AppDatabase.build(context) }

    val gcLogin: GCLogin by lazy { GCLogin(httpClient, preferences) }

    val cacheRepository: CacheRepository by lazy {
        CacheRepository(database.cacheDao(), httpClient)
    }
}
