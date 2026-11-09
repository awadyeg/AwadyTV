package com.awadytv.feature.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.awadytv.core.designsystem.AwadyDimens
import com.awadytv.core.designsystem.ChannelCard
import com.awadytv.core.designsystem.EmptyView
import com.awadytv.core.designsystem.GlassIconButton
import com.awadytv.core.designsystem.GlassTextField
import com.awadytv.core.designsystem.LoadingView
import com.awadytv.core.designsystem.LocalBackdrop
import com.awadytv.core.designsystem.PosterCard
import com.awadytv.core.designsystem.Shelf
import com.awadytv.core.security.AdultFilter
import com.awadytv.data.repository.LiveRepository
import com.awadytv.data.repository.MoviesRepository
import com.awadytv.data.repository.ProfileRepository
import com.awadytv.data.repository.SeriesRepository
import com.awadytv.domain.Channel
import com.awadytv.domain.ContentKind
import com.awadytv.domain.PosterItem
import com.awadytv.feature.common.ratingBadge
import com.awadytv.navigation.AppNavigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchState(
    val query: String = "",
    val searching: Boolean = false,
    val channels: List<Channel> = emptyList(),
    val movies: List<PosterItem> = emptyList(),
    val series: List<PosterItem> = emptyList(),
    val searched: Boolean = false,
) {
    val isEmpty: Boolean get() = channels.isEmpty() && movies.isEmpty() && series.isEmpty()
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val live: LiveRepository,
    private val movies: MoviesRepository,
    private val series: SeriesRepository,
    private val adult: AdultFilter,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state
    private val query = MutableStateFlow("")

    init {
        viewModelScope.launch {
            query.debounce(300).distinctUntilChanged().collectLatest { q -> run(q.trim()) }
        }
    }

    fun onQuery(q: String) {
        _state.update { it.copy(query = q) }
        query.value = q
    }

    private suspend fun run(q: String) {
        if (q.length < 2) {
            _state.update { it.copy(searching = false, searched = false, channels = emptyList(), movies = emptyList(), series = emptyList()) }
            return
        }
        val s = profiles.currentSession() ?: return
        _state.update { it.copy(searching = true) }
        val blockedLive = adult.blockedLive(s.profileId)
        val blockedMovies = adult.blockedMovies(s.profileId)
        val blockedSeries = adult.blockedSeries(s.profileId)
        val channels = live.search(s.profileId, q, 40).filter { it.categoryId !in blockedLive }
        val movieItems = movies.search(s.profileId, q, 60).filter { it.categoryId !in blockedMovies }
            .map { PosterItem(ContentKind.MOVIE, it.streamId, it.name, it.icon, it.backdrop, ratingBadge(it.rating)) }
        val seriesItems = series.search(s.profileId, q, 60).filter { it.categoryId !in blockedSeries }
            .map { PosterItem(ContentKind.SERIES, it.seriesId, it.name, it.cover, it.backdrop, ratingBadge(it.rating)) }
        _state.update { it.copy(searching = false, searched = true, channels = channels, movies = movieItems, series = seriesItems) }
    }
}

@Composable
fun SearchScreen(navigator: AppNavigator, viewModel: SearchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val backdrop = LocalBackdrop.current
    val voiceIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Search channels, movies and series")
    }
    val voiceAvailable = remember { voiceIntent.resolveActivity(context.packageManager) != null }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(viewModel::onQuery)
        }
    }

    Column(Modifier.fillMaxSize().padding(top = AwadyDimens.ContentTop)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = AwadyDimens.SafeHorizontal),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center,
        ) {
            GlassTextField(
                value = state.query,
                onValueChange = viewModel::onQuery,
                label = "Search",
                placeholder = "Channels, movies, series…",
                leadingIcon = Icons.Rounded.Search,
                imeAction = ImeAction.Search,
                onImeAction = {},
                modifier = Modifier.widthIn(max = 640.dp).weight(1f, fill = false),
            )
            if (voiceAvailable) {
                Spacer(Modifier.width(16.dp))
                GlassIconButton(Icons.Rounded.Mic, "Voice search", onClick = { runCatching { voice.launch(voiceIntent) } })
            }
        }
        Box(Modifier.fillMaxSize()) {
            when {
                state.query.trim().length < 2 -> EmptyView(
                    "Search everything",
                    if (voiceAvailable) "Type with the on-screen keyboard or press the microphone to speak." else "Type at least two letters with the on-screen keyboard.",
                    icon = Icons.Rounded.Search,
                )
                state.searching && !state.searched -> LoadingView(message = "Searching")
                state.searched && state.isEmpty -> EmptyView("No results", "Nothing matches “${state.query.trim()}”. Try a shorter or different word.", icon = Icons.Rounded.SearchOff)
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, bottom = AwadyDimens.SafeVertical)) {
                    if (state.channels.isNotEmpty()) item(key = "channels") {
                        Shelf("Live channels", state.channels, key = { it.streamId }) { c ->
                            ChannelCard(c.name, c.icon, onClick = { navigator.playLive(c.streamId, c.categoryId) }, number = c.num)
                        }
                    }
                    if (state.movies.isNotEmpty()) item(key = "movies") {
                        Shelf("Movies", state.movies, key = { it.id }) { m ->
                            PosterCard(m.title, m.image, onClick = { navigator.openMovie(m.id) }, badge = m.badge, onFocus = { backdrop.show(m.backdrop ?: m.image) })
                        }
                    }
                    if (state.series.isNotEmpty()) item(key = "series") {
                        Shelf("Series", state.series, key = { it.id }) { s ->
                            PosterCard(s.title, s.image, onClick = { navigator.openSeries(s.id) }, badge = s.badge, onFocus = { backdrop.show(s.backdrop ?: s.image) })
                        }
                    }
                }
            }
        }
    }
}
