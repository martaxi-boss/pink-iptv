package com.pinkiptv.app

import com.pinkiptv.app.model.LivePlaybackRef
import com.pinkiptv.app.model.PlayerError
import com.pinkiptv.app.model.PlayerUiState
import com.pinkiptv.app.model.VodPlaybackRef
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSecurityTest {
    @Test
    fun playbackReferencesAndPublicStateContainNoCredentialOrUriFields() {
        val publicTypes = listOf(
            LivePlaybackRef::class.java,
            VodPlaybackRef::class.java,
            PlayerUiState::class.java,
        )
        val forbidden = listOf("username", "password", "uri", "url", "token")

        for (type in publicTypes) {
            val names = type.declaredFields.map { it.name.lowercase() }
            for (word in forbidden) {
                assertFalse(type.simpleName + " leaked " + word, names.any { it.contains(word) })
            }
        }
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
