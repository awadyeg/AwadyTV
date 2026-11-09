package com.awadytv.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

data class TabItem(val label: String, val icon: ImageVector? = null)

/**
 * tvOS pill tab bar: one floating glass capsule; the selected/focused tab is a brighter pill that
 * slides between items. Focusing a tab selects it (after a short dwell, like tvOS). When focus is in the
 * content below the bar collapses to a thin line, and it re-expands as soon as D-pad Up re-enters it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TopTabBar(
    tabs: List<TabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    collapsed: Boolean,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
    requestInitialFocus: Boolean = false,
    /** Where D-pad Down from any tab goes. Full-bleed content that starts under the bar is not "below" it for focus search. */
    contentFocusRequester: FocusRequester? = null,
) {
    val requesters = remember(tabs.size) { List(tabs.size) { FocusRequester() } }
    val bounds = remember { mutableStateMapOf<Int, Pair<Dp, Dp>>() }
    var focusedIndex by remember { mutableIntStateOf(-1) }
    var barFocused by remember { mutableStateOf(false) }
    val currentSelected by rememberUpdatedState(selectedIndex)
    val density = LocalDensity.current

    val pillIndex = if (focusedIndex >= 0) focusedIndex else selectedIndex
    val target = bounds[pillIndex]
    val pillX by animateDpAsState(target?.first ?: 0.dp, tween(AwadyMotion.FOCUS_MS), label = "pillX")
    val pillW by animateDpAsState(target?.second ?: 0.dp, tween(AwadyMotion.FOCUS_MS), label = "pillW")
    val isCollapsed = collapsed && !barFocused
    val height by animateDpAsState(if (isCollapsed) AwadyDimens.TabBarCollapsedHeight else AwadyDimens.TabBarHeight, tween(250), label = "barHeight")
    val labelAlpha by animateFloatAsState(if (isCollapsed) 0f else 1f, tween(180), label = "labelAlpha")
    val pillColor by animateColorAsState(if (focusedIndex >= 0) AwadyColors.GlassFocused else Color(0x40FFFFFF), tween(150), label = "pillColor")

    // Deterministic first focus: the selected tab, like tvOS.
    LaunchedEffect(Unit) {
        if (requestInitialFocus) runCatching { requesters.getOrNull(selectedIndex)?.requestFocus() }
    }

    // Dwell-to-select: moving quickly across tabs does not load every screen on the way.
    LaunchedEffect(focusedIndex) {
        val idx = focusedIndex
        if (idx >= 0 && idx != currentSelected) {
            delay(350)
            onSelect(idx)
        }
    }

    GlassSurface(
        modifier = modifier.height(height),
        shape = RoundedCornerShape(percent = 50),
        elevation = if (isCollapsed) 2.dp else 16.dp,
    ) {
        Box(Modifier.wrapContentHeight(unbounded = true, align = Alignment.Top)) {
            if (target != null) {
                Box(
                    Modifier
                        .offset { IntOffset(pillX.roundToPx(), 0) }
                        .padding(vertical = 6.dp)
                        .width(pillW)
                        .height(AwadyDimens.TabBarHeight - 12.dp)
                        .graphicsLayer { alpha = if (isCollapsed) 0f else 1f }
                        .background(pillColor, RoundedCornerShape(percent = 50)),
                )
            }
            Row(
                Modifier
                    .height(AwadyDimens.TabBarHeight)
                    .padding(horizontal = 6.dp)
                    .focusProperties { enter = { requesters.getOrElse(currentSelected) { FocusRequester.Default } } }
                    .onFocusChanged {
                        barFocused = it.hasFocus
                        onFocusChanged(it.hasFocus)
                        if (!it.hasFocus) focusedIndex = -1
                    }
                    .focusGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { index, tab ->
                    val interaction = remember { MutableInteractionSource() }
                    val focused by interaction.collectIsFocusedAsState()
                    LaunchedEffect(focused) { if (focused) focusedIndex = index }
                    val color = when {
                        focused -> AwadyColors.TextOnFocus
                        index == selectedIndex -> AwadyColors.TextPrimary
                        else -> AwadyColors.TextSecondary
                    }
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .onGloballyPositioned {
                                with(density) {
                                    bounds[index] = it.positionInParent().x.toDp() + 6.dp to it.size.width.toDp()
                                }
                            }
                            .focusRequester(requesters[index])
                            .focusProperties { if (contentFocusRequester != null) down = contentFocusRequester }
                            .clickable(interactionSource = interaction, indication = null) { onSelect(index) }
                            .padding(horizontal = 22.dp)
                            .graphicsLayer { alpha = labelAlpha },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (tab.icon != null && tab.label.isEmpty()) {
                            Icon(tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                        } else {
                            Text(tab.label, style = AwadyType.Label, color = color, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
