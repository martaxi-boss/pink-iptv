package com.pinkiptv.extreme

import android.net.Uri
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.CheckedTextView
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.ui.DefaultTrackNameProvider
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Real container extraction, real controller dialogs and decoder output; no provider fixture or VPN bypass. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PinkVodTracksTest {
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

    private fun fixture(name: String, verify: (ActivityScenario<MainActivity>) -> Unit) {
        val context = instrumentation.targetContext
        val file = context.cacheDir.resolve(name)
        instrumentation.context.assets.open("pink-vod/$name").use { source ->
            file.outputStream().use { source.copyTo(it) }
        }
        val activity = ActivityScenario.launch(MainActivity::class.java)
        var container: View? = null
        try {
            activity.onActivity { host ->
                val root = host.findViewById<ViewGroup>(android.R.id.content)
                // Test-only local media in the real host window, with the production layout
                // and selector. VideoActivity's protected-provider readiness guard is unchanged.
                val created = LayoutInflater.from(host).inflate(R.layout.activity_video, root, false)
                container = created
                root.addView(created)
                view = created.findViewById(R.id.player_view)
                // Avoid the host H.264 decoder implicated by this emulator's crash log.
                // Exercise real guest codecs; this test policy does not change the APK player.
                val renderers = DefaultRenderersFactory(host).setMediaCodecSelector { mime, secure, tunnel ->
                    MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunnel).sortedBy { !it.softwareOnly }
                }
                player = ExoPlayer.Builder(host, renderers).build()
                view.player = player
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                PinkVodTracks.attach(view, player)
                player.setMediaItem(androidx.media3.common.MediaItem.fromUri(Uri.fromFile(file)))
                player.prepare()
                player.playWhenReady = true
            }
            await("presented video and decoded audio") {
                player.playerError == null && player.isPlaying && player.currentPosition > 1000 &&
                    (player.videoDecoderCounters?.renderedOutputBufferCount ?: 0) > 0 &&
                    (player.audioDecoderCounters?.renderedOutputBufferCount ?: 0) > 0
            }
            verify(activity)
            activity.onActivity { host ->
                assertFalse(host.isFinishing)
                assertTrue(descendants(host.findViewById(android.R.id.content)).any { it is WebView })
            }
        } finally {
            activity.onActivity { host ->
                if (::player.isInitialized) { view.player = null; player.release() }
                container?.let { host.findViewById<ViewGroup>(android.R.id.content).removeView(it) }
            }
            activity.close()
            file.delete()
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

    @Test fun singleRealAudioAndNoInventedSubtitles() = fixture("single-audio.mp4") { activity ->
        lateinit var audio: List<Track>
        instrumentation.runOnMainSync {
            audio = tracks(C.TRACK_TYPE_AUDIO)
            assertEquals(1, audio.size)
            assertTrue(tracks(C.TRACK_TYPE_TEXT).isEmpty())
        }
        await("trackless CC is disabled") { !view.findViewById<View>(androidx.media3.ui.R.id.exo_subtitle).isEnabled }
        choose(C.TRACK_TYPE_AUDIO, audio.single(), audio)
        activity.onActivity { host ->
            assertFalse(PinkVpnRuntime.isReady())
            assertFalse(AndroidVideoBridge(host, { null }).launchVod("fixture", "https://example.invalid/fixture.mp4",
                "", "", "fixture", "", 0))
            assertFalse(host.isFinishing)
        }
        report("REAL_MEDIA3_SINGLE_AUDIO_NO_SUBTITLES_AND_PROTECTED_LAUNCH_FALLBACK=PASS")
    }

    @Test fun allRealAudioAndTextTracksIncludingForcedCanBeSelectedWithoutBreakingVideo() = fixture("multi-tracks.mkv") {
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
