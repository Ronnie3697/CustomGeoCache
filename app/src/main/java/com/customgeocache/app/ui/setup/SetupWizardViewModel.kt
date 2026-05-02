package com.customgeocache.app.ui.setup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.customgeocache.app.CustomGeoCacheApp
import com.customgeocache.app.data.AppContainer
import com.customgeocache.app.data.auth.GCLogin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetupUiState(
    val folderUri: String? = null,
    val mapyApiKey: String = "",
    val loginUsername: String = "",
    val loginPassword: String = "",
    val loginInProgress: Boolean = false,
    val loginError: LoginError? = null,
    val loggedInUsername: String? = null
) {
    enum class LoginError { Invalid, Network, Captcha, Unknown }
}

class SetupWizardViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    fun onFolderSelected(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // některá zařízení / OEM neumožní persist — pokračujeme s URI bez toho
        }
        _state.update { it.copy(folderUri = uri.toString()) }
        viewModelScope.launch { container.preferences.setFolderUri(uri.toString()) }
    }

    fun onMapyKeyChange(value: String) {
        _state.update { it.copy(mapyApiKey = value) }
    }

    fun saveMapyKey() {
        val key = _state.value.mapyApiKey.trim()
        if (key.isNotEmpty()) {
            viewModelScope.launch { container.preferences.setMapyApiKey(key) }
        }
    }

    fun onUsernameChange(value: String) {
        _state.update { it.copy(loginUsername = value, loginError = null) }
    }

    fun onPasswordChange(value: String) {
        _state.update { it.copy(loginPassword = value, loginError = null) }
    }

    fun login() {
        val u = _state.value.loginUsername.trim()
        val p = _state.value.loginPassword
        if (u.isEmpty() || p.isEmpty()) return
        _state.update { it.copy(loginInProgress = true, loginError = null) }
        viewModelScope.launch {
            when (val r = container.gcLogin.login(u, p)) {
                is GCLogin.Result.Success -> _state.update {
                    it.copy(loginInProgress = false, loggedInUsername = r.username)
                }
                is GCLogin.Result.InvalidCredentials -> _state.update {
                    it.copy(loginInProgress = false, loginError = SetupUiState.LoginError.Invalid)
                }
                is GCLogin.Result.NetworkError -> _state.update {
                    it.copy(loginInProgress = false, loginError = SetupUiState.LoginError.Network)
                }
                is GCLogin.Result.CaptchaRequired -> _state.update {
                    it.copy(loginInProgress = false, loginError = SetupUiState.LoginError.Captcha)
                }
                is GCLogin.Result.UnvalidatedAccount -> _state.update {
                    it.copy(loginInProgress = false, loginError = SetupUiState.LoginError.Invalid)
                }
                is GCLogin.Result.Unknown -> _state.update {
                    it.copy(loginInProgress = false, loginError = SetupUiState.LoginError.Unknown)
                }
            }
        }
    }

    fun finish() {
        viewModelScope.launch {
            saveMapyKey()
            container.preferences.setFirstRunDone(true)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CustomGeoCacheApp)
                SetupWizardViewModel(app.container)
            }
        }
    }
}
