package com.awadytv.feature.taste

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.awadytv.core.designsystem.Artwork
import com.awadytv.core.designsystem.AwadyColors
import com.awadytv.core.designsystem.AwadyDimens
import com.awadytv.core.designsystem.AwadyType
import com.awadytv.core.designsystem.EmptyView
import com.awadytv.core.designsystem.GlassSurface
import com.awadytv.core.designsystem.LoadingView
import com.awadytv.data.recommendations.RecommendationRepository
import com.awadytv.data.recommendations.TasteCard
import com.awadytv.data.repository.LibraryRepository
import com.awadytv.data.repository.LibraryRepository.Verdict
import com.awadytv.data.repository.MoviesRepository
import com.awadytv.data.repository.ProfileRepository
import com.awadytv.di.ApplicationScope
import com.awadytv.domain.ContentKind
import com.awadytv.domain.Session
import com.awadytv.navigation.AppNavigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import javax.inject.Inject

data class TasteTrainerState(
    val loading: Boolean = true,
    val cards: List<TasteCard> = emptyList(),
    val index: Int = 0,
    val loved: Int = 0,
    val disliked: Int = 0,
    val skipped: Int = 0,
) {
    val current: TasteCard? get() = cards.getOrNull(index)
    val next: TasteCard? get() = cards.getOrNull(index + 1)
    val rated: Int get() = loved + disliked
}

/**
 * Taste trainer: one title at a time, Right = love it, Left = not for me, Up = skip. Answers are saved as
 * explicit likes / dislikes (skips only hide the card for a while). Recommendations are recomputed once
 * when the session ends, not per card.
 */
@HiltViewModel
class TasteTrainerViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val recommendations: RecommendationRepository,
    private val library: LibraryRepository,
    private val movies: MoviesRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val _state = MutableStateFlow(TasteTrainerState())
    val state: StateFlow<TasteTrainerState> = _state
    private var session: Session? = null
    private var loadingMore = false

    init {
        viewModelScope.launch {
            val s = profiles.currentSession() ?: run { _state.update { it.copy(loading = false) }; return@launch }
            session = s
            val cards = recommendations.tasteCards(s.profileId)
            _state.update { it.copy(loading = false, cards = cards) }
            enrich(0)
        }
    }

    fun answer(verdict: Verdict) {
        val s = session ?: return
        val st = _state.value
        val card = st.current ?: return
        viewModelScope.launch { library.rate(s.profileId, card.kind, card.id, verdict) }
        _state.update {
            when (verdict) {
                Verdict.LIKE -> it.copy(index = it.index + 1, loved = it.loved + 1)
                Verdict.DISLIKE -> it.copy(index = it.index + 1, disliked = it.disliked + 1)
                Verdict.SKIP -> it.copy(index = it.index + 1, skipped = it.skipped + 1)
            }
        }
        enrich(_state.value.index)
        if (_state.value.cards.size - _state.value.index < 6) loadMore()
    }

    /** Movie lists carry no plot or genre; fetch them for the card on screen and the next one. */
    private fun enrich(from: Int) {
        val s = session ?: return
        _state.value.cards.drop(from).take(2).filter { it.kind == ContentKind.MOVIE && it.plot.isNullOrBlank() }.forEach { card ->
            viewModelScope.launch {
                runCatching { movies.ensureInfo(s, card.id) }
                val movie = movies.get(s.profileId, card.id) ?: return@launch
                _state.update { st ->
                    st.copy(cards = st.cards.map { c -> if (c.kind == card.kind && c.id == card.id) c.copy(plot = movie.plot, genre = movie.genre, year = movie.year ?: c.year, rating = movie.rating ?: c.rating, backdrop = movie.backdrop ?: c.backdrop) else c })
                }
            }
        }
    }

    private fun loadMore() {
        val s = session ?: return
        if (loadingMore) return
        loadingMore = true
        viewModelScope.launch {
            val seen = _state.value.cards.map { "${it.kind.key}:${it.id}" }.toSet()
            val more = recommendations.tasteCards(s.profileId, exclude = seen)
            _state.update { it.copy(cards = it.cards + more) }
            loadingMore = false
        }
    }

    override fun onCleared() {
        val s = session ?: return
        // One recompute for the whole session; it runs in the app scope after this screen is gone.
        if (_state.value.rated > 0) appScope.launch { library.notifyLikesChanged(s.profileId) }
    }
}

private enum class Exit { LEFT, RIGHT, UP }

