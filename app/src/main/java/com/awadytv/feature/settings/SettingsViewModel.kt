package com.awadytv.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import com.awadytv.BuildConfig
import com.awadytv.core.database.EpgDao
import com.awadytv.core.database.RecommendationDao
import com.awadytv.core.datastore.AppSettings
import com.awadytv.core.datastore.DecoderMode
import com.awadytv.core.datastore.PreferredFormat
import com.awadytv.core.datastore.SettingsRepository
import com.awadytv.core.security.ParentalGate
import com.awadytv.core.security.SecretStore
import com.awadytv.data.recommendations.RecommendationRepository
import com.awadytv.data.repository.ProfileRepository
import com.awadytv.domain.Profile
import com.awadytv.work.BackgroundWork
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsState(
    val settings: AppSettings = AppSettings(),
    val profiles: List<Profile> = emptyList(),
    val active: Profile? = null,
    val pinSet: Boolean = false,
    val claudeKeySet: Boolean = false,
    val secretsPersistent: Boolean = true,
    val version: String = BuildConfig.VERSION_NAME,
)

sealed interface SettingsEvent {
    data class Message(val text: String) : SettingsEvent
    data class Resync(val profileId: Long) : SettingsEvent
    data object SignedOut : SettingsEvent
    data object Restart : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val profiles: ProfileRepository,
    private val parental: ParentalGate,
    private val secrets: SecretStore,
    private val recommendations: RecommendationRepository,
    private val epgDao: EpgDao,
    private val recommendationDao: RecommendationDao,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state
    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(settingsRepository.settings, profiles.profiles, profiles.activeProfile) { s, list, active -> Triple(s, list, active) }
                .collect { (s, list, active) ->
                    _state.update {
                        it.copy(
                            settings = s, profiles = list, active = active, pinSet = parental.pinSet,
                            claudeKeySet = secrets.claudeApiKey != null, secretsPersistent = secrets.isPersistent,
                        )
                    }
                }
        }
    }

    private fun message(text: String) = viewModelScope.launch { _events.send(SettingsEvent.Message(text)) }

    // Playback
    fun cycleDecoder() = viewModelScope.launch {
        settingsRepository.setDecoder(if (_state.value.settings.decoder == DecoderMode.HARDWARE) DecoderMode.SOFTWARE else DecoderMode.HARDWARE)
    }

    fun cycleFormat() = viewModelScope.launch {
        val all = PreferredFormat.entries
        settingsRepository.setPreferredFormat(all[(all.indexOf(_state.value.settings.preferredFormat) + 1) % all.size])
    }

    fun cycleBuffer() = viewModelScope.launch {
        val options = listOf(15, 30, 60)
        val next = options[(options.indexOf(_state.value.settings.bufferSeconds).coerceAtLeast(0) + 1) % options.size]
        settingsRepository.setBufferSeconds(next)
    }

    fun cycleSubtitleSize() = viewModelScope.launch {
        val options = listOf(0.8f, 1f, 1.3f, 1.6f)
        val current = options.indexOfFirst { kotlin.math.abs(it - _state.value.settings.subtitleScale) < 0.01f }.coerceAtLeast(0)
        settingsRepository.setSubtitleScale(options[(current + 1) % options.size])
    }

    fun toggleSubtitleBackground() = viewModelScope.launch { settingsRepository.setSubtitleBackground(!_state.value.settings.subtitleBackground) }
    fun toggleAutoplay() = viewModelScope.launch { settingsRepository.setAutoplayNext(!_state.value.settings.autoplayNextEpisode) }
    fun toggleLowEnd() = viewModelScope.launch { settingsRepository.setForceLowEnd(!_state.value.settings.forceLowEnd) }

    // EPG
    fun cycleEpgHours() = viewModelScope.launch {
        val options = listOf(3, 6, 12, 24)
        val next = options[(options.indexOf(_state.value.settings.epgRefreshHours).coerceAtLeast(0) + 1) % options.size]
        settingsRepository.setEpgRefreshHours(next)
        BackgroundWork.scheduleEpg(context, next)
    }

    fun refreshEpgNow() {
        BackgroundWork.refreshEpgNow(context)
        message("Refreshing the TV guide in the background")
    }

    // AI
    fun toggleAi() = viewModelScope.launch {
        val enable = !_state.value.settings.aiRecommendations
        settingsRepository.setAiRecommendations(enable)
        val active = _state.value.active ?: return@launch
        if (enable) runCatching { recommendations.refresh(active.id, force = true) } else recommendationDao.clear(active.id)
    }

    fun recomputeRecommendations() = viewModelScope.launch {
        val active = _state.value.active ?: return@launch
        runCatching { recommendations.refresh(active.id, force = true) }
        message("Recommendations updated")
    }

    fun toggleClaude() = viewModelScope.launch {
        val enable = !_state.value.settings.claudeEnabled
        if (enable && secrets.claudeApiKey == null) {
            message("Add your Claude API key first")
            return@launch
        }
        settingsRepository.setClaudeEnabled(enable)
        recomputeRecommendations()
    }

    fun saveClaudeKey(key: String) = viewModelScope.launch {
        secrets.claudeApiKey = key
        _state.update { it.copy(claudeKeySet = secrets.claudeApiKey != null) }
        if (secrets.claudeApiKey == null) settingsRepository.setClaudeEnabled(false)
        message(if (secrets.claudeApiKey != null) "API key saved (encrypted on this TV)" else "API key removed")
    }

    // Parental
    fun setPin(pin: String) {
        parental.setPin(pin)
        _state.update { it.copy(pinSet = true) }
        message("Parental PIN set. Adult categories are now locked.")
    }

    fun removePin(currentPin: String): Boolean {
        if (!parental.unlock(currentPin)) return false
        parental.setPin(null)
        _state.update { it.copy(pinSet = false) }
        message("Parental PIN removed")
        return true
    }

    fun lockNow() {
        parental.lock()
        message("Adult categories locked")
    }

    // Profiles
    fun switchProfile(profile: Profile) = viewModelScope.launch {
        profiles.switchTo(profile.id)
        _events.send(if (profile.lastSyncAt == null) SettingsEvent.Resync(profile.id) else SettingsEvent.Restart)
    }

    fun removeProfile(profile: Profile) = viewModelScope.launch {
        val wasActive = profile.id == _state.value.active?.id
        profiles.delete(profile.id)
        if (wasActive) {
            val next = profiles.currentSession()
            _events.send(if (next == null) SettingsEvent.SignedOut else SettingsEvent.Restart)
        }
    }

    fun refreshCatalog() = viewModelScope.launch {
        val active = _state.value.active ?: return@launch
        _events.send(SettingsEvent.Resync(active.id))
    }

    fun signOut() = viewModelScope.launch {
        settingsRepository.setActiveProfile(null)
        _events.send(SettingsEvent.SignedOut)
    }

    // Storage
    fun clearImageCache() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            val loader = SingletonImageLoader.get(context)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
        message("Image cache cleared")
    }

    fun clearGuide() = viewModelScope.launch {
        val active = _state.value.active ?: return@launch
        epgDao.clear(active.id)
        message("TV guide cache cleared")
    }
}
