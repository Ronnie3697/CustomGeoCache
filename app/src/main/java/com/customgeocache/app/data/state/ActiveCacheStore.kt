package com.customgeocache.app.data.state

import com.customgeocache.app.data.db.entities.CacheEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton držící "aktivní keš" — tu, kterou uživatel právě navštěvuje
 * (loguje, navádí kompasem). Sdíleno mezi obrazovkami.
 */
class ActiveCacheStore {
    private val _active = MutableStateFlow<CacheEntity?>(null)
    val active: StateFlow<CacheEntity?> = _active.asStateFlow()

    fun setActive(cache: CacheEntity?) { _active.value = cache }
    fun clear() { _active.value = null }
}
