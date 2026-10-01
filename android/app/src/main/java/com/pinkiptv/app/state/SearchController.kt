package com.pinkiptv.app.state

import com.pinkiptv.app.model.CatalogError
import com.pinkiptv.app.model.CatalogRepository
import com.pinkiptv.app.model.CatalogResult
import com.pinkiptv.app.model.LivePlaybackRef
import com.pinkiptv.app.model.SearchItem
import com.pinkiptv.app.model.SearchKind
import com.pinkiptv.app.model.SearchPhase
import com.pinkiptv.app.model.SearchSourceState
import com.pinkiptv.app.model.SearchSourceStatus
import com.pinkiptv.app.model.SearchUiError
import com.pinkiptv.app.model.SearchUiState
import com.pinkiptv.app.model.RuntimeProviderSessionStore
import com.pinkiptv.app.model.VodPlaybackRef
import com.pinkiptv.app.model.filterSearchItems
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class SearchController(
    private val repository: CatalogRepository,
    private val providerSessionStore: RuntimeProviderSessionStore,
    private val scope: CoroutineScope,
    private val resultLimitPerKind: Int = 100,
) {
    private val mutableState = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()

    private val sourceItems = mutableMapOf<SearchKind, List<SearchItem>>()
    private val sourceStates = mutableMapOf(
        SearchKind.Live to SearchSourceState(),
        SearchKind.Movies to SearchSourceState(),
        SearchKind.Series to SearchSourceState(),
    )

    private var generation = 0L
    private var cachedSessionIdentity: Any? = null

    init {
        scope.launch {
            providerSessionStore.available.collect { available ->
                if (!available) {
                    clear()
                }
            }
        }
    }

    fun load() {
        val currentSession = providerSessionStore.current()
        if (currentSession == null) {
            showMissingSession()
            return
        }
        if (cachedSessionIdentity != null && cachedSessionIdentity !== currentSession) {
            clear()
        }
        if (sourceStates.values.any { it.status != SearchSourceStatus.Idle }) {
            return
        }
        startLoad(
            kinds = setOf(SearchKind.Live, SearchKind.Movies, SearchKind.Series),
            resetSources = true,
        )
    }

    fun retry() {
        val failedKinds = sourceStates
            .filterValues { it.status == SearchSourceStatus.Error }
            .keys
            .toSet()
        if (failedKinds.isEmpty()) return

        val currentSession = providerSessionStore.current()
        if (currentSession == null) {
            showMissingSession()
            return
        }
        if (cachedSessionIdentity != null && cachedSessionIdentity !== currentSession) {
            clear()
            startLoad(
                kinds = setOf(SearchKind.Live, SearchKind.Movies, SearchKind.Series),
                resetSources = true,
            )
            return
        }

        startLoad(
            kinds = failedKinds,
            resetSources = false,
        )
    }

    fun updateQuery(query: String) {
        mutableState.value = mutableState.value.copy(query = query)
        publish()
    }

    fun selectKind(kind: SearchKind) {
        mutableState.value = mutableState.value.copy(selectedKind = kind)
        publish()
    }

    fun clear() {
        generation += 1
        cachedSessionIdentity = null
        sourceItems.clear()
        sourceStates.keys.toList().forEach { kind ->
            sourceStates[kind] = SearchSourceState()
        }
        mutableState.value = SearchUiState()
    }

    private fun startLoad(
        kinds: Set<SearchKind>,
        resetSources: Boolean,
    ) {
        require(SearchKind.All !in kinds)
        val session = providerSessionStore.current()
        if (session == null) {
            showMissingSession()
            return
        }

        val loadGeneration = ++generation
        if (resetSources) {
            sourceItems.clear()
            sourceStates.keys.toList().forEach { kind ->
                sourceStates[kind] = SearchSourceState()
            }
        }
        kinds.forEach { kind ->
            sourceStates[kind] = SearchSourceState(SearchSourceStatus.Loading)
        }
        publish()

        scope.launch {
            val results = coroutineScope {
                kinds.associateWith { kind ->
                    async {
                        try {
                            loadSource(kind)
                        } catch (_: Throwable) {
                            CatalogResult.Failure(CatalogError.NetworkFailure)
                        }
                    }
                }.mapValues { (_, deferred) -> deferred.await() }
            }

            if (
                generation != loadGeneration ||
                providerSessionStore.current() !== session
            ) {
                return@launch
            }

            cachedSessionIdentity = session
            results.forEach { (kind, result) ->
                when (result) {
                    is CatalogResult.Success -> {
                        sourceItems[kind] = result.value
                        sourceStates[kind] = SearchSourceState(SearchSourceStatus.Ready)
                    }
                    is CatalogResult.Failure -> {
                        sourceItems.remove(kind)
                        sourceStates[kind] = SearchSourceState(
                            status = SearchSourceStatus.Error,
                            error = result.error.toSearchUiError(),
                        )
                    }
                }
            }
            publish()
        }
    }

    private suspend fun loadSource(
        kind: SearchKind,
    ): CatalogResult<List<SearchItem>> =
        when (kind) {
            SearchKind.Live -> when (val result = repository.liveStreams()) {
                is CatalogResult.Success -> CatalogResult.Success(
                    result.value.map { item ->
                        SearchItem(
                            kind = SearchKind.Live,
                            providerId = item.streamId,
                            title = item.name,
                            artworkUrl = item.artworkUrl,
                            playbackRef = LivePlaybackRef(
                                streamId = item.streamId,
                                title = item.name,
                                artworkUrl = item.artworkUrl,
                            ),
                        )
                    },
                )
                is CatalogResult.Failure -> result
            }
            SearchKind.Movies -> when (val result = repository.vodStreams()) {
                is CatalogResult.Success -> CatalogResult.Success(
                    result.value.map { item ->
                        SearchItem(
                            kind = SearchKind.Movies,
                            providerId = item.streamId,
                            title = item.name,
                            artworkUrl = item.artworkUrl,
                            playbackRef = VodPlaybackRef(
                                streamId = item.streamId,
                                title = item.name,
                                containerExtension = item.containerExtension,
                                artworkUrl = item.artworkUrl,
                            ),
                        )
                    },
                )
                is CatalogResult.Failure -> result
            }
            SearchKind.Series -> when (val result = repository.series()) {
                is CatalogResult.Success -> CatalogResult.Success(
                    result.value.map { item ->
                        SearchItem(
                            kind = SearchKind.Series,
                            providerId = item.seriesId,
                            title = item.name,
                            artworkUrl = item.artworkUrl,
                            playbackRef = null,
                        )
                    },
                )
                is CatalogResult.Failure -> result
            }
            SearchKind.All -> error("SearchKind.All is not a catalog source")
        }

    private fun publish() {
        val current = mutableState.value
        val allItems = sourceItems.values.flatten()
        val results = filterSearchItems(
            items = allItems,
            query = current.query,
            selectedKind = current.selectedKind,
            limitPerKind = resultLimitPerKind,
        )

        val states = sourceStates.values.toList()
        val readyCount = states.count { it.status == SearchSourceStatus.Ready }
        val errorCount = states.count { it.status == SearchSourceStatus.Error }
        val loadingCount = states.count { it.status == SearchSourceStatus.Loading }

        val phase = when {
            errorCount == sourceStates.size -> SearchPhase.FullError
            loadingCount > 0 && readyCount == 0 -> SearchPhase.LoadingCatalog
            errorCount > 0 || (loadingCount > 0 && readyCount > 0) ->
                SearchPhase.PartialError
            !current.effectiveQuery -> SearchPhase.Inactive
            results.isEmpty() -> SearchPhase.NoMatches
            else -> SearchPhase.Ready
        }

        mutableState.value = current.copy(
            phase = phase,
            results = results,
            liveSource = sourceState(SearchKind.Live),
            movieSource = sourceState(SearchKind.Movies),
            seriesSource = sourceState(SearchKind.Series),
        )
    }

    private fun showMissingSession() {
        generation += 1
        cachedSessionIdentity = null
        sourceItems.clear()
        sourceStates.keys.toList().forEach { kind ->
            sourceStates[kind] = SearchSourceState(
                status = SearchSourceStatus.Error,
                error = SearchUiError.SessionUnavailable,
            )
        }
        mutableState.value = SearchUiState(
            phase = SearchPhase.FullError,
            liveSource = sourceState(SearchKind.Live),
            movieSource = sourceState(SearchKind.Movies),
            seriesSource = sourceState(SearchKind.Series),
        )
    }

    private fun sourceState(kind: SearchKind): SearchSourceState =
        sourceStates[kind] ?: SearchSourceState()

    private fun CatalogError.toSearchUiError(): SearchUiError =
        when (this) {
            CatalogError.MissingSession -> SearchUiError.SessionUnavailable
            CatalogError.InvalidMetadata,
            CatalogError.InvalidResponse,
            -> SearchUiError.InvalidResponse
            CatalogError.HttpFailure,
            CatalogError.NetworkFailure,
            -> SearchUiError.ProviderUnavailable
        }
}
