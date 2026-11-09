package com.awadytv.feature.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.tv.material3.Text
import com.awadytv.core.designsystem.AwadyColors
import com.awadytv.core.designsystem.AwadyDimens
import com.awadytv.core.designsystem.AwadyType
import com.awadytv.core.designsystem.EmptyView
import com.awadytv.core.designsystem.GlassButton
import com.awadytv.core.designsystem.GlassChip
import com.awadytv.core.designsystem.GlassIconButton
import com.awadytv.core.designsystem.GlassSpinner
import com.awadytv.core.designsystem.GlassVariant
import com.awadytv.core.designsystem.LandscapeCard
import com.awadytv.core.designsystem.LoadingView
import com.awadytv.core.designsystem.LocalBackdrop
import com.awadytv.core.designsystem.PosterCard
import com.awadytv.core.designsystem.Shelf
import com.awadytv.core.util.AppError
import com.awadytv.core.util.AppResult
import com.awadytv.core.util.TimeFormat
import com.awadytv.data.recommendations.RecommendationRepository
import com.awadytv.data.repository.LibraryRepository
import com.awadytv.data.repository.ProfileRepository
import com.awadytv.data.repository.SeriesRepository
import com.awadytv.domain.ContentKind
import com.awadytv.domain.Episode
import com.awadytv.domain.Series
import com.awadytv.domain.Session
import com.awadytv.domain.WatchProgress
import com.awadytv.feature.common.DetailBackdrop
import com.awadytv.feature.common.DetailHeader
import com.awadytv.domain.PosterItem
import com.awadytv.feature.common.metaLine
import com.awadytv.feature.common.openTrailer
import com.awadytv.navigation.AppNavigator
import com.awadytv.navigation.SeriesDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class SeriesDetailState(
    val loading: Boolean = true,
    val series: Series? = null,
    val episodesLoading: Boolean = true,
    val episodesError: AppError? = null,
    val episodes: List<Episode> = emptyList(),
    val selectedSeason: Int? = null,
    val progress: Map<Long, WatchProgress> = emptyMap(),
    val liked: Boolean = false,
    val favourite: Boolean = false,
    val related: List<PosterItem> = emptyList(),
) {
    val seasons: List<Int> get() = episodes.map { it.season }.distinct().sorted()
    val seasonEpisodes: List<Episode> get() = episodes.filter { it.season == selectedSeason }.sortedBy { it.num }

    /** The episode "Play" should start: the most recently watched unfinished one, else the next after a finished one, else the first. */
    fun resumeTarget(): Pair<Episode, WatchProgress?>? {
        if (episodes.isEmpty()) return null
        val ordered = episodes.sortedWith(compareBy({ it.season }, { it.num }))
        val latest = progress.values.maxByOrNull { it.updatedAt }
        if (latest != null) {
            val idx = ordered.indexOfFirst { it.episodeId == latest.itemId }
            if (idx >= 0) {
                return if (latest.fraction >= 0.95f) ordered.getOrNull(idx + 1)?.let { it to null } ?: (ordered[idx] to latest)
                else ordered[idx] to latest
            }
        }
        return ordered.first() to null
    }
}

