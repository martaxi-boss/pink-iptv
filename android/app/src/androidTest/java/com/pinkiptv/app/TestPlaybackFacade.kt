package com.pinkiptv.app

import androidx.media3.common.Player
import com.pinkiptv.app.model.PlaybackRef
import com.pinkiptv.app.model.PlayerPhase
import com.pinkiptv.app.model.PlayerUiState
import com.pinkiptv.app.player.PlaybackFacade
import com.pinkiptv.app.player.PlaybackFacadeFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TestPlaybackFacadeFactory(
    private val initialState: PlayerUiState,
) : PlaybackFacadeFactory {
    lateinit var lastFacade: TestPlaybackFacade

    override fun create(): PlaybackFacade =
        TestPlaybackFacade(initialState).also { lastFacade = it }
}

class TestPlaybackFacade(
    initialState: PlayerUiState,
) : PlaybackFacade {
    private val mutableState = MutableStateFlow(initialState)
    override val uiState: StateFlow<PlayerUiState> = mutableState
    override val media3Player: Player? = null

    var preparedRef: PlaybackRef? = null
    var retryCount = 0
    var closeCount = 0
    var seekTotalMs = 0L

    override fun prepare(ref: PlaybackRef) {
        preparedRef = ref
    }

    override fun togglePlayPause() {
        val current = mutableState.value
        mutableState.value = current.copy(
            phase = if (current.isPlaying) PlayerPhase.Paused else PlayerPhase.Playing,
            isPlaying = !current.isPlaying,
        )
    }

    override fun seekBy(deltaMs: Long) {
        seekTotalMs += deltaMs
    }

    override fun retry() {
        retryCount += 1
    }

    override fun close() {
        closeCount += 1
    }
}
