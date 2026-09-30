package com.pinkiptv.app

import com.pinkiptv.app.model.EpisodePlaybackRef
import com.pinkiptv.app.model.LivePlaybackRef
import com.pinkiptv.app.model.PlayerError
import com.pinkiptv.app.model.PlaybackKind
import com.pinkiptv.app.model.SeriesDetail
import com.pinkiptv.app.model.SeriesEpisode
import com.pinkiptv.app.model.PlayerUiState
import com.pinkiptv.app.model.VodPlaybackRef
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSecurityTest {
    @Test
    fun playbackReferencesAndPublicStateContainNoCredentialOrPlaybackUriFields() {
        val publicTypes = listOf(
            LivePlaybackRef::class.java,
            VodPlaybackRef::class.java,
            EpisodePlaybackRef::class.java,
            PlayerUiState::class.java,
            SeriesDetail::class.java,
            SeriesEpisode::class.java,
        )
        val forbidden = setOf(
            "username",
            "password",
            "uri",
            "playbackuri",
            "playbackurl",
            "sessiontoken",
        )

        for (type in publicTypes) {
            val names = type.declaredFields.map { it.name.lowercase() }
            for (word in forbidden) {
                assertFalse(type.simpleName + " leaked " + word, names.any { it == word })
            }
        }
    }

    @Test
    fun seriesPlaybackKindIsPublicAndSecretFree() {
        val state = PlayerUiState(kind = PlaybackKind.Series)
        assertTrue(state.toString().contains("Series"))
        assertFalse(state.toString().contains("fixture-pass"))
    }

    @Test
    fun safeStatesAndErrorsDoNotRenderCredentialMaterial() {
        val state = PlayerUiState(error = PlayerError.PlaybackError)
        val text = state.toString() + PlayerError.SessionUnavailable.toString()

        assertFalse(text.contains("fixture-user"))
        assertFalse(text.contains("fixture-pass"))
        assertFalse(text.contains("catalog.invalid"))
        assertTrue(text.contains("PlaybackError"))
    }
}
