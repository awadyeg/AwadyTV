package com.awadytv.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.awadytv.core.designsystem.LoadingView
import com.awadytv.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StartViewModel @Inject constructor(private val profiles: ProfileRepository) : ViewModel() {
    private val _destination = MutableStateFlow<Any?>(null)
    val destination: StateFlow<Any?> = _destination

    init {
        viewModelScope.launch {
            val session = profiles.currentSession()
            _destination.value = when {
                session == null -> LoginRoute()
                session.profile.lastSyncAt == null -> SyncRoute(session.profileId)
                else -> MainRoute
            }
        }
    }
}

/** Decides where launch lands: sign-in, first sync, or straight to Home with the warm cache. */
@Composable
fun StartScreen(onResolved: (Any) -> Unit, viewModel: StartViewModel = hiltViewModel()) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    LaunchedEffect(destination) { destination?.let(onResolved) }
    LoadingView()
}
