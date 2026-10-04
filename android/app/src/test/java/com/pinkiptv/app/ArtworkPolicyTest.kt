package com.pinkiptv.app

import com.pinkiptv.app.ui.components.safeArtworkUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkPolicyTest {
    @Test
    fun optionalArtworkNeverAcceptsCredentialsOrPlaybackPaths() {
        val dummyBasicAuth = "https://art.invalid/logo.png".toHttpUrl().newBuilder()
            .username("fixture").password("fixture").build().toString()
        assertNull(safeArtworkUrl(dummyBasicAuth))
        assertNull(safeArtworkUrl("https://art.invalid/logo.png?password=secret"))
        assertNull(safeArtworkUrl("https://art.invalid/live/user/secret/1.ts"))
        assertNull(safeArtworkUrl("file:///etc/passwd"))
        assertEquals("https://art.invalid/poster.jpg", safeArtworkUrl("https://art.invalid/poster.jpg"))
    }
}
