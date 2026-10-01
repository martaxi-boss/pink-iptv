package com.pinkiptv.app.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pinkiptv.app.library.ActiveLibraryProfileStore
import com.pinkiptv.app.library.LocalLibraryRepository
import com.pinkiptv.app.library.PlaybackActivityRecorder
import com.pinkiptv.app.model.CatalogKind
import com.pinkiptv.app.model.CatalogRepository
import com.pinkiptv.app.model.CatalogUiItem
import com.pinkiptv.app.model.CatchUpPlaybackRef
import com.pinkiptv.app.model.EpisodePlaybackRef
import com.pinkiptv.app.model.FavoriteItem
import com.pinkiptv.app.model.HistoryItem
import com.pinkiptv.app.model.PlaybackRef
import com.pinkiptv.app.model.RuntimeProviderSessionStore
import com.pinkiptv.app.model.SearchItem
import com.pinkiptv.app.model.SearchKind
import com.pinkiptv.app.model.SeriesEpisode
import com.pinkiptv.app.model.SessionRepository
import com.pinkiptv.app.model.continueWatchingPositionMs
import com.pinkiptv.app.model.toCatalogKindOrNull
import com.pinkiptv.app.model.toCatalogUiItem
import com.pinkiptv.app.model.toPlaybackRef
import com.pinkiptv.app.storage.CredentialStore
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class AppViewModel(
    repository: SessionRepository,
    credentialStore: CredentialStore,
    catalogRepository: CatalogRepository,
    providerSessionStore: RuntimeProviderSessionStore,
    localLibraryRepository: LocalLibraryRepository,
    activeLibraryProfileStore: ActiveLibraryProfileStore,
    private val playbackActivityRecorder: PlaybackActivityRecorder,
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
    private val searchController = SearchController(
        repository = catalogRepository,
        providerSessionStore = providerSessionStore,
        scope = viewModelScope,
    )
    private val seriesDetailController = SeriesDetailController(
        repository = catalogRepository,
        scope = viewModelScope,
    )
    private val epgController = EpgController(
        repository = catalogRepository,
        scope = viewModelScope,
    )
    private val playbackSelectionController = PlaybackSelectionController()
    private val libraryController = LibraryController(
        repository = localLibraryRepository,
        providerSessionStore = providerSessionStore,
        profileStore = activeLibraryProfileStore,
        scope = viewModelScope,
    )

    val uiState = sessionController.state
    val liveCatalog = catalogController.live
    val movieCatalog = catalogController.movies
    val seriesCatalog = catalogController.series
    val search = searchController.state
    val seriesDetail = seriesDetailController.state
    val epg = epgController.state
    val selectedPlayback = playbackSelectionController.selection
    val selectedPlaybackStartPositionMs = playbackSelectionController.resumePositionMs
    val library = libraryController.state

    init {
        viewModelScope.launch {
            providerSessionStore.available.collect { available ->
                if (!available) {
                    playbackActivityRecorder.clearSession()
                    playbackSelectionController.clear()
                    seriesDetailController.clear()
                    epgController.clear()
                    searchController.clear()
                    libraryController.clearVisible()
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
        epgController.clear()
        catalogController.clear()
        searchController.clear()
        libraryController.clearVisible()
        playbackActivityRecorder.clearSession()
        sessionController.logout()
    }

    fun loadCatalog(kind: CatalogKind) {
        catalogController.load(kind)
    }

    fun selectCatalogCategory(kind: CatalogKind, categoryId: String?) {
        catalogController.selectCategory(kind, categoryId)
    }

    fun loadSearch() {
        searchController.load()
    }

    fun updateSearchQuery(query: String) {
        searchController.updateQuery(query)
    }

    fun selectSearchKind(kind: SearchKind) {
        searchController.selectKind(kind)
    }

    fun retrySearch() {
        searchController.retry()
    }

    fun selectSearchPlayback(item: SearchItem) {
        item.playbackRef?.let(playbackSelectionController::select)
    }

    fun openSearchSeries(item: SearchItem) {
        if (item.kind != SearchKind.Series) return
        seriesDetailController.openSeriesById(item.providerId, item.title)
    }

    fun toggleSearchFavorite(item: SearchItem) {
        val kind = item.kind.toCatalogKindOrNull() ?: return
        libraryController.toggleFavorite(kind, item.toCatalogUiItem())
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

    fun openEpg() {
        epgController.open()
    }

    fun selectEpgChannel(streamId: String) {
        epgController.selectChannel(streamId)
    }

    fun retryEpg() {
        epgController.retry()
    }

    fun clearEpg() {
        epgController.clear()
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

    fun selectCatchUp(ref: CatchUpPlaybackRef) {
        playbackSelectionController.select(ref)
    }

    fun selectLibraryPlayback(
        ref: PlaybackRef,
        startPositionMs: Long = 0L,
    ) {
        playbackSelectionController.select(ref, startPositionMs)
    }

    fun toggleFavorite(
        kind: CatalogKind,
        item: CatalogUiItem,
    ) {
        libraryController.toggleFavorite(kind, item)
    }

    fun removeFavorite(item: FavoriteItem) {
        libraryController.removeFavorite(item)
    }

    fun clearHistory() {
        libraryController.clearHistory()
    }

    fun openSeriesFavorite(item: FavoriteItem) {
        seriesDetailController.openSeriesById(item.providerId, item.title)
    }

    fun selectRecent(item: HistoryItem) {
        val ref = item.toPlaybackRef() ?: return
        playbackSelectionController.select(
            ref = ref,
            startPositionMs = item.continueWatchingPositionMs() ?: 0L,
        )
    }

    fun selectContinue(item: HistoryItem) {
        val ref = item.toPlaybackRef() ?: return
        val position = item.continueWatchingPositionMs() ?: return
        playbackSelectionController.select(ref, position)
    }

    fun clearPlayback() {
        playbackSelectionController.clear()
    }

    class Factory(
        private val repository: SessionRepository,
        private val credentialStore: CredentialStore,
        private val catalogRepository: CatalogRepository,
        private val providerSessionStore: RuntimeProviderSessionStore,
        private val localLibraryRepository: LocalLibraryRepository,
        private val activeLibraryProfileStore: ActiveLibraryProfileStore,
        private val playbackActivityRecorder: PlaybackActivityRecorder,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AppViewModel::class.java))
            return AppViewModel(
                repository = repository,
                credentialStore = credentialStore,
                catalogRepository = catalogRepository,
                providerSessionStore = providerSessionStore,
                localLibraryRepository = localLibraryRepository,
                activeLibraryProfileStore = activeLibraryProfileStore,
                playbackActivityRecorder = playbackActivityRecorder,
            ) as T
        }
    }
}
