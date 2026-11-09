package com.awadytv.feature.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.awadytv.core.designsystem.Artwork
import com.awadytv.core.designsystem.AwadyColors
import com.awadytv.core.designsystem.AwadyDimens
import com.awadytv.core.designsystem.AwadyType
import com.awadytv.core.designsystem.ChannelCard
import com.awadytv.core.designsystem.EmptyView
import com.awadytv.core.designsystem.GlassButton
import com.awadytv.core.designsystem.GlassSurface
import com.awadytv.core.designsystem.GlassToastHost
import com.awadytv.core.designsystem.GlassVariant
import com.awadytv.core.designsystem.LandscapeCard
import com.awadytv.core.designsystem.LoadingView
import com.awadytv.core.designsystem.LocalBackdrop
import com.awadytv.core.designsystem.PosterCard
import com.awadytv.core.designsystem.Shelf
import com.awadytv.core.designsystem.rememberToastState
import com.awadytv.core.util.TimeFormat
import com.awadytv.data.recommendations.RecommendationEngine
import com.awadytv.data.repository.LiveRepository
import com.awadytv.domain.ContentKind
import com.awadytv.navigation.AppNavigator
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(navigator: AppNavigator, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toast = rememberToastState()
    val backdrop = LocalBackdrop.current
    LaunchedEffect(Unit) { viewModel.messages.collect { toast.show(it) } }

    var lastInput by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val listState = rememberLazyListState()
    val rowScrollSpec = LocalBringIntoViewSpec.current
    val scope = rememberCoroutineScope()
    var heroIndex by rememberSaveable { mutableIntStateOf(0) }
    // Hero auto-rotates among (up to) 5 items every 8s while the user is idle.
    LaunchedEffect(state.hero.size) {
        while (state.hero.size > 1) {
            delay(8_000)
            if (System.currentTimeMillis() - lastInput >= 8_000) heroIndex = (heroIndex + 1) % state.hero.size
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent {
                lastInput = System.currentTimeMillis()
                false
            },
    ) {
        when {
            state.loading -> LoadingView()
            state.hero.isEmpty() && state.shelves.isEmpty() -> EmptyView(
                title = "Your library is empty",
                message = "Your provider returned no movies, series or channels. Refresh the catalog from Settings.",
                icon = Icons.AutoMirrored.Rounded.LibraryBooks,
            )
            else -> CompositionLocalProvider(LocalBringIntoViewSpec provides CalmVerticalScroll) { LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = AwadyDimens.SafeVertical + 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.hero.isNotEmpty()) {
                    item(key = "hero") {
                        val hero = state.hero[heroIndex.coerceIn(0, state.hero.lastIndex)]
                        Hero(
                            hero = hero,
                            index = heroIndex.coerceIn(0, state.hero.lastIndex),
                            count = state.hero.size,
                            onPlay = {
                                if (hero.kind == ContentKind.MOVIE) navigator.playMovie(hero.id) else navigator.openSeries(hero.id)
                            },
                            onInfo = { if (hero.kind == ContentKind.MOVIE) navigator.openMovie(hero.id) else navigator.openSeries(hero.id) },
                            onFocus = {
                                backdrop.show(hero.backdrop)
                                // Coming back up to the hero shows the whole top of the page, not just its buttons.
                                scope.launch {
                                    delay(60) // after the focus change's own bring-into-view
                                    listState.animateScrollToItem(0)
                                }
                            },
                            // Left on the first button / Right on the last flips through the carousel.
                            onPrevious = { heroIndex = (heroIndex - 1).mod(state.hero.size) },
                            onNext = { heroIndex = (heroIndex + 1).mod(state.hero.size) },
                        )
                    }
                }
                items(state.shelves, key = { it.id }) { shelf ->
                    // Rows keep the platform's horizontal behaviour.
                    CompositionLocalProvider(LocalBringIntoViewSpec provides rowScrollSpec) {
                    when (shelf) {
                        is HomeShelf.ContinueWatching -> Shelf(title = shelf.title, items = shelf.items, key = { "${it.kind}:${it.itemId}" }) { p ->
                            val remaining = (p.durationMs - p.positionMs).coerceAtLeast(0)
                            LandscapeCard(
                                title = p.title ?: "Continue",
                                imageUrl = p.image,
                                subtitle = if (p.durationMs > 0) "${TimeFormat.duration(remaining)} left" else null,
                                progress = p.fraction,
                                onFocus = { backdrop.show(p.image) },
                                onClick = {
                                    if (p.kind == ContentKind.EPISODE && p.seriesId != null) navigator.playEpisode(p.seriesId, p.itemId) else navigator.playMovie(p.itemId)
                                },
                                onLongClick = {
                                    if (p.kind == ContentKind.EPISODE && p.seriesId != null) navigator.openSeries(p.seriesId) else navigator.openMovie(p.itemId)
                                },
                            )
                        }
                        is HomeShelf.Channels -> Shelf(title = shelf.title, items = shelf.items, key = { it.streamId }) { channel ->
                            ChannelCard(
                                name = channel.name,
                                logoUrl = channel.icon,
                                number = channel.num,
                                onClick = { navigator.playLive(channel.streamId, LiveRepository.FAVOURITES) },
                            )
                        }
                        is HomeShelf.Posters -> Shelf(
                            title = shelf.title,
                            subtitle = when {
                                shelf.id == RecommendationEngine.SHELF_RECOMMENDED -> "On-device picks from what you like · hold OK on any title to like it"
                                else -> null
                            },
                            items = shelf.items,
                            key = { "${it.kind}:${it.id}" },
                        ) { item ->
                            PosterCard(
                                title = item.title,
                                imageUrl = item.image,
                                caption = if (shelf.id == RecommendationEngine.SHELF_RECOMMENDED) item.caption else null,
                                // Same height for every card in the row: captions of 1 vs 2 lines made the row
                                // resize as cards scrolled in, shaking everything below it.
                                captionLines = if (shelf.id == RecommendationEngine.SHELF_RECOMMENDED) 2 else 0,
                                badge = item.badge,
                                onFocus = { backdrop.show(item.backdrop ?: item.image) },
                                onClick = { if (item.kind == ContentKind.MOVIE) navigator.openMovie(item.id) else navigator.openSeries(item.id) },
                                onLongClick = { viewModel.toggleLike(item.kind, item.id, item.title) },
                            )
                        }
                    }
                    }
                }
            } }
        }
        GlassToastHost(toast, Modifier.align(Alignment.BottomCenter).padding(bottom = AwadyDimens.SafeVertical + 8.dp))
    }
}

