package com.awadytv.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text

/**
 * A horizontal shelf: uppercase tracked header and a lazily composed row of cards.
 * The row restores focus to the last focused card. No enter animation: fading a whole shelf in needs an
 * offscreen alpha layer, which caused the slowest frames while scrolling on TV GPUs.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun <T> Shelf(
    title: String,
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    capItems: Boolean = true,
    itemContent: @Composable (T) -> Unit,
) {
    val performance = LocalPerformance.current
    val visible = if (capItems) items.take(performance.shelfItemCap) else items
    var rowFocused by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxWidth(),
    ) {
        Text(
            title.uppercase(),
            style = AwadyType.ShelfHeader,
            color = if (rowFocused) AwadyColors.TextPrimary else AwadyColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = AwadyDimens.SafeHorizontal),
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = AwadyType.Caption,
                color = AwadyColors.TextTertiary,
                modifier = Modifier.padding(horizontal = AwadyDimens.SafeHorizontal, vertical = 2.dp),
            )
        }
        CompositionLocalProvider(LocalShelfFocused provides rowFocused) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRestorer()
                    .onFocusChanged { rowFocused = it.hasFocus },
                contentPadding = PaddingValues(horizontal = AwadyDimens.SafeHorizontal, vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(AwadyDimens.ShelfSpacing),
            ) {
                items(visible, key = key) { item -> itemContent(item) }
            }
        }
    }
}
