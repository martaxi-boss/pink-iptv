package com.pinkiptv.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pinkiptv.app.R
import com.pinkiptv.app.library.NoopPlaybackActivityRecorder
import com.pinkiptv.app.library.PlaybackActivityRecorder
import com.pinkiptv.app.model.CatalogKind
import com.pinkiptv.app.model.CatalogUiItem
import com.pinkiptv.app.model.CatalogUiState
import com.pinkiptv.app.model.CatchUpPlaybackRef
import com.pinkiptv.app.model.EpgUiState
import com.pinkiptv.app.model.FavoriteItem
import com.pinkiptv.app.model.FavoriteKind
import com.pinkiptv.app.model.LibraryUiState
import com.pinkiptv.app.model.PlaybackRef
import com.pinkiptv.app.model.SearchItem
import com.pinkiptv.app.model.SearchKind
import com.pinkiptv.app.model.SearchUiState
import com.pinkiptv.app.model.SeriesDetailUiState
import com.pinkiptv.app.model.SeriesEpisode
import com.pinkiptv.app.model.continueWatchingPositionMs
import com.pinkiptv.app.model.searchFavoriteKey
import com.pinkiptv.app.model.toPlaybackRef
import com.pinkiptv.app.player.PlaybackFacadeFactory
import com.pinkiptv.app.ui.screens.CatalogScreen
import com.pinkiptv.app.ui.screens.EpgScreen
import com.pinkiptv.app.ui.screens.HomeScreen
import com.pinkiptv.app.ui.screens.LibraryScreen
import com.pinkiptv.app.ui.screens.PlayerScreen
import com.pinkiptv.app.ui.screens.SearchScreen
import com.pinkiptv.app.ui.screens.SeriesDetailScreen
import com.pinkiptv.app.ui.screens.SettingsScreen
import com.pinkiptv.app.ui.screens.ShellScreen
import com.pinkiptv.app.vpn.VpnPreparationState

