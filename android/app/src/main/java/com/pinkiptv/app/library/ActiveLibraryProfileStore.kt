package com.pinkiptv.app.library

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ActiveLibraryProfileStore {
    @Volatile
    private var activeKey: String? = null
    private val mutableKey = MutableStateFlow<String?>(null)

    val key: StateFlow<String?> = mutableKey.asStateFlow()

    fun current(): String? = activeKey

    fun activate(profileKey: String) {
        activeKey = profileKey
        mutableKey.value = profileKey
    }

    fun clear() {
        activeKey = null
        mutableKey.value = null
    }
}
