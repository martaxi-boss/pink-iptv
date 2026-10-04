package com.pinkiptv.app

import com.pinkiptv.app.model.LivePlaybackRef
import com.pinkiptv.app.model.VodPlaybackRef
import com.pinkiptv.app.player.LiveSourceFallback
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSourceFallbackTest {
    @Test
    fun fallbackIsOncePerSelectionAndSessionClearPreventsReuse() {
        val retry = LiveSourceFallback()
        val live = LivePlaybackRef("101", "News")
        assertTrue(retry.take(live, 401, true))
        assertFalse(retry.take(live, 401, true))
        retry.reset()
        assertTrue(retry.take(live, 404, true))
        retry.clear()
        assertFalse(retry.take(live, 404, true))
    }

    @Test
    fun noFallbackForOtherMediaNetworkErrorsForbiddenOrAbsentSession() {
        val retry = LiveSourceFallback()
        val live = LivePlaybackRef("101", "News")
        assertFalse(retry.take(VodPlaybackRef("102", "Film", "mp4"), 401, true))
        assertFalse(retry.take(live, null, true))
        assertFalse(retry.take(live, 403, true))
        assertFalse(retry.take(live, 500, true))
        assertFalse(retry.take(live, 401, false))
        assertTrue(retry.take(live, 401, true))
    }
}
