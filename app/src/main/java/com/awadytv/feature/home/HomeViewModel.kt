package com.awadytv.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.awadytv.core.datastore.SettingsRepository
import com.awadytv.core.security.AdultFilter
import com.awadytv.core.security.ParentalGate
import com.awadytv.core.util.TimeFormat
import com.awadytv.data.recommendations.RecommendationEngine
import com.awadytv.data.recommendations.RecommendationRepository
import com.awadytv.data.recommendations.RecommendationShelf
import com.awadytv.data.repository.LibraryRepository
import com.awadytv.data.repository.LiveRepository
import com.awadytv.data.repository.MoviesRepository
import com.awadytv.data.repository.ProfileRepository
import com.awadytv.data.repository.SeriesRepository
import com.awadytv.domain.Channel
import com.awadytv.domain.ContentKind
import com.awadytv.domain.PosterItem
import com.awadytv.domain.WatchProgress
import com.awadytv.feature.common.metaLine
import com.awadytv.feature.common.ratingBadge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel as EventChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class HeroItem(
    val kind: ContentKind,
    val id: Long,
    val title: String,
    val backdrop: String?,
    val poster: String?,
    val meta: String,
    val reason: String?,
    val plot: String?,
)

sealed interface HomeShelf {
    val id: String
    val title: String

    data class Posters(override val id: String, override val title: String, val items: List<PosterItem>, val ai: Boolean = false) : HomeShelf
    data class ContinueWatching(override val id: String = "continue", override val title: String = "Continue watching", val items: List<WatchProgress>) : HomeShelf
    data class Channels(override val id: String = "channels", override val title: String = "Favourite channels", val items: List<Channel>) : HomeShelf
}

data class HomeState(
    val loading: Boolean = true,
    val hero: List<HeroItem> = emptyList(),
    val shelves: List<HomeShelf> = emptyList(),
    val aiEnabled: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val library: LibraryRepository,
    private val recommendations: RecommendationRepository,
    private val movies: MoviesRepository,
    private val series: SeriesRepository,
    private val live: LiveRepository,
    private val settings: SettingsRepository,
    private val parental: ParentalGate,
    private val adult: AdultFilter,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state
    private val _messages = EventChannel<String>(EventChannel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(profiles.session.filterNotNull(), parental.unlocked) { s, _ -> s }.flatMapLatest { s ->
                launch { runCatching { recommendations.refresh(s.profileId) } }
                // Adult categories stay off Home while the parental lock is on.
                val blockedMovies = adult.blockedMovies(s.profileId)
                val blockedSeries = adult.blockedSeries(s.profileId)
                val blockedLive = adult.blockedLive(s.profileId)
                val base = combine(
                    library.continueWatching(s.profileId),
                    recommendations.shelves(s.profileId),
                    movies.recent(s.profileId, 40),
                    series.recent(s.profileId, 40),
                    live.favouriteChannelsFlow(s.profileId),
                ) { cw, recs, recentMovies, recentSeries, channels ->
                    Inputs(
                        cw,
                        recs,
                        recentMovies.filter { it.categoryId !in blockedMovies }.map { it.toPoster() },
                        recentSeries.filter { it.categoryId !in blockedSeries }.map { it.toPoster() },
                        channels.filter { it.categoryId !in blockedLive },
                    )
                }
                combine(base, settings.settings) { inputs, st -> inputs to st.aiRecommendations }
            }.collect { (inputs, ai) -> publish(inputs, ai) }
        }
    }

    private data class Inputs(
        val continueWatching: List<WatchProgress>,
        val recs: List<RecommendationShelf>,
        val recentMovies: List<PosterItem>,
        val recentSeries: List<PosterItem>,
        val channels: List<Channel>,
    )

    private suspend fun publish(inputs: Inputs, ai: Boolean) {
        val shelves = mutableListOf<HomeShelf>()
        if (inputs.continueWatching.isNotEmpty()) shelves += HomeShelf.ContinueWatching(items = inputs.continueWatching)
        val aiShelves = if (ai) inputs.recs else emptyList()
        aiShelves.forEach { shelves += HomeShelf.Posters(it.id, it.title, it.items, ai = true) }
        if (inputs.recentMovies.isNotEmpty()) shelves += HomeShelf.Posters("recent_movies", "Recently added movies", inputs.recentMovies)
        if (inputs.recentSeries.isNotEmpty()) shelves += HomeShelf.Posters("recent_series", "Recently added series", inputs.recentSeries)
        if (inputs.channels.isNotEmpty()) shelves += HomeShelf.Channels(items = inputs.channels)

        val heroSource = aiShelves.firstOrNull { it.id == RecommendationEngine.SHELF_RECOMMENDED }?.items.orEmpty()
            .ifEmpty { inputs.recentMovies + inputs.recentSeries }
        _state.value = HomeState(loading = false, hero = buildHero(heroSource), shelves = shelves, aiEnabled = ai)
    }

    /** Five hero items, preferring ones with wide artwork. */
    private suspend fun buildHero(source: List<PosterItem>): List<HeroItem> {
        val s = profiles.currentSession() ?: return emptyList()
        val picks = (source.filter { !it.backdrop.isNullOrBlank() } + source.filter { it.backdrop.isNullOrBlank() }).distinctBy { it.kind to it.id }.take(5)
        return picks.mapNotNull { item ->
            when (item.kind) {
                ContentKind.MOVIE -> movies.get(s.profileId, item.id)?.let { m ->
                    HeroItem(
                        ContentKind.MOVIE, m.streamId, m.name, m.backdrop ?: m.icon, m.icon,
                        metaLine("Movie", m.year?.toString(), m.genre?.substringBefore(','), m.durationSecs?.let { TimeFormat.duration(it * 1000) }, m.rating?.takeIf { it > 0 }?.let { "★ " + String.format(Locale.US, "%.1f", it) }),
                        item.caption, m.plot,
                    )
                }
                ContentKind.SERIES -> series.get(s.profileId, item.id)?.let { sr ->
                    HeroItem(
                        ContentKind.SERIES, sr.seriesId, sr.name, sr.backdrop ?: sr.cover, sr.cover,
                        metaLine("Series", sr.year?.toString(), sr.genre?.substringBefore(','), sr.rating?.takeIf { it > 0 }?.let { "★ " + String.format(Locale.US, "%.1f", it) }),
                        item.caption, sr.plot,
                    )
                }
                else -> null
            }
        }
    }

    /** Hold OK on any poster: toggle like (feeds the recommendation engine immediately). */
    fun toggleLike(kind: ContentKind, id: Long, title: String) {
        viewModelScope.launch {
            val s = profiles.currentSession() ?: return@launch
            val liked = library.isLiked(s.profileId, kind, id).first()
            library.setLiked(s.profileId, kind, id, !liked)
            _messages.send(if (liked) "Removed “$title” from likes" else "♥ Liked “$title” – updating your recommendations")
        }
    }

    fun refreshRecommendations() {
        viewModelScope.launch {
            val s = profiles.currentSession() ?: return@launch
            runCatching { recommendations.refresh(s.profileId, force = true) }
        }
    }
}

private fun com.awadytv.domain.Movie.toPoster() = PosterItem(ContentKind.MOVIE, streamId, name, icon, backdrop, ratingBadge(rating))
private fun com.awadytv.domain.Series.toPoster() = PosterItem(ContentKind.SERIES, seriesId, name, cover, backdrop, ratingBadge(rating))
