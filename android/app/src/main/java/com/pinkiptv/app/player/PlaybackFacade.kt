package com.pinkiptv.app.player

import androidx.media3.common.Player
import com.pinkiptv.app.model.PlaybackRef
import com.pinkiptv.app.model.PlayerUiState
import kotlinx.coroutines.flow.StateFlow

interface PlaybackFacade {
    val uiState: StateFlow<PlayerUiState>
    val media3Player: Player?

    fun prepare(
        ref: PlaybackRef,
        startPositionMs: Long = 0L,
    )

    fun togglePlayPause()
    fun seekBy(deltaMs: Long)
    fun retry()
    fun close()
}

fun interface PlaybackFacadeFactory {
    fun create(): PlaybackFacade
}
