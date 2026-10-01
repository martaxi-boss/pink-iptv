package com.pinkiptv.app

import com.pinkiptv.app.model.CatalogUiItem
import com.pinkiptv.app.model.EpisodePlaybackRef
import com.pinkiptv.app.model.LivePlaybackRef
import com.pinkiptv.app.model.VodPlaybackRef
import com.pinkiptv.app.state.PlaybackSelectionController
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSelectionControllerTest {
    @Test
    fun liveAndVodSelectTypedReferencesButSeriesDoesNot() {
        val controller = PlaybackSelectionController()
        val live = LivePlaybackRef("10", "Live")
        val vod = VodPlaybackRef("20", "Movie", "mp4")

        assertTrue(controller.select(item("10", live)))
        assertSame(live, controller.selection.value)

        assertTrue(controller.select(item("20", vod)))
        assertSame(vod, controller.selection.value)

        controller.clear()
        assertNull(controller.selection.value)

        assertFalse(controller.select(item("30", null)))
        assertNull(controller.selection.value)
    }

    @Test
    fun episodeReferenceCanBeSelectedAndClearedWithoutCredentials() {
        val controller = PlaybackSelectionController()
        val episode = EpisodePlaybackRef("501", "Episode", "mkv")

        assertTrue(controller.select(episode))
        assertSame(episode, controller.selection.value)
        controller.clear()
        assertNull(controller.selection.value)
    }

    @Test
    fun retryIdentityCanRemainTheSameOpaqueReference() {
        val controller = PlaybackSelectionController()
        val episode = EpisodePlaybackRef("44", "Episode", "mkv")

        controller.select(episode)
        val first = controller.selection.value
        controller.select(episode)
        assertSame(first, controller.selection.value)
    }

    private fun item(
        id: String,
        ref: com.pinkiptv.app.model.PlaybackRef?,
    ) = CatalogUiItem(
        id = id,
        name = "Item",
        categoryId = null,
        artworkUrl = null,
        subtitle = null,
        playbackRef = ref,
    )
}
