package com.awadytv.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.awadytv.core.designsystem.Artwork
import com.awadytv.core.designsystem.AwadyColors
import com.awadytv.core.designsystem.AwadyDimens
import com.awadytv.core.designsystem.AwadyType
import com.awadytv.core.designsystem.GlassButton
import com.awadytv.core.designsystem.GlassIconButton
import com.awadytv.core.designsystem.GlassListRow
import com.awadytv.core.designsystem.GlassSideSheet
import com.awadytv.core.designsystem.GlassSpinner
import com.awadytv.core.designsystem.GlassSurface
import com.awadytv.core.designsystem.GlassVariant
import com.awadytv.core.designsystem.LoadingView
import com.awadytv.core.util.TimeFormat
import com.awadytv.navigation.AppNavigator

@Composable
fun VodPlayerScreen(navigator: AppNavigator, viewModel: VodPlayerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val nextFocus = remember { FocusRequester() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) viewModel.onStop() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = !state.tracksVisible) {
        when {
            state.showNextCard -> viewModel.dismissNextCard()
            state.controlsVisible && state.isPlaying -> viewModel.hideControls()
            else -> navigator.back()
        }
    }
    LaunchedEffect(state.finished) { if (state.finished) navigator.back() }
    LaunchedEffect(state.controlsVisible, state.tracksVisible) {
        if (state.tracksVisible) return@LaunchedEffect
        runCatching { if (state.controlsVisible) playFocus.requestFocus() else rootFocus.requestFocus() }
    }
    LaunchedEffect(state.showNextCard) { if (state.showNextCard) runCatching { nextFocus.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Any key while controls are up restarts their 5s auto-hide timer.
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && state.controlsVisible && !state.tracksVisible) viewModel.showControls()
                false
            }
            .focusRequester(rootFocus)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || state.tracksVisible) return@onKeyEvent false
                when (event.key) {
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { viewModel.togglePlay(); true }
                    Key.MediaFastForward -> { viewModel.seekBy(30_000); true }
                    Key.MediaRewind -> { viewModel.seekBy(-30_000); true }
                    Key.MediaNext -> { viewModel.playNext(); true }
                    else -> if (!state.controlsVisible) {
                        when (event.key) {
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { viewModel.togglePlay(); true }
                            Key.DirectionLeft -> { viewModel.seekBy(-10_000); true }
                            Key.DirectionRight -> { viewModel.seekBy(10_000); true }
                            Key.DirectionUp, Key.DirectionDown, Key.Menu, Key.Info -> { viewModel.showControls(); true }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
            }
            .focusable(),
    ) {
        val exo = player
        if (exo != null) {
            VideoSurface(exo, Modifier.fillMaxSize(), state.settings.subtitleScale, state.settings.subtitleBackground)
        } else {
            LoadingView()
        }
        if ((state.buffering || state.loading) && state.error == null && exo != null) {
            GlassSpinner(Modifier.align(Alignment.Center), size = 52.dp)
        }

        AnimatedVisibility(
            visible = state.controlsVisible && !state.tracksVisible,
            enter = fadeIn() + slideInVertically { it / 5 },
            exit = fadeOut() + slideOutVertically { it / 5 },
            modifier = Modifier.fillMaxSize(),
        ) {
            Controls(state, viewModel, navigator, playFocus)
        }

        AnimatedVisibility(
            visible = state.showNextCard && state.nextEpisode != null,
            enter = fadeIn() + slideInHorizontally { it / 3 },
            exit = fadeOut() + slideOutHorizontally { it / 3 },
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            NextEpisodeCard(state, viewModel, nextFocus)
        }

        state.error?.let { message ->
            val retry = remember { FocusRequester() }
            GlassSurface(Modifier.align(Alignment.Center), shape = RoundedCornerShape(AwadyDimens.RadiusXL)) {
                Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(state.title.ifBlank { "Playback error" }, style = AwadyType.Headline, color = AwadyColors.TextPrimary)
                    Text(message, style = AwadyType.Body, color = AwadyColors.TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        GlassButton("Try again", onClick = viewModel::retry, icon = Icons.Rounded.Refresh, variant = GlassVariant.Tinted, focusRequester = retry)
                        GlassButton("Back", onClick = navigator::back, icon = Icons.AutoMirrored.Rounded.ArrowBack)
                    }
                }
            }
            LaunchedEffect(message) { runCatching { retry.requestFocus() } }
        }

        GlassSideSheet(visible = state.tracksVisible, onDismiss = { viewModel.setTracksVisible(false) }, alignEnd = true, width = 420.dp) {
            TrackPicker(state, viewModel)
        }
    }
}

