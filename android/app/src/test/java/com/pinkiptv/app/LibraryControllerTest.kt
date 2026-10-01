package com.pinkiptv.app

import com.pinkiptv.app.library.ActiveLibraryProfileStore
import com.pinkiptv.app.library.LocalLibraryRepository
import com.pinkiptv.app.library.LocalProfileKey
import com.pinkiptv.app.model.CatalogKind
import com.pinkiptv.app.model.CatalogUiItem
import com.pinkiptv.app.model.FavoriteItem
import com.pinkiptv.app.model.FavoriteKind
import com.pinkiptv.app.model.HistoryItem
import com.pinkiptv.app.model.LibraryPhase
import com.pinkiptv.app.model.LivePlaybackRef
import com.pinkiptv.app.model.PlaybackKind
import com.pinkiptv.app.model.VodPlaybackRef
import com.pinkiptv.app.model.RuntimeProviderSessionStore
import com.pinkiptv.app.model.SessionResult
import com.pinkiptv.app.state.LibraryController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryControllerTest {
    @Test
    fun profileSwitchClearsVisibleStateWithoutDeletingSavedRows() = runTest {
        val repository = FakeLibraryRepository()
        val runtime = RuntimeProviderSessionStore()
        val profileStore = ActiveLibraryProfileStore()
        val profileA = LocalProfileKey.derive("account-a")
        val profileB = LocalProfileKey.derive("account-b")
        repository.favoriteFlow(profileA).value = listOf(favorite("A"))
        repository.historyFlow(profileA).value = listOf(history("A"))
        repository.favoriteFlow(profileB).value = listOf(favorite("B"))

        runtime.establish("account-a", "fixture-pass", success()) // pragma: allowlist secret
        val controller = LibraryController(
            repository = repository,
            providerSessionStore = runtime,
            profileStore = profileStore,
            scope = backgroundScope,
        )
        runCurrent()

        assertEquals(LibraryPhase.Ready, controller.state.value.phase)
        assertEquals(listOf("A"), controller.state.value.favorites.map { it.title })
        assertEquals(listOf("A"), controller.state.value.recents.map { it.title })
        assertEquals(profileA, profileStore.current())

        runtime.clear()
        runCurrent()

        assertEquals(LibraryPhase.Inactive, controller.state.value.phase)
        assertTrue(controller.state.value.favorites.isEmpty())
        assertTrue(controller.state.value.recents.isEmpty())
        assertEquals(listOf("A"), repository.favoriteFlow(profileA).value.map { it.title })

        runtime.establish("account-b", "fixture-pass", success()) // pragma: allowlist secret
        runCurrent()

        assertEquals(profileB, profileStore.current())
        assertEquals(listOf("B"), controller.state.value.favorites.map { it.title })
        assertFalse(controller.state.value.favorites.any { it.title == "A" })

        runtime.clear()
        runCurrent()
        runtime.establish("account-a", "fixture-pass", success()) // pragma: allowlist secret
        runCurrent()

        assertEquals(listOf("A"), controller.state.value.favorites.map { it.title })
        assertEquals(listOf("A"), controller.state.value.recents.map { it.title })
    }


    @Test
    fun favoritesSupportLiveMovieSeriesAndRepeatedToggleDoesNotDuplicate() = runTest {
        val repository = FakeLibraryRepository()
        val runtime = RuntimeProviderSessionStore()
        val profileStore = ActiveLibraryProfileStore()
        val profile = LocalProfileKey.derive("account-a")
        runtime.establish("account-a", "fixture-pass", success()) // pragma: allowlist secret
        val controller = LibraryController(
            repository = repository,
            providerSessionStore = runtime,
            profileStore = profileStore,
            scope = backgroundScope,
            clockMs = { 100L },
        )
        runCurrent()

        controller.toggleFavorite(
            CatalogKind.Live,
            catalogItem("shared", "Live", LivePlaybackRef("shared", "Live")),
        )
        controller.toggleFavorite(
            CatalogKind.Movies,
            catalogItem("shared", "Movie", VodPlaybackRef("shared", "Movie", "mp4")),
        )
        controller.toggleFavorite(
            CatalogKind.Series,
            catalogItem("series", "Series", null),
        )
        runCurrent()

        val first = repository.favoriteFlow(profile).value
        assertEquals(3, first.size)
        assertEquals(1, first.count { it.kind == FavoriteKind.Live && it.providerId == "shared" })
        assertEquals(1, first.count { it.kind == FavoriteKind.Movie && it.providerId == "shared" })
        assertEquals(1, first.count { it.kind == FavoriteKind.Series && it.providerId == "series" })

        controller.toggleFavorite(
            CatalogKind.Live,
            catalogItem("shared", "Live", LivePlaybackRef("shared", "Live")),
        )
        runCurrent()

        val second = repository.favoriteFlow(profile).value
        assertEquals(2, second.size)
        assertFalse(second.any { it.kind == FavoriteKind.Live && it.providerId == "shared" })
        assertTrue(second.any { it.kind == FavoriteKind.Movie && it.providerId == "shared" })
    }

    @Test
    fun continueWatchingIsDerivedOnlyFromEligibleHistory() = runTest {
        val repository = FakeLibraryRepository()
        val runtime = RuntimeProviderSessionStore()
        val profileStore = ActiveLibraryProfileStore()
        val profile = LocalProfileKey.derive("account-a")
        repository.historyFlow(profile).value = listOf(
            history(
                title = "Continue",
                kind = PlaybackKind.Vod,
                position = 30_000L,
                duration = 120_000L,
                seekable = true,
            ),
            history(
                title = "Live",
                kind = PlaybackKind.Live,
                position = 30_000L,
                duration = 120_000L,
                seekable = true,
            ),
            history(
                title = "Complete",
                kind = PlaybackKind.Series,
                position = 110_000L,
                duration = 120_000L,
                seekable = true,
            ),
        )

        runtime.establish("account-a", "fixture-pass", success()) // pragma: allowlist secret
        val controller = LibraryController(
            repository = repository,
            providerSessionStore = runtime,
            profileStore = profileStore,
            scope = backgroundScope,
        )
        runCurrent()

        assertEquals(listOf("Continue"), controller.state.value.continueWatching.map { it.history.title })
        assertEquals(25, controller.state.value.continueWatching.single().progressPercent)
    }


    @Test
    fun favoriteToggleSupportsLiveMovieSeriesAndKindScopedIdentity() = runTest {
        val repository = FakeLibraryRepository()
        val runtime = RuntimeProviderSessionStore()
        val profileStore = ActiveLibraryProfileStore()
        val controller = LibraryController(
            repository = repository,
            providerSessionStore = runtime,
            profileStore = profileStore,
            scope = backgroundScope,
            clockMs = { 10L },
        )
        runtime.establish("account-a", "fixture-pass", success()) // pragma: allowlist secret
        runCurrent()

        val live = catalogItem(
            id = "same-id",
            name = "Live",
            ref = LivePlaybackRef("same-id", "Live"),
        )
        val movie = catalogItem(
            id = "same-id",
            name = "Movie",
            ref = VodPlaybackRef("same-id", "Movie", "mp4"),
        )
        val series = catalogItem(
            id = "same-id",
            name = "Series",
            ref = null,
        )

        controller.toggleFavorite(CatalogKind.Live, live)
        controller.toggleFavorite(CatalogKind.Movies, movie)
        controller.toggleFavorite(CatalogKind.Series, series)
        runCurrent()

        assertEquals(3, controller.state.value.favorites.size)
        assertEquals(
            setOf(FavoriteKind.Live, FavoriteKind.Movie, FavoriteKind.Series),
            controller.state.value.favorites.map { it.kind }.toSet(),
        )

        controller.toggleFavorite(CatalogKind.Live, live)
        runCurrent()
        assertFalse(controller.state.value.favorites.any { it.kind == FavoriteKind.Live })

        controller.toggleFavorite(CatalogKind.Live, live)
        runCurrent()
        assertEquals(
            1,
            controller.state.value.favorites.count {
                it.kind == FavoriteKind.Live && it.providerId == "same-id"
            },
        )
    }

    @Test
    fun favoriteRemovalAndHistoryClearStayIndependent() = runTest {
        val repository = FakeLibraryRepository()
        val runtime = RuntimeProviderSessionStore()
        val profileStore = ActiveLibraryProfileStore()
        val profile = LocalProfileKey.derive("account-a")
        val savedFavorite = favorite("Favorite")
        repository.favoriteFlow(profile).value = listOf(savedFavorite)
        repository.historyFlow(profile).value = listOf(history("Recent"))

        runtime.establish("account-a", "fixture-pass", success()) // pragma: allowlist secret
        val controller = LibraryController(
            repository = repository,
            providerSessionStore = runtime,
            profileStore = profileStore,
            scope = backgroundScope,
        )
        runCurrent()

        controller.removeFavorite(savedFavorite)
        runCurrent()
        assertTrue(controller.state.value.favorites.isEmpty())
        assertEquals(listOf("Recent"), controller.state.value.recents.map { it.title })

        repository.favoriteFlow(profile).value = listOf(savedFavorite)
        runCurrent()
        controller.clearHistory()
        runCurrent()
        assertEquals(listOf("Favorite"), controller.state.value.favorites.map { it.title })
        assertTrue(controller.state.value.recents.isEmpty())
    }


    private fun success() = SessionResult.Success(
        sessionToken = "fixture-session",
        sessionExpiresAt = null,
        xtreamBaseUrl = "https://catalog.invalid/",
        accountExpiresAt = null,
    )


    private fun catalogItem(
        id: String,
        name: String,
        ref: com.pinkiptv.app.model.PlaybackRef?,
    ) = CatalogUiItem(
        id = id,
        name = name,
        categoryId = null,
        artworkUrl = null,
        subtitle = null,
        playbackRef = ref,
    )

    private fun favorite(title: String) = FavoriteItem(
        kind = FavoriteKind.Live,
        providerId = title,
        title = title,
        artworkUrl = null,
        containerExtension = null,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
    )

    private fun history(
        title: String,
        kind: PlaybackKind = PlaybackKind.Live,
        position: Long = 0L,
        duration: Long? = null,
        seekable: Boolean = false,
    ) = HistoryItem(
        playbackKind = kind,
        mediaId = title,
        title = title,
        artworkUrl = null,
        containerExtension = "mp4",
        lastPlayedAtEpochMs = 1L,
        lastPositionMs = position,
        durationMs = duration,
        seekable = seekable,
        completed = false,
    )

    private class FakeLibraryRepository : LocalLibraryRepository {
        private val favorites = mutableMapOf<String, MutableStateFlow<List<FavoriteItem>>>()
        private val history = mutableMapOf<String, MutableStateFlow<List<HistoryItem>>>()

        fun favoriteFlow(profileKey: String) =
            favorites.getOrPut(profileKey) { MutableStateFlow(emptyList()) }

        fun historyFlow(profileKey: String) =
            history.getOrPut(profileKey) { MutableStateFlow(emptyList()) }

        override fun observeFavorites(profileKey: String): Flow<List<FavoriteItem>> =
            favoriteFlow(profileKey)

        override fun observeHistory(profileKey: String): Flow<List<HistoryItem>> =
            historyFlow(profileKey)

        override suspend fun toggleFavorite(profileKey: String, item: FavoriteItem): Boolean {
            val flow = favoriteFlow(profileKey)
            val match = flow.value.any { it.kind == item.kind && it.providerId == item.providerId }
            flow.value = if (match) {
                flow.value.filterNot { it.kind == item.kind && it.providerId == item.providerId }
            } else {
                listOf(item) + flow.value
            }
            return !match
        }

        override suspend fun removeFavorite(profileKey: String, item: FavoriteItem) {
            val flow = favoriteFlow(profileKey)
            flow.value = flow.value.filterNot {
                it.kind == item.kind && it.providerId == item.providerId
            }
        }

        override suspend fun upsertHistory(profileKey: String, item: HistoryItem) {
            val flow = historyFlow(profileKey)
            flow.value = listOf(item) + flow.value.filterNot {
                it.playbackKind == item.playbackKind && it.mediaId == item.mediaId
            }
        }

        override suspend fun clearHistory(profileKey: String) {
            historyFlow(profileKey).value = emptyList()
        }
    }
}
