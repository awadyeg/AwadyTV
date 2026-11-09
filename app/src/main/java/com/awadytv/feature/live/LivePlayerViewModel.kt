package com.awadytv.feature.live

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.toRoute
import com.awadytv.core.datastore.AppSettings
import com.awadytv.core.datastore.SettingsRepository
import com.awadytv.data.repository.EpgRepository
import com.awadytv.data.repository.LibraryRepository
import com.awadytv.data.repository.LiveRepository
import com.awadytv.data.repository.ProfileRepository
import com.awadytv.domain.Channel
import com.awadytv.domain.ContentKind
import com.awadytv.domain.NowNext
import com.awadytv.domain.Session
import com.awadytv.feature.player.PlayerFactory
import com.awadytv.navigation.LivePlayerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LivePlayerState(
    val channel: Channel? = null,
    val zapList: List<Channel> = emptyList(),
    val index: Int = 0,
    val nowNext: NowNext? = null,
    val overlayVisible: Boolean = true,
    val sheetVisible: Boolean = false,
    val buffering: Boolean = true,
    val error: String? = null,
    val favourite: Boolean = false,
    val digits: String = "",
    val settings: AppSettings = AppSettings(),
    val now: Long = System.currentTimeMillis(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LivePlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val profiles: ProfileRepository,
    private val live: LiveRepository,
    private val epg: EpgRepository,
    private val library: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val factory: PlayerFactory,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<LivePlayerRoute>()
    private val _state = MutableStateFlow(LivePlayerState())
    val state: StateFlow<LivePlayerState> = _state
    private val _player = MutableStateFlow<ExoPlayer?>(null)
    val player: StateFlow<ExoPlayer?> = _player

    private var session: Session? = null
    private var overlayJob: Job? = null
    private var zapJob: Job? = null
    private var digitJob: Job? = null
    private var favJob: Job? = null
    private var triedTsFallback = false

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update { it.copy(buffering = playbackState == Player.STATE_BUFFERING) }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) _state.update { it.copy(error = null, buffering = false) }
        }

        override fun onPlayerError(error: PlaybackException) {
            val channel = _state.value.channel ?: return
            val s = session ?: return
            val current = _player.value?.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty()
            if (!triedTsFallback && current.endsWith(".m3u8")) {
                // Some panels list m3u8 but only serve MPEG-TS for certain channels.
                triedTsFallback = true
                start(current.removeSuffix(".m3u8") + ".ts", channel)
                return
            }
            _state.update { it.copy(buffering = false, error = describe(error), overlayVisible = true) }
        }
    }

    init {
        viewModelScope.launch {
            val s = profiles.currentSession() ?: return@launch
            session = s
            val settings = settingsRepository.current()
            val exo = factory.create(settings).also { it.addListener(listener) }
            _player.value = exo
            val list = live.zapList(s.profileId, route.categoryId).ifEmpty { listOfNotNull(live.channel(s.profileId, route.streamId)) }
            val index = list.indexOfFirst { it.streamId == route.streamId }.coerceAtLeast(0)
            _state.update { it.copy(zapList = list, index = index, settings = settings) }
            list.getOrNull(index)?.let { play(it) }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(15_000)
                _state.update { it.copy(now = System.currentTimeMillis()) }
                refreshEpg()
            }
        }
    }

    private fun play(channel: Channel) {
        val s = session ?: return
        triedTsFallback = false
        _state.update { it.copy(channel = channel, error = null, buffering = true, nowNext = null) }
        start(live.streamUrl(s, channel, _state.value.settings.preferredFormat), channel)
        showOverlay()
        favJob?.cancel()
        favJob = viewModelScope.launch {
            library.isFavourite(s.profileId, ContentKind.LIVE, channel.streamId).collect { fav -> _state.update { it.copy(favourite = fav) } }
        }
        viewModelScope.launch {
            epg.ensureShortEpg(s, channel)
            refreshEpg()
        }
    }

    private fun start(url: String, channel: Channel) {
        val exo = _player.value ?: return
        exo.setMediaItem(factory.mediaItem(url, "live:${channel.streamId}", live = true))
        exo.prepare()
        exo.playWhenReady = true
    }

    private suspend fun refreshEpg() {
        val s = session ?: return
        val channel = _state.value.channel ?: return
        val id = channel.epgChannelId ?: return
        val map = epg.nowNext(s.profileId, listOf(channel))
        _state.update { it.copy(nowNext = map[id]) }
    }

    /** Up/Down zapping inside the current category; the overlay updates immediately, playback after a short pause. */
    fun zap(delta: Int) {
        val st = _state.value
        if (st.zapList.isEmpty()) return
        val index = (st.index + delta).mod(st.zapList.size)
        val target = st.zapList[index]
        _state.update { it.copy(index = index, channel = target, nowNext = null) }
        showOverlay()
        zapJob?.cancel()
        zapJob = viewModelScope.launch {
            delay(350)
            play(target)
        }
    }

    fun playAt(index: Int) {
        val target = _state.value.zapList.getOrNull(index) ?: return
        _state.update { it.copy(index = index, sheetVisible = false) }
        play(target)
    }

    fun onDigit(digit: Int) {
        val digits = (_state.value.digits + digit).takeLast(4)
        _state.update { it.copy(digits = digits) }
        showOverlay()
        digitJob?.cancel()
        digitJob = viewModelScope.launch {
            delay(1_500)
            val number = digits.toIntOrNull()
            _state.update { it.copy(digits = "") }
            val list = _state.value.zapList
            val index = list.indexOfFirst { it.num == number }
            if (index >= 0) playAt(index)
        }
    }

    fun showOverlay() {
        _state.update { it.copy(overlayVisible = true) }
        overlayJob?.cancel()
        overlayJob = viewModelScope.launch {
            delay(OVERLAY_MS)
            if (_state.value.error == null) _state.update { it.copy(overlayVisible = false) }
        }
    }

    fun hideOverlay() {
        overlayJob?.cancel()
        _state.update { it.copy(overlayVisible = false) }
    }

    fun setSheetVisible(visible: Boolean) {
        _state.update { it.copy(sheetVisible = visible) }
        if (visible) hideOverlay()
    }

    fun toggleFavourite() {
        val s = session ?: return
        val channel = _state.value.channel ?: return
        viewModelScope.launch { library.setFavourite(s.profileId, ContentKind.LIVE, channel.streamId, !_state.value.favourite) }
        showOverlay()
    }

    fun retry() {
        _state.value.channel?.let { play(it) }
    }

    fun togglePlayPause() {
        val exo = _player.value ?: return
        exo.playWhenReady = !exo.playWhenReady
        showOverlay()
    }

    fun onStop() {
        _player.value?.stop()
    }

    fun onStart() {
        val exo = _player.value ?: return
        if (exo.playbackState == Player.STATE_IDLE && exo.currentMediaItem != null) {
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    override fun onCleared() {
        _player.value?.let {
            it.removeListener(listener)
            it.release()
        }
        _player.value = null
    }

    private companion object {
        /** Channel banner auto-hide. */
        const val OVERLAY_MS = 4_000L
    }

    private fun describe(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "The provider refused this stream. Your connection limit may be reached."
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Network problem while loading the stream."
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "This TV could not decode the stream. Try the other decoder in Settings."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "The stream format was not recognised. Try MPEG-TS in Settings."
        else -> "This channel can't be played right now."
    }
}
