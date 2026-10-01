package com.pinkiptv.app

import com.pinkiptv.app.model.CatchUpPlaybackRef
import com.pinkiptv.app.model.EpgChannel
import com.pinkiptv.app.model.EpgProgramme
import com.pinkiptv.app.model.EpgUiState
import org.junit.Assert.assertFalse
import org.junit.Test

class EpgSecurityTest {
    @Test
    fun publicEpgAndCatchUpModelsHaveNoCredentialUriOriginOrSessionFields() {
        val publicTypes = listOf(
            CatchUpPlaybackRef::class.java,
            EpgChannel::class.java,
            EpgProgramme::class.java,
            EpgUiState::class.java,
        )
        val forbidden = setOf(
            "username",
            "password",
            "uri",
            "url",
            "origin",
            "hostname",
            "sessiontoken",
            "requesturl",
        )

        for (type in publicTypes) {
            val fields = type.declaredFields.map { it.name.lowercase() }
            for (name in forbidden) {
                assertFalse(type.simpleName + " leaked " + name, fields.contains(name))
            }
        }
    }

    @Test
    fun publicEpgStateDoesNotRenderFixtureCredentialMaterial() {
        val text = EpgUiState().toString() +
            EpgProgramme(
                programmeId = "1",
                title = "Programme",
                description = null,
                startProvider = null,
                endProvider = null,
                startTimestamp = null,
                stopTimestamp = null,
                nowPlaying = false,
                hasArchive = false,
            ).toString()

        assertFalse(text.contains("fixture-user"))
        assertFalse(text.contains("fixture-pass"))
        assertFalse(text.contains("catalog.invalid"))
        assertFalse(text.contains("/timeshift/"))
    }
}