@Composable
fun TasteTrainerScreen(navigator: AppNavigator, viewModel: TasteTrainerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val tilt = remember { Animatable(0f) }
    var exiting by remember { mutableStateOf<Exit?>(null) }

    BackHandler { navigator.back() }
    LaunchedEffect(state.loading) { runCatching { focus.requestFocus() } }

    fun swipe(exit: Exit, verdict: Verdict) {
        if (exiting != null || state.current == null) return
        exiting = exit
        scope.launch {
            val x = when (exit) { Exit.LEFT -> -1400f; Exit.RIGHT -> 1400f; Exit.UP -> 0f }
            val y = if (exit == Exit.UP) -1100f else 80f
            launch { tilt.animateTo(if (exit == Exit.LEFT) -14f else if (exit == Exit.RIGHT) 14f else 0f, tween(260)) }
            launch { offsetY.animateTo(y, tween(260)) }
            offsetX.animateTo(x, tween(260))
            viewModel.answer(verdict)
            offsetX.snapTo(0f); offsetY.snapTo(0f); tilt.snapTo(0f)
            exiting = null
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(AwadyColors.Canvas)
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> { swipe(Exit.RIGHT, Verdict.LIKE); true }
                    Key.DirectionLeft -> { swipe(Exit.LEFT, Verdict.DISLIKE); true }
                    Key.DirectionUp -> { swipe(Exit.UP, Verdict.SKIP); true }
                    Key.DirectionDown, Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> true
                    else -> false
                }
            }
            .focusable(),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = AwadyDimens.SafeHorizontal, vertical = AwadyDimens.SafeVertical)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Tune, contentDescription = null, tint = AwadyColors.AccentSoft, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text("Train your taste", style = AwadyType.Title, color = AwadyColors.TextPrimary)
                Spacer(Modifier.weight(1f))
                Text("♥ ${state.loved}    ✕ ${state.disliked}    ↑ ${state.skipped}", style = AwadyType.Label, color = AwadyColors.TextSecondary)
            }
            Text(
                "Every answer teaches your recommendations. New titles each time you come back.",
                style = AwadyType.Caption,
                color = AwadyColors.TextTertiary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                val current = state.current
                when {
                    state.loading -> LoadingView(message = "Picking titles")
                    current == null -> EmptyView(
                        "That's all for now",
                        "You've answered every title picked for this session. Come back later for new ones; your recommendations are being updated.",
                        icon = Icons.Rounded.Favorite,
                    )
                    else -> {
                        state.next?.let { next ->
                            TasteCardView(next, Modifier.graphicsLayer { scaleX = 0.94f; scaleY = 0.94f; alpha = 0.5f })
                        }
                        TasteCardView(
                            current,
                            Modifier.graphicsLayer {
                                translationX = offsetX.value
                                translationY = offsetY.value
                                rotationZ = tilt.value
                            },
                            stamp = exiting,
                        )
                    }
                }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Hint(Icons.Rounded.ThumbDown, "◀  Not for me", AwadyColors.Danger)
                Hint(Icons.Rounded.ArrowUpward, "▲  Skip", AwadyColors.TextSecondary)
                Hint(Icons.Rounded.Favorite, "Love it  ▶", AwadyColors.Like)
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Hint(icon: ImageVector, label: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(AwadyColors.Surface), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(label, style = AwadyType.Label, color = AwadyColors.TextSecondary)
    }
}

@Composable
private fun TasteCardView(card: TasteCard, modifier: Modifier = Modifier, stamp: Exit? = null) {
    GlassSurface(modifier.widthIn(max = 980.dp), shape = RoundedCornerShape(AwadyDimens.RadiusXL)) {
        Box {
            Row(Modifier.padding(24.dp)) {
                Artwork(card.image, card.title, Modifier.size(width = 220.dp, height = 330.dp).clip(RoundedCornerShape(AwadyDimens.RadiusMedium)))
                Spacer(Modifier.width(28.dp))
                Column(Modifier.weight(1f).height(330.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(card.why.uppercase(), style = AwadyType.ShelfHeader, color = AwadyColors.AccentSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(card.title, style = AwadyType.Headline, color = AwadyColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val meta = listOfNotNull(
                        if (card.kind == ContentKind.SERIES) "Series" else "Movie",
                        card.year?.toString(),
                        card.rating?.takeIf { it > 0 }?.let { "★ " + String.format(Locale.US, "%.1f", it) },
                    ).joinToString("  ·  ")
                    Text(meta, style = AwadyType.Label, color = AwadyColors.TextSecondary)
                    card.genre?.takeIf { it.isNotBlank() }?.let { Text(it, style = AwadyType.Caption, color = AwadyColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Text(
                        card.plot?.takeIf { it.isNotBlank() } ?: "No description from your provider.",
                        style = AwadyType.Body,
                        color = if (card.plot.isNullOrBlank()) AwadyColors.TextTertiary else AwadyColors.TextSecondary,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (stamp != null) {
                val (text, color) = when (stamp) {
                    Exit.RIGHT -> "LOVE" to AwadyColors.Like
                    Exit.LEFT -> "NOPE" to AwadyColors.Danger
                    Exit.UP -> "SKIP" to AwadyColors.TextSecondary
                }
                Text(
                    text,
                    fontSize = 56.sp,
                    style = AwadyType.Display,
                    color = color,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer { rotationZ = if (stamp == Exit.LEFT) 12f else -12f }
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 28.dp, vertical = 8.dp),
                )
            }
        }
    }
}
