package com.pinkiptv.app

import com.pinkiptv.app.model.CatalogCategory
import com.pinkiptv.app.model.CatalogError
import com.pinkiptv.app.model.CatalogRepository
import com.pinkiptv.app.model.CatalogResult
import com.pinkiptv.app.model.LivePlaybackRef
import com.pinkiptv.app.model.LiveStream
import com.pinkiptv.app.model.RuntimeProviderSessionStore
import com.pinkiptv.app.model.SearchItem
import com.pinkiptv.app.model.SearchKind
import com.pinkiptv.app.model.SearchPhase
import com.pinkiptv.app.model.SearchSourceStatus
import com.pinkiptv.app.model.SearchUiState
import com.pinkiptv.app.model.SeriesDetail
import com.pinkiptv.app.model.SeriesItem
import com.pinkiptv.app.model.SessionResult
import com.pinkiptv.app.model.VodItem
import com.pinkiptv.app.model.VodPlaybackRef
import com.pinkiptv.app.model.filterSearchItems
import com.pinkiptv.app.model.isEffectiveSearchQuery
import com.pinkiptv.app.model.normalizeSearchText
import com.pinkiptv.app.model.toCatalogKindOrNull
import com.pinkiptv.app.model.toCatalogUiItem
import com.pinkiptv.app.state.SearchController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchControllerTest {
    @Test
    fun normalizationTrimsHandlesCaseAccentsAndUnicodeSafely() {
        assertEquals("acao", normalizeSearchText("  Ação  "))
        assertEquals("serie", normalizeSearchText("SÉRIE"))
        assertEquals("plain ascii", normalizeSearchText("Plain ASCII"))
        assertFalse(isEffectiveSearchQuery(" "))
        assertFalse(isEffectiveSearchQuery("a"))
        assertTrue(isEffectiveSearchQuery("ab"))
        assertTrue(isEffectiveSearchQuery("😀😀"))

        val items = listOf(
            search(SearchKind.Movies, "1", "Ação Total"),
            search(SearchKind.Series, "2", "Série Um"),
        )
        assertEquals(
            listOf("1"),
            filterSearchItems(items, "acao", SearchKind.All).map { it.providerId },
        )
        assertEquals(
            listOf("2"),
            filterSearchItems(items, "serie", SearchKind.All).map { it.providerId },
        )
    }

    @Test
    fun rankingIsExactThenPrefixThenContainsThenAlphabeticAndStableIdentity() {
        val items = listOf(
            search(SearchKind.Live, "9", "Canal Acao Noite"),
            search(SearchKind.Live, "8", "Acao Zeta"),
            search(SearchKind.Live, "7", "Acao Alfa"),
            search(SearchKind.Live, "3", "Acao"),
            search(SearchKind.Live, "2", "Acao"),
        )

        assertEquals(
            listOf("2", "3", "7", "8", "9"),
            filterSearchItems(items, "acao", SearchKind.All).map { it.providerId },
        )
    }

    @Test
    fun sameTitleAcrossKindsRemainsDistinctAndPerKindLimitIsDeterministic() {
        val duplicates = listOf(
            search(SearchKind.Live, "same", "News"),
            search(SearchKind.Movies, "same", "News"),
            search(SearchKind.Series, "same", "News"),
        )
        val distinct = filterSearchItems(duplicates, "news", SearchKind.All)
        assertEquals(3, distinct.size)
        assertEquals(
            setOf(SearchKind.Live, SearchKind.Movies, SearchKind.Series),
            distinct.map { it.kind }.toSet(),
        )

        val many = (0 until 5).flatMap { index ->
            listOf(
                search(SearchKind.Live, "L" + index, "Match " + index),
                search(SearchKind.Movies, "M" + index, "Match " + index),
            )
        }
        val limited = filterSearchItems(many, "match", SearchKind.All, limitPerKind = 2)
        assertEquals(4, limited.size)
        assertEquals(2, limited.count { it.kind == SearchKind.Live })
        assertEquals(2, limited.count { it.kind == SearchKind.Movies })
    }

    @Test
    fun threeSourcesLoadOnceAndQueryAndFilterStayLocal() = runTest {
        val repository = FakeCatalogRepository()
        val runtime = authenticatedRuntime()
        val controller = SearchController(repository, runtime, backgroundScope)
        runCurrent()

        controller.load()
        advanceUntilIdle()

        assertEquals(1, repository.liveCalls)
        assertEquals(1, repository.vodCalls)
        assertEquals(1, repository.seriesCalls)

        controller.updateQuery("one")
        controller.updateQuery("one ")
        controller.selectKind(SearchKind.Movies)
        controller.selectKind(SearchKind.All)
        controller.load()
        advanceUntilIdle()

        assertEquals(1, repository.liveCalls)
        assertEquals(1, repository.vodCalls)
        assertEquals(1, repository.seriesCalls)
    }

    @Test
    fun oneCharacterAndEmptyQueriesNeverRenderWholeCatalog() = runTest {
        val repository = FakeCatalogRepository()
        val runtime = authenticatedRuntime()
        val controller = SearchController(repository, runtime, backgroundScope)
        runCurrent()
        controller.load()
        advanceUntilIdle()

        controller.updateQuery(" ")
        assertEquals(SearchPhase.Inactive, controller.state.value.phase)
        assertTrue(controller.state.value.results.isEmpty())

        controller.updateQuery("o")
        assertEquals(SearchPhase.Inactive, controller.state.value.phase)
        assertTrue(controller.state.value.results.isEmpty())

        controller.updateQuery("one")
        assertEquals(SearchPhase.Ready, controller.state.value.phase)
        assertTrue(controller.state.value.results.isNotEmpty())
    }

    @Test
    fun partialFailureKeepsSuccessfulSourcesSearchableAndRetryReloadsOnlyFailure() = runTest {
        val repository = FakeCatalogRepository().apply {
            liveHandler = { CatalogResult.Failure(CatalogError.NetworkFailure) }
        }
        val runtime = authenticatedRuntime()
        val controller = SearchController(repository, runtime, backgroundScope)
        runCurrent()

        controller.load()
        advanceUntilIdle()
        controller.updateQuery("one")

        assertEquals(SearchPhase.PartialError, controller.state.value.phase)
        assertEquals(SearchSourceStatus.Error, controller.state.value.liveSource.status)
        assertEquals(SearchSourceStatus.Ready, controller.state.value.movieSource.status)
        assertEquals(SearchSourceStatus.Ready, controller.state.value.seriesSource.status)
        assertTrue(controller.state.value.results.any { it.kind == SearchKind.Movies })
        assertTrue(controller.state.value.results.any { it.kind == SearchKind.Series })

        repository.liveHandler = {
            CatalogResult.Success(listOf(live("101", "Live One")))
        }
        controller.retry()
        advanceUntilIdle()

        assertEquals(2, repository.liveCalls)
        assertEquals(1, repository.vodCalls)
        assertEquals(1, repository.seriesCalls)
        assertEquals(SearchPhase.Ready, controller.state.value.phase)
    }

    @Test
    fun allFailureAndMissingSessionAreRecoverableAndCredentialFree() = runTest {
        val failedRepository = FakeCatalogRepository().apply {
            liveHandler = { CatalogResult.Failure(CatalogError.HttpFailure) }
            vodHandler = { CatalogResult.Failure(CatalogError.InvalidResponse) }
            seriesHandler = { CatalogResult.Failure(CatalogError.NetworkFailure) }
        }
        val runtime = authenticatedRuntime()
        val controller = SearchController(failedRepository, runtime, backgroundScope)
        runCurrent()
        controller.load()
        advanceUntilIdle()

        assertEquals(SearchPhase.FullError, controller.state.value.phase)
        assertTrue(controller.state.value.results.isEmpty())

        val missingRepository = FakeCatalogRepository()
        val missing = SearchController(
            repository = missingRepository,
            providerSessionStore = RuntimeProviderSessionStore(),
            scope = backgroundScope,
        )
        runCurrent()
        missing.load()
        assertEquals(SearchPhase.FullError, missing.state.value.phase)
        assertEquals(0, missingRepository.liveCalls)
        assertEquals(0, missingRepository.vodCalls)
        assertEquals(0, missingRepository.seriesCalls)
    }

    @Test
    fun logoutClearsQueryResultsFilterAndRuntimeSourceCache() = runTest {
        val repository = FakeCatalogRepository()
        val runtime = authenticatedRuntime()
        val controller = SearchController(repository, runtime, backgroundScope)
        runCurrent()
        controller.load()
        advanceUntilIdle()
        controller.updateQuery("one")
        controller.selectKind(SearchKind.Movies)

        runtime.clear()
        runCurrent()

        val state = controller.state.value
        assertEquals("", state.query)
        assertEquals(SearchKind.All, state.selectedKind)
        assertEquals(SearchPhase.Inactive, state.phase)
        assertTrue(state.results.isEmpty())
        assertEquals(SearchSourceStatus.Idle, state.liveSource.status)
    }

    @Test
    fun newAccountDoesNotSeeOldCatalogAndLateOldLoadCannotRepopulate() = runTest {
        val oldLive = CompletableDeferred<CatalogResult<List<LiveStream>>>()
        val oldVod = CompletableDeferred<CatalogResult<List<VodItem>>>()
        val oldSeries = CompletableDeferred<CatalogResult<List<SeriesItem>>>()
        val repository = FakeCatalogRepository().apply {
            liveHandler = { oldLive.await() }
            vodHandler = { oldVod.await() }
            seriesHandler = { oldSeries.await() }
        }
        val runtime = authenticatedRuntime("account-a")
        val controller = SearchController(repository, runtime, backgroundScope)
        runCurrent()
        controller.load()
        runCurrent()

        controller.clear()
        runtime.clear()
        runtime.establish(
            "account-b",
            "fixture-pass", // pragma: allowlist secret
            success(),
        )
        repository.liveHandler = {
            CatalogResult.Success(listOf(live("B1", "Beta Live")))
        }
        repository.vodHandler = {
            CatalogResult.Success(listOf(vod("B2", "Beta Movie")))
        }
        repository.seriesHandler = {
            CatalogResult.Success(listOf(series("B3", "Beta Series")))
        }

        controller.load()
        advanceUntilIdle()
        controller.updateQuery("beta")
        assertEquals(setOf("B1", "B2", "B3"), controller.state.value.results.map { it.providerId }.toSet())

        oldLive.complete(CatalogResult.Success(listOf(live("A1", "Alpha Live"))))
        oldVod.complete(CatalogResult.Success(listOf(vod("A2", "Alpha Movie"))))
        oldSeries.complete(CatalogResult.Success(listOf(series("A3", "Alpha Series"))))
        advanceUntilIdle()

        assertEquals(setOf("B1", "B2", "B3"), controller.state.value.results.map { it.providerId }.toSet())
        assertFalse(controller.state.value.results.any { it.providerId.startsWith("A") })
    }

    @Test
    fun retryGenerationCannotOverwriteAReplacementSession() = runTest {
        val repository = FakeCatalogRepository().apply {
            liveHandler = { CatalogResult.Failure(CatalogError.NetworkFailure) }
        }
        val runtime = authenticatedRuntime("account-a")
        val controller = SearchController(repository, runtime, backgroundScope)
        runCurrent()
        controller.load()
        advanceUntilIdle()

        val retryLive = CompletableDeferred<CatalogResult<List<LiveStream>>>()
        repository.liveHandler = { retryLive.await() }
        controller.retry()
        runCurrent()

        controller.clear()
        runtime.clear()
        runtime.establish(
            "account-b",
            "fixture-pass", // pragma: allowlist secret
            success(),
        )
        repository.liveHandler = {
            CatalogResult.Success(listOf(live("B1", "Beta Live")))
        }
        repository.vodHandler = {
            CatalogResult.Success(listOf(vod("B2", "Beta Movie")))
        }
        repository.seriesHandler = {
            CatalogResult.Success(listOf(series("B3", "Beta Series")))
        }
        controller.load()
        advanceUntilIdle()
        controller.updateQuery("beta")

        retryLive.complete(CatalogResult.Success(listOf(live("A1", "Alpha Retry"))))
        advanceUntilIdle()

        assertEquals(setOf("B1", "B2", "B3"), controller.state.value.results.map { it.providerId }.toSet())
    }

    @Test
    fun resultActionsRetainExactTypedIdentityWithoutSeriesPrefetch() = runTest {
        val repository = FakeCatalogRepository().apply {
            vodHandler = {
                CatalogResult.Success(
                    listOf(
                        VodItem(
                            streamId = "202",
                            name = "Movie One",
                            categoryId = null,
                            artworkUrl = "https://art.invalid/movie.jpg",
                            containerExtension = "mkv",
                        ),
                    ),
                )
            }
        }
        val runtime = authenticatedRuntime()
        val controller = SearchController(repository, runtime, backgroundScope)
        runCurrent()
        controller.load()
        advanceUntilIdle()
        controller.updateQuery("one")

        val live = controller.state.value.results.single { it.kind == SearchKind.Live }
        val movie = controller.state.value.results.single { it.kind == SearchKind.Movies }
        val series = controller.state.value.results.single { it.kind == SearchKind.Series }

        assertEquals("101", (live.playbackRef as LivePlaybackRef).streamId)
        assertEquals("202", (movie.playbackRef as VodPlaybackRef).streamId)
        assertEquals("mkv", (movie.playbackRef as VodPlaybackRef).containerExtension)
        assertEquals("301", series.providerId)
        assertEquals(null, series.playbackRef)
        assertEquals(0, repository.seriesInfoCalls)

        assertEquals("202", movie.toCatalogUiItem().id)
        assertEquals("mkv", movie.toCatalogUiItem().subtitle)
        assertEquals(com.pinkiptv.app.model.CatalogKind.Movies, movie.kind.toCatalogKindOrNull())
    }

    @Test
    fun publicSearchModelsHaveNoCredentialOrProviderTransportFields() {
        val forbidden = listOf(
            "username",
            "password",
            "origin",
            "sessiontoken",
            "megatoken",
            "uri",
            "requesturl",
        )
        val fields = (
            SearchItem::class.java.declaredFields.map { it.name } +
                SearchUiState::class.java.declaredFields.map { it.name }
            ).map { it.lowercase() }

        forbidden.forEach { secretName ->
            assertFalse(
                "Unexpected Search field containing " + secretName,
                fields.any { it.contains(secretName) },
            )
        }
    }

    private fun authenticatedRuntime(username: String = "account-a") =
        RuntimeProviderSessionStore().apply {
            establish(
                username,
                "fixture-pass", // pragma: allowlist secret
                success(),
            )
        }

    private fun success() = SessionResult.Success(
        sessionToken = "fixture-session",
        sessionExpiresAt = null,
        xtreamBaseUrl = "https://catalog.invalid/",
        accountExpiresAt = null,
    )

    private fun search(
        kind: SearchKind,
        id: String,
        title: String,
    ) = SearchItem(
        kind = kind,
        providerId = id,
        title = title,
        artworkUrl = null,
        playbackRef = when (kind) {
            SearchKind.Live -> LivePlaybackRef(id, title)
            SearchKind.Movies -> VodPlaybackRef(id, title, "mp4")
            SearchKind.Series -> null
            SearchKind.All -> error("not a concrete result kind")
        },
    )

    private fun live(id: String, title: String) = LiveStream(
        streamId = id,
        name = title,
        categoryId = null,
        artworkUrl = null,
        streamType = "live",
    )

    private fun vod(id: String, title: String) = VodItem(
        streamId = id,
        name = title,
        categoryId = null,
        artworkUrl = null,
        containerExtension = "mp4",
    )

    private fun series(id: String, title: String) = SeriesItem(
        seriesId = id,
        name = title,
        categoryId = null,
        artworkUrl = null,
    )

    private class FakeCatalogRepository : CatalogRepository {
        var liveCalls = 0
        var vodCalls = 0
        var seriesCalls = 0
        var seriesInfoCalls = 0

        var liveHandler: suspend () -> CatalogResult<List<LiveStream>> = {
            CatalogResult.Success(listOf(liveStatic("101", "Live One")))
        }
        var vodHandler: suspend () -> CatalogResult<List<VodItem>> = {
            CatalogResult.Success(listOf(vodStatic("202", "Movie One")))
        }
        var seriesHandler: suspend () -> CatalogResult<List<SeriesItem>> = {
            CatalogResult.Success(listOf(seriesStatic("301", "Series One")))
        }

        override suspend fun liveCategories() =
            CatalogResult.Success(emptyList<CatalogCategory>())

        override suspend fun liveStreams(): CatalogResult<List<LiveStream>> {
            liveCalls += 1
            return liveHandler()
        }

        override suspend fun vodCategories() =
            CatalogResult.Success(emptyList<CatalogCategory>())

        override suspend fun vodStreams(): CatalogResult<List<VodItem>> {
            vodCalls += 1
            return vodHandler()
        }

        override suspend fun seriesCategories() =
            CatalogResult.Success(emptyList<CatalogCategory>())

        override suspend fun series(): CatalogResult<List<SeriesItem>> {
            seriesCalls += 1
            return seriesHandler()
        }

        override suspend fun seriesInfo(seriesId: String): CatalogResult<SeriesDetail> {
            seriesInfoCalls += 1
            return CatalogResult.Success(
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

        companion object {
            private fun liveStatic(id: String, title: String) = LiveStream(
                streamId = id,
                name = title,
                categoryId = null,
                artworkUrl = null,
                streamType = "live",
            )

            private fun vodStatic(id: String, title: String) = VodItem(
                streamId = id,
                name = title,
                categoryId = null,
                artworkUrl = null,
                containerExtension = "mp4",
            )

            private fun seriesStatic(id: String, title: String) = SeriesItem(
                seriesId = id,
                name = title,
                categoryId = null,
                artworkUrl = null,
            )
        }
    }
}
