package com.awadytv.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import com.awadytv.core.designsystem.AwadyDimens
import com.awadytv.core.designsystem.AwadyMotion
import com.awadytv.core.designsystem.TabItem
import com.awadytv.core.designsystem.TopTabBar

enum class MainTab(val item: TabItem) {
    Home(TabItem("Home")),
    Live(TabItem("Live TV")),
    Movies(TabItem("Movies")),
    Series(TabItem("Series")),
    Search(TabItem("", Icons.Rounded.Search)),
    Settings(TabItem("", Icons.Rounded.Settings)),
}

/**
 * The tabbed shell: floating glass tab bar over the selected tab's content. The bar collapses while
 * focus is in the content. Back on any tab other than Home returns to Home first.
 */
@Composable
fun MainShell(
    tabContent: @Composable (MainTab) -> Unit,
    onExit: (() -> Unit)? = null,
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var contentFocused by remember { mutableStateOf(false) }
    val tab = MainTab.entries[selected]

    BackHandler(enabled = tab != MainTab.Home || onExit != null) {
        if (tab != MainTab.Home) selected = MainTab.Home.ordinal else onExit?.invoke()
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(tween(AwadyMotion.SCREEN_TRANSITION_MS)) + scaleIn(tween(AwadyMotion.SCREEN_TRANSITION_MS), initialScale = 0.98f))
                    .togetherWith(fadeOut(tween(AwadyMotion.SCREEN_TRANSITION_MS / 2)))
            },
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { contentFocused = it.hasFocus },
            label = "tabContent",
        ) { current ->
            tabContent(current)
        }
        TopTabBar(
            tabs = MainTab.entries.map { it.item },
            selectedIndex = selected,
            onSelect = { selected = it },
            collapsed = contentFocused,
            requestInitialFocus = true,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = AwadyDimens.SafeVertical),
        )
    }
}
