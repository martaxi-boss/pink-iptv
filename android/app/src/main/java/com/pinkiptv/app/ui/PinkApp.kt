package com.pinkiptv.app.ui

import androidx.compose.runtime.Composable
import com.pinkiptv.app.model.CatalogKind
import com.pinkiptv.app.model.CatalogUiItem
import com.pinkiptv.app.model.CatalogUiState
import com.pinkiptv.app.model.PlaybackRef
import com.pinkiptv.app.model.SeriesDetailUiState
import com.pinkiptv.app.model.SeriesEpisode
import com.pinkiptv.app.player.PlaybackFacadeFactory
import com.pinkiptv.app.state.AppUiState
import com.pinkiptv.app.state.RootScreen
import com.pinkiptv.app.ui.navigation.AuthenticatedShell
import com.pinkiptv.app.ui.screens.LoginScreen
import com.pinkiptv.app.ui.screens.SplashScreen

@Composable
fun PinkApp(
    state: AppUiState,
    liveCatalog: CatalogUiState,
    movieCatalog: CatalogUiState,
    seriesCatalog: CatalogUiState,
    seriesDetail: SeriesDetailUiState,
    selectedPlayback: PlaybackRef?,
    playbackFacadeFactory: PlaybackFacadeFactory,
    onLogin: (String, String) -> Unit,
    onLogout: () -> Unit,
    onLoadCatalog: (CatalogKind) -> Unit,
    onSelectCatalogCategory: (CatalogKind, String?) -> Unit,
    onOpenSeries: (CatalogUiItem) -> Unit,
    onRetrySeriesDetail: () -> Unit,
    onSelectSeriesSeason: (String) -> Unit,
    onClearSeriesDetail: () -> Unit,
    onSelectPlayback: (CatalogUiItem) -> Unit,
    onSelectEpisode: (SeriesEpisode) -> Unit,
    onClearPlayback: () -> Unit,
) {
    when (state.screen) {
        RootScreen.Splash -> SplashScreen()
        RootScreen.Login -> LoginScreen(
            loginInFlight = state.loginInFlight,
            loginError = state.loginError,
            onLogin = onLogin,
        )
        RootScreen.Home -> AuthenticatedShell(
            liveCatalog = liveCatalog,
            movieCatalog = movieCatalog,
            seriesCatalog = seriesCatalog,
            seriesDetail = seriesDetail,
            selectedPlayback = selectedPlayback,
            playbackFacadeFactory = playbackFacadeFactory,
            onLoadCatalog = onLoadCatalog,
            onSelectCatalogCategory = onSelectCatalogCategory,
            onOpenSeries = onOpenSeries,
            onRetrySeriesDetail = onRetrySeriesDetail,
            onSelectSeriesSeason = onSelectSeriesSeason,
            onClearSeriesDetail = onClearSeriesDetail,
            onSelectPlayback = onSelectPlayback,
            onSelectEpisode = onSelectEpisode,
            onClearPlayback = onClearPlayback,
            onLogout = onLogout,
        )
    }
}
