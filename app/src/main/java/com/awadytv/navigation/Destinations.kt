package com.awadytv.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.awadytv.feature.live.LivePlayerScreen

/** Detail and player destinations pushed on top of the tabbed shell. */
fun NavGraphBuilder.detailAndPlayerDestinations(navigator: AppNavigator) {
    composable<LivePlayerRoute> { LivePlayerScreen(navigator) }
}
