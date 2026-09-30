package com.pinkiptv.app.state

import com.pinkiptv.app.model.CatalogUiItem
import com.pinkiptv.app.model.PlaybackRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaybackSelectionController {
    private val mutableSelection = MutableStateFlow<PlaybackRef?>(null)
    val selection: StateFlow<PlaybackRef?> = mutableSelection.asStateFlow()

    fun select(item: CatalogUiItem): Boolean {
        val ref = item.playbackRef ?: return false
        mutableSelection.value = ref
        return true
    }

    fun clear() {
        mutableSelection.value = null
    }
}
