package com.pinkiptv.app

import com.pinkiptv.app.model.HistoryItem
import com.pinkiptv.app.model.PlaybackKind
import com.pinkiptv.app.model.continueWatchingPositionMs
import com.pinkiptv.app.model.isContinueWatchingEligible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueWatchingPolicyTest {
    @Test
    fun vodAtThirtySecondsWithKnownSeekableDurationIsEligible() {
        val item = history(
            kind = PlaybackKind.Vod,
            position = 30_000L,
            duration = 120_000L,
            seekable = true,
        )
        assertTrue(item.isContinueWatchingEligible())
        assertEquals(30_000L, item.continueWatchingPositionMs())
    }

    @Test
    fun seriesEpisodeAtThirtySecondsIsEligible() {
        val item = history(
            kind = PlaybackKind.Series,
            position = 30_000L,
            duration = 100_000L,
            seekable = true,
        )
        assertTrue(item.isContinueWatchingEligible())
    }

    @Test
    fun shortUnknownNonSeekableLiveCatchUpAndCompletedAreExcluded() {
        assertFalse(
            history(PlaybackKind.Vod, 29_999L, 120_000L, true)
                .isContinueWatchingEligible(),
        )
        assertFalse(
            history(PlaybackKind.Vod, 30_000L, null, true)
                .isContinueWatchingEligible(),
        )
        assertFalse(
            history(PlaybackKind.Vod, 30_000L, 120_000L, false)
                .isContinueWatchingEligible(),
        )
        assertFalse(
            history(PlaybackKind.Vod, 30_000L, 59_999L, true)
                .isContinueWatchingEligible(),
        )
        assertFalse(
            history(PlaybackKind.Live, 30_000L, 120_000L, true)
                .isContinueWatchingEligible(),
        )
        assertFalse(
            history(PlaybackKind.CatchUp, 30_000L, 120_000L, true)
                .isContinueWatchingEligible(),
        )
        assertFalse(
            history(
                PlaybackKind.Vod,
                108_000L,
                120_000L,
                true,
            ).isContinueWatchingEligible(),
        )
        assertFalse(
            history(
                PlaybackKind.Vod,
                30_000L,
                120_000L,
                true,
                completed = true,
            ).isContinueWatchingEligible(),
        )
    }

    @Test
    fun invalidResumeDataFailsClosed() {
        assertNull(
            history(
                PlaybackKind.Series,
                -1L,
                120_000L,
                true,
            ).continueWatchingPositionMs(),
        )
        assertNull(
            history(
                PlaybackKind.Series,
                200_000L,
                120_000L,
                true,
            ).continueWatchingPositionMs(),
        )
    }

    private fun history(
        kind: PlaybackKind,
        position: Long,
        duration: Long?,
        seekable: Boolean,
        completed: Boolean = false,
    ) = HistoryItem(
        playbackKind = kind,
        mediaId = "10",
        title = "Item",
        artworkUrl = null,
        containerExtension = "mp4",
        lastPlayedAtEpochMs = 1L,
        lastPositionMs = position,
        durationMs = duration,
        seekable = seekable,
        completed = completed,
    )
}