/**
 * Home's vertical scrolling: leave the page still while the focused card is comfortably on screen, and
 * otherwise scroll only as far as needed. The TV default pins every focused item at a fixed height, which
 * cut the hero off when entering it and nudged the page on every move.
 */
@OptIn(ExperimentalFoundationApi::class)
private object CalmVerticalScroll : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val top = containerSize * 0.14f
        val bottom = containerSize * 0.96f
        return when {
            offset >= top && offset + size <= bottom -> 0f
            offset < top || size > bottom - top -> offset - top
            else -> offset + size - bottom
        }
    }
}

@Composable
private fun Hero(hero: HeroItem, index: Int, count: Int, onPlay: () -> Unit, onInfo: () -> Unit, onFocus: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(400.dp)) {
        // Fill-rate budget: the artwork covers only the right 72% (the left sat under a 92% scrim anyway),
        // and each scrim covers only the strip it fades, instead of three full-width 1080p layers.
        Crossfade(
            targetState = hero,
            animationSpec = tween(500),
            label = "hero",
            modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.72f).fillMaxHeight(),
        ) { item ->
            Box(Modifier.fillMaxSize()) {
                Artwork(item.backdrop, item.title, Modifier.fillMaxSize(), showInitials = false)
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.6f)
                        .background(Brush.horizontalGradient(0f to AwadyColors.Canvas, 0.45f to AwadyColors.Canvas.copy(alpha = 0.6f), 1f to Color.Transparent)),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.4f)
                        .background(Brush.verticalGradient(0f to Color.Transparent, 1f to AwadyColors.Canvas)),
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = AwadyDimens.SafeHorizontal, end = AwadyDimens.SafeHorizontal, bottom = 12.dp)
                .widthIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!hero.reason.isNullOrBlank()) {
                GlassSurface(shape = RoundedCornerShape(percent = 50), variant = GlassVariant.Regular, elevation = 0.dp) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = AwadyColors.AccentSoft, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(hero.reason, style = AwadyType.Caption, color = AwadyColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Text(hero.title, style = AwadyType.Display, color = AwadyColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (hero.meta.isNotBlank()) Text(hero.meta, style = AwadyType.Label, color = AwadyColors.TextSecondary, maxLines = 1)
            if (!hero.plot.isNullOrBlank()) Text(hero.plot, style = AwadyType.Body, color = AwadyColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                HeroButton(if (hero.kind == ContentKind.MOVIE) "Play" else "Episodes", Icons.Rounded.PlayArrow, GlassVariant.Tinted, onPlay, onFocus, onLeft = onPrevious.takeIf { count > 1 })
                HeroButton("More info", Icons.Rounded.Info, GlassVariant.Regular, onInfo, onFocus, onRight = onNext.takeIf { count > 1 })
                Spacer(Modifier.width(12.dp))
                repeat(count) { i ->
                    Box(
                        Modifier
                            .size(if (i == index) 10.dp else 7.dp)
                            .clip(CircleShape)
                            .background(if (i == index) AwadyColors.TextPrimary else AwadyColors.TextTertiary),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    variant: GlassVariant,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
) {
    Box(
        Modifier
            .onFocusEvent { if (it.hasFocus) onFocus() }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    event.key == Key.DirectionLeft && onLeft != null -> { onLeft(); true }
                    event.key == Key.DirectionRight && onRight != null -> { onRight(); true }
                    else -> false
                }
            },
    ) {
        GlassButton(text = text, icon = icon, variant = variant, onClick = onClick)
    }
}
