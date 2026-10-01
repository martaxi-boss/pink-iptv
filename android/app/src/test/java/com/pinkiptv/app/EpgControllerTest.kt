package com.pinkiptv.app

import com.pinkiptv.app.model.CatalogCategory
import com.pinkiptv.app.model.CatalogRepository
import com.pinkiptv.app.model.CatalogResult
import com.pinkiptv.app.model.EpgError
import com.pinkiptv.app.model.EpgPhase
import com.pinkiptv.app.model.EpgProgramme
import com.pinkiptv.app.model.EpgResult
import com.pinkiptv.app.model.LiveStream
import com.pinkiptv.app.model.SeriesDetail
import com.pinkiptv.app.model.SeriesItem
import com.pinkiptv.app.model.VodItem
import com.pinkiptv.app.state.EpgController
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgControllerTest {
    @Test
    fun initialLoadSelectsFirstChannelAndExposesNowNextAndCatchUp() = runTest {
        val repository = FakeRepository(
            live = CatalogResult.Success(
                listOf(live("10", archive = true), live("20", archive = false)),
            ),
        )
        repository.full["10"] = EpgResult.Success(
            listOf(programme("old", 1000L, 1600L, archive = true)),
        )
        repository.short["10"] = EpgResult.Success(
            listOf(
                programme("now", 4900L, 5100L, archive = false, now = true),
                programme("next", 5100L, 5200L, archive = false),
            ),
        )
        val controller = EpgController(repository, this) { 5000L }

        controller.open()
        assertEquals(EpgPhase.Loading, controller.state.value.phase)
        advanceUntilIdle()

        val state = controller.state.value
        assertEquals(EpgPhase.Content, state.phase)
        assertEquals("10", state.selectedChannelId)
        assertEquals(2, state.nowNext.size)
        assertEquals("now", state.nowNext.first().programmeId)
        assertNotNull(state.programmes.single().catchUpRef)
        assertEquals(listOf("10"), repository.fullRequests)
        assertEquals(listOf("10"), repository.shortRequests)
    }

    @Test
    fun channelSelectionLoadsOnlyTheSelectedChannel() = runTest {
        val repository = FakeRepository(
            live = CatalogResult.Success(
                listOf(live("10", true), live("20", true)),
            ),
        )
        repository.full["10"] = EpgResult.Success(listOf(programme("a", 1000L, 1600L, true)))
        repository.full["20"] = EpgResult.Success(listOf(programme("b", 2000L, 2600L, true)))
        val controller = EpgController(repository, this) { 5000L }

        controller.open()
        advanceUntilIdle()
        controller.selectChannel("20")
        assertEquals(EpgPhase.Loading, controller.state.value.phase)
        advanceUntilIdle()

        assertEquals("20", controller.state.value.selectedChannelId)
        assertEquals("b", controller.state.value.programmes.single().programme.programmeId)
        assertEquals(listOf("10", "20"), repository.fullRequests)
    }

    @Test
    fun emptyErrorAndRetryRemainRecoverable() = runTest {
        val repository = FakeRepository(
            live = CatalogResult.Success(listOf(live("10", true))),
        )
        repository.full["10"] = EpgResult.Success(emptyList())
        val controller = EpgController(repository, this) { 5000L }

        controller.open()
        advanceUntilIdle()
        assertEquals(EpgPhase.Empty, controller.state.value.phase)

        repository.full["10"] = EpgResult.Failure(EpgError.NetworkFailure)
        controller.retry()
        advanceUntilIdle()
        assertEquals(EpgPhase.Error, controller.state.value.phase)

        repository.full["10"] = EpgResult.Success(
            listOf(programme("recovered", 1000L, 1600L, true)),
        )
        controller.retry()
        advanceUntilIdle()
        assertEquals(EpgPhase.Content, controller.state.value.phase)
        assertEquals("recovered", controller.state.value.programmes.single().programme.programmeId)
    }

    @Test
    fun catchUpIsFailClosedAndClearRemovesAuthenticatedEpgState() = runTest {
        val repository = FakeRepository(
            live = CatalogResult.Success(listOf(live("10", archive = false))),
        )
        repository.full["10"] = EpgResult.Success(
            listOf(programme("old", 1000L, 1600L, archive = true)),
        )
        val controller = EpgController(repository, this) { 5000L }

        controller.open()
        advanceUntilIdle()
        assertNull(controller.state.value.programmes.single().catchUpRef)

        controller.clear()
        assertEquals(EpgPhase.Idle, controller.state.value.phase)
        assertTrue(controller.state.value.channels.isEmpty())
        assertTrue(controller.state.value.programmes.isEmpty())
        assertNull(controller.state.value.selectedChannelId)
    }

    private fun live(id: String, archive: Boolean) = LiveStream(
        streamId = id,
        name = "Channel " + id,
        categoryId = "1",
        artworkUrl = null,
        streamType = "live",
        epgChannelId = "epg-" + id,
        tvArchive = archive,
        tvArchiveDurationDays = if (archive) 7 else null,
    )

    private fun programme(
        id: String,
        start: Long,
        stop: Long,
        archive: Boolean,
        now: Boolean = false,
    ) = EpgProgramme(
        programmeId = id,
        title = "Programme " + id,
        description = null,
        startProvider = "2026-09-30 12:34:56",
        endProvider = "2026-09-30 12:44:56",
        startTimestamp = start,
        stopTimestamp = stop,
        nowPlaying = now,
        hasArchive = archive,
    )

    private class FakeRepository(
        var live: CatalogResult<List<LiveStream>>,
    ) : CatalogRepository {
        val full = mutableMapOf<String, EpgResult<List<EpgProgramme>>>()
        val short = mutableMapOf<String, EpgResult<List<EpgProgramme>>>()
        val fullRequests = mutableListOf<String>()
        val shortRequests = mutableListOf<String>()

        override suspend fun liveStreams() = live

        override suspend fun simpleDataTable(streamId: String): EpgResult<List<EpgProgramme>> {
            fullRequests += streamId
            return full[streamId] ?: EpgResult.Success(emptyList())
        }

        override suspend fun shortEpg(
            streamId: String,
            limit: Int,
        ): EpgResult<List<EpgProgramme>> {
            assertEquals(2, limit)
            shortRequests += streamId
            return short[streamId] ?: EpgResult.Success(emptyList())
        }

        override suspend fun liveCategories() =
            CatalogResult.Success(emptyList<CatalogCategory>())

        override suspend fun vodCategories() =
            CatalogResult.Success(emptyList<CatalogCategory>())

        override suspend fun vodStreams() =
            CatalogResult.Success(emptyList<VodItem>())

        override suspend fun seriesCategories() =
            CatalogResult.Success(emptyList<CatalogCategory>())

        override suspend fun series() =
            CatalogResult.Success(emptyList<SeriesItem>())

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
