package com.pinkiptv.app.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pinkiptv.app.model.CatalogKind
import com.pinkiptv.app.model.CatalogRepository
import com.pinkiptv.app.model.CatalogUiItem
import com.pinkiptv.app.model.EpisodePlaybackRef
import com.pinkiptv.app.model.RuntimeProviderSessionStore
import com.pinkiptv.app.model.SeriesEpisode
import com.pinkiptv.app.model.SessionRepository
import com.pinkiptv.app.storage.CredentialStore
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class AppViewModel(
    repository: SessionRepository,
    credentialStore: CredentialStore,
    catalogRepository: CatalogRepository,
    providerSessionStore: RuntimeProviderSessionStore,
) : ViewModel() {
    private val sessionController = SessionController(
        repository = repository,
        credentialStore = credentialStore,
        scope = viewModelScope,
        providerSessionStore = providerSessionStore,
    )
    private val catalogController = CatalogController(
        repository = catalogRepository,
        scope = viewModelScope,
    )
    private val seriesDetailController = SeriesDetailController(
        repository = catalogRepository,
        scope = viewModelScope,
    )
    private val playbackSelectionController = PlaybackSelectionController()

    val uiState = sessionController.state
    val liveCatalog = catalogController.live
    val movieCatalog = catalogController.movies
    val seriesCatalog = catalogController.series
    val seriesDetail = seriesDetailController.state
    val selectedPlayback = playbackSelectionController.selection

    init {
        viewModelScope.launch {
            providerSessionStore.available.collect { available ->
                if (!available) {
                    playbackSelectionController.clear()
                    seriesDetailController.clear()
                }
            }
        }
    }

    fun login(username: String, password: String) {
        sessionController.login(username, password)
    }

    fun logout() {
        playbackSelectionController.clear()
        seriesDetailController.clear()
        catalogController.clear()
        sessionController.logout()
    }

    fun loadCatalog(kind: CatalogKind) {
        catalogController.load(kind)
    }

    fun selectCatalogCategory(kind: CatalogKind, categoryId: String?) {
        catalogController.selectCategory(kind, categoryId)
    }

    fun openSeries(item: CatalogUiItem) {
        seriesDetailController.openSeries(item)
    }

    fun retrySeriesDetail() {
        seriesDetailController.retry()
    }

    fun selectSeriesSeason(seasonId: String) {
        seriesDetailController.selectSeason(seasonId)
    }

    fun clearSeriesDetail() {
        seriesDetailController.clear()
    }

    fun selectPlayback(item: CatalogUiItem) {
        playbackSelectionController.select(item)
    }

    fun selectEpisode(episode: SeriesEpisode) {
        playbackSelectionController.select(
            EpisodePlaybackRef(
                episodeId = episode.episodeId,
                title = episode.title,
                containerExtension = episode.containerExtension,
                artworkUrl = episode.artworkUrl,
            ),
        )
    }

    fun clearPlayback() {
        playbackSelectionController.clear()
    }

    class Factory(
        private val repository: SessionRepository,
        private val credentialStore: CredentialStore,
        private val catalogRepository: CatalogRepository,
        private val providerSessionStore: RuntimeProviderSessionStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AppViewModel::class.java))
            return AppViewModel(
                repository = repository,
                credentialStore = credentialStore,
                catalogRepository = catalogRepository,
                providerSessionStore = providerSessionStore,
            ) as T
        }
    }
}
