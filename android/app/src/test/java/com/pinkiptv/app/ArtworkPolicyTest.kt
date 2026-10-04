package com.pinkiptv.app

import com.pinkiptv.app.ui.components.safeArtworkUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkPolicyTest {
    @Test
    fun optionalArtworkNeverAcceptsCredentialsOrPlaybackPaths() {
        assertNull(safeArtworkUrl("https://user:secret@art.invalid/logo.png"))
        assertNull(safeArtworkUrl("https://art.invalid/logo.png?password=secret"))
        assertNull(safeArtworkUrl("https://art.invalid/live/user/secret/1.ts"))
        assertNull(safeArtworkUrl("file:///etc/passwd"))
        assertEquals("https://art.invalid/poster.jpg", safeArtworkUrl("https://art.invalid/poster.jpg"))
    }
}
