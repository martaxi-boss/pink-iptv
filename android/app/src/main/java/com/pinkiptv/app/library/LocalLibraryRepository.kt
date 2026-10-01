package com.pinkiptv.app.library

import com.pinkiptv.app.model.FavoriteItem
import com.pinkiptv.app.model.HistoryItem
import kotlinx.coroutines.flow.Flow

interface LocalLibraryRepository {
    fun observeFavorites(profileKey: String): Flow<List<FavoriteItem>>
    fun observeHistory(profileKey: String): Flow<List<HistoryItem>>

    suspend fun toggleFavorite(
        profileKey: String,
        item: FavoriteItem,
    ): Boolean

    suspend fun removeFavorite(
        profileKey: String,
        item: FavoriteItem,
    )

    suspend fun upsertHistory(
        profileKey: String,
        item: HistoryItem,
    )

    suspend fun clearHistory(profileKey: String)
}
