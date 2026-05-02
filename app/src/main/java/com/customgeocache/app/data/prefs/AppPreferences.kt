package com.customgeocache.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.customgeocache.app.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "settings")

class AppPreferences(private val context: Context) {

    private object Keys {
        val FIRST_RUN_DONE = booleanPreferencesKey("first_run_done")
        val FOLDER_URI = stringPreferencesKey("folder_uri")
        val MAPY_API_KEY = stringPreferencesKey("mapy_api_key")
        val MAP_LAYER = stringPreferencesKey("map_layer")
        val GC_USERNAME = stringPreferencesKey("gc_username")
        val GC_COOKIES = stringPreferencesKey("gc_cookies")
    }

    val firstRunDone: Flow<Boolean> = context.dataStore.data.map { it[Keys.FIRST_RUN_DONE] ?: false }

    /** Synchronní snapshot pro výběr startovní destinace v MainActivity (volat jen jednou na startu). */
    fun firstRunDoneBlocking(): Boolean = runBlocking {
        context.dataStore.data.first()[Keys.FIRST_RUN_DONE] ?: false
    }
    val folderUri: Flow<String?> = context.dataStore.data.map { it[Keys.FOLDER_URI] }

    val mapyApiKey: Flow<String?> = context.dataStore.data.map { prefs ->
        val user = prefs[Keys.MAPY_API_KEY]?.takeIf { it.isNotBlank() }
        user ?: BuildConfig.MAPY_CZ_API_KEY_DEFAULT.takeIf { it.isNotBlank() }
    }

    val mapLayer: Flow<String> = context.dataStore.data.map { it[Keys.MAP_LAYER] ?: "basic" }
    val gcUsername: Flow<String?> = context.dataStore.data.map { it[Keys.GC_USERNAME] }

    suspend fun setFirstRunDone(value: Boolean) = context.dataStore.edit { it[Keys.FIRST_RUN_DONE] = value }
    suspend fun setFolderUri(uri: String) = context.dataStore.edit { it[Keys.FOLDER_URI] = uri }
    suspend fun setMapyApiKey(key: String) = context.dataStore.edit { it[Keys.MAPY_API_KEY] = key }
    suspend fun setMapLayer(layer: String) = context.dataStore.edit { it[Keys.MAP_LAYER] = layer }

    suspend fun setGcUsername(username: String) = context.dataStore.edit { it[Keys.GC_USERNAME] = username }
    suspend fun setGcCookies(cookies: String) = context.dataStore.edit { it[Keys.GC_COOKIES] = cookies }

    suspend fun clearGcSession() = context.dataStore.edit {
        it.remove(Keys.GC_USERNAME)
        it.remove(Keys.GC_COOKIES)
    }

    suspend fun snapshotMapyKey(): String? = mapyApiKey.first()
    suspend fun snapshotGcUsername(): String? = gcUsername.first()

    /** Synchronní snapshot cookies pro init OkHttp jaru. Volat jen na BG threadu. */
    fun loadCookiesBlocking(): String? = runBlocking {
        context.dataStore.data.first()[Keys.GC_COOKIES]
    }
}