@Composable
private fun Controls(state: VodPlayerState, viewModel: VodPlayerViewModel, navigator: AppNavigator, playFocus: FocusRequester) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to Color(0x99000000), 0.25f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color(0xCC000000))),
        )
        Column(Modifier.align(Alignment.TopStart).padding(horizontal = AwadyDimens.SafeHorizontal, vertical = AwadyDimens.SafeVertical)) {
            Text(state.title, style = AwadyType.Title, color = AwadyColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            state.subtitle?.let { Text(it, style = AwadyType.Body, color = AwadyColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        GlassSurface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = AwadyDimens.SafeHorizontal, vertical = AwadyDimens.SafeVertical),
            shape = RoundedCornerShape(AwadyDimens.RadiusXL),
            elevation = 24.dp,
        ) {
            Column(Modifier.padding(horizontal = 28.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(TimeFormat.playback(state.positionMs), style = AwadyType.Label, color = AwadyColors.TextPrimary)
                    Spacer(Modifier.width(16.dp))
                    Scrubber(
                        positionMs = state.positionMs,
                        durationMs = state.durationMs,
                        bufferedMs = state.bufferedMs,
                        onSeek = viewModel::seekBy,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        if (state.durationMs > 0) "-" + TimeFormat.playback(state.durationMs - state.positionMs) else "--:--",
                        style = AwadyType.Label,
                        color = AwadyColors.TextSecondary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(Icons.Rounded.Replay10, "Back 10 seconds", onClick = { viewModel.seekBy(-10_000) })
                    GlassIconButton(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (state.isPlaying) "Pause" else "Play",
                        onClick = viewModel::togglePlay,
                        focusRequester = playFocus,
                    )
                    GlassIconButton(Icons.Rounded.Forward10, "Forward 10 seconds", onClick = { viewModel.seekBy(10_000) })
                    Spacer(Modifier.weight(1f))
                    if (state.audioTracks.size > 1 || state.textTracks.isNotEmpty()) {
                        GlassButton("Audio & subtitles", icon = Icons.Rounded.Subtitles, onClick = { viewModel.setTracksVisible(true) })
                    }
                    if (state.nextEpisode != null) {
                        GlassButton("Next episode", icon = Icons.Rounded.SkipNext, onClick = viewModel::playNext)
                    }
                    GlassButton("", icon = Icons.AutoMirrored.Rounded.ArrowBack, onClick = navigator::back)
                }
            }
        }
    }
}

/** Focusable scrubber: Left/Right seek 10s while focused; no thumbnails (Xtream has no sprites). */
@Composable
private fun Scrubber(positionMs: Long, durationMs: Long, bufferedMs: Long, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val height by animateDpAsState(if (focused) 10.dp else 6.dp, label = "scrubHeight")
    val color by animateColorAsState(if (focused) Color.White else Color(0xE6FFFFFF), label = "scrubColor")
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val buffered = if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    BoxWithConstraints(
        modifier
            .height(28.dp)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onSeek(-10_000); true }
                    Key.DirectionRight -> { onSeek(10_000); true }
                    else -> false
                }
            }
            .focusable(interactionSource = interaction),
        contentAlignment = Alignment.CenterStart,
    ) {
        val width = maxWidth
        Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(Color(0x40FFFFFF)))
        Box(Modifier.width(width * buffered).height(height).clip(RoundedCornerShape(50)).background(Color(0x59FFFFFF)))
        Box(Modifier.width(width * fraction).height(height).clip(RoundedCornerShape(50)).background(color))
        if (focused) {
            Box(
                Modifier
                    .offset(x = (width * fraction - 10.dp).coerceAtLeast(0.dp))
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun NextEpisodeCard(state: VodPlayerState, viewModel: VodPlayerViewModel, focus: FocusRequester) {
    val next = state.nextEpisode ?: return
    GlassSurface(
        modifier = Modifier.padding(horizontal = AwadyDimens.SafeHorizontal, vertical = AwadyDimens.SafeVertical + 150.dp).width(420.dp),
        shape = RoundedCornerShape(AwadyDimens.RadiusLarge),
        elevation = 24.dp,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(next.image ?: state.artwork, next.title, Modifier.size(width = 144.dp, height = 81.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(state.nextCountdown?.let { "UP NEXT IN $it" } ?: "UP NEXT", style = AwadyType.ShelfHeader, color = AwadyColors.TextSecondary)
                Text("S${next.season}:E${next.num}  ${next.title}", style = AwadyType.Label, color = AwadyColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton("Play now", icon = Icons.Rounded.SkipNext, variant = GlassVariant.Tinted, onClick = viewModel::playNext, focusRequester = focus)
                    GlassButton("Hide", onClick = viewModel::dismissNextCard)
                }
            }
        }
    }
}

@Composable
private fun TrackPicker(state: VodPlayerState, viewModel: VodPlayerViewModel) {
    val first = remember { FocusRequester() }
    LazyColumn(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { Text("AUDIO", style = AwadyType.ShelfHeader, color = AwadyColors.TextSecondary, modifier = Modifier.padding(8.dp)) }
        if (state.audioTracks.isEmpty()) {
            item { Text("Default audio", style = AwadyType.Label, color = AwadyColors.TextTertiary, modifier = Modifier.padding(8.dp)) }
        }
        items(state.audioTracks.withIndex().toList(), key = { "a${it.index}" }) { (i, option) ->
            TrackRow(option.label, option.selected, onClick = { viewModel.selectTrack(C.TRACK_TYPE_AUDIO, option) }, focus = if (i == 0) first else null)
        }
        item { Spacer(Modifier.height(12.dp)) }
        item { Text("SUBTITLES", style = AwadyType.ShelfHeader, color = AwadyColors.TextSecondary, modifier = Modifier.padding(8.dp)) }
        item {
            TrackRow("Off", state.textTracks.none { it.selected }, onClick = { viewModel.selectTrack(C.TRACK_TYPE_TEXT, null) }, focus = if (state.audioTracks.isEmpty()) first else null)
        }
        items(state.textTracks.withIndex().toList(), key = { "t${it.index}" }) { (_, option) ->
            TrackRow(option.label, option.selected, onClick = { viewModel.selectTrack(C.TRACK_TYPE_TEXT, option) })
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit, focus: FocusRequester? = null) {
    GlassListRow(onClick = onClick, selected = selected, focusRequester = focus, modifier = Modifier.fillMaxWidth()) { focused ->
        val color = if (focused) AwadyColors.TextOnFocus else AwadyColors.TextPrimary
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (selected) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(label, style = AwadyType.Label, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
