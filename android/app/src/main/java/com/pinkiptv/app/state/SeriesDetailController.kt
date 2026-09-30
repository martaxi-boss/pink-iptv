package com.pinkiptv.app.state

import com.pinkiptv.app.model.CatalogError
import com.pinkiptv.app.model.CatalogRepository
import com.pinkiptv.app.model.CatalogResult
import com.pinkiptv.app.model.CatalogUiItem
import com.pinkiptv.app.model.SeriesDetail
import com.pinkiptv.app.model.SeriesDetailPhase
import com.pinkiptv.app.model.SeriesDetailUiError
import com.pinkiptv.app.model.SeriesDetailUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SeriesDetailController(
    private val repository: CatalogRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(SeriesDetailUiState())
    val state: StateFlow<SeriesDetailUiState> = mutableState.asStateFlow()

    private var selectedSeriesTitle: String? = null

    fun openSeries(item: CatalogUiItem) {
        selectedSeriesTitle = item.name
        load(item.id, item.name)
    }

    fun retry() {
        val seriesId = mutableState.value.selectedSeriesId ?: return
        load(seriesId, selectedSeriesTitle ?: mutableState.value.title ?: "Série")
    }

    fun selectSeason(seasonId: String) {
        val current = mutableState.value
        if (current.phase != SeriesDetailPhase.Content) return
        if (current.seasons.none { it.seasonId == seasonId }) return
        val detail = currentDetail ?: return
        mutableState.value = current.copy(
            selectedSeasonId = seasonId,
            episodes = detail.episodes.filter { it.seasonId == seasonId },
        )
    }

    fun clear() {
        selectedSeriesTitle = null
        currentDetail = null
        mutableState.value = SeriesDetailUiState()
    }

    private var currentDetail: SeriesDetail? = null

    private fun load(seriesId: String, fallbackTitle: String) {
        mutableState.value = SeriesDetailUiState(
            phase = SeriesDetailPhase.Loading,
            selectedSeriesId = seriesId,
            title = fallbackTitle,
        )

        scope.launch {
            when (val result = repository.seriesInfo(seriesId)) {
                is CatalogResult.Success -> applyDetail(
                    requestedSeriesId = seriesId,
                    fallbackTitle = fallbackTitle,
                    detail = result.value,
                )
                is CatalogResult.Failure -> {
                    if (mutableState.value.selectedSeriesId != seriesId) return@launch
                    currentDetail = null
                    mutableState.value = SeriesDetailUiState(
                        phase = SeriesDetailPhase.Error,
                        selectedSeriesId = seriesId,
                        title = fallbackTitle,
                        error = result.error.toSeriesDetailUiError(),
                    )
                }
            }
        }
    }

    private fun applyDetail(
        requestedSeriesId: String,
        fallbackTitle: String,
        detail: SeriesDetail,
    ) {
        if (mutableState.value.selectedSeriesId != requestedSeriesId) return
        currentDetail = detail

        if (detail.episodes.isEmpty()) {
            mutableState.value = SeriesDetailUiState(
                phase = SeriesDetailPhase.Empty,
                selectedSeriesId = requestedSeriesId,
                title = detail.name ?: fallbackTitle,
                plot = detail.plot,
                artworkUrl = detail.artworkUrl,
                genre = detail.genre,
                rating = detail.rating,
                seasons = detail.seasons,
            )
            return
        }

        val seasonWithEpisodes = detail.seasons.firstOrNull { season ->
            detail.episodes.any { it.seasonId == season.seasonId }
        }
        val selectedSeasonId = seasonWithEpisodes?.seasonId
            ?: detail.episodes.first().seasonId
        mutableState.value = SeriesDetailUiState(
            phase = SeriesDetailPhase.Content,
            selectedSeriesId = requestedSeriesId,
            title = detail.name ?: fallbackTitle,
            plot = detail.plot,
            artworkUrl = detail.artworkUrl,
            genre = detail.genre,
            rating = detail.rating,
            seasons = detail.seasons,
            selectedSeasonId = selectedSeasonId,
            episodes = detail.episodes.filter { it.seasonId == selectedSeasonId },
        )
    }

    private fun CatalogError.toSeriesDetailUiError(): SeriesDetailUiError =
        when (this) {
            CatalogError.MissingSession -> SeriesDetailUiError.SessionUnavailable
            CatalogError.InvalidMetadata,
            CatalogError.InvalidResponse,
            -> SeriesDetailUiError.InvalidResponse
            CatalogError.HttpFailure,
            CatalogError.NetworkFailure,
            -> SeriesDetailUiError.ProviderUnavailable
        }
}