@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val profiles: ProfileRepository,
    private val seriesRepo: SeriesRepository,
    private val library: LibraryRepository,
    private val recommendations: RecommendationRepository,
) : ViewModel() {
    private val seriesId = savedStateHandle.toRoute<SeriesDetailRoute>().seriesId
    private val _state = MutableStateFlow(SeriesDetailState())
    val state: StateFlow<SeriesDetailState> = _state
    private var session: Session? = null

    init {
        viewModelScope.launch {
            val s = profiles.currentSession() ?: run { _state.update { it.copy(loading = false) }; return@launch }
            session = s
            launch { seriesRepo.observe(s.profileId, seriesId).collect { v -> _state.update { it.copy(loading = false, series = v) } } }
            launch {
                seriesRepo.episodes(s.profileId, seriesId).collect { list ->
                    _state.update { st ->
                        val season = st.selectedSeason?.takeIf { sel -> list.any { it.season == sel } }
                            ?: st.copy(episodes = list).resumeTarget()?.first?.season
                            ?: list.minOfOrNull { it.season }
                        st.copy(episodes = list, selectedSeason = season, episodesLoading = st.episodesLoading && list.isEmpty())
                    }
                }
            }
            launch { library.observeSeriesProgress(s.profileId, seriesId).collect { v -> _state.update { it.copy(progress = v) } } }
            launch { library.isLiked(s.profileId, ContentKind.SERIES, seriesId).collect { v -> _state.update { it.copy(liked = v) } } }
            launch { library.isFavourite(s.profileId, ContentKind.SERIES, seriesId).collect { v -> _state.update { it.copy(favourite = v) } } }
            launch { _state.update { it.copy(related = recommendations.moreLikeThis(s.profileId, ContentKind.SERIES, seriesId)) } }
            loadEpisodes(force = false)
        }
    }

    fun loadEpisodes(force: Boolean = true) {
        val s = session ?: return
        viewModelScope.launch {
            _state.update { it.copy(episodesLoading = true, episodesError = null) }
            when (val r = seriesRepo.ensureEpisodes(s, seriesId, force)) {
                is AppResult.Success -> _state.update { it.copy(episodesLoading = false) }
                is AppResult.Failure -> _state.update { it.copy(episodesLoading = false, episodesError = r.error) }
            }
        }
    }

    fun selectSeason(season: Int) = _state.update { it.copy(selectedSeason = season) }

    fun toggleLike() {
        val s = session ?: return
        viewModelScope.launch { library.setLiked(s.profileId, ContentKind.SERIES, seriesId, !_state.value.liked) }
    }

    fun toggleFavourite() {
        val s = session ?: return
        viewModelScope.launch { library.setFavourite(s.profileId, ContentKind.SERIES, seriesId, !_state.value.favourite) }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SeriesDetailScreen(navigator: AppNavigator, viewModel: SeriesDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val backdrop = LocalBackdrop.current
    val playFocus = remember { FocusRequester() }
    val series = state.series

    when {
        state.loading -> LoadingView()
        series == null -> EmptyView("Series not found", "It may have been removed by your provider. Refresh the catalog in Settings.", icon = Icons.Rounded.Tv)
        else -> {
            LaunchedEffect(series.seriesId) { backdrop.show(series.backdrop ?: series.cover) }
            Box(Modifier.fillMaxSize()) {
                DetailBackdrop(series.backdrop ?: series.cover, series.name)
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = AwadyDimens.SafeVertical + 24.dp, bottom = AwadyDimens.SafeVertical)) {
                    val target = state.resumeTarget()
                    DetailHeader(
                        title = series.name,
                        poster = series.cover,
                        meta = metaLine(
                            series.year?.toString(),
                            state.seasons.size.takeIf { it > 0 }?.let { if (it == 1) "1 season" else "$it seasons" },
                            series.rating?.takeIf { it > 0 }?.let { "★ " + String.format(Locale.US, "%.1f", it) },
                        ),
                        tagline = series.genre,
                        plot = series.plot,
                        credits = listOf("Director" to series.director, "Cast" to series.cast),
                        actions = {
                            val label = when {
                                target == null -> "Play"
                                target.second != null -> "Resume S${target.first.season}:E${target.first.num}"
                                state.progress.isNotEmpty() -> "Play S${target.first.season}:E${target.first.num}"
                                else -> "Play"
                            }
                            GlassButton(
                                text = label,
                                icon = Icons.Rounded.PlayArrow,
                                variant = GlassVariant.Tinted,
                                enabled = target != null,
                                onClick = { target?.let { navigator.playEpisode(series.seriesId, it.first.episodeId, fromStart = it.second == null) } },
                                focusRequester = playFocus,
                            )
                            GlassIconButton(
                                icon = if (state.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = if (state.liked) "Unlike" else "Like",
                                active = state.liked,
                                onClick = viewModel::toggleLike,
                            )
                            GlassIconButton(
                                icon = if (state.favourite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                                contentDescription = if (state.favourite) "Remove from favourites" else "Add to favourites",
                                active = state.favourite,
                                activeTint = AwadyColors.Warning,
                                onClick = viewModel::toggleFavourite,
                            )
                            series.trailer?.takeIf { it.isNotBlank() }?.let { trailer ->
                                GlassButton(text = "Trailer", icon = Icons.Rounded.Movie, onClick = { openTrailer(context, trailer) })
                            }
                        },
                    )
                    Spacer(Modifier.height(24.dp))
                    EpisodesSection(state, viewModel, navigator, series.seriesId)
                    if (state.related.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Shelf(title = "More like this", items = state.related, key = { "${it.kind}:${it.id}" }) { item ->
                            PosterCard(
                                title = item.title,
                                imageUrl = item.image,
                                caption = item.caption,
                                onClick = { if (item.kind == ContentKind.MOVIE) navigator.openMovie(item.id) else navigator.openSeries(item.id) },
                            )
                        }
                    }
                }
            }
            LaunchedEffect(series.seriesId) { runCatching { playFocus.requestFocus() } }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun EpisodesSection(state: SeriesDetailState, viewModel: SeriesDetailViewModel, navigator: AppNavigator, seriesId: Long) {
    Column(Modifier.fillMaxWidth()) {
        when {
            state.episodesLoading && state.episodes.isEmpty() -> Row(Modifier.padding(horizontal = AwadyDimens.SafeHorizontal, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassSpinner(size = 24.dp)
                Spacer(Modifier.width(12.dp))
                Text("Loading episodes…", style = AwadyType.Label, color = AwadyColors.TextSecondary)
            }
            state.episodesError != null && state.episodes.isEmpty() -> Row(Modifier.padding(horizontal = AwadyDimens.SafeHorizontal, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(state.episodesError.title, style = AwadyType.Label, color = AwadyColors.Danger)
                    Text(state.episodesError.message, style = AwadyType.Caption, color = AwadyColors.TextSecondary)
                }
                GlassButton("Try again", onClick = { viewModel.loadEpisodes(true) }, icon = Icons.Rounded.Refresh)
            }
            state.episodes.isEmpty() -> Text(
                "No episodes are available for this series yet.",
                style = AwadyType.Label,
                color = AwadyColors.TextSecondary,
                modifier = Modifier.padding(horizontal = AwadyDimens.SafeHorizontal, vertical = 16.dp),
            )
            else -> {
                if (state.seasons.size > 1) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().focusRestorer(),
                        contentPadding = PaddingValues(horizontal = AwadyDimens.SafeHorizontal, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.seasons, key = { it }) { season ->
                            GlassChip(
                                text = "Season $season",
                                selected = season == state.selectedSeason,
                                onClick = { viewModel.selectSeason(season) },
                                onFocus = { viewModel.selectSeason(season) },
                            )
                        }
                    }
                }
                Shelf(
                    title = state.selectedSeason?.let { "Season $it  ·  ${state.seasonEpisodes.size} episodes" } ?: "Episodes",
                    items = state.seasonEpisodes,
                    key = { it.episodeId },
                    capItems = false,
                ) { episode ->
                    val progress = state.progress[episode.episodeId]
                    LandscapeCard(
                        title = "${episode.num}. ${episode.title}",
                        imageUrl = episode.image ?: state.series?.backdrop ?: state.series?.cover,
                        subtitle = metaLine(episode.durationSecs?.let { TimeFormat.duration(it * 1000) }, progress?.let { if (it.fraction >= 0.95f) "Watched" else null }),
                        progress = progress?.fraction,
                        onClick = { navigator.playEpisode(seriesId, episode.episodeId, fromStart = progress == null || progress.fraction >= 0.95f) },
                    )
                }
            }
        }
    }
}
