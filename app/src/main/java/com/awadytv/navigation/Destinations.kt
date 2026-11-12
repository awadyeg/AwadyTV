package com.awadytv.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.awadytv.feature.live.LivePlayerScreen
import com.awadytv.feature.movies.MovieDetailScreen
import com.awadytv.feature.player.VodPlayerScreen
import com.awadytv.feature.series.SeriesDetailScreen

/** Detail and player destinations pushed on top of the tabbed shell. */
fun NavGraphBuilder.detailAndPlayerDestinations(navigator: AppNavigator) {
    composable<LivePlayerRoute> { LivePlayerScreen(navigator) }
    composable<MovieDetailRoute> { MovieDetailScreen(navigator) }
    composable<SeriesDetailRoute> { SeriesDetailScreen(navigator) }
    composable<VodPlayerRoute> { VodPlayerScreen(navigator) }
    composable<TasteTrainerRoute> { com.awadytv.feature.taste.TasteTrainerScreen(navigator) }
}
