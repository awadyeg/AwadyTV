package com.awadytv.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.awadytv.core.datastore.AppSettings
import com.awadytv.core.datastore.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Activity-level state: settings that change how the whole UI renders (low-end glass path). */
@HiltViewModel
class RootViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
}
