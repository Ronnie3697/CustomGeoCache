package com.customgeocache.app.data

import android.content.Context
import com.customgeocache.app.data.api.GcDetailApi
import com.customgeocache.app.data.api.GcLogApi
import com.customgeocache.app.data.api.GcProfileApi
import com.customgeocache.app.data.api.GcSearchApi
import com.customgeocache.app.data.auth.GCLogin
import com.customgeocache.app.data.auth.PersistentCookieJar
import com.customgeocache.app.data.db.AppDatabase
import com.customgeocache.app.data.network.GcAuth
import com.customgeocache.app.data.network.HttpClientFactory
import com.customgeocache.app.data.prefs.AppPreferences
import com.customgeocache.app.data.repo.CacheRepository
import com.customgeocache.app.data.state.ActiveCacheStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

    val gcAuth: GcAuth by lazy { GcAuth(httpClient) }
    val gcSearchApi: GcSearchApi by lazy { GcSearchApi(httpClient, gcAuth) }
    val gcDetailApi: GcDetailApi by lazy { GcDetailApi(httpClient) }
    val gcLogApi: GcLogApi by lazy { GcLogApi(httpClient, gcAuth) }
    val gcProfileApi: GcProfileApi by lazy { GcProfileApi(httpClient) }

    val activeCacheStore: ActiveCacheStore by lazy { ActiveCacheStore() }

    val cacheRepository: CacheRepository by lazy {
        CacheRepository(
            dao = database.cacheDao(),
            searchApi = gcSearchApi,
            detailApi = gcDetailApi,
            logApi = gcLogApi
        )
    }
}
