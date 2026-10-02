package com.pinkiptv.app

import android.app.Activity
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pinkiptv.app.state.AppViewModel
import com.pinkiptv.app.ui.PinkApp
import com.pinkiptv.app.ui.theme.PinkTheme
import com.pinkiptv.app.vpn.VpnPrepareAction

class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels {
        val container = (application as PinkApplication).container
        AppViewModel.Factory(
            repository = container.sessionRepository,
            credentialStore = container.credentialStore,
            catalogRepository = container.catalogRepository,
            providerSessionStore = container.providerSessionStore,
            localLibraryRepository = container.localLibraryRepository,
            activeLibraryProfileStore = container.activeLibraryProfileStore,
            playbackActivityRecorder = container.playbackActivityRecorder,
            vpnIdentityStore = container.vpnIdentityStore,
            vpnPermissionGateway = container.vpnPermissionGateway,
        )
    }

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            appViewModel.onVpnPermissionResult(result.resultCode == Activity.RESULT_OK)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as PinkApplication).container

        setContent {
            PinkTheme {
                val state by appViewModel.uiState.collectAsStateWithLifecycle()
                val liveCatalog by appViewModel.liveCatalog.collectAsStateWithLifecycle()
                val movieCatalog by appViewModel.movieCatalog.collectAsStateWithLifecycle()
                val seriesCatalog by appViewModel.seriesCatalog.collectAsStateWithLifecycle()
                val searchState by appViewModel.search.collectAsStateWithLifecycle()
                val seriesDetail by appViewModel.seriesDetail.collectAsStateWithLifecycle()
                val epgState by appViewModel.epg.collectAsStateWithLifecycle()
                val libraryState by appViewModel.library.collectAsStateWithLifecycle()
                val vpnPreparationState by appViewModel.vpnPreparation.collectAsStateWithLifecycle()
                val selectedPlayback by appViewModel.selectedPlayback.collectAsStateWithLifecycle()
                val selectedPlaybackStartPositionMs by
                    appViewModel.selectedPlaybackStartPositionMs.collectAsStateWithLifecycle()

                PinkApp(
                    state = state,
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
                    playbackFacadeFactory = container.playbackFacadeFactory,
                    playbackActivityRecorder = container.playbackActivityRecorder,
                    onLogin = appViewModel::login,
                    onLogout = appViewModel::logout,
                    onPrepareVpn = {
                        when (appViewModel.prepareVpn()) {
                            VpnPrepareAction.None -> Unit
                            VpnPrepareAction.LaunchSystemPermission -> {
                                val intent = container.vpnPermissionGateway.takePendingIntent()
                                if (intent == null) {
                                    appViewModel.onVpnPermissionLaunchFailed()
                                } else {
                                    vpnPermissionLauncher.launch(intent)
                                }
                            }
                        }
                    },
                    onLoadCatalog = appViewModel::loadCatalog,
                    onSelectCatalogCategory = appViewModel::selectCatalogCategory,
                    onLoadSearch = appViewModel::loadSearch,
                    onSearchQueryChange = appViewModel::updateSearchQuery,
                    onSelectSearchKind = appViewModel::selectSearchKind,
                    onRetrySearch = appViewModel::retrySearch,
                    onSelectSearchPlayback = appViewModel::selectSearchPlayback,
                    onOpenSearchSeries = appViewModel::openSearchSeries,
                    onToggleSearchFavorite = appViewModel::toggleSearchFavorite,
                    onOpenSeries = appViewModel::openSeries,
                    onRetrySeriesDetail = appViewModel::retrySeriesDetail,
                    onSelectSeriesSeason = appViewModel::selectSeriesSeason,
                    onClearSeriesDetail = appViewModel::clearSeriesDetail,
                    onOpenEpg = appViewModel::openEpg,
                    onSelectEpgChannel = appViewModel::selectEpgChannel,
                    onRetryEpg = appViewModel::retryEpg,
                    onClearEpg = appViewModel::clearEpg,
                    onSelectPlayback = appViewModel::selectPlayback,
                    onSelectEpisode = appViewModel::selectEpisode,
                    onSelectCatchUp = appViewModel::selectCatchUp,
                    onToggleFavorite = appViewModel::toggleFavorite,
                    onRemoveFavorite = appViewModel::removeFavorite,
                    onClearHistory = appViewModel::clearHistory,
                    onOpenSeriesFavorite = appViewModel::openSeriesFavorite,
                    onSelectLibraryPlayback = appViewModel::selectLibraryPlayback,
                    onClearPlayback = appViewModel::clearPlayback,
                )
            }
        }
    }
}
