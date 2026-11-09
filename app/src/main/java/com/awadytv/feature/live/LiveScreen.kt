package com.awadytv.feature.live

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.awadytv.core.designsystem.Artwork
import com.awadytv.core.designsystem.ChannelLogo
import com.awadytv.core.designsystem.AwadyColors
import com.awadytv.core.designsystem.AwadyDimens
import com.awadytv.core.designsystem.AwadyType
import com.awadytv.core.designsystem.EmptyView
import com.awadytv.core.designsystem.GlassListRow
import com.awadytv.core.designsystem.GlassSurface
import com.awadytv.core.designsystem.LoadingView
import com.awadytv.core.designsystem.LocalBackdrop
import com.awadytv.core.designsystem.PinDialog
import com.awadytv.core.designsystem.ProgressLine
import com.awadytv.core.designsystem.RailItem
import com.awadytv.core.util.TimeFormat
import com.awadytv.data.repository.LiveRepository
import com.awadytv.domain.Channel
import com.awadytv.domain.NowNext
import com.awadytv.feature.common.CategoryRail
import com.awadytv.navigation.AppNavigator
import kotlinx.coroutines.delay

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LiveScreen(navigator: AppNavigator, viewModel: LiveViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val backdrop = LocalBackdrop.current
    var dwellCategory by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(dwellCategory) {
        val id = dwellCategory ?: return@LaunchedEffect
        delay(400)
        viewModel.select(id)
    }

    Row(
        Modifier
            .fillMaxSize()
            .padding(start = AwadyDimens.SafeHorizontal, end = AwadyDimens.SafeHorizontal, top = AwadyDimens.ContentTop, bottom = AwadyDimens.SafeVertical),
    ) {
        CategoryRail(
            title = "Live TV",
            loading = state.loadingCategories,
            categories = state.categories,
            selectedId = state.selectedCategoryId,
            adultLocked = state.adultLocked,
            onSelect = viewModel::select,
            onDwell = { dwellCategory = it },
            onMove = viewModel::moveCategory,
            modifier = Modifier.width(AwadyDimens.SideRailWidth).fillMaxHeight(),
        )
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            val title = state.categories.firstOrNull { it.id == state.selectedCategoryId }?.name ?: "Channels"
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)) {
                Text(title, style = AwadyType.Title, color = AwadyColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(12.dp))
                Text("${state.channels.size} channels  ·  hold OK to favourite", style = AwadyType.Caption, color = AwadyColors.TextTertiary, modifier = Modifier.padding(bottom = 5.dp))
            }
            when {
                state.loadingChannels -> LoadingView(message = "Loading channels")
                state.channels.isEmpty() && state.selectedCategoryId == LiveRepository.FAVOURITES ->
                    EmptyView("No favourite channels yet", "Hold OK on any channel to add it to your favourites.", icon = Icons.Rounded.Star)
                state.channels.isEmpty() -> EmptyView("No channels here", "This category is empty on your provider.", icon = Icons.Rounded.LiveTv)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().focusRestorer(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
                ) {
                    items(state.channels, key = { it.streamId }) { channel ->
                        ChannelRow(
                            channel = channel,
                            nowNext = channel.epgChannelId?.let { state.epg[it] },
                            favourite = channel.streamId in state.favourites,
                            now = state.now,
                            onClick = { navigator.playLive(channel.streamId, state.selectedCategoryId) },
                            onLongClick = { viewModel.toggleFavourite(channel) },
                            onFocus = {
                                viewModel.onChannelFocused(channel)
                                backdrop.show(channel.icon)
                            },
                        )
                    }
                }
            }
        }
    }

    state.pinPromptFor?.let { category ->
        PinDialog(title = "Unlock ${category.name}", onDismiss = viewModel::dismissPin, onSubmit = viewModel::submitPin)
    }
}

@Composable
fun ChannelRow(
    channel: Channel,
    nowNext: NowNext?,
    favourite: Boolean,
    now: Long,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    GlassListRow(onClick = onClick, onLongClick = onLongClick, onFocus = onFocus, selected = selected, modifier = modifier.fillMaxWidth()) { focused ->
        val primary = if (focused) AwadyColors.TextOnFocus else AwadyColors.TextPrimary
        val secondary = if (focused) AwadyColors.TextOnFocus.copy(alpha = 0.7f) else AwadyColors.TextSecondary
        val tertiary = if (focused) AwadyColors.TextOnFocus.copy(alpha = 0.5f) else AwadyColors.TextTertiary
        ChannelLogo(channel.icon, channel.name, Modifier.size(width = 84.dp, height = 52.dp), padding = 5.dp)
        Spacer(Modifier.width(14.dp))
        Text(channel.num.toString(), style = AwadyType.Label, color = secondary, modifier = Modifier.width(44.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(channel.name, style = AwadyType.Label, color = primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (favourite) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.Star, contentDescription = "Favourite", tint = if (focused) primary else AwadyColors.Warning, modifier = Modifier.size(16.dp))
                }
            }
            val current = nowNext?.now
            if (current != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(current.title, style = AwadyType.Caption, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(10.dp))
                    ProgressLine(current.progress(now), Modifier.width(90.dp), height = 3.dp, color = primary)
                }
            } else {
                Text("No programme information", style = AwadyType.Caption, color = tertiary, maxLines = 1)
            }
            nowNext?.next?.let { next ->
                Text("Next  ${TimeFormat.clock(next.start)}  ${next.title}", style = AwadyType.Caption, color = tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    Spacer(Modifier.height(0.dp))
}
