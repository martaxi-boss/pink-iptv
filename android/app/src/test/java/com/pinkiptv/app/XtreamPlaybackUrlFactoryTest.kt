package com.pinkiptv.app

import com.pinkiptv.app.model.LivePlaybackRef
import com.pinkiptv.app.model.PlayerError
import com.pinkiptv.app.model.RuntimeProviderSessionStore
import com.pinkiptv.app.model.SessionResult
import com.pinkiptv.app.model.VodPlaybackRef
import com.pinkiptv.app.player.PlaybackSourceResult
import com.pinkiptv.app.player.XtreamPlaybackUrlFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamPlaybackUrlFactoryTest {
    @Test
    fun livePreservesHttpHttpsAndExplicitPort() {
        val http = factory("http://catalog.invalid:8080/")
            .source(LivePlaybackRef("101", "News"))
        assertEquals("http", http.url.scheme)
        assertEquals("catalog.invalid", http.url.host)
        assertEquals(8080, http.url.port)
        assertEquals("/live/fixture-user/fixture-pass/101.ts", http.url.encodedPath)

        val https = factory("https://catalog.invalid:8443/")
            .source(LivePlaybackRef("202", "Sports"))
        assertEquals("https", https.url.scheme)
        assertEquals("catalog.invalid", https.url.host)
        assertEquals(8443, https.url.port)
        assertEquals("/live/fixture-user/fixture-pass/202.ts", https.url.encodedPath)
    }

    @Test
    fun liveCredentialsAreEncodedAsPathSegmentsAndHostNeverChanges() {
        val store = RuntimeProviderSessionStore()
        assertTrue(
            store.establish(
                username = "fixture/user",
                password = "fixture/pass", // pragma: allowlist secret
                result = success("https://catalog.invalid/"),
            ),
        )
        val source = (XtreamPlaybackUrlFactory(store).resolve(
            LivePlaybackRef("303", "Encoded"),
        ) as PlaybackSourceResult.Success).source

        assertEquals("catalog.invalid", source.url.host)
        assertEquals(
            "/live/fixture%2Fuser/fixture%2Fpass/303.ts",
            source.url.encodedPath,
        )
    }

    @Test
    fun invalidLiveStreamIdFailsClosed() {
        val result = factory("https://catalog.invalid/").resolve(
            LivePlaybackRef("../303", "Bad"),
        )
        assertEquals(
            PlayerError.InvalidStreamMetadata,
            (result as PlaybackSourceResult.Failure).error,
        )
    }

    @Test
    fun vodUsesValidatedExtensionAndRejectsMissingOrUnsafeValues() {
        val factory = factory("https://catalog.invalid/")

        val valid = factory.source(
            VodPlaybackRef("404", "Movie", "mkv"),
        )
        assertEquals(
            "/movie/fixture-user/fixture-pass/404.mkv",
            valid.url.encodedPath,
        )

        for (extension in listOf(null, "../mp4", "mp/4", "mp\\4", "mp4?x", "mp4#x", "m p4")) {
            val result = factory.resolve(
                VodPlaybackRef("404", "Movie", extension),
            )
            assertTrue(result is PlaybackSourceResult.Failure)
        }
    }

    @Test
    fun missingOrClearedRuntimeSessionFailsClosedWithoutSecretText() {
        val store = RuntimeProviderSessionStore()
        val factory = XtreamPlaybackUrlFactory(store)

        var result = factory.resolve(LivePlaybackRef("1", "One"))
        assertEquals(
            PlayerError.SessionUnavailable,
            (result as PlaybackSourceResult.Failure).error,
        )

        store.establish(
            "fixture-user",
            "fixture-pass", // pragma: allowlist secret
            success("https://catalog.invalid/"),
        )
        store.clear()
        result = factory.resolve(LivePlaybackRef("1", "One"))
        val text = result.toString()
        assertTrue(result is PlaybackSourceResult.Failure)
        assertFalse(text.contains("fixture-user"))
        assertFalse(text.contains("fixture-pass"))
        assertFalse(text.contains("catalog.invalid"))
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
        fun resolve(ref: com.pinkiptv.app.model.PlaybackRef) = delegate.resolve(ref)

        fun source(ref: com.pinkiptv.app.model.PlaybackRef) =
            (delegate.resolve(ref) as PlaybackSourceResult.Success).source
    }
}
