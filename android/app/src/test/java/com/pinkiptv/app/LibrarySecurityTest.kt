package com.pinkiptv.app

import com.pinkiptv.app.library.FavoriteEntity
import com.pinkiptv.app.library.HistoryEntity
import com.pinkiptv.app.model.FavoriteItem
import com.pinkiptv.app.model.FavoriteKind
import com.pinkiptv.app.model.HistoryItem
import com.pinkiptv.app.model.LivePlaybackRef
import com.pinkiptv.app.model.PlaybackKind
import com.pinkiptv.app.model.VodPlaybackRef
import com.pinkiptv.app.model.toPlaybackRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySecurityTest {
    @Test
    fun roomEntitiesContainNoPlaintextAccountCredentialOrProviderUrlFields() {
        val forbidden = listOf(
            "username",
            "password",
            "origin",
            "hostname",
            "sessiontoken",
            "megatoken",
            "uri",
            "url",
        )

        for (type in listOf(FavoriteEntity::class.java, HistoryEntity::class.java)) {
            val fields = type.declaredFields.map { it.name.lowercase() }
            for (name in forbidden) {
                assertFalse(type.simpleName + " leaked " + name, fields.contains(name))
            }
        }

        assertTrue(
            FavoriteEntity::class.java.declaredFields.any { it.name == "profileKey" },
        )
        assertTrue(
            HistoryEntity::class.java.declaredFields.any { it.name == "profileKey" },
        )
    }

    @Test
    fun favoritesReconstructOnlyTypedNonSecretReferences() {
        val live = favorite(FavoriteKind.Live, "10", null).toPlaybackRef()
        val movie = favorite(FavoriteKind.Movie, "20", "mp4").toPlaybackRef()
        val series = favorite(FavoriteKind.Series, "30", null).toPlaybackRef()

        assertTrue(live is LivePlaybackRef)
        assertTrue(movie is VodPlaybackRef)
        assertNull(series)
        assertEquals("10", live?.streamId)
        assertEquals("20", movie?.streamId)
    }

    @Test
    fun historyReconstructsTypedRefsButCatchUpFailsClosed() {
        assertTrue(history(PlaybackKind.Live, "10", null).toPlaybackRef() is LivePlaybackRef)
        assertTrue(history(PlaybackKind.Vod, "20", "mp4").toPlaybackRef() is VodPlaybackRef)
        assertEquals("30", history(PlaybackKind.Series, "30", "mkv").toPlaybackRef()?.streamId)
        assertNull(history(PlaybackKind.CatchUp, "40", null).toPlaybackRef())
    }

    private fun favorite(
        kind: FavoriteKind,
        id: String,
        extension: String?,
    ) = FavoriteItem(
        kind = kind,
        providerId = id,
        title = "Item",
        artworkUrl = null,
        containerExtension = extension,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
    )

    private fun history(
        kind: PlaybackKind,
        id: String,
        extension: String?,
    ) = HistoryItem(
        playbackKind = kind,
        mediaId = id,
        title = "Item",
        artworkUrl = null,
        containerExtension = extension,
        lastPlayedAtEpochMs = 1L,
        lastPositionMs = 0L,
        durationMs = null,
        seekable = false,
        completed = false,
    )
}
