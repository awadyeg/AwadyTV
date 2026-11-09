package com.awadytv.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.awadytv.core.datastore.SettingsRepository
import com.awadytv.core.designsystem.LoadingView
import com.awadytv.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StartViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    /** Where launch lands, plus the player to reopen on top of Home if the app was closed mid-film. */
    data class Decision(val destination: Any, val resume: VodPlayerRoute? = null)

    private val _destination = MutableStateFlow<Decision?>(null)
    val destination: StateFlow<Decision?> = _destination

    init {
        viewModelScope.launch {
            val session = profiles.currentSession()
            _destination.value = when {
                session == null -> Decision(LoginRoute())
                session.profile.lastSyncAt == null -> Decision(SyncRoute(session.profileId))
                else -> Decision(MainRoute, resumeRoute(settings.lastPlayback()))
            }
        }
    }

    /** "movie:<id>" or "episode:<id>:<seriesId>" -> the player route, resuming from the saved position. */
    private fun resumeRoute(key: String?): VodPlayerRoute? {
        val parts = key?.split(':') ?: return null
        val id = parts.getOrNull(1)?.toLongOrNull() ?: return null
        return when (parts[0]) {
            "movie" -> VodPlayerRoute("movie", id)
            "episode" -> VodPlayerRoute("episode", id, parts.getOrNull(2)?.toLongOrNull() ?: -1L)
            else -> null
        }
    }
}

/** Decides where launch lands: sign-in, first sync, or straight to Home with the warm cache. */
@Composable
fun StartScreen(onResolved: (StartViewModel.Decision) -> Unit, viewModel: StartViewModel = hiltViewModel()) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    LaunchedEffect(destination) { destination?.let(onResolved) }
    LoadingView()
}
