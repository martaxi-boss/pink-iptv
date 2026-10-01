package com.pinkiptv.app.model

enum class FavoriteKind {
    Live,
    Movie,
    Series,
}

enum class LibraryPhase {
    Inactive,
    Loading,
    Ready,
    Error,
}

enum class LibraryError {
    StorageUnavailable,
}

data class FavoriteItem(
    val kind: FavoriteKind,
    val providerId: String,
    val title: String,
    val artworkUrl: String?,
    val containerExtension: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

data class HistoryItem(
    val playbackKind: PlaybackKind,
    val mediaId: String,
    val title: String,
    val artworkUrl: String?,
    val containerExtension: String?,
    val lastPlayedAtEpochMs: Long,
    val lastPositionMs: Long,
    val durationMs: Long?,
    val seekable: Boolean,
    val completed: Boolean,
)

data class ContinueWatchingItem(
    val history: HistoryItem,
    val progressPercent: Int,
)

data class LibraryUiState(
    val phase: LibraryPhase = LibraryPhase.Inactive,
    val favorites: List<FavoriteItem> = emptyList(),
    val continueWatching: List<ContinueWatchingItem> = emptyList(),
    val recents: List<HistoryItem> = emptyList(),
    val error: LibraryError? = null,
)

fun FavoriteItem.toPlaybackRef(): PlaybackRef? =
    when (kind) {
        FavoriteKind.Live -> LivePlaybackRef(
            streamId = providerId,
            title = title,
            artworkUrl = artworkUrl,
        )
        FavoriteKind.Movie -> VodPlaybackRef(
            streamId = providerId,
            title = title,
            containerExtension = containerExtension,
            artworkUrl = artworkUrl,
        )
        FavoriteKind.Series -> null
    }

fun HistoryItem.toPlaybackRef(): PlaybackRef? =
    when (playbackKind) {
        PlaybackKind.Live -> LivePlaybackRef(
            streamId = mediaId,
            title = title,
            artworkUrl = artworkUrl,
        )
        PlaybackKind.Vod -> VodPlaybackRef(
            streamId = mediaId,
            title = title,
            containerExtension = containerExtension,
            artworkUrl = artworkUrl,
        )
        PlaybackKind.Series -> EpisodePlaybackRef(
            episodeId = mediaId,
            title = title,
            containerExtension = containerExtension,
            artworkUrl = artworkUrl,
        )
        PlaybackKind.CatchUp -> null
    }

fun HistoryItem.continueWatchingPositionMs(): Long? {
    val duration = durationMs ?: return null
    if (playbackKind != PlaybackKind.Vod && playbackKind != PlaybackKind.Series) return null
    if (!seekable || completed || duration < 60_000L) return null
    if (lastPositionMs < 30_000L || lastPositionMs < 0L) return null
    if (
        lastPositionMs > Long.MAX_VALUE / 10L ||
        duration > Long.MAX_VALUE / 9L ||
        lastPositionMs * 10L >= duration * 9L
    ) {
        return null
    }
    return lastPositionMs
}

fun HistoryItem.isContinueWatchingEligible(): Boolean =
    continueWatchingPositionMs() != null

fun HistoryItem.progressPercent(): Int {
    val duration = durationMs ?: return 0
    if (duration <= 0L) return 0
    val clamped = lastPositionMs.coerceIn(0L, duration)
    return ((clamped.toDouble() / duration.toDouble()) * 100.0)
        .toInt()
        .coerceIn(0, 100)
}
