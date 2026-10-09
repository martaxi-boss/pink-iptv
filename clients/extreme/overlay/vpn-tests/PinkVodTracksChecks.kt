package com.pinkiptv.extreme

import android.content.ContentValues
import android.provider.MediaStore
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.CheckedTextView
import androidx.media3.common.C
import androidx.media3.common.TrackGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.DefaultTrackNameProvider
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionView
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.UiDevice
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*

/** Real container extraction, real controller dialogs and decoder output; no provider fixture or VPN bypass. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PinkVodTracksChecks {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var player: ExoPlayer
    private lateinit var view: PlayerView
    private data class Track(val group: TrackGroup, val index: Int, val name: String, val language: String?)

    private fun tracks(type: Int): List<Track> {
        val names = DefaultTrackNameProvider(instrumentation.targetContext.resources)
        return player.currentTracks.groups.filter { it.type == type }.flatMap { group ->
            (0 until group.length).map { index ->
                val format = group.getTrackFormat(index)
                Track(group.mediaTrackGroup, index, names.getTrackName(format), format.language)
            }
        }
    }
    private fun await(label: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15000
        while (SystemClock.elapsedRealtime() < deadline) {
            var done = false
            instrumentation.runOnMainSync { done = condition() }
            if (done) return
            Thread.sleep(100)
        }
        throw AssertionError("Real Media3 fixture checkpoint unavailable: $label")
    }
    private fun descendants(root: View): List<View> = listOf(root) +
        if (root is ViewGroup) (0 until root.childCount).flatMap { descendants(root.getChildAt(it)) } else emptyList()

    private fun fixture(name: String, verify: (MainActivity) -> Unit) {
        val context = instrumentation.targetContext
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, if (name.endsWith("mkv")) "video/x-matroska" else "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/PINK-fixture")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values))
        instrumentation.context.assets.open("pink-vod/$name").use { source ->
            requireNotNull(resolver.openOutputStream(uri)).use { source.copyTo(it) }
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        lateinit var host: MainActivity
        var video: VideoActivity? = null
        try {
            instrumentation.runOnMainSync {
                host = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().single()
                // Exercise the real native bridge, VideoActivity, MediaItem and selector.
                // This is local test media; the remote provider VPN guard stays enabled.
                assertTrue(AndroidVideoBridge(host, { null }).launchVod("fixture", uri.toString(),
                    "", "", "fixture", "", 0))
            }
            await("production native VOD Activity") {
                video = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<VideoActivity>().singleOrNull()
                val nativeView = video?.findViewById<PlayerView>(R.id.player_view)
                val nativePlayer = nativeView?.player as? ExoPlayer
                if (nativePlayer == null) false else {
                    view = requireNotNull(nativeView); player = nativePlayer; true
                }
            }
            await("presented video and decoded audio") {
                player.playerError == null && player.isPlaying && player.currentPosition > 1000 &&
                    (player.videoDecoderCounters?.renderedOutputBufferCount ?: 0) > 0 &&
                    (player.audioDecoderCounters?.renderedOutputBufferCount ?: 0) > 0
            }
            verify(host)
            // The existing approved player hides visible controls on the first Back.
            instrumentation.runOnMainSync { view.showController() }
            UiDevice.getInstance(instrumentation).pressBack()
            await("Back hides native controls") { !view.isControllerFullyVisible }
            UiDevice.getInstance(instrumentation).pressBack()
            await("native Back returns to the same WebView host") {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).contains(host)
            }
            instrumentation.runOnMainSync {
                assertFalse(host.isFinishing)
                assertTrue(descendants(host.findViewById(android.R.id.content)).any { it is WebView })
            }
        } finally {
            instrumentation.runOnMainSync { video?.let { if (!it.isFinishing) it.finish() } }
            resolver.delete(uri, null, null)
            // Keep Tauri alive until the combined startup/media case publishes its result.
        }
    }

    private fun choose(type: Int, track: Track, expected: List<Track>) {
        instrumentation.runOnMainSync { view.showController() }
        onView(withId(if (type == C.TRACK_TYPE_AUDIO) androidx.media3.ui.R.id.exo_audio_track
            else androidx.media3.ui.R.id.exo_subtitle)).perform(click())
        onView(isAssignableFrom(TrackSelectionView::class.java)).check { selection, failure ->
            if (failure != null) throw failure
            val displayed = descendants(selection).filterIsInstance<CheckedTextView>()
                .filter { it.visibility == View.VISIBLE }.map { it.text.toString() }
            for (option in expected) assertEquals(1, displayed.count { it == option.name })
            assertEquals(expected.size, displayed.count { value -> expected.any { it.name == value } })
        }
        onView(withText(track.name)).perform(click())
        onView(withText(android.R.string.ok)).perform(click())
        await("selected real track") {
            player.currentTracks.groups.any { it.mediaTrackGroup == track.group && it.isTrackSelected(track.index) }
        }
        var before = 0
        instrumentation.runOnMainSync { before = player.videoDecoderCounters?.renderedOutputBufferCount ?: 0 }
        await("video continues after selection") {
            player.playerError == null && player.isPlaying &&
                (player.videoDecoderCounters?.renderedOutputBufferCount ?: 0) > before &&
                (player.audioDecoderCounters?.renderedOutputBufferCount ?: 0) > 0
        }
        if (type == C.TRACK_TYPE_AUDIO) await("selected audio decoder format") {
            player.audioFormat?.language == track.language
        }
    }

    // Interact with the *installed native Activity*, not source-string
    // assertions: the customer must be able to open the dots menu, see Cast,
    // see PiP and actually change image sizing while video is playing.
    private fun verifyOwnerPlaybackMenu() {
        instrumentation.runOnMainSync { view.showController() }
        onView(withId(R.id.pink_more_actions)).check(matches(isDisplayed())).perform(click())
        onView(withText(R.string.xt_video_display_mode)).check(matches(isDisplayed()))
        onView(withText(R.string.pink_video_picture_in_picture)).check(matches(isDisplayed()))
        onView(withText(R.string.pink_video_cast_screen)).check(matches(isDisplayed()))
        onView(withText(R.string.xt_video_playback_speed)).check(matches(isDisplayed()))
        onView(withText(R.string.xt_video_display_mode)).perform(click())
        onView(withText(R.string.xt_video_display_zoom)).perform(click())
        await("owner image Zoom actually applies") { view.resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM }
        instrumentation.runOnMainSync { view.showController() }
        onView(withId(R.id.pink_more_actions)).perform(click())
        onView(withText(R.string.xt_video_display_mode)).perform(click())
        onView(withText(R.string.xt_video_display_fit)).perform(click())
        await("owner image Fit restores") { view.resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT }
        report("PINK_ANDROID_NATIVE_DOTS_MENU_PIP_CAST_AND_REAL_DISPLAY_MODE=PASS")
    }

    fun singleRealAudioAndNoInventedSubtitles() = fixture("single-audio.mp4") { host ->
        verifyOwnerPlaybackMenu()
        lateinit var audio: List<Track>
        instrumentation.runOnMainSync {
            audio = tracks(C.TRACK_TYPE_AUDIO)
            assertEquals(1, audio.size)
            assertTrue(tracks(C.TRACK_TYPE_TEXT).isEmpty())
        }
        await("trackless CC is disabled") { !view.findViewById<View>(androidx.media3.ui.R.id.exo_subtitle).isEnabled }
        choose(C.TRACK_TYPE_AUDIO, audio.single(), audio)
        instrumentation.runOnMainSync {
            assertFalse(PinkVpnRuntime.isReady())
            assertFalse(AndroidVideoBridge(host, { null }).launchVod("fixture", "https://example.invalid/fixture.mp4",
                "", "", "fixture", "", 0))
            assertFalse(host.isFinishing)
        }
        report("REAL_MEDIA3_SINGLE_AUDIO_NO_SUBTITLES_AND_PROTECTED_LAUNCH_FALLBACK=PASS")
    }

    fun allRealAudioAndTextTracksIncludingForcedCanBeSelectedWithoutBreakingVideo() = fixture("multi-tracks.mkv") {
        lateinit var audio: List<Track>
        lateinit var text: List<Track>
        instrumentation.runOnMainSync {
            audio = tracks(C.TRACK_TYPE_AUDIO); text = tracks(C.TRACK_TYPE_TEXT)
            assertEquals(2, audio.size); assertEquals(2, text.size)
            assertTrue(player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.any { group ->
                (0 until group.length).any { group.getTrackFormat(it).selectionFlags and C.SELECTION_FLAG_FORCED != 0 }
            })
        }
        audio.forEach { choose(C.TRACK_TYPE_AUDIO, it, audio) }
        text.forEach { track ->
            choose(C.TRACK_TYPE_TEXT, track, text)
            await("selected subtitle produces real cues") {
                player.currentCues.cues.any { it.text?.toString() == if (track.language == "en") "PINK fixture EN" else "PINK fixture PT" }
            }
        }
        report("REAL_MEDIA3_MULTI_AUDIO_MULTI_SUBTITLE_FORCED_SELECTION_AND_PRESENTED_VIDEO=PASS")
    }
    private fun report(value: String) = instrumentation.sendStatus(2, android.os.Bundle().apply { putString("stream", "\n$value\n") })
}
