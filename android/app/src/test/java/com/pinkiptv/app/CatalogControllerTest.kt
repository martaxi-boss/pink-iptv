package com.pinkiptv.app

import com.pinkiptv.app.model.CatalogCategory
import com.pinkiptv.app.model.CatalogError
import com.pinkiptv.app.model.CatalogKind
import com.pinkiptv.app.model.CatalogPhase
import com.pinkiptv.app.model.CatalogRepository
import com.pinkiptv.app.model.CatalogResult
import com.pinkiptv.app.model.CatalogUiError
import com.pinkiptv.app.model.LiveStream
import com.pinkiptv.app.model.SeriesDetail
import com.pinkiptv.app.model.SeriesItem
import com.pinkiptv.app.model.VodItem
import com.pinkiptv.app.state.CatalogController
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogControllerTest {
    @Test
    fun liveCatalogLoadsContentAndFiltersByCategory() = runTest {
        val repository = FakeCatalogRepository(
            liveCategoriesResult = CatalogResult.Success(
                listOf(
                    CatalogCategory("news", "News"),
                    CatalogCategory("sports", "Sports"),
                ),
            ),
            liveStreamsResult = CatalogResult.Success(
                listOf(
                    LiveStream("1", "One", "news", null, "live"),
                    LiveStream("2", "Two", "sports", null, "live"),
                ),
            ),
        )
        val controller = CatalogController(repository, this)

        controller.load(CatalogKind.Live)
        advanceUntilIdle()

        assertEquals(CatalogPhase.Content, controller.live.value.phase)
        assertEquals(2, controller.live.value.items.size)

        controller.selectCategory(CatalogKind.Live, "news")
        assertEquals(1, controller.live.value.items.size)
        assertEquals("1", controller.live.value.items.single().id)
    }

    @Test
    fun emptyAndRecoverableErrorStatesAreDeterministic() = runTest {
        val repository = FakeCatalogRepository(
            vodCategoriesResult = CatalogResult.Success(emptyList()),
            vodStreamsResult = CatalogResult.Success(emptyList()),
            seriesCategoriesResult = CatalogResult.Failure(CatalogError.NetworkFailure),
        )
        val controller = CatalogController(repository, this)

        controller.load(CatalogKind.Movies)
        advanceUntilIdle()
        assertEquals(CatalogPhase.Empty, controller.movies.value.phase)

        controller.load(CatalogKind.Series)
        advanceUntilIdle()
        assertEquals(CatalogPhase.Error, controller.series.value.phase)
        assertEquals(CatalogUiError.ProviderUnavailable, controller.series.value.error)
    }

    private class FakeCatalogRepository(
        private val liveCategoriesResult: CatalogResult<List<CatalogCategory>> =
            CatalogResult.Success(emptyList()),
        private val liveStreamsResult: CatalogResult<List<LiveStream>> =
            CatalogResult.Success(emptyList()),
        private val vodCategoriesResult: CatalogResult<List<CatalogCategory>> =
            CatalogResult.Success(emptyList()),
        private val vodStreamsResult: CatalogResult<List<VodItem>> =
            CatalogResult.Success(emptyList()),
        private val seriesCategoriesResult: CatalogResult<List<CatalogCategory>> =
            CatalogResult.Success(emptyList()),
        private val seriesResult: CatalogResult<List<SeriesItem>> =
            CatalogResult.Success(emptyList()),
    ) : CatalogRepository {
        override suspend fun liveCategories() = liveCategoriesResult
        override suspend fun liveStreams() = liveStreamsResult
        override suspend fun vodCategories() = vodCategoriesResult
        override suspend fun vodStreams() = vodStreamsResult
        override suspend fun seriesCategories() = seriesCategoriesResult
        override suspend fun series() = seriesResult
        override suspend fun seriesInfo(seriesId: String) =
            CatalogResult.Success(
                SeriesDetail(
                    seriesId = seriesId,
                    name = null,
                    plot = null,
                    artworkUrl = null,
                    genre = null,
                    rating = null,
                    seasons = emptyList(),
                    episodes = emptyList(),
                ),
            )
    }
}
