package com.customgeocache.app.ui.caches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.customgeocache.app.CustomGeoCacheApp
import com.customgeocache.app.data.AppContainer
import com.customgeocache.app.data.db.entities.CacheEntity
import com.customgeocache.app.data.db.entities.LogEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CacheDetailUiState(
    val cache: CacheEntity? = null,
    val logs: List<LogEntity> = emptyList(),
    val refreshing: Boolean = false,
    val error: String? = null
)

class CacheDetailViewModel(
    private val container: AppContainer,
    private val gccode: String
) : ViewModel() {

    private val _state = MutableStateFlow(CacheDetailUiState())
    val state: StateFlow<CacheDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                container.cacheRepository.observeByGcCode(gccode),
                container.cacheRepository.observeLogs(gccode, limit = 25)
            ) { cache, logs -> cache to logs }
                .collect { (cache, logs) ->
                    _state.update { it.copy(cache = cache, logs = logs) }
                }
        }
        // Při prvním otevření vždy zkusíme fetchnout čerstvý detail
        refresh()
    }

    fun refresh() {
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch {
            try {
                container.cacheRepository.fetchDetail(gccode)
            } catch (t: Throwable) {
                _state.update { it.copy(error = t.message ?: "Chyba načítání") }
            } finally {
                _state.update { it.copy(refreshing = false) }
            }
        }
    }

    fun navigate() {
        _state.value.cache?.let { container.activeCacheStore.setActive(it) }
    }

    companion object {
        fun factory(gccode: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CustomGeoCacheApp)
                CacheDetailViewModel(app.container, gccode)
            }
        }
    }
}
