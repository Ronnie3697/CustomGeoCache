package com.customgeocache.app.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.customgeocache.app.CustomGeoCacheApp
import com.customgeocache.app.data.AppContainer
import com.customgeocache.app.data.api.GcLogApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date

data class LogEntryState(
    val gccode: String = "",
    val cacheName: String = "",
    val type: GcLogApi.LogType = GcLogApi.LogType.FOUND,
    val text: String = "",
    val date: Date = Date(),
    val usedFavoritePoint: Boolean = false,
    val submitting: Boolean = false,
    val submittedLogCode: String? = null,
    val error: String? = null
)

class LogEntryViewModel(
    private val container: AppContainer,
    private val gccode: String
) : ViewModel() {

    private val _state = MutableStateFlow(LogEntryState(gccode = gccode))
    val state: StateFlow<LogEntryState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val cache = container.cacheRepository.observeByGcCode(gccode)
            cache.collect { c ->
                if (c != null) _state.update { it.copy(cacheName = c.name) }
            }
        }
    }

    fun setType(type: GcLogApi.LogType) = _state.update { it.copy(type = type) }
    fun setText(text: String) = _state.update { it.copy(text = text) }
    fun setUsedFavoritePoint(value: Boolean) = _state.update { it.copy(usedFavoritePoint = value) }

    fun submit() {
        val s = _state.value
        if (s.text.isBlank() || s.submitting) return
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val r = container.cacheRepository.postLog(
                gccode = s.gccode,
                type = s.type,
                text = s.text,
                date = s.date,
                usedFavoritePoint = s.usedFavoritePoint
            )) {
                is GcLogApi.Result.Success -> {
                    // Po úspěšném "Found it" smažeme keš z lokální DB — uživatel ji
                    // už zalogoval, nepotřebuje ji držet offline. DNF/Note ponecháme
                    // (uživatel se k ní pravděpodobně bude vracet).
                    if (s.type == GcLogApi.LogType.FOUND) {
                        container.cacheRepository.deleteCache(s.gccode)
                        if (container.activeCacheStore.active.value?.gccode == s.gccode) {
                            container.activeCacheStore.clear()
                        }
                    }
                    _state.update {
                        it.copy(submitting = false, submittedLogCode = r.logCode.ifBlank { "OK" })
                    }
                }
                is GcLogApi.Result.NotAuthenticated -> _state.update {
                    it.copy(submitting = false, error = "Nejsi přihlášený. Přihlaš se v Nastavení.")
                }
                is GcLogApi.Result.Error -> _state.update {
                    it.copy(submitting = false, error = r.message)
                }
            }
        }
    }

    companion object {
        fun factory(gccode: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CustomGeoCacheApp)
                LogEntryViewModel(app.container, gccode)
            }
        }
    }
}
