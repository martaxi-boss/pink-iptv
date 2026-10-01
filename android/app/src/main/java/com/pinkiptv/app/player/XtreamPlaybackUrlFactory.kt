package com.pinkiptv.app.player

import com.pinkiptv.app.model.EpisodePlaybackRef
import com.pinkiptv.app.model.LivePlaybackRef
import com.pinkiptv.app.model.PlaybackKind
import com.pinkiptv.app.model.PlaybackRef
import com.pinkiptv.app.model.PlayerError
import com.pinkiptv.app.model.RuntimeProviderSessionStore
import com.pinkiptv.app.model.VodPlaybackRef
import okhttp3.HttpUrl

internal class ResolvedPlaybackSource(
    val kind: PlaybackKind,
    val title: String,
    internal val url: HttpUrl,
) {
    override fun toString(): String =
        "ResolvedPlaybackSource(kind=" + kind + ", uri=<redacted>)"
}

internal sealed interface PlaybackSourceResult {
    data class Success(val source: ResolvedPlaybackSource) : PlaybackSourceResult
    data class Failure(val error: PlayerError) : PlaybackSourceResult
}

internal class XtreamPlaybackUrlFactory(
    private val sessionStore: RuntimeProviderSessionStore,
) {
    internal fun resolve(ref: PlaybackRef): PlaybackSourceResult {
        val session = sessionStore.current()
            ?: return PlaybackSourceResult.Failure(PlayerError.SessionUnavailable)

        if (!STREAM_ID.matches(ref.streamId)) {
            return PlaybackSourceResult.Failure(PlayerError.InvalidStreamMetadata)
        }

        val builder = session.origin.newBuilder()
        val kind = when (ref) {
            is LivePlaybackRef -> {
                builder
                    .addPathSegment("live")
                    .addPathSegment(session.username)
                    .addPathSegment(session.password)
                    .addPathSegment(ref.streamId + ".ts")
                PlaybackKind.Live
            }
            is VodPlaybackRef -> {
                val extension = ref.containerExtension
                if (extension == null || !MEDIA_EXTENSION.matches(extension)) {
                    return PlaybackSourceResult.Failure(PlayerError.InvalidStreamMetadata)
                }
                builder
                    .addPathSegment("movie")
                    .addPathSegment(session.username)
                    .addPathSegment(session.password)
                    .addPathSegment(ref.streamId + "." + extension)
                PlaybackKind.Vod
            }
            is EpisodePlaybackRef -> {
                val extension = ref.containerExtension
                if (extension == null || !MEDIA_EXTENSION.matches(extension)) {
                    return PlaybackSourceResult.Failure(PlayerError.InvalidStreamMetadata)
                }
                builder
                    .addPathSegment("series")
                    .addPathSegment(session.username)
                    .addPathSegment(session.password)
                    .addPathSegment(ref.episodeId + "." + extension)
                PlaybackKind.Series
            }
        }

        return PlaybackSourceResult.Success(
            ResolvedPlaybackSource(
                kind = kind,
                title = ref.title,
                url = builder.build(),
            ),
        )
    }

    private companion object {
        val STREAM_ID = Regex("^[A-Za-z0-9_-]{1,64}$")
        val MEDIA_EXTENSION = Regex("^[A-Za-z0-9]{1,12}$")
    }
}
