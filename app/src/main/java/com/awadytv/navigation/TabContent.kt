package com.awadytv.navigation

import androidx.compose.runtime.Composable
import com.awadytv.feature.live.LiveScreen
import com.awadytv.feature.movies.MoviesScreen
import com.awadytv.feature.series.SeriesScreen

@Composable
fun TabContent(tab: MainTab, navigator: AppNavigator) {
    when (tab) {
        MainTab.Live -> LiveScreen(navigator)
        MainTab.Movies -> MoviesScreen(navigator)
        MainTab.Series -> SeriesScreen(navigator)
        else -> PlaceholderTab(tab)
    }
}
