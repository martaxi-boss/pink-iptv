package com.pinkiptv.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pinkiptv.app.R
import com.pinkiptv.app.model.CatalogKind
import com.pinkiptv.app.model.CatalogUiItem
import com.pinkiptv.app.model.CatalogUiState
import com.pinkiptv.app.model.PlaybackRef
import com.pinkiptv.app.player.PlaybackFacadeFactory
import com.pinkiptv.app.ui.screens.CatalogScreen
import com.pinkiptv.app.ui.screens.HomeScreen
import com.pinkiptv.app.ui.screens.PlayerScreen
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
    const val PLAYER = "player"
}

@Composable
fun AuthenticatedShell(
    liveCatalog: CatalogUiState,
    movieCatalog: CatalogUiState,
    seriesCatalog: CatalogUiState,
    onLoadCatalog: (CatalogKind) -> Unit,
    onSelectCatalogCategory: (CatalogKind, String?) -> Unit,
    onLogout: () -> Unit,
    selectedPlayback: PlaybackRef? = null,
    playbackFacadeFactory: PlaybackFacadeFactory? = null,
    onSelectPlayback: (CatalogUiItem) -> Unit = {},
    onClearPlayback: () -> Unit = {},
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
    ) {
        composable(Routes.HOME) {
            HomeScreen(onNavigate = { route -> navController.navigate(route) })
        }
        composable(Routes.LIVE) {
            CatalogScreen(
                titleRes = R.string.live_tv,
                state = liveCatalog,
                onLoad = { onLoadCatalog(CatalogKind.Live) },
                onSelectCategory = { onSelectCatalogCategory(CatalogKind.Live, it) },
                onOpenItem = { item ->
                    onSelectPlayback(item)
                    navController.navigate(Routes.PLAYER)
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.MOVIES) {
            CatalogScreen(
                titleRes = R.string.movies,
                state = movieCatalog,
                onLoad = { onLoadCatalog(CatalogKind.Movies) },
                onSelectCategory = { onSelectCatalogCategory(CatalogKind.Movies, it) },
                onOpenItem = { item ->
                    onSelectPlayback(item)
                    navController.navigate(Routes.PLAYER)
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SERIES) {
            CatalogScreen(
                titleRes = R.string.series,
                state = seriesCatalog,
                onLoad = { onLoadCatalog(CatalogKind.Series) },
                onSelectCategory = { onSelectCatalogCategory(CatalogKind.Series, it) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.PLAYER) {
            val factory = playbackFacadeFactory
            if (factory == null) {
                ShellScreen(
                    titleRes = R.string.next_phase,
                    onBack = {
                        onClearPlayback()
                        navController.popBackStack()
                    },
                )
            } else {
                PlayerScreen(
                    playbackRef = selectedPlayback,
                    facadeFactory = factory,
                    onBack = {
                        onClearPlayback()
                        navController.popBackStack()
                    },
                )
            }
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
