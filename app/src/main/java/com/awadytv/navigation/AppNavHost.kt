package com.awadytv.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.awadytv.core.designsystem.AwadyMotion
import com.awadytv.feature.login.LoginScreen
import com.awadytv.feature.login.SyncScreen
import com.awadytv.work.BackgroundWork

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    val navigator = remember(navController) { NavControllerNavigator(navController) }
    val enter = fadeIn(tween(AwadyMotion.SCREEN_TRANSITION_MS)) + scaleIn(tween(AwadyMotion.SCREEN_TRANSITION_MS), initialScale = 0.98f)
    val exit = fadeOut(tween(AwadyMotion.SCREEN_TRANSITION_MS / 2))
    NavHost(
        navController = navController,
        startDestination = StartRoute,
        enterTransition = { enter },
        exitTransition = { exit },
        popEnterTransition = { enter },
        popExitTransition = { exit },
    ) {
        composable<StartRoute> {
            StartScreen(
                onResolved = { decision ->
                    navController.navigate(decision.destination) { popUpTo(StartRoute) { inclusive = true } }
                    decision.resume?.let { navController.navigate(it) }
                },
            )
        }
        composable<LoginRoute> {
            LoginScreen(onSignedIn = { id, needsSync -> navigator.afterSignIn(id, needsSync) })
        }
        composable<SyncRoute> {
            SyncScreen(
                onDone = { navigator.resetTo(MainRoute) },
                onBackToLogin = { navigator.resetTo(LoginRoute()) },
            )
        }
        composable<MainRoute> {
            val context = LocalContext.current
            LaunchedEffect(Unit) { BackgroundWork.refreshCatalogIfStale(context) }
            MainShell(tabContent = { tab, openKeyboard, onKeyboardOpened -> TabContent(tab, navigator, openKeyboard, onKeyboardOpened) })
        }
        detailAndPlayerDestinations(navigator)
    }
}

class NavControllerNavigator(private val nav: NavHostController) : AppNavigator {
    fun resetTo(route: Any) = nav.navigate(route) { popUpTo(nav.graph.id) { inclusive = true } }

    fun afterSignIn(profileId: Long, needsSync: Boolean) = resetTo(if (needsSync) SyncRoute(profileId) else MainRoute)

    override fun openMovie(streamId: Long) = nav.navigate(MovieDetailRoute(streamId)) { launchSingleTop = true }
    override fun openSeries(seriesId: Long) = nav.navigate(SeriesDetailRoute(seriesId)) { launchSingleTop = true }
    override fun playMovie(streamId: Long, fromStart: Boolean) = nav.navigate(VodPlayerRoute("movie", streamId, fromStart = fromStart))
    override fun playEpisode(seriesId: Long, episodeId: Long, fromStart: Boolean) =
        nav.navigate(VodPlayerRoute("episode", episodeId, seriesId, fromStart))
    override fun playLive(streamId: Long, categoryId: String?) = nav.navigate(LivePlayerRoute(streamId, categoryId))
    override fun openTasteTrainer() = nav.navigate(TasteTrainerRoute) { launchSingleTop = true }
    override fun addProfile() = nav.navigate(LoginRoute(addProfile = true))
    override fun resync(profileId: Long) = resetTo(SyncRoute(profileId))
    override fun signedOut() = resetTo(LoginRoute())
    override fun restartHome() = resetTo(MainRoute)
    override fun back() {
        nav.popBackStack()
    }
}
