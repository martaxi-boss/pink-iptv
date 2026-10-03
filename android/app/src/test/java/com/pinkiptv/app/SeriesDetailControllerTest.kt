package com.pinkiptv.app

import com.pinkiptv.app.model.CatalogCategory
import com.pinkiptv.app.model.CatalogError
import com.pinkiptv.app.model.CatalogRepository
import com.pinkiptv.app.model.CatalogResult
import com.pinkiptv.app.model.CatalogUiItem
import com.pinkiptv.app.model.LiveStream
import com.pinkiptv.app.model.SeriesDetail
import com.pinkiptv.app.model.SeriesDetailPhase
import com.pinkiptv.app.model.SeriesEpisode
import com.pinkiptv.app.model.SeriesItem
import com.pinkiptv.app.model.SeriesSeason
import com.pinkiptv.app.model.VodItem
import com.pinkiptv.app.state.SeriesDetailController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesDetailControllerTest {
    @Test
    fun seriesSelectionLoadsDetailAndSeasonSelectionFiltersEpisodes() = runTest {
        val repository = FakeRepository(result = CatalogResult.Success(detail()))
        val controller = SeriesDetailController(repository, this)

        controller.openSeries(seriesItem())
        assertEquals(SeriesDetailPhase.Loading, controller.state.value.phase)
        advanceUntilIdle()

        assertEquals(SeriesDetailPhase.Content, controller.state.value.phase)
        assertEquals("Show", controller.state.value.title)
        assertEquals("1", controller.state.value.selectedSeasonId)
        assertEquals(listOf("101"), controller.state.value.episodes.map { it.episodeId })

        controller.selectSeason("2")
        assertEquals("2", controller.state.value.selectedSeasonId)
        assertEquals(listOf("201"), controller.state.value.episodes.map { it.episodeId })
        assertEquals(listOf("77"), repository.requestedIds)
    }

    @Test
    fun emptySeriesAndRecoverableRetryAreDeterministic() = runTest {
        val repository = FakeRepository(
            result = CatalogResult.Success(
                detail().copy(seasons = emptyList(), episodes = emptyList()),
            ),
        )
        val controller = SeriesDetailController(repository, this)

        controller.openSeries(seriesItem())
        advanceUntilIdle()
        assertEquals(SeriesDetailPhase.Empty, controller.state.value.phase)

        repository.result = CatalogResult.Failure(CatalogError.NetworkFailure)
        controller.openSeries(seriesItem())
        advanceUntilIdle()
        assertEquals(SeriesDetailPhase.Error, controller.state.value.phase)

        repository.result = CatalogResult.Success(detail())
        controller.retry()
        advanceUntilIdle()
        assertEquals(SeriesDetailPhase.Content, controller.state.value.phase)
        assertEquals(3, repository.requestedIds.size)
    }

    @Test
    fun clearRemovesSelectedSeriesAndEpisodeContextContract() = runTest {
        val repository = FakeRepository(result = CatalogResult.Success(detail()))
        val controller = SeriesDetailController(repository, this)

        controller.openSeries(seriesItem())
        advanceUntilIdle()
        assertEquals("77", controller.state.value.selectedSeriesId)

        controller.clear()
        assertEquals(SeriesDetailPhase.Idle, controller.state.value.phase)
        assertEquals(null, controller.state.value.selectedSeriesId)
        assertTrue(controller.state.value.episodes.isEmpty())
    }

    @Test
    fun olderSameSeriesResponseCannotOverwriteNewerRequest() = runTest {
        val oldDetail = CompletableDeferred<CatalogResult<SeriesDetail>>()
        var calls = 0
        val repository = object : CatalogRepository by FakeRepository(CatalogResult.Success(detail())) {
            override suspend fun seriesInfo(seriesId: String): CatalogResult<SeriesDetail> {
                calls += 1
                return if (calls == 1) oldDetail.await() else CatalogResult.Success(
                    detail().copy(name = "Newest"),
                )
            }
        }
        val controller = SeriesDetailController(repository, this)
        controller.openSeries(seriesItem())
        runCurrent()
        controller.clear()
        controller.openSeries(seriesItem())
        runCurrent()
        oldDetail.complete(CatalogResult.Success(detail().copy(name = "Stale")))
        advanceUntilIdle()
        assertEquals("Newest", controller.state.value.title)
    }

    private fun seriesItem() = CatalogUiItem(
        id = "77",
        name = "Fallback",
        categoryId = "3",
        artworkUrl = null,
        subtitle = null,
    )

    private fun detail() = SeriesDetail(
        seriesId = "77",
        name = "Show",
        plot = "Plot",
        artworkUrl = null,
        genre = "Drama",
        rating = "8.0",
        seasons = listOf(
            SeriesSeason("1", "Season 1", 1, null),
            SeriesSeason("2", "Season 2", 1, null),
        ),
        episodes = listOf(
            SeriesEpisode("101", "1", "First", "1", "mkv", null, "00:40:00", null),
            SeriesEpisode("201", "1", "Second", "2", "mp4", null, null, null),
        ),
    )

    private class FakeRepository(
        var result: CatalogResult<SeriesDetail>,
    ) : CatalogRepository {
        val requestedIds = mutableListOf<String>()

        override suspend fun seriesInfo(seriesId: String): CatalogResult<SeriesDetail> {
            requestedIds += seriesId
            return result
        }

        override suspend fun liveCategories() = CatalogResult.Success(emptyList<CatalogCategory>())
        override suspend fun liveStreams() = CatalogResult.Success(emptyList<LiveStream>())
        override suspend fun vodCategories() = CatalogResult.Success(emptyList<CatalogCategory>())
        override suspend fun vodStreams() = CatalogResult.Success(emptyList<VodItem>())
        override suspend fun seriesCategories() = CatalogResult.Success(emptyList<CatalogCategory>())
        override suspend fun series() = CatalogResult.Success(emptyList<SeriesItem>())
    }
}
