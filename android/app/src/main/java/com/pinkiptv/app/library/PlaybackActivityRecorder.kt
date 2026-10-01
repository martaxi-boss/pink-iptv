package com.pinkiptv.app.library

import com.pinkiptv.app.model.CatchUpPlaybackRef
import com.pinkiptv.app.model.EpisodePlaybackRef
import com.pinkiptv.app.model.HistoryItem
import com.pinkiptv.app.model.LivePlaybackRef
import com.pinkiptv.app.model.PlaybackKind
import com.pinkiptv.app.model.PlaybackRef
import com.pinkiptv.app.model.PlayerPhase
import com.pinkiptv.app.model.PlayerUiState
import com.pinkiptv.app.model.VodPlaybackRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

interface PlaybackActivityRecorder {
    fun onState(ref: PlaybackRef, state: PlayerUiState)
    fun onExit(ref: PlaybackRef, state: PlayerUiState)
    fun clearSession()
}

object NoopPlaybackActivityRecorder : PlaybackActivityRecorder {
    override fun onState(ref: PlaybackRef, state: PlayerUiState) = Unit
    override fun onExit(ref: PlaybackRef, state: PlayerUiState) = Unit
    override fun clearSession() = Unit
}

class RoomPlaybackActivityRecorder(
    private val repository: LocalLibraryRepository,
    private val profileStore: ActiveLibraryProfileStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val minimumWriteIntervalMs: Long = 10_000L,
) : PlaybackActivityRecorder {
    private val lastWriteAt = mutableMapOf<String, Long>()
    private val usableSeen = mutableSetOf<String>()
    private val lastUsableState = mutableMapOf<String, PlayerUiState>()

    override fun onState(ref: PlaybackRef, state: PlayerUiState) {
        val profileKey = profileStore.current() ?: return
        val identity = identity(profileKey, ref) ?: return
        if (!isUsable(state.phase)) return

        synchronized(this) {
            usableSeen += identity
            lastUsableState[identity] = state
        }

        val now = clockMs()
        val boundary = state.phase == PlayerPhase.Paused || state.phase == PlayerPhase.Ended
        val shouldWrite = synchronized(this) {
            val previous = lastWriteAt[identity]
            boundary || previous == null || now - previous >= minimumWriteIntervalMs
        }
        if (!shouldWrite) return

        synchronized(this) { lastWriteAt[identity] = now }
        scheduleWrite(profileKey, ref, state, now)
    }

    override fun onExit(ref: PlaybackRef, state: PlayerUiState) {
        val profileKey = profileStore.current() ?: return
        val identity = identity(profileKey, ref) ?: return
        val snapshot = synchronized(this) {
            if (identity !in usableSeen) return
            if (isUsable(state.phase)) state else lastUsableState[identity]
        } ?: return

        val now = clockMs()
        synchronized(this) { lastWriteAt[identity] = now }
        scheduleWrite(profileKey, ref, snapshot, now)
    }

    override fun clearSession() {
        synchronized(this) {
            lastWriteAt.clear()
            usableSeen.clear()
            lastUsableState.clear()
        }
    }

    private fun scheduleWrite(
        profileKey: String,
        ref: PlaybackRef,
        state: PlayerUiState,
        now: Long,
    ) {
        val item = historyItem(ref, state, now) ?: return
        scope.launch {
            runCatching {
                repository.upsertHistory(profileKey, item)
            }
        }
    }

    private fun historyItem(
        ref: PlaybackRef,
        state: PlayerUiState,
        now: Long,
    ): HistoryItem? {
        if (ref is CatchUpPlaybackRef) return null

        val kind = when (ref) {
            is LivePlaybackRef -> PlaybackKind.Live
            is VodPlaybackRef -> PlaybackKind.Vod
            is EpisodePlaybackRef -> PlaybackKind.Series
            is CatchUpPlaybackRef -> return null
        }
        val artwork = when (ref) {
            is LivePlaybackRef -> ref.artworkUrl
            is VodPlaybackRef -> ref.artworkUrl
            is EpisodePlaybackRef -> ref.artworkUrl
            is CatchUpPlaybackRef -> null
        }
        val extension = when (ref) {
            is VodPlaybackRef -> ref.containerExtension
            is EpisodePlaybackRef -> ref.containerExtension
            else -> null
        }
        val duration = state.durationMs?.takeIf { it > 0L }
        val position = state.positionMs.coerceAtLeast(0L)
        val completed = state.phase == PlayerPhase.Ended ||
            (duration != null && position * 10L >= duration * 9L)

        return HistoryItem(
            playbackKind = kind,
            mediaId = ref.streamId,
            title = ref.title,
            artworkUrl = artwork,
            containerExtension = extension,
            lastPlayedAtEpochMs = now,
            lastPositionMs = position,
            durationMs = duration,
            seekable = state.seekable,
            completed = completed,
        )
    }

    private fun identity(
        profileKey: String,
        ref: PlaybackRef,
    ): String? =
        when (ref) {
            is CatchUpPlaybackRef -> null
            is LivePlaybackRef -> profileKey + "|Live|" + ref.streamId
            is VodPlaybackRef -> profileKey + "|Vod|" + ref.streamId
            is EpisodePlaybackRef -> profileKey + "|Series|" + ref.streamId
        }

    private fun isUsable(phase: PlayerPhase): Boolean =
        phase == PlayerPhase.Playing ||
            phase == PlayerPhase.Paused ||
            phase == PlayerPhase.Ended
}
