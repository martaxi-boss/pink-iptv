package com.pinkiptv.app.model

sealed interface PlaybackRef {
    val streamId: String
    val title: String
}

data class LivePlaybackRef(
    override val streamId: String,
    override val title: String,
    val artworkUrl: String? = null,
) : PlaybackRef

data class VodPlaybackRef(
    override val streamId: String,
    override val title: String,
    val containerExtension: String?,
    val artworkUrl: String? = null,
) : PlaybackRef

data class EpisodePlaybackRef(
    val episodeId: String,
    override val title: String,
    val containerExtension: String?,
    val artworkUrl: String? = null,
) : PlaybackRef {
    override val streamId: String
        get() = episodeId
}

enum class PlaybackKind {
    Live,
    Vod,
    Series,
}

enum class PlayerPhase {
    Idle,
    Preparing,
    Buffering,
    Playing,
    Paused,
    Ended,
    Error,
}

enum class PlayerError {
    SessionUnavailable,
    InvalidStreamMetadata,
    Network,
    SourceUnavailable,
    UnsupportedFormat,
    PlaybackError,
}

data class PlayerUiState(
    val phase: PlayerPhase = PlayerPhase.Idle,
    val title: String? = null,
    val kind: PlaybackKind? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long? = null,
    val positionMs: Long = 0L,
    val seekable: Boolean = false,
    val error: PlayerError? = null,
)
