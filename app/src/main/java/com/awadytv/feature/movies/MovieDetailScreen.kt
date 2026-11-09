package com.awadytv.feature.movies

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.awadytv.core.designsystem.AwadyColors
import com.awadytv.core.designsystem.AwadyDimens
import com.awadytv.core.designsystem.EmptyView
import com.awadytv.core.designsystem.GlassButton
import com.awadytv.core.designsystem.GlassIconButton
import com.awadytv.core.designsystem.GlassVariant
import com.awadytv.core.designsystem.LoadingView
import com.awadytv.core.designsystem.LocalBackdrop
import com.awadytv.core.designsystem.PosterCard
import com.awadytv.core.designsystem.Shelf
import com.awadytv.core.util.TimeFormat
import com.awadytv.data.recommendations.RecommendationRepository
import com.awadytv.data.repository.LibraryRepository
import com.awadytv.data.repository.MoviesRepository
import com.awadytv.data.repository.ProfileRepository
import com.awadytv.domain.ContentKind
import com.awadytv.domain.Movie
import com.awadytv.domain.Session
import com.awadytv.domain.WatchProgress
import com.awadytv.feature.common.DetailBackdrop
import com.awadytv.feature.common.DetailHeader
import com.awadytv.domain.PosterItem
import com.awadytv.feature.common.metaLine
import com.awadytv.feature.common.openTrailer
import com.awadytv.navigation.AppNavigator
import com.awadytv.navigation.MovieDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class MovieDetailState(
    val loading: Boolean = true,
    val movie: Movie? = null,
    val notFound: Boolean = false,
    val liked: Boolean = false,
    val favourite: Boolean = false,
    val progress: WatchProgress? = null,
    val related: List<PosterItem> = emptyList(),
)

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val profiles: ProfileRepository,
    private val movies: MoviesRepository,
    private val library: LibraryRepository,
    private val recommendations: RecommendationRepository,
) : ViewModel() {
    private val streamId = savedStateHandle.toRoute<MovieDetailRoute>().streamId
    private val _state = MutableStateFlow(MovieDetailState())
    val state: StateFlow<MovieDetailState> = _state
    private var session: Session? = null

    init {
        viewModelScope.launch {
            val s = profiles.currentSession() ?: run { _state.update { it.copy(loading = false, notFound = true) }; return@launch }
            session = s
            launch { movies.ensureInfo(s, streamId) }
            launch {
                movies.observe(s.profileId, streamId).collect { movie ->
                    _state.update { it.copy(loading = false, movie = movie, notFound = movie == null) }
                }
            }
            launch { library.isLiked(s.profileId, ContentKind.MOVIE, streamId).collect { v -> _state.update { it.copy(liked = v) } } }
            launch { library.isFavourite(s.profileId, ContentKind.MOVIE, streamId).collect { v -> _state.update { it.copy(favourite = v) } } }
            launch { library.observeProgress(s.profileId, ContentKind.MOVIE, streamId).collect { v -> _state.update { it.copy(progress = v) } } }
            launch {
                val related = recommendations.moreLikeThis(s.profileId, ContentKind.MOVIE, streamId)
                _state.update { it.copy(related = related) }
            }
        }
    }

    fun toggleLike() {
        val s = session ?: return
        viewModelScope.launch { library.setLiked(s.profileId, ContentKind.MOVIE, streamId, !_state.value.liked) }
    }

    fun toggleFavourite() {
        val s = session ?: return
        viewModelScope.launch { library.setFavourite(s.profileId, ContentKind.MOVIE, streamId, !_state.value.favourite) }
    }
}

@Composable
fun MovieDetailScreen(navigator: AppNavigator, viewModel: MovieDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val backdrop = LocalBackdrop.current
    val playFocus = remember { FocusRequester() }
    val movie = state.movie

    when {
        state.loading -> LoadingView()
        movie == null -> EmptyView("Movie not found", "It may have been removed by your provider. Refresh the catalog in Settings.", icon = Icons.Rounded.Movie)
        else -> {
            LaunchedEffect(movie.streamId) { backdrop.show(movie.backdrop ?: movie.icon) }
            Box(Modifier.fillMaxSize()) {
                DetailBackdrop(movie.backdrop ?: movie.icon, movie.name)
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = AwadyDimens.SafeVertical + 24.dp, bottom = AwadyDimens.SafeVertical)) {
                    val progress = state.progress?.takeIf { it.positionMs > 30_000 && it.fraction < 0.95f }
                    DetailHeader(
                        title = movie.name,
                        poster = movie.icon,
                        meta = metaLine(
                            movie.year?.toString(),
                            movie.durationSecs?.let { TimeFormat.duration(it * 1000) },
                            movie.rating?.takeIf { it > 0 }?.let { "★ " + String.format(Locale.US, "%.1f", it) },
                        ),
                        tagline = movie.genre,
                        plot = movie.plot ?: if (!movie.infoLoaded) "Loading details…" else null,
                        credits = listOf("Director" to movie.director, "Cast" to movie.cast),
                        actions = {
                            if (progress != null) {
                                GlassButton(
                                    text = "Resume ${TimeFormat.playback(progress.positionMs)}",
                                    icon = Icons.Rounded.PlayArrow,
                                    variant = GlassVariant.Tinted,
                                    onClick = { navigator.playMovie(movie.streamId) },
                                    focusRequester = playFocus,
                                )
                                GlassButton(text = "Start over", icon = Icons.Rounded.Replay, onClick = { navigator.playMovie(movie.streamId, fromStart = true) })
                            } else {
                                GlassButton(text = "Play", icon = Icons.Rounded.PlayArrow, variant = GlassVariant.Tinted, onClick = { navigator.playMovie(movie.streamId, fromStart = true) }, focusRequester = playFocus)
                            }
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
                            movie.trailer?.takeIf { it.isNotBlank() }?.let { trailer ->
                                GlassButton(text = "Trailer", icon = Icons.Rounded.Movie, onClick = { openTrailer(context, trailer) })
                            }
                        },
                    )
                    Spacer(Modifier.height(28.dp))
                    if (state.related.isNotEmpty()) {
                        Shelf(title = "More like this", items = state.related, key = { "${it.kind}:${it.id}" }) { item ->
                            PosterCard(
                                title = item.title,
                                imageUrl = item.image,
                                caption = item.caption,
                                onClick = { if (item.kind == ContentKind.SERIES) navigator.openSeries(item.id) else navigator.openMovie(item.id) },
                            )
                        }
                    }
                }
            }
            LaunchedEffect(movie.streamId) { runCatching { playFocus.requestFocus() } }
        }
    }
}
