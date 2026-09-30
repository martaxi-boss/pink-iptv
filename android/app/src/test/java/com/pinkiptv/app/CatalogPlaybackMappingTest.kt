package com.pinkiptv.app

import com.pinkiptv.app.model.CatalogCategory
import com.pinkiptv.app.model.CatalogKind
import com.pinkiptv.app.model.CatalogRepository
import com.pinkiptv.app.model.CatalogResult
import com.pinkiptv.app.model.LivePlaybackRef
import com.pinkiptv.app.model.LiveStream
import com.pinkiptv.app.model.SeriesDetail
import com.pinkiptv.app.model.SeriesItem
import com.pinkiptv.app.model.VodItem
import com.pinkiptv.app.model.VodPlaybackRef
import com.pinkiptv.app.state.CatalogController
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogPlaybackMappingTest {
    @Test
    fun catalogBuildsTypedLiveVodRefsAndNoSeriesPlaybackRef() = runTest {
        val controller = CatalogController(FakeRepository(), this)

        controller.load(CatalogKind.Live)
        controller.load(CatalogKind.Movies)
        controller.load(CatalogKind.Series)
        advanceUntilIdle()

        val live = controller.live.value.items.single().playbackRef
        val vod = controller.movies.value.items.single().playbackRef
        val series = controller.series.value.items.single().playbackRef

        assertTrue(live is LivePlaybackRef)
        assertEquals("11", live?.streamId)
        assertTrue(vod is VodPlaybackRef)
        assertEquals("mp4", (vod as VodPlaybackRef).containerExtension)
        assertNull(series)
    }

    private class FakeRepository : CatalogRepository {
        override suspend fun liveCategories() =
            CatalogResult.Success(listOf(CatalogCategory("1", "Live")))
        override suspend fun liveStreams() =
            CatalogResult.Success(listOf(LiveStream("11", "Channel", "1", null, "live")))
        override suspend fun vodCategories() =
            CatalogResult.Success(listOf(CatalogCategory("2", "Movies")))
        override suspend fun vodStreams() =
            CatalogResult.Success(listOf(VodItem("22", "Movie", "2", null, "mp4")))
        override suspend fun seriesCategories() =
            CatalogResult.Success(listOf(CatalogCategory("3", "Series")))
        override suspend fun series() =
            CatalogResult.Success(listOf(SeriesItem("33", "Series", "3", null)))
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
