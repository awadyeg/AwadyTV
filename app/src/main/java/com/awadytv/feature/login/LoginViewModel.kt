package com.awadytv.feature.login

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.toRoute
import com.awadytv.navigation.LoginRoute
import androidx.lifecycle.viewModelScope
import com.awadytv.core.util.AppError
import com.awadytv.core.util.AppResult
import com.awadytv.data.repository.ProfileRepository
import com.awadytv.domain.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val loading: Boolean = false,
    val error: AppError? = null,
    val profiles: List<Profile> = emptyList(),
    val showProfiles: Boolean = false,
)

sealed interface LoginEvent {
    data class SignedIn(val profileId: Long, val needsSync: Boolean) : LoginEvent
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProfileRepository,
) : ViewModel() {
    private val addingProfile = savedStateHandle.toRoute<LoginRoute>().addProfile
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state
    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            // Remember the last profile: prefill its server and username (not when adding another account).
            val last = if (addingProfile) null else repository.profiles.first().firstOrNull()
            if (last != null) _state.update { it.copy(server = last.serverUrl, username = last.username) }
        }
        viewModelScope.launch {
            repository.profiles.collect { list -> _state.update { it.copy(profiles = list) } }
        }
    }

    fun onServer(v: String) = _state.update { it.copy(server = v, error = null) }
    fun onUsername(v: String) = _state.update { it.copy(username = v, error = null) }
    fun onPassword(v: String) = _state.update { it.copy(password = v, error = null) }
    fun togglePasswordVisible() = _state.update { it.copy(passwordVisible = !it.passwordVisible) }
    fun showProfiles(show: Boolean) = _state.update { it.copy(showProfiles = show) }

    fun signIn() {
        val s = _state.value
        if (s.loading) return
        when {
            s.server.isBlank() -> { _state.update { it.copy(error = AppError.InvalidUrl) }; return }
            s.username.isBlank() || s.password.isEmpty() -> { _state.update { it.copy(error = AppError.WrongCredentials) }; return }
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.login(s.server, s.username, s.password)) {
                is AppResult.Success -> {
                    val profile = repository.sessionFor(result.value)?.profile
                    _state.update { it.copy(loading = false, password = "") }
                    _events.send(LoginEvent.SignedIn(result.value, needsSync = profile?.lastSyncAt == null))
                }
                is AppResult.Failure -> _state.update { it.copy(loading = false, error = result.error) }
            }
        }
    }

    fun useProfile(profile: Profile) {
        viewModelScope.launch {
            repository.switchTo(profile.id)
            _state.update { it.copy(showProfiles = false) }
            _events.send(LoginEvent.SignedIn(profile.id, needsSync = profile.lastSyncAt == null))
        }
    }

    fun deleteProfile(profile: Profile) {
        viewModelScope.launch { repository.delete(profile.id) }
    }
}