private object Routes {
    const val HOME = "home"
    const val LIVE = "live"
    const val MOVIES = "movies"
    const val SERIES = "series"
    const val SERIES_DETAIL = "series-detail"
    const val EPG = "epg"
    const val FAVORITES = "favorites"
    const val SEARCH = "search"
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
    vpnPreparationState: VpnPreparationState = VpnPreparationState(),
    onPrepareVpn: () -> Unit = {},
    searchState: SearchUiState = SearchUiState(),
    seriesDetail: SeriesDetailUiState = SeriesDetailUiState(),
    epgState: EpgUiState = EpgUiState(),
    libraryState: LibraryUiState = LibraryUiState(),
    selectedPlayback: PlaybackRef? = null,
    selectedPlaybackStartPositionMs: Long = 0L,
    playbackFacadeFactory: PlaybackFacadeFactory? = null,
    playbackActivityRecorder: PlaybackActivityRecorder = NoopPlaybackActivityRecorder,
    onLoadSearch: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onSelectSearchKind: (SearchKind) -> Unit = {},
    onRetrySearch: () -> Unit = {},
    onSelectSearchPlayback: (SearchItem) -> Unit = {},
    onOpenSearchSeries: (SearchItem) -> Unit = {},
    onToggleSearchFavorite: (SearchItem) -> Unit = {},
    onOpenSeries: (CatalogUiItem) -> Unit = {},
    onRetrySeriesDetail: () -> Unit = {},
    onSelectSeriesSeason: (String) -> Unit = {},
    onClearSeriesDetail: () -> Unit = {},
    onOpenEpg: () -> Unit = {},
    onSelectEpgChannel: (String) -> Unit = {},
    onRetryEpg: () -> Unit = {},
    onClearEpg: () -> Unit = {},
    onSelectPlayback: (CatalogUiItem) -> Unit = {},
    onSelectEpisode: (SeriesEpisode) -> Unit = {},
    onSelectCatchUp: (CatchUpPlaybackRef) -> Unit = {},
    onToggleFavorite: (CatalogKind, CatalogUiItem) -> Unit = { _, _ -> },
    onRemoveFavorite: (FavoriteItem) -> Unit = {},
    onClearHistory: () -> Unit = {},
    onOpenSeriesFavorite: (FavoriteItem) -> Unit = {},
    onSelectLibraryPlayback: (PlaybackRef, Long) -> Unit = { _, _ -> },
    onClearPlayback: () -> Unit = {},
    accountName: String? = null,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
    ) {
        composable(Routes.HOME) {
            HomeScreen(onNavigate = { route -> navController.navigate(route) }, accountName = accountName)
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
                favoriteIds = libraryState.favorites
                    .filter { it.kind == FavoriteKind.Live }
                    .mapTo(mutableSetOf()) { it.providerId },
                onToggleFavorite = { item ->
                    onToggleFavorite(CatalogKind.Live, item)
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
                favoriteIds = libraryState.favorites
                    .filter { it.kind == FavoriteKind.Movie }
                    .mapTo(mutableSetOf()) { it.providerId },
                onToggleFavorite = { item ->
                    onToggleFavorite(CatalogKind.Movies, item)
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
                onOpenItem = { item ->
                    onOpenSeries(item)
                    navController.navigate(Routes.SERIES_DETAIL)
                },
                favoriteIds = libraryState.favorites
                    .filter { it.kind == FavoriteKind.Series }
                    .mapTo(mutableSetOf()) { it.providerId },
                onToggleFavorite = { item ->
                    onToggleFavorite(CatalogKind.Series, item)
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                state = searchState,
                favoriteKeys = libraryState.favorites
                    .mapTo(mutableSetOf()) { it.searchFavoriteKey() },
                onLoad = onLoadSearch,
                onQueryChange = onSearchQueryChange,
                onSelectKind = onSelectSearchKind,
                onRetry = onRetrySearch,
                onOpenItem = { item ->
                    when (item.kind) {
                        SearchKind.Live,
                        SearchKind.Movies,
                        -> {
                            onSelectSearchPlayback(item)
                            navController.navigate(Routes.PLAYER)
                        }
                        SearchKind.Series -> {
                            onOpenSearchSeries(item)
                            navController.navigate(Routes.SERIES_DETAIL)
                        }
                        SearchKind.All -> Unit
                    }
                },
                onToggleFavorite = onToggleSearchFavorite,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SERIES_DETAIL) {
            SeriesDetailScreen(
                state = seriesDetail,
                onRetry = onRetrySeriesDetail,
                onSelectSeason = onSelectSeriesSeason,
                onOpenEpisode = { episode ->
                    onSelectEpisode(episode)
                    navController.navigate(Routes.PLAYER)
                },
                onBack = {
                    onClearSeriesDetail()
                    navController.popBackStack()
                },
            )
        }
        composable(Routes.EPG) {
            EpgScreen(
                state = epgState,
                onLoad = onOpenEpg,
                onSelectChannel = onSelectEpgChannel,
                onRetry = onRetryEpg,
                onOpenCatchUp = { ref ->
                    onSelectCatchUp(ref)
                    navController.navigate(Routes.PLAYER)
                },
                onBack = {
                    onClearEpg()
                    navController.popBackStack()
                },
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
                    startPositionMs = selectedPlaybackStartPositionMs,
                    activityRecorder = playbackActivityRecorder,
                    onBack = {
                        onClearPlayback()
                        navController.popBackStack()
                    },
                )
            }
        }
        composable(Routes.FAVORITES) {
            LibraryScreen(
                state = libraryState,
                onOpenFavorite = { item ->
                    if (item.kind == FavoriteKind.Series) {
                        onOpenSeriesFavorite(item)
                        navController.navigate(Routes.SERIES_DETAIL)
                    } else {
                        item.toPlaybackRef()?.let { ref ->
                            onSelectLibraryPlayback(ref, 0L)
                            navController.navigate(Routes.PLAYER)
                        }
                    }
                },
                onRemoveFavorite = onRemoveFavorite,
                onOpenContinue = { item ->
                    val ref = item.history.toPlaybackRef()
                    val position = item.history.continueWatchingPositionMs()
                    if (ref != null && position != null) {
                        onSelectLibraryPlayback(ref, position)
                        navController.navigate(Routes.PLAYER)
                    }
                },
                onOpenRecent = { item ->
                    item.toPlaybackRef()?.let { ref ->
                        onSelectLibraryPlayback(
                            ref,
                            item.continueWatchingPositionMs() ?: 0L,
                        )
                        navController.navigate(Routes.PLAYER)
                    }
                },
                onClearHistory = onClearHistory,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                accountName = accountName,
                vpnState = vpnPreparationState,
                onPrepareVpn = onPrepareVpn,
                onBack = { navController.popBackStack() },
                onLogout = onLogout,
            )
        }
    }
}
