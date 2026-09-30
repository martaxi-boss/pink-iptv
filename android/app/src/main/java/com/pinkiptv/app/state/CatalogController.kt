package com.pinkiptv.app.state

import com.pinkiptv.app.model.CatalogError
import com.pinkiptv.app.model.CatalogKind
import com.pinkiptv.app.model.CatalogPhase
import com.pinkiptv.app.model.CatalogRepository
import com.pinkiptv.app.model.CatalogResult
import com.pinkiptv.app.model.CatalogUiError
import com.pinkiptv.app.model.CatalogUiItem
import com.pinkiptv.app.model.CatalogUiState
import com.pinkiptv.app.model.LivePlaybackRef
import com.pinkiptv.app.model.VodPlaybackRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CatalogController(
    private val repository: CatalogRepository,
    private val scope: CoroutineScope,
) {
    private val mutableLive = MutableStateFlow(CatalogUiState(CatalogKind.Live))
    private val mutableMovies = MutableStateFlow(CatalogUiState(CatalogKind.Movies))
    private val mutableSeries = MutableStateFlow(CatalogUiState(CatalogKind.Series))

    val live: StateFlow<CatalogUiState> = mutableLive.asStateFlow()
    val movies: StateFlow<CatalogUiState> = mutableMovies.asStateFlow()
    val series: StateFlow<CatalogUiState> = mutableSeries.asStateFlow()

    private val allItems = mutableMapOf<CatalogKind, List<CatalogUiItem>>()

    fun load(kind: CatalogKind) {
        val state = stateFor(kind)
        if (state.value.phase == CatalogPhase.Loading) return

        scope.launch {
            state.value = state.value.copy(
                phase = CatalogPhase.Loading,
                error = null,
            )

            val categories = when (val result = categoryResult(kind)) {
                is CatalogResult.Success -> result.value
                is CatalogResult.Failure -> {
                    fail(kind, result.error)
                    return@launch
                }
            }

            val items = when (kind) {
                CatalogKind.Live -> when (val result = repository.liveStreams()) {
                    is CatalogResult.Success -> result.value.map { item ->
                        CatalogUiItem(
                            id = item.streamId,
                            name = item.name,
                            categoryId = item.categoryId,
                            artworkUrl = item.artworkUrl,
                            subtitle = item.streamType,
                            playbackRef = LivePlaybackRef(
                                streamId = item.streamId,
                                title = item.name,
                                artworkUrl = item.artworkUrl,
                            ),
                        )
                    }
                    is CatalogResult.Failure -> {
                        fail(kind, result.error)
                        return@launch
                    }
                }
                CatalogKind.Movies -> when (val result = repository.vodStreams()) {
                    is CatalogResult.Success -> result.value.map { item ->
                        CatalogUiItem(
                            id = item.streamId,
                            name = item.name,
                            categoryId = item.categoryId,
                            artworkUrl = item.artworkUrl,
                            subtitle = item.containerExtension,
                            playbackRef = VodPlaybackRef(
                                streamId = item.streamId,
                                title = item.name,
                                containerExtension = item.containerExtension,
                                artworkUrl = item.artworkUrl,
                            ),
                        )
                    }
                    is CatalogResult.Failure -> {
                        fail(kind, result.error)
                        return@launch
                    }
                }
                CatalogKind.Series -> when (val result = repository.series()) {
                    is CatalogResult.Success -> result.value.map { item ->
                        CatalogUiItem(
                            id = item.seriesId,
                            name = item.name,
                            categoryId = item.categoryId,
                            artworkUrl = item.artworkUrl,
                            subtitle = null,
                            playbackRef = null,
                        )
                    }
                    is CatalogResult.Failure -> {
                        fail(kind, result.error)
                        return@launch
                    }
                }
            }

            allItems[kind] = items
            val previousCategory = state.value.selectedCategoryId
            val selectedCategory = previousCategory
                ?.takeIf { id -> categories.any { it.id == id } }
            val visibleItems = filterItems(items, selectedCategory)

            state.value = CatalogUiState(
                kind = kind,
                phase = if (categories.isEmpty() && items.isEmpty()) {
                    CatalogPhase.Empty
                } else {
                    CatalogPhase.Content
                },
                categories = categories,
                selectedCategoryId = selectedCategory,
                items = visibleItems,
            )
        }
    }

    fun selectCategory(kind: CatalogKind, categoryId: String?) {
        val state = stateFor(kind)
        if (state.value.phase != CatalogPhase.Content) return
        val all = allItems[kind].orEmpty()
        state.value = state.value.copy(
            selectedCategoryId = categoryId,
            items = filterItems(all, categoryId),
        )
    }

    fun clear() {
        allItems.clear()
        mutableLive.value = CatalogUiState(CatalogKind.Live)
        mutableMovies.value = CatalogUiState(CatalogKind.Movies)
        mutableSeries.value = CatalogUiState(CatalogKind.Series)
    }

    private suspend fun categoryResult(kind: CatalogKind) =
        when (kind) {
            CatalogKind.Live -> repository.liveCategories()
            CatalogKind.Movies -> repository.vodCategories()
            CatalogKind.Series -> repository.seriesCategories()
        }

    private fun fail(kind: CatalogKind, error: CatalogError) {
        stateFor(kind).value = CatalogUiState(
            kind = kind,
            phase = CatalogPhase.Error,
            error = when (error) {
                CatalogError.MissingSession -> CatalogUiError.SessionUnavailable
                CatalogError.InvalidResponse -> CatalogUiError.InvalidResponse
                CatalogError.HttpFailure,
                CatalogError.NetworkFailure,
                -> CatalogUiError.ProviderUnavailable
            },
        )
    }

    private fun stateFor(kind: CatalogKind): MutableStateFlow<CatalogUiState> =
        when (kind) {
            CatalogKind.Live -> mutableLive
            CatalogKind.Movies -> mutableMovies
            CatalogKind.Series -> mutableSeries
        }

    private fun filterItems(
        items: List<CatalogUiItem>,
        categoryId: String?,
    ): List<CatalogUiItem> =
        if (categoryId == null) items else items.filter { it.categoryId == categoryId }
}
