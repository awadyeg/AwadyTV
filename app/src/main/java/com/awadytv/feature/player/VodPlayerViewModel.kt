package com.awadytv.feature.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.toRoute
import com.awadytv.core.datastore.AppSettings
import com.awadytv.core.datastore.SettingsRepository
import com.awadytv.data.repository.LibraryRepository
import com.awadytv.data.repository.MoviesRepository
import com.awadytv.data.repository.ProfileRepository
import com.awadytv.data.repository.SeriesRepository
import com.awadytv.di.ApplicationScope
import com.awadytv.domain.ContentKind
import com.awadytv.domain.Episode
import com.awadytv.domain.Session
import com.awadytv.navigation.VodPlayerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/** Quick-seek feedback while the controls are hidden: total seconds skipped in this burst and the target. */
data class SeekFeedback(val seconds: Int, val targetMs: Long)

data class TrackOption(val label: String, val selected: Boolean, val group: Tracks.Group?, val index: Int)

data class VodPlayerState(
    val title: String = "",
    val subtitle: String? = null,
    val artwork: String? = null,
    val loading: Boolean = true,
    val buffering: Boolean = true,
    val error: String? = null,
    val controlsVisible: Boolean = true,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val tracksVisible: Boolean = false,
    val audioTracks: List<TrackOption> = emptyList(),
    val textTracks: List<TrackOption> = emptyList(),
    val nextEpisode: Episode? = null,
    val showNextCard: Boolean = false,
    val nextCountdown: Int? = null,
    val settings: AppSettings = AppSettings(),
    val finished: Boolean = false,
    val seekFeedback: SeekFeedback? = null,
)

private data class NowPlaying(
    val kind: ContentKind,
    val itemId: Long,
    val seriesId: Long?,
    val title: String,
    val image: String?,
    val containerExt: String?,
    val episode: Episode?,
)

