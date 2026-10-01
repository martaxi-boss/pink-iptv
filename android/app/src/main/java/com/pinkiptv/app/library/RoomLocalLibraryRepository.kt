package com.pinkiptv.app.library

import com.pinkiptv.app.model.FavoriteItem
import com.pinkiptv.app.model.FavoriteKind
import com.pinkiptv.app.model.HistoryItem
import com.pinkiptv.app.model.PlaybackKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomLocalLibraryRepository(
    private val database: PinkLibraryDatabase,
) : LocalLibraryRepository {
    private val favorites = database.favoriteDao()
    private val history = database.historyDao()

    override fun observeFavorites(profileKey: String): Flow<List<FavoriteItem>> =
        favorites.observeForProfile(profileKey).map { rows ->
            rows.mapNotNull(FavoriteEntity::toDomain)
        }

    override fun observeHistory(profileKey: String): Flow<List<HistoryItem>> =
        history.observeForProfile(profileKey).map { rows ->
            rows.mapNotNull(HistoryEntity::toDomain)
        }

    override suspend fun toggleFavorite(
        profileKey: String,
        item: FavoriteItem,
    ): Boolean {
        val kind = item.kind.name
        val existing = favorites.find(profileKey, kind, item.providerId)
        if (existing != null) {
            favorites.delete(profileKey, kind, item.providerId)
            return false
        }

        favorites.upsert(item.toEntity(profileKey))
        return true
    }

    override suspend fun removeFavorite(
        profileKey: String,
        item: FavoriteItem,
    ) {
        favorites.delete(profileKey, item.kind.name, item.providerId)
    }

    override suspend fun upsertHistory(
        profileKey: String,
        item: HistoryItem,
    ) {
        history.upsertAndPrune(item.toEntity(profileKey))
    }

    override suspend fun clearHistory(profileKey: String) {
        history.clearProfile(profileKey)
    }

    private fun FavoriteItem.toEntity(profileKey: String) = FavoriteEntity(
        profileKey = profileKey,
        contentKind = kind.name,
        providerId = providerId,
        title = title,
        artworkUrl = artworkUrl,
        containerExtension = containerExtension,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    private fun FavoriteEntity.toDomain(): FavoriteItem? {
        val kind = runCatching { FavoriteKind.valueOf(contentKind) }.getOrNull()
            ?: return null
        return FavoriteItem(
            kind = kind,
            providerId = providerId,
            title = title,
            artworkUrl = artworkUrl,
            containerExtension = containerExtension,
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
        )
    }

    private fun HistoryItem.toEntity(profileKey: String) = HistoryEntity(
        profileKey = profileKey,
        playbackKind = playbackKind.name,
        providerMediaId = mediaId,
        title = title,
        artworkUrl = artworkUrl,
        containerExtension = containerExtension,
        lastPlayedAtEpochMs = lastPlayedAtEpochMs,
        lastPositionMs = lastPositionMs,
        durationMs = durationMs,
        seekable = seekable,
        completed = completed,
    )

    private fun HistoryEntity.toDomain(): HistoryItem? {
        val kind = runCatching { PlaybackKind.valueOf(playbackKind) }.getOrNull()
            ?: return null
        if (kind == PlaybackKind.CatchUp) return null
        return HistoryItem(
            playbackKind = kind,
            mediaId = providerMediaId,
            title = title,
            artworkUrl = artworkUrl,
            containerExtension = containerExtension,
            lastPlayedAtEpochMs = lastPlayedAtEpochMs,
            lastPositionMs = lastPositionMs,
            durationMs = durationMs,
            seekable = seekable,
            completed = completed,
        )
    }
}
