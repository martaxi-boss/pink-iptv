package com.pinkiptv.app

import com.pinkiptv.app.library.ActiveLibraryProfileStore
import com.pinkiptv.app.library.LocalLibraryRepository
import com.pinkiptv.app.library.RoomPlaybackActivityRecorder
import com.pinkiptv.app.model.CatchUpPlaybackRef
import com.pinkiptv.app.model.FavoriteItem
import com.pinkiptv.app.model.HistoryItem
import com.pinkiptv.app.model.LivePlaybackRef
import com.pinkiptv.app.model.PlaybackKind
import com.pinkiptv.app.model.PlayerPhase
import com.pinkiptv.app.model.PlayerUiState
import com.pinkiptv.app.model.VodPlaybackRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackActivityRecorderTest {
    @Test
    fun errorsAndCatchUpNeverCreateLongTermHistory() = runTest {
        val repository = FakeLibraryRepository()
        val profile = ActiveLibraryProfileStore().apply { activate("profile-a") }
        val recorder = RoomPlaybackActivityRecorder(
            repository = repository,
            profileStore = profile,
            scope = this,
            clockMs = { 1_000L },
        )

        recorder.onState(
            VodPlaybackRef("1", "Movie", "mp4"),
            PlayerUiState(phase = PlayerPhase.Error),
        )
        recorder.onState(
            CatchUpPlaybackRef("2", "Archive", "2026-01-01:10-00", 30),
            usableState(PlaybackKind.CatchUp, 30_000L),
        )
        advanceUntilIdle()

        assertTrue(repository.writes.isEmpty())
    }

    @Test
    fun firstUsableStateWritesThenProgressIsThrottledToTenSeconds() = runTest {
        val repository = FakeLibraryRepository()
        val profile = ActiveLibraryProfileStore().apply { activate("profile-a") }
        var now = 1_000L
        val recorder = RoomPlaybackActivityRecorder(
            repository = repository,
            profileStore = profile,
            scope = this,
            clockMs = { now },
            minimumWriteIntervalMs = 10_000L,
        )
        val ref = VodPlaybackRef("10", "Movie", "mp4")

        recorder.onState(ref, usableState(PlaybackKind.Vod, 30_000L))
        advanceUntilIdle()
        assertEquals(1, repository.writes.size)

        now = 1_500L
        recorder.onState(ref, usableState(PlaybackKind.Vod, 31_000L))
        advanceUntilIdle()
        assertEquals(1, repository.writes.size)

        now = 10_999L
        recorder.onState(ref, usableState(PlaybackKind.Vod, 39_000L))
        advanceUntilIdle()
        assertEquals(1, repository.writes.size)

        now = 11_000L
        recorder.onState(ref, usableState(PlaybackKind.Vod, 40_000L))
        advanceUntilIdle()
        assertEquals(2, repository.writes.size)
    }

    @Test
    fun pauseExitAndEndedFlushWithoutWaitingForThrottle() = runTest {
        val repository = FakeLibraryRepository()
        val profile = ActiveLibraryProfileStore().apply { activate("profile-a") }
        var now = 1_000L
        val recorder = RoomPlaybackActivityRecorder(
            repository = repository,
            profileStore = profile,
            scope = this,
            clockMs = { now },
        )
        val ref = VodPlaybackRef("10", "Movie", "mp4")

        recorder.onState(ref, usableState(PlaybackKind.Vod, 30_000L))
        advanceUntilIdle()

        now = 2_000L
        recorder.onState(
            ref,
            usableState(
                PlaybackKind.Vod,
                31_000L,
                phase = PlayerPhase.Paused,
            ),
        )
        advanceUntilIdle()

        now = 3_000L
        recorder.onExit(
            ref,
            PlayerUiState(
                phase = PlayerPhase.Buffering,
                positionMs = 32_000L,
            ),
        )
        advanceUntilIdle()

        now = 4_000L
        recorder.onState(
            ref,
            usableState(
                PlaybackKind.Vod,
                120_000L,
                phase = PlayerPhase.Ended,
            ),
        )
        advanceUntilIdle()

        assertEquals(4, repository.writes.size)
        assertTrue(repository.writes.last().second.completed)
    }

    @Test
    fun liveIsRecentButNeverContinueMetadataAndSessionClearStopsExitFlush() = runTest {
        val repository = FakeLibraryRepository()
        val profile = ActiveLibraryProfileStore().apply { activate("profile-a") }
        val recorder = RoomPlaybackActivityRecorder(
            repository = repository,
            profileStore = profile,
            scope = this,
            clockMs = { 1_000L },
        )
        val ref = LivePlaybackRef("99", "Live")

        recorder.onState(
            ref,
            PlayerUiState(
                phase = PlayerPhase.Playing,
                kind = PlaybackKind.Live,
                isPlaying = true,
                positionMs = 50_000L,
                durationMs = null,
                seekable = false,
            ),
        )
        advanceUntilIdle()

        assertEquals(1, repository.writes.size)
        assertEquals(PlaybackKind.Live, repository.writes.single().second.playbackKind)

        recorder.clearSession()
        recorder.onExit(ref, PlayerUiState(phase = PlayerPhase.Playing))
        advanceUntilIdle()
        assertEquals(1, repository.writes.size)
    }

    @Test
    fun persistedHistoryModelHasNoResolvedUriOrCredentialFields() {
        val names = HistoryItem::class.java.declaredFields.map { it.name.lowercase() }
        for (forbidden in listOf("uri", "url", "username", "password", "origin", "hostname")) {
            assertFalse(names.contains(forbidden))
        }
    }

    private fun usableState(
        kind: PlaybackKind,
        position: Long,
        phase: PlayerPhase = PlayerPhase.Playing,
    ) = PlayerUiState(
        phase = phase,
        kind = kind,
        isPlaying = phase == PlayerPhase.Playing,
        positionMs = position,
        durationMs = 120_000L,
        seekable = true,
    )

    private class FakeLibraryRepository : LocalLibraryRepository {
        val favorites = MutableStateFlow<List<FavoriteItem>>(emptyList())
        val history = MutableStateFlow<List<HistoryItem>>(emptyList())
        val writes = mutableListOf<Pair<String, HistoryItem>>()

        override fun observeFavorites(profileKey: String): Flow<List<FavoriteItem>> = favorites
        override fun observeHistory(profileKey: String): Flow<List<HistoryItem>> = history
        override suspend fun toggleFavorite(profileKey: String, item: FavoriteItem) = true
        override suspend fun removeFavorite(profileKey: String, item: FavoriteItem) = Unit
        override suspend fun upsertHistory(profileKey: String, item: HistoryItem) {
            writes += profileKey to item
        }
        override suspend fun clearHistory(profileKey: String) = Unit
    }
}
