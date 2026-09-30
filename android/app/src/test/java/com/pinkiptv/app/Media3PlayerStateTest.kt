package com.pinkiptv.app

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.pinkiptv.app.model.PlayerError
import com.pinkiptv.app.model.PlayerPhase
import com.pinkiptv.app.model.PlayerUiState
import com.pinkiptv.app.player.mapMedia3Error
import com.pinkiptv.app.player.media3Phase
import org.junit.Assert.assertEquals
import org.junit.Test

class Media3PlayerStateTest {
    @Test
    fun publicPlayerStateCoversIdleAndMedia3LifecyclePhases() {
        assertEquals(PlayerPhase.Idle, PlayerUiState().phase)
        assertEquals(
            PlayerPhase.Preparing,
            media3Phase(Player.STATE_IDLE, isPlaying = false),
        )
        assertEquals(
            PlayerPhase.Buffering,
            media3Phase(Player.STATE_BUFFERING, isPlaying = false),
        )
        assertEquals(
            PlayerPhase.Playing,
            media3Phase(Player.STATE_READY, isPlaying = true),
        )
        assertEquals(
            PlayerPhase.Paused,
            media3Phase(Player.STATE_READY, isPlaying = false),
        )
        assertEquals(
            PlayerPhase.Ended,
            media3Phase(Player.STATE_ENDED, isPlaying = false),
        )
    }

    @Test
    fun media3ErrorsMapToSafeClasses() {
        assertEquals(
            PlayerError.Network,
            mapMedia3Error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT),
        )
        assertEquals(
            PlayerError.SourceUnavailable,
            mapMedia3Error(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS),
        )
        assertEquals(
            PlayerError.UnsupportedFormat,
            mapMedia3Error(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED),
        )
        assertEquals(
            PlayerError.PlaybackError,
            mapMedia3Error(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED),
        )
    }
}
