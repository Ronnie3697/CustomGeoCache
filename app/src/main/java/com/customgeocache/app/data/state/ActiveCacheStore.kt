package com.customgeocache.app.data.state

import com.customgeocache.app.data.db.entities.CacheEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton držící "aktivní keš" — tu, kterou uživatel právě navštěvuje
 * (loguje, navádí kompasem). Sdíleno mezi obrazovkami.
 */
data class MapCameraState(
    val lat: Double,
    val lon: Double,
    val zoom: Double,
    val bearing: Double = 0.0,
    val tilt: Double = 0.0
)

class ActiveCacheStore {
    private val _active = MutableStateFlow<CacheEntity?>(null)
    val active: StateFlow<CacheEntity?> = _active.asStateFlow()

    /** Krátkodobá zpráva pro HomeScreen, ať přepne na konkrétní tab (např. COMPASS po stisku „Naviguj"). */
    private val _requestedTab = MutableStateFlow<String?>(null)
    val requestedTab: StateFlow<String?> = _requestedTab.asStateFlow()

    /** Persistovaná pozice kamery na mapě — drží se napříč přepínáním tabů. */
    var mapCamera: MapCameraState? = null
        private set

    fun setActive(cache: CacheEntity?) { _active.value = cache }
    fun clear() { _active.value = null }

    fun requestTab(tab: String) { _requestedTab.value = tab }
    fun consumeRequestedTab() { _requestedTab.value = null }

    fun saveMapCamera(state: MapCameraState) { mapCamera = state }
}