@HiltViewModel
class VodPlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val profiles: ProfileRepository,
    private val movies: MoviesRepository,
    private val seriesRepo: SeriesRepository,
    private val library: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val factory: PlayerFactory,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<VodPlayerRoute>()
    private val _state = MutableStateFlow(VodPlayerState())
    val state: StateFlow<VodPlayerState> = _state
    private val _player = MutableStateFlow<ExoPlayer?>(null)
    val player: StateFlow<ExoPlayer?> = _player

    private var session: Session? = null
    private var current: NowPlaying? = null
    private var controlsJob: Job? = null
    private var countdownJob: Job? = null
    private var nextCardDismissed = false
    private var seekTarget: Long? = null
    private var seekJob: Job? = null
    private var seekFeedbackJob: Job? = null
    private var resumeOnStart = false

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update { it.copy(buffering = playbackState == Player.STATE_BUFFERING, loading = false) }
            if (playbackState == Player.STATE_ENDED) onEnded()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying, error = if (isPlaying) null else it.error) }
            // The auto-hide timer only hides while playing; if it ran out while the stream was still loading
            // (e.g. resuming after a relaunch) the controls stayed up. Restart it once playback really starts.
            if (isPlaying && _state.value.controlsVisible && !_state.value.tracksVisible) showControls()
        }

        override fun onTracksChanged(tracks: Tracks) = updateTracks(tracks)

        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(error = describe(error), buffering = false, controlsVisible = true) }
        }
    }

    init {
        viewModelScope.launch {
            val s = profiles.currentSession() ?: run { _state.update { it.copy(loading = false, error = "Not signed in") }; return@launch }
            session = s
            val settings = settingsRepository.current()
            _state.update { it.copy(settings = settings) }
            _player.value = factory.create(settings).also { it.addListener(listener) }
            val ok = if (route.kind == ContentKind.EPISODE.key) loadEpisode(route.itemId, route.fromStart) else loadMovie(route.itemId, route.fromStart)
            if (!ok) _state.update { it.copy(loading = false, error = "This title is no longer available.") }
        }
        viewModelScope.launch {
            var tick = 0
            while (isActive) {
                delay(500)
                val exo = _player.value ?: continue
                val duration = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
                val position = exo.currentPosition
                _state.update { it.copy(positionMs = position, durationMs = duration, bufferedMs = exo.bufferedPosition) }
                checkNextCard(position, duration)
                if (++tick % 20 == 0 && exo.isPlaying) saveProgress()
            }
        }
    }

    private suspend fun loadMovie(id: Long, fromStart: Boolean): Boolean {
        val s = session ?: return false
        val movie = movies.get(s.profileId, id) ?: return false
        current = NowPlaying(ContentKind.MOVIE, id, null, movie.name, movie.backdrop ?: movie.icon, movie.containerExt, null)
        _state.update { it.copy(title = movie.name, subtitle = movie.year?.toString(), artwork = movie.backdrop ?: movie.icon, nextEpisode = null) }
        start(movies.streamUrl(s, movie), "movie:$id", resumeFrom(ContentKind.MOVIE, id, fromStart))
        return true
    }

    private suspend fun loadEpisode(id: Long, fromStart: Boolean): Boolean {
        val s = session ?: return false
        var episode = seriesRepo.episode(s.profileId, id)
        if (episode == null && route.seriesId > 0) {
            seriesRepo.ensureEpisodes(s, route.seriesId)
            episode = seriesRepo.episode(s.profileId, id)
        }
        if (episode == null) return false
        val series = seriesRepo.get(s.profileId, episode.seriesId)
        val seriesName = series?.name ?: ""
        val label = "S${episode.season}:E${episode.num}"
        current = NowPlaying(
            ContentKind.EPISODE, id, episode.seriesId, listOf(seriesName, label).filter { it.isNotBlank() }.joinToString(" · "),
            episode.image ?: series?.backdrop ?: series?.cover, episode.containerExt, episode,
        )
        nextCardDismissed = false
        countdownJob?.cancel()
        _state.update {
            it.copy(
                title = seriesName.ifBlank { episode.title },
                subtitle = "$label  ${episode.title}",
                artwork = series?.backdrop ?: series?.cover,
                nextEpisode = seriesRepo.nextEpisode(s.profileId, episode),
                showNextCard = false,
                nextCountdown = null,
                finished = false,
                error = null,
            )
        }
        start(seriesRepo.streamUrl(s, episode), "episode:$id", resumeFrom(ContentKind.EPISODE, id, fromStart))
        return true
    }

    private suspend fun resumeFrom(kind: ContentKind, id: Long, fromStart: Boolean): Long {
        if (fromStart) return 0
        val s = session ?: return 0
        val p = library.progressOf(s.profileId, kind, id) ?: return 0
        return if (p.fraction in 0.01f..0.95f || (p.durationMs <= 0 && p.positionMs > 0)) p.positionMs else 0
    }

    private fun start(url: String, mediaId: String, positionMs: Long) {
        val exo = _player.value ?: return
        current?.let { now ->
            val key = if (now.kind == ContentKind.EPISODE) "episode:${now.itemId}:${now.seriesId ?: -1}" else "movie:${now.itemId}"
            appScope.launch { settingsRepository.setLastPlayback(key) }
        }
        exo.setMediaItem(factory.mediaItem(url, mediaId, live = false), positionMs)
        exo.prepare()
        exo.playWhenReady = true
        showControls()
    }

    private fun checkNextCard(position: Long, duration: Long) {
        val next = _state.value.nextEpisode ?: return
        if (duration <= 0 || nextCardDismissed || _state.value.showNextCard) return
        if (position.toFloat() / duration >= 0.95f) {
            _state.update { it.copy(showNextCard = true) }
            if (_state.value.settings.autoplayNextEpisode) startCountdown(next)
        }
    }

    private fun startCountdown(next: Episode) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (sec in 10 downTo 1) {
                _state.update { it.copy(nextCountdown = sec) }
                delay(1000)
            }
            playEpisode(next)
        }
    }

    fun playNext() {
        _state.value.nextEpisode?.let { playEpisode(it) }
    }

    fun dismissNextCard() {
        nextCardDismissed = true
        countdownJob?.cancel()
        _state.update { it.copy(showNextCard = false, nextCountdown = null) }
    }

    private fun playEpisode(next: Episode) {
        countdownJob?.cancel()
        viewModelScope.launch {
            saveProgress(markFinished = true)
            loadEpisode(next.episodeId, fromStart = false)
        }
    }

    private fun onEnded() {
        val next = _state.value.nextEpisode
        viewModelScope.launch { saveProgress(markFinished = true) }
        if (next != null && _state.value.settings.autoplayNextEpisode) {
            playEpisode(next)
        } else {
            _state.update { it.copy(finished = true) }
        }
    }

    fun togglePlay() {
        val exo = _player.value ?: return
        if (exo.playbackState == Player.STATE_ENDED) exo.seekTo(0)
        exo.playWhenReady = !exo.playWhenReady
        showControls()
    }

    fun seekBy(deltaMs: Long) {
        val exo = _player.value ?: return
        val duration = exo.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        exo.seekTo((exo.currentPosition + deltaMs).coerceIn(0, duration))
        _state.update { it.copy(positionMs = exo.currentPosition) }
        showControls()
    }

    /**
     * Left/Right while the controls are hidden: skip 10 s without showing them. Presses in a burst add up
     * (+10, +20, ...) on screen at once; the player seeks once the burst pauses, so it does not rebuffer per press.
     */
    fun quickSeek(deltaMs: Long) {
        val exo = _player.value ?: return
        val duration = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: Long.MAX_VALUE
        val base = seekTarget ?: exo.currentPosition
        val target = (base + deltaMs).coerceIn(0, duration)
        seekTarget = target
        val previous = _state.value.seekFeedback
        val step = (deltaMs / 1000).toInt()
        val seconds = if (previous != null && (previous.seconds > 0) == (step > 0)) previous.seconds + step else step
        _state.update { it.copy(seekFeedback = SeekFeedback(seconds, target), positionMs = target) }
        seekJob?.cancel()
        seekJob = viewModelScope.launch {
            delay(300)
            exo.seekTo(target)
        }
        seekFeedbackJob?.cancel()
        seekFeedbackJob = viewModelScope.launch {
            delay(900)
            seekTarget = null
            _state.update { it.copy(seekFeedback = null) }
        }
    }

    fun showControls() {
        _state.update { it.copy(controlsVisible = true) }
        controlsJob?.cancel()
        controlsJob = viewModelScope.launch {
            delay(5_000)
            if (_player.value?.isPlaying == true && !_state.value.tracksVisible) _state.update { it.copy(controlsVisible = false) }
        }
    }

    fun hideControls() {
        controlsJob?.cancel()
        _state.update { it.copy(controlsVisible = false) }
    }

    fun setTracksVisible(visible: Boolean) {
        _state.update { it.copy(tracksVisible = visible) }
        if (!visible) showControls()
    }

    fun selectTrack(type: Int, option: TrackOption?) {
        val exo = _player.value ?: return
        val builder = exo.trackSelectionParameters.buildUpon()
        if (option?.group == null) {
            builder.setTrackTypeDisabled(type, true)
        } else {
            builder.setTrackTypeDisabled(type, false)
            builder.setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.index))
        }
        exo.trackSelectionParameters = builder.build()
    }

    fun retry() {
        val exo = _player.value ?: return
        _state.update { it.copy(error = null, buffering = true) }
        exo.prepare()
        exo.playWhenReady = true
    }

    private fun updateTracks(tracks: Tracks) {
        fun options(type: Int): List<TrackOption> = tracks.groups.filter { it.type == type }.flatMap { group ->
            (0 until group.length).filter { group.isTrackSupported(it) }.map { i ->
                val format = group.getTrackFormat(i)
                val language = format.language?.let { Locale.forLanguageTag(it).displayLanguage.takeIf { d -> d.isNotBlank() && d != it } ?: it }
                val parts = listOfNotNull(format.label, language, format.channelCount.takeIf { it > 0 && type == C.TRACK_TYPE_AUDIO }?.let { if (it >= 6) "5.1" else if (it == 2) "Stereo" else "${it}ch" })
                TrackOption(parts.distinct().joinToString(" · ").ifBlank { "Track ${i + 1}" }, group.isTrackSelected(i), group, i)
            }
        }
        _state.update { it.copy(audioTracks = options(C.TRACK_TYPE_AUDIO), textTracks = options(C.TRACK_TYPE_TEXT)) }
    }

    /** Persists the position; called every 10s while playing, on pause/stop and on exit. Call on the main thread. */
    private suspend fun saveProgress(markFinished: Boolean = false) {
        val write = progressWrite(markFinished) ?: return
        write()
    }

    /**
     * Reads the position from the player now (ExoPlayer may only be touched on the main thread) and
     * returns the database write, which may then run on any thread.
     */
    private fun progressWrite(markFinished: Boolean = false): (suspend () -> Unit)? {
        val s = session ?: return null
        val now = current ?: return null
        val exo = _player.value ?: return null
        val duration = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: _state.value.durationMs
        val position = if (markFinished && duration > 0) duration else exo.currentPosition
        if (position <= 0) return null
        return {
            library.saveProgress(s.profileId, now.kind, now.itemId, position, duration, now.seriesId, now.title, now.image, now.containerExt)
            if (markFinished && duration > 0) {
                library.recordImplicitSignal(s.profileId, now.kind, if (now.kind == ContentKind.EPISODE) now.seriesId ?: now.itemId else now.itemId, 1f)
            }
        }
    }

    fun onStop() {
        val exo = _player.value ?: return
        resumeOnStart = exo.playWhenReady
        exo.pause()
        // Read the position here on the main thread; only the database write goes to the app scope.
        // (Reading the player inside appScope, a background dispatcher, crashed the app on leaving the player.)
        val write = progressWrite() ?: return
        appScope.launch { write() }
    }

    /** Back in the app after leaving it mid-film: carry on from where it stopped. */
    fun onStart() {
        val exo = _player.value ?: return
        if (resumeOnStart) {
            resumeOnStart = false
            exo.playWhenReady = true
            showControls()
        }
    }

    override fun onCleared() {
        // The user left the player (Back or finished), so a relaunch should not reopen it.
        appScope.launch { settingsRepository.setLastPlayback(null) }
        val s = session
        val now = current
        val exo = _player.value
        if (s != null && now != null && exo != null) {
            val duration = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
            val position = exo.currentPosition
            appScope.launch {
                if (position > 0) library.saveProgress(s.profileId, now.kind, now.itemId, position, duration, now.seriesId, now.title, now.image, now.containerExt)
                if (duration > 0) {
                    val target = if (now.kind == ContentKind.EPISODE) now.seriesId ?: now.itemId else now.itemId
                    library.recordImplicitSignal(s.profileId, now.kind, target, position.toFloat() / duration)
                }
            }
        }
        exo?.removeListener(listener)
        exo?.release()
        _player.value = null
    }

    private fun describe(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "The provider refused this stream. Your connection limit may be reached."
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Network problem while loading the video."
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "This TV could not decode the video. Try the other decoder in Settings."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "This file format is not supported."
        else -> "This video can't be played right now."
    }
}
