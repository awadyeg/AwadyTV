package com.awadytv.feature.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.awadytv.core.designsystem.AwadyColors
import com.awadytv.core.designsystem.AwadyDimens
import com.awadytv.core.designsystem.AwadyType
import com.awadytv.core.designsystem.GlassSurface
import com.awadytv.core.designsystem.LoadingView
import com.awadytv.core.designsystem.RailItem
import com.awadytv.domain.Category

/**
 * Category rail of Live TV, Movies and Series. Hold OK on a category to pick it up, move it with
 * Up/Down, and press OK (or Back) to drop it; [onMove] saves each step.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CategoryRail(
    title: String,
    loading: Boolean,
    categories: List<Category>,
    selectedId: String?,
    adultLocked: Boolean,
    onSelect: (String) -> Unit,
    onDwell: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var moving by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = moving != null) { moving = null }
    val listState = rememberLazyListState()
    // Entering the rail (e.g. Down from the tab bar) lands on the selected category, not the first one,
    // which would otherwise get selected on focus and swap the list.
    val selectedFocus = remember { FocusRequester() }

    // Keep the category being moved on screen.
    LaunchedEffect(categories, moving) {
        val id = moving ?: return@LaunchedEffect
        val index = categories.indexOfFirst { it.id == id }
        val visible = listState.layoutInfo.visibleItemsInfo
        if (index >= 0 && visible.isNotEmpty() && (index <= visible.first().index || index >= visible.last().index)) {
            listState.animateScrollToItem((index - 2).coerceAtLeast(0))
        }
    }

    GlassSurface(modifier, shape = RoundedCornerShape(AwadyDimens.RadiusXL)) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Text(title.uppercase(), style = AwadyType.ShelfHeader, color = AwadyColors.TextSecondary, modifier = Modifier.padding(start = 12.dp, top = 8.dp))
            Text(
                if (moving != null) "▲▼ move   OK done" else "Hold OK to reorder",
                style = AwadyType.Caption,
                color = if (moving != null) AwadyColors.AccentSoft else AwadyColors.TextTertiary,
                modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 8.dp),
            )
            if (loading) {
                LoadingView()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().focusRestorer(selectedFocus),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(4.dp),
                ) {
                    items(categories, key = { it.id }) { category ->
                        val locked = category.isAdult && adultLocked
                        val isMoving = moving == category.id
                        RailItem(
                            title = category.name,
                            selected = category.id == selectedId,
                            locked = locked,
                            moving = isMoving,
                            focusRequester = if (category.id == selectedId) selectedFocus else null,
                            onClick = { if (moving == null) onSelect(category.id) },
                            onLongClick = { moving = category.id },
                            onFocus = { if (!locked && moving == null) onDwell(category.id) },
                            modifier = Modifier
                                .animateItem()
                                .then(
                                    if (!isMoving) {
                                        Modifier
                                    } else {
                                        Modifier.onPreviewKeyEvent { event ->
                                            when (event.key) {
                                                Key.DirectionUp -> { if (event.type == KeyEventType.KeyDown) onMove(category.id, -1); true }
                                                Key.DirectionDown -> { if (event.type == KeyEventType.KeyDown) onMove(category.id, +1); true }
                                                Key.DirectionLeft, Key.DirectionRight -> true
                                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                                    // Drop on press; the release of the long press that picked it up is swallowed.
                                                    if (event.type == KeyEventType.KeyDown) moving = null
                                                    true
                                                }
                                                else -> false
                                            }
                                        }
                                    },
                                ),
                        )
                    }
                }
            }
        }
    }
}
