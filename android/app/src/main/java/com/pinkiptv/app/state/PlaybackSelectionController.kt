package com.pinkiptv.app.state

import com.pinkiptv.app.model.CatalogUiItem
import com.pinkiptv.app.model.PlaybackRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaybackSelectionController {
    private val mutableSelection = MutableStateFlow<PlaybackRef?>(null)
    private val mutableResumePositionMs = MutableStateFlow(0L)

    val selection: StateFlow<PlaybackRef?> = mutableSelection.asStateFlow()
    val resumePositionMs: StateFlow<Long> = mutableResumePositionMs.asStateFlow()

    fun select(
        item: CatalogUiItem,
        startPositionMs: Long = 0L,
    ): Boolean {
        val ref = item.playbackRef ?: return false
        return select(ref, startPositionMs)
    }

    fun select(
        ref: PlaybackRef,
        startPositionMs: Long = 0L,
    ): Boolean {
        mutableSelection.value = ref
        mutableResumePositionMs.value = startPositionMs.coerceAtLeast(0L)
        return true
    }

    fun clear() {
        mutableSelection.value = null
        mutableResumePositionMs.value = 0L
    }
}
