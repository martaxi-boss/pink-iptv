package com.pinkiptv.app.ui

import androidx.compose.runtime.Composable
import com.pinkiptv.app.library.PlaybackActivityRecorder
import com.pinkiptv.app.model.CatalogKind
import com.pinkiptv.app.model.CatalogUiItem
import com.pinkiptv.app.model.CatalogUiState
import com.pinkiptv.app.model.CatchUpPlaybackRef
import com.pinkiptv.app.model.EpgUiState
import com.pinkiptv.app.model.FavoriteItem
import com.pinkiptv.app.model.LibraryUiState
import com.pinkiptv.app.model.PlaybackRef
import com.pinkiptv.app.model.SearchItem
import com.pinkiptv.app.model.SearchKind
import com.pinkiptv.app.model.SearchUiState
import com.pinkiptv.app.model.SeriesDetailUiState
import com.pinkiptv.app.model.SeriesEpisode
import com.pinkiptv.app.player.PlaybackFacadeFactory
import com.pinkiptv.app.state.AppUiState
import com.pinkiptv.app.state.RootScreen
import com.pinkiptv.app.ui.navigation.AuthenticatedShell
import com.pinkiptv.app.ui.screens.LoginScreen
import com.pinkiptv.app.ui.screens.SplashScreen
import com.pinkiptv.app.vpn.VpnPreparationState

@Composable
fun PinkApp(
    state: AppUiState,
    liveCatalog: CatalogUiState,
    movieCatalog: CatalogUiState,
    seriesCatalog: CatalogUiState,
    searchState: SearchUiState,
    seriesDetail: SeriesDetailUiState,
    epgState: EpgUiState,
    libraryState: LibraryUiState,
    vpnPreparationState: VpnPreparationState,
    selectedPlayback: PlaybackRef?,
    selectedPlaybackStartPositionMs: Long,
    playbackFacadeFactory: PlaybackFacadeFactory,
    playbackActivityRecorder: PlaybackActivityRecorder,
    onLogin: (String, String) -> Unit,
    onLogout: () -> Unit,
    onPrepareVpn: () -> Unit,
    onLoadCatalog: (CatalogKind) -> Unit,
    onSelectCatalogCategory: (CatalogKind, String?) -> Unit,
    onLoadSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectSearchKind: (SearchKind) -> Unit,
    onRetrySearch: () -> Unit,
    onSelectSearchPlayback: (SearchItem) -> Unit,
    onOpenSearchSeries: (SearchItem) -> Unit,
    onToggleSearchFavorite: (SearchItem) -> Unit,
    onOpenSeries: (CatalogUiItem) -> Unit,
    onRetrySeriesDetail: () -> Unit,
    onSelectSeriesSeason: (String) -> Unit,
    onClearSeriesDetail: () -> Unit,
    onOpenEpg: () -> Unit,
    onSelectEpgChannel: (String) -> Unit,
    onRetryEpg: () -> Unit,
    onClearEpg: () -> Unit,
    onSelectPlayback: (CatalogUiItem) -> Unit,
    onSelectEpisode: (SeriesEpisode) -> Unit,
    onSelectCatchUp: (CatchUpPlaybackRef) -> Unit,
    onToggleFavorite: (CatalogKind, CatalogUiItem) -> Unit,
    onRemoveFavorite: (FavoriteItem) -> Unit,
    onClearHistory: () -> Unit,
    onOpenSeriesFavorite: (FavoriteItem) -> Unit,
    onSelectLibraryPlayback: (PlaybackRef, Long) -> Unit,
    onClearPlayback: () -> Unit,
    onRetrySavedLogin: () -> Unit = {},
) {
    when (state.screen) {
        RootScreen.Splash -> SplashScreen()
        RootScreen.Login -> LoginScreen(
            loginInFlight = state.loginInFlight,
            loginError = state.loginError,
            onLogin = onLogin,
            savedAccountName = state.accountName.takeIf { state.savedAccountAvailable },
            onRetrySavedLogin = onRetrySavedLogin,
        )
        RootScreen.Home -> AuthenticatedShell(
            accountName = state.accountName,
            liveCatalog = liveCatalog,
            movieCatalog = movieCatalog,
            seriesCatalog = seriesCatalog,
            searchState = searchState,
            seriesDetail = seriesDetail,
            epgState = epgState,
            libraryState = libraryState,
            vpnPreparationState = vpnPreparationState,
            selectedPlayback = selectedPlayback,
            selectedPlaybackStartPositionMs = selectedPlaybackStartPositionMs,
            playbackFacadeFactory = playbackFacadeFactory,
            playbackActivityRecorder = playbackActivityRecorder,
            onLoadCatalog = onLoadCatalog,
            onSelectCatalogCategory = onSelectCatalogCategory,
            onLoadSearch = onLoadSearch,
            onSearchQueryChange = onSearchQueryChange,
            onSelectSearchKind = onSelectSearchKind,
            onRetrySearch = onRetrySearch,
            onSelectSearchPlayback = onSelectSearchPlayback,
            onOpenSearchSeries = onOpenSearchSeries,
            onToggleSearchFavorite = onToggleSearchFavorite,
            onOpenSeries = onOpenSeries,
            onRetrySeriesDetail = onRetrySeriesDetail,
            onSelectSeriesSeason = onSelectSeriesSeason,
            onClearSeriesDetail = onClearSeriesDetail,
            onOpenEpg = onOpenEpg,
            onSelectEpgChannel = onSelectEpgChannel,
            onRetryEpg = onRetryEpg,
            onClearEpg = onClearEpg,
            onSelectPlayback = onSelectPlayback,
            onSelectEpisode = onSelectEpisode,
            onSelectCatchUp = onSelectCatchUp,
            onToggleFavorite = onToggleFavorite,
            onRemoveFavorite = onRemoveFavorite,
            onClearHistory = onClearHistory,
            onOpenSeriesFavorite = onOpenSeriesFavorite,
            onSelectLibraryPlayback = onSelectLibraryPlayback,
            onClearPlayback = onClearPlayback,
            onLogout = onLogout,
            onPrepareVpn = onPrepareVpn,
        )
    }
}
