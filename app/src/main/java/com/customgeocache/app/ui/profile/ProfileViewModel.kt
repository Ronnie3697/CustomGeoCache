package com.customgeocache.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.customgeocache.app.CustomGeoCacheApp
import com.customgeocache.app.data.AppContainer
import com.customgeocache.app.data.api.GcProfileApi
import com.customgeocache.app.data.api.GcSearchApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: GcProfileApi.Profile? = null,
    val foundCount: Int? = null,
    val loading: Boolean = false,
    val error: String? = null
)

class ProfileViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val profile = container.gcProfileApi.fetchProfile()
            // Z lokálních prefs jako fallback (po loginu se ukládá username)
            val username = profile?.username?.takeIf { it.isNotBlank() }
                ?: container.preferences.snapshotGcUsername()

            if (username.isNullOrBlank()) {
                _state.update {
                    it.copy(loading = false, error = "Nejsi přihlášený. Přihlaš se v Nastavení.")
                }
                return@launch
            }

            // Počet nalezených: search v2 s fb=username&take=1 vrátí total
            val searchResult = container.gcSearchApi.searchFoundBy(username, take = 1, skip = 0)
            val foundCount = when (searchResult) {
                is GcSearchApi.Result.Success -> searchResult.total
                else -> null
            }

            _state.update {
                it.copy(
                    profile = profile ?: GcProfileApi.Profile(
                        username = username, userType = null, publicGuid = null,
                        avatarUrl = null, referenceCode = null, isLoggedIn = true
                    ),
                    foundCount = foundCount,
                    loading = false,
                    error = if (searchResult is GcSearchApi.Result.NotAuthenticated)
                        "Nejsi přihlášený k API." else null
                )
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CustomGeoCacheApp)
                ProfileViewModel(app.container)
            }
        }
    }
}
