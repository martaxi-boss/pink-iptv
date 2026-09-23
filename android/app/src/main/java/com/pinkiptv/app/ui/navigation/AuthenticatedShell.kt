package com.pinkiptv.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pinkiptv.app.R
import com.pinkiptv.app.ui.screens.HomeScreen
import com.pinkiptv.app.ui.screens.SettingsScreen
import com.pinkiptv.app.ui.screens.ShellScreen

private object Routes {
    const val HOME = "home"
    const val LIVE = "live"
    const val MOVIES = "movies"
    const val SERIES = "series"
    const val EPG = "epg"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
}

@Composable
fun AuthenticatedShell(onLogout: () -> Unit) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
    ) {
        composable(Routes.HOME) {
            HomeScreen(onNavigate = { route -> navController.navigate(route) })
        }
        composable(Routes.LIVE) {
            ShellScreen(R.string.live_tv, onBack = { navController.popBackStack() })
        }
        composable(Routes.MOVIES) {
            ShellScreen(R.string.movies, onBack = { navController.popBackStack() })
        }
        composable(Routes.SERIES) {
            ShellScreen(R.string.series, onBack = { navController.popBackStack() })
        }
        composable(Routes.EPG) {
            ShellScreen(R.string.epg, onBack = { navController.popBackStack() })
        }
        composable(Routes.FAVORITES) {
            ShellScreen(R.string.favorites, onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLogout = onLogout,
            )
        }
    }
}
