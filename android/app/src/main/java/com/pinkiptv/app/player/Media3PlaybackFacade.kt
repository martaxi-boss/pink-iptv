package com.pinkiptv.app.player

import android.content.Context
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.pinkiptv.app.model.CatchUpPlaybackRef
import com.pinkiptv.app.model.EpisodePlaybackRef
import com.pinkiptv.app.model.LivePlaybackRef
import com.pinkiptv.app.model.PlaybackKind
import com.pinkiptv.app.model.PlaybackRef
import com.pinkiptv.app.model.PlayerError
import com.pinkiptv.app.model.PlayerPhase
import com.pinkiptv.app.model.PlayerUiState
import com.pinkiptv.app.model.RuntimeProviderSessionStore
import com.pinkiptv.app.network.PINK_XTREAM_USER_AGENT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class Media3PlaybackFacadeFactory(
    context: Context,
    private val sessionStore: RuntimeProviderSessionStore,
    private val client: OkHttpClient,
) : PlaybackFacadeFactory {
    private val applicationContext = context.applicationContext

    init {
        require(!client.followRedirects && !client.followSslRedirects) {
            "Playback redirects must remain disabled"
        }
    }

    override fun create(): PlaybackFacade =
        Media3PlaybackFacade(
            context = applicationContext,
            urlFactory = XtreamPlaybackUrlFactory(sessionStore),
            sessionStore = sessionStore,
            client = client,
        )
}

@OptIn(UnstableApi::class)
internal class Media3PlaybackFacade(
    context: Context,
    private val urlFactory: XtreamPlaybackUrlFactory,
    private val sessionStore: RuntimeProviderSessionStore,
    client: OkHttpClient,
) : PlaybackFacade {
    private val mutableState = MutableStateFlow(PlayerUiState())
    override val uiState: StateFlow<PlayerUiState> = mutableState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var currentRef: PlaybackRef? = null
    private var currentError: PlayerError? = null
    private var released = false

    private val player = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(context).setDataSourceFactory(
                OkHttpDataSource.Factory(client)
                    .setUserAgent(PINK_XTREAM_USER_AGENT),
            ),
        )
        .build()

    override val media3Player: Player
        get() = player

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            syncState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncState()
        }

        override fun onIsLoadingChanged(isLoading: Boolean) {
            syncState()
        }

        override fun onPlayerError(error: PlaybackException) {
            currentError = mapMedia3Error(error.errorCode)
            syncState()
        }
    }

    init {
        requireMainThread()
        player.addListener(listener)
        scope.launch {
            sessionStore.available.collect { available ->
                if (!available && currentRef != null && !released) {
                    player.stop()
                    player.clearMediaItems()
                    currentError = PlayerError.SessionUnavailable
                    syncState()
                }
            }
        }
        scope.launch {
            while (isActive) {
                delay(500)
                if (!released) {
                    syncState()
                }
            }
        }
    }

    override fun prepare(ref: PlaybackRef) {
        requireMainThread()
        if (released) return

        currentRef = ref
        currentError = null
        when (val result = urlFactory.resolve(ref)) {
            is PlaybackSourceResult.Failure -> {
                player.stop()
                player.clearMediaItems()
                currentError = result.error
                syncState()
            }
            is PlaybackSourceResult.Success -> {
                mutableState.value = PlayerUiState(
                    phase = PlayerPhase.Preparing,
                    title = ref.title,
                    kind = kindOf(ref),
                )
                val mediaItem = MediaItem.Builder()
                    .setMediaId(ref.streamId)
                    .setUri(result.source.url.toString())
                    .build()
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = true
            }
        }
    }

    override fun togglePlayPause() {
        requireMainThread()
        if (released || currentError != null) return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
        syncState()
    }

    override fun seekBy(deltaMs: Long) {
        requireMainThread()
        if (released || !mutableState.value.seekable) return
        val duration = mutableState.value.durationMs
        val target = (player.currentPosition + deltaMs).coerceAtLeast(0L)
        player.seekTo(
            if (duration == null) target else target.coerceAtMost(duration),
        )
        syncState()
    }

    override fun retry() {
        requireMainThread()
        val ref = currentRef ?: return
        if (released) return
        prepare(ref)
    }

    override fun close() {
        requireMainThread()
        if (released) return
        released = true
        player.removeListener(listener)
        player.stop()
        player.clearMediaItems()
        player.release()
        scope.cancel()
        currentRef = null
        currentError = null
        mutableState.value = PlayerUiState()
    }

    private fun syncState() {
        requireMainThread()
        if (released) return

        val ref = currentRef
        if (ref == null) {
            mutableState.value = PlayerUiState()
            return
        }

        val error = currentError
        if (error != null) {
            mutableState.value = PlayerUiState(
                phase = PlayerPhase.Error,
                title = ref.title,
                kind = kindOf(ref),
                error = error,
            )
            return
        }

        val duration = player.duration.takeIf { value ->
            value != C.TIME_UNSET && value >= 0L
        }
        val seekable = player.isCurrentMediaItemSeekable
        val phase = media3Phase(
            playbackState = player.playbackState,
            isPlaying = player.isPlaying,
        )

        mutableState.value = PlayerUiState(
            phase = phase,
            title = ref.title,
            kind = kindOf(ref),
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            durationMs = duration,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            seekable = seekable,
        )
    }

    private fun kindOf(ref: PlaybackRef): PlaybackKind =
        when (ref) {
            is LivePlaybackRef -> PlaybackKind.Live
            is EpisodePlaybackRef -> PlaybackKind.Series
            is CatchUpPlaybackRef -> PlaybackKind.CatchUp
            else -> PlaybackKind.Vod
        }

    private fun requireMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Player access must occur on the main thread"
        }
    }
}

internal fun media3Phase(
    playbackState: Int,
    isPlaying: Boolean,
): PlayerPhase =
    when (playbackState) {
        Player.STATE_BUFFERING -> PlayerPhase.Buffering
        Player.STATE_READY -> if (isPlaying) PlayerPhase.Playing else PlayerPhase.Paused
        Player.STATE_ENDED -> PlayerPhase.Ended
        else -> PlayerPhase.Preparing
    }

internal fun mapMedia3Error(errorCode: Int): PlayerError =
    when (errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> PlayerError.Network

        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
        -> PlayerError.SourceUnavailable

        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        -> PlayerError.UnsupportedFormat

        else -> PlayerError.PlaybackError
    }
