package com.pinkiptv.app

import com.pinkiptv.app.model.CatchUpPlaybackRef
import com.pinkiptv.app.model.PlaybackKind
import com.pinkiptv.app.model.PlayerError
import com.pinkiptv.app.model.PlayerUiState
import com.pinkiptv.app.model.RuntimeProviderSessionStore
import com.pinkiptv.app.model.SessionResult
import com.pinkiptv.app.player.PlaybackSourceResult
import com.pinkiptv.app.player.XtreamPlaybackUrlFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatchUpPlaybackTest {
    @Test
    fun timeshiftPreservesHttpHttpsExplicitPortAndCanonicalHlsPath() {
        val http = factory("http://catalog.invalid:8080/").source(
            CatchUpPlaybackRef("101", "Past", "2026-09-30:12-34", 61),
        )
        assertEquals("http", http.url.scheme)
        assertEquals("catalog.invalid", http.url.host)
        assertEquals(8080, http.url.port)
        assertEquals(PlaybackKind.CatchUp, http.kind)
        assertEquals(
            "/timeshift/fixture-user/fixture-pass/61/2026-09-30:12-34/101.m3u8",
            http.url.encodedPath,
        )

        val https = factory("https://catalog.invalid:8443/").source(
            CatchUpPlaybackRef("202", "Past", "2026-09-30:09-05", 30),
        )
        assertEquals("https", https.url.scheme)
        assertEquals(8443, https.url.port)
        assertTrue(https.url.encodedPath.endsWith("/202.m3u8"))
    }

    @Test
    fun unsafeTimeshiftMetadataFailsClosed() {
        val factory = factory("https://catalog.invalid/")

        for (streamId in listOf("../1", "1/2", "1\\2", "1?2", "1 2")) {
            assertFailure(
                factory.resolve(
                    CatchUpPlaybackRef(streamId, "Past", "2026-09-30:12-34", 30),
                ),
            )
        }
        for (duration in listOf(0, 1441, -1)) {
            assertFailure(
                factory.resolve(
                    CatchUpPlaybackRef("1", "Past", "2026-09-30:12-34", duration),
                ),
            )
        }
        for (
            start in listOf(
                "bad",
                "2026-09-30 12:34",
                "../2026-09-30:12-34",
                "2026-99-99:99-99",
            )
        ) {
            assertFailure(
                factory.resolve(
                    CatchUpPlaybackRef("1", "Past", start, 30),
                ),
            )
        }
    }

    @Test
    fun catchUpReferenceAndPublicPlayerStateRemainCredentialFree() {
        val ref = CatchUpPlaybackRef(
            streamId = "1",
            title = "Past",
            providerStart = "2026-09-30:12-34",
            durationMinutes = 30,
        )
        val fields = CatchUpPlaybackRef::class.java.declaredFields
            .map { it.name.lowercase() }
        for (forbidden in listOf("username", "password", "uri", "url", "hostname", "sessiontoken")) {
            assertFalse(fields.contains(forbidden))
        }

        val state = PlayerUiState(kind = PlaybackKind.CatchUp)
        assertTrue(state.toString().contains("CatchUp"))
        assertFalse(state.toString().contains("fixture-pass"))
    }

    @Test
    fun resolvedTimeshiftToStringStaysRedacted() {
        val source = factory("https://catalog.invalid/").source(
            CatchUpPlaybackRef("1", "Past", "2026-09-30:12-34", 30),
        )
        val text = source.toString()
        assertTrue(text.contains("<redacted>"))
        assertFalse(text.contains("fixture-user"))
        assertFalse(text.contains("fixture-pass"))
        assertFalse(text.contains("catalog.invalid"))
        assertFalse(text.contains("timeshift"))
    }

    private fun assertFailure(result: PlaybackSourceResult) {
        assertTrue(result is PlaybackSourceResult.Failure)
        assertEquals(
            PlayerError.InvalidStreamMetadata,
            (result as PlaybackSourceResult.Failure).error,
        )
    }

    private fun factory(origin: String): TestFactory {
        val store = RuntimeProviderSessionStore()
        assertTrue(
            store.establish(
                username = "fixture-user",
                password = "fixture-pass", // pragma: allowlist secret
                result = success(origin),
            ),
        )
        return TestFactory(XtreamPlaybackUrlFactory(store))
    }

    private fun success(origin: String) = SessionResult.Success(
        sessionToken = "fixture-session",
        sessionExpiresAt = null,
        xtreamBaseUrl = origin,
        accountExpiresAt = null,
    )

    private class TestFactory(
        private val delegate: XtreamPlaybackUrlFactory,
    ) {
        fun resolve(ref: CatchUpPlaybackRef) = delegate.resolve(ref)

        fun source(ref: CatchUpPlaybackRef) =
            (delegate.resolve(ref) as PlaybackSourceResult.Success).source
    }
}
