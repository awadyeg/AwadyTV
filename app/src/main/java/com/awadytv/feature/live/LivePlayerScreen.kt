package com.awadytv.feature.live

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.awadytv.core.designsystem.Artwork
import com.awadytv.core.designsystem.AwadyColors
import com.awadytv.core.designsystem.AwadyDimens
import com.awadytv.core.designsystem.AwadyType
import com.awadytv.core.designsystem.GlassButton
import com.awadytv.core.designsystem.GlassSideSheet
import com.awadytv.core.designsystem.GlassSpinner
import com.awadytv.core.designsystem.GlassSurface
import com.awadytv.core.designsystem.GlassVariant
import com.awadytv.core.designsystem.LoadingView
import com.awadytv.core.designsystem.ProgressLine
import com.awadytv.core.util.TimeFormat
import com.awadytv.feature.player.VideoSurface
import com.awadytv.navigation.AppNavigator

@Composable
fun LivePlayerScreen(navigator: AppNavigator, viewModel: LivePlayerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val rootFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onStop()
                Lifecycle.Event.ON_START -> viewModel.onStart()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = !state.sheetVisible) { navigator.back() }
    LaunchedEffect(state.sheetVisible, state.error) {
        if (!state.sheetVisible) runCatching { if (state.error != null) retryFocus.requestFocus() else rootFocus.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onKeyEvent { event ->
                if (state.sheetVisible || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.ChannelUp -> { viewModel.zap(+1); true }
                    Key.DirectionDown, Key.ChannelDown -> { viewModel.zap(-1); true }
                    Key.DirectionLeft -> { viewModel.setSheetVisible(true); true }
                    Key.DirectionRight, Key.Info -> { viewModel.showOverlay(); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (state.overlayVisible) viewModel.setSheetVisible(true) else viewModel.showOverlay()
                        true
                    }
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { viewModel.togglePlayPause(); true }
                    Key.ProgramRed, Key.Bookmark -> { viewModel.toggleFavourite(); true }
                    else -> {
                        val digit = digitOf(event.key)
                        if (digit != null) { viewModel.onDigit(digit); true } else false
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
        if (state.buffering && state.error == null && exo != null) {
            GlassSpinner(Modifier.align(Alignment.Center), size = 48.dp)
        }
        if (state.digits.isNotEmpty()) {
            GlassSurface(Modifier.align(Alignment.TopEnd).padding(horizontal = AwadyDimens.SafeHorizontal, vertical = AwadyDimens.SafeVertical), shape = RoundedCornerShape(20.dp)) {
                Text(state.digits, style = AwadyType.Display, color = AwadyColors.TextPrimary, modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
            }
        }
        state.error?.let { message ->
            GlassSurface(Modifier.align(Alignment.Center), shape = RoundedCornerShape(AwadyDimens.RadiusXL)) {
                Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(state.channel?.name ?: "Playback error", style = AwadyType.Headline, color = AwadyColors.TextPrimary)
                    Text(message, style = AwadyType.Body, color = AwadyColors.TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        GlassButton("Try again", onClick = viewModel::retry, icon = Icons.Rounded.Refresh, variant = GlassVariant.Tinted, focusRequester = retryFocus)
                        GlassButton("Channel list", onClick = { viewModel.setSheetVisible(true) })
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = state.overlayVisible && !state.sheetVisible,
            enter = fadeIn() + slideInVertically { it / 4 },
            exit = fadeOut() + slideOutVertically { it / 4 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            LiveOverlay(state)
        }
        GlassSideSheet(visible = state.sheetVisible, onDismiss = { viewModel.setSheetVisible(false) }, width = 460.dp) {
            Text("CHANNELS", style = AwadyType.ShelfHeader, color = AwadyColors.TextSecondary, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = (state.index - 3).coerceAtLeast(0))
            val currentFocus = remember { FocusRequester() }
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(4.dp)) {
                itemsIndexed(state.zapList, key = { _, c -> c.streamId }) { index, channel ->
                    ChannelRow(
                        channel = channel,
                        nowNext = null,
                        favourite = false,
                        now = state.now,
                        selected = index == state.index,
                        onClick = { viewModel.playAt(index) },
                        onLongClick = {},
                        onFocus = {},
                        modifier = if (index == state.index) Modifier.focusRequester(currentFocus) else Modifier,
                    )
                }
            }
            LaunchedEffect(Unit) { runCatching { currentFocus.requestFocus() } }
        }
    }
}

@Composable
private fun LiveOverlay(state: LivePlayerState) {
    val channel = state.channel ?: return
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AwadyDimens.SafeHorizontal, vertical = AwadyDimens.SafeVertical),
        shape = RoundedCornerShape(AwadyDimens.RadiusXL),
        elevation = 24.dp,
    ) {
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(width = 120.dp, height = 72.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x26FFFFFF)),
                contentAlignment = Alignment.Center,
            ) {
                Artwork(channel.icon, channel.name, Modifier.size(width = 100.dp, height = 60.dp), contentScale = ContentScale.Fit, showInitials = channel.icon.isNullOrBlank())
            }
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${channel.num}", style = AwadyType.Title, color = AwadyColors.TextSecondary)
                    Spacer(Modifier.width(14.dp))
                    Text(channel.name, style = AwadyType.Title, color = AwadyColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(10.dp))
                    Icon(if (state.favourite) Icons.Rounded.Star else Icons.Rounded.StarOutline, contentDescription = null, tint = if (state.favourite) AwadyColors.Warning else AwadyColors.TextTertiary, modifier = Modifier.size(22.dp))
                }
                val now = state.nowNext?.now
                if (now != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(now.title, style = AwadyType.BodyStrong, color = AwadyColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(12.dp))
                        Text("${TimeFormat.clock(now.start)} – ${TimeFormat.clock(now.end)}", style = AwadyType.Caption, color = AwadyColors.TextSecondary)
                    }
                    ProgressLine(now.progress(state.now), Modifier.fillMaxWidth(0.7f).padding(vertical = 4.dp))
                } else {
                    Text("No programme information", style = AwadyType.Body, color = AwadyColors.TextTertiary)
                }
                state.nowNext?.next?.let { next ->
                    Text("Next  ${TimeFormat.clock(next.start)}  ${next.title}", style = AwadyType.Caption, color = AwadyColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("▲▼ change channel   ◀ channel list   OK again for list   red: favourite", style = AwadyType.Caption, color = AwadyColors.TextTertiary)
            }
            Spacer(Modifier.width(24.dp))
            Text(TimeFormat.clock(state.now), style = AwadyType.Title, color = AwadyColors.TextPrimary)
        }
    }
}

private fun digitOf(key: Key): Int? = when (key) {
    Key.Zero, Key.NumPad0 -> 0
    Key.One, Key.NumPad1 -> 1
    Key.Two, Key.NumPad2 -> 2
    Key.Three, Key.NumPad3 -> 3
    Key.Four, Key.NumPad4 -> 4
    Key.Five, Key.NumPad5 -> 5
    Key.Six, Key.NumPad6 -> 6
    Key.Seven, Key.NumPad7 -> 7
    Key.Eight, Key.NumPad8 -> 8
    Key.Nine, Key.NumPad9 -> 9
    else -> null
}
