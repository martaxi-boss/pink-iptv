package com.pinkiptv.app.library

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "favorites",
    primaryKeys = ["profileKey", "contentKind", "providerId"],
    indices = [Index(value = ["profileKey"])],
)
data class FavoriteEntity(
    val profileKey: String,
    val contentKind: String,
    val providerId: String,
    val title: String,
    val artworkUrl: String?,
    val containerExtension: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "history",
    primaryKeys = ["profileKey", "playbackKind", "providerMediaId"],
    indices = [Index(value = ["profileKey"])],
)
data class HistoryEntity(
    val profileKey: String,
    val playbackKind: String,
    val providerMediaId: String,
    val title: String,
    val artworkUrl: String?,
    val containerExtension: String?,
    val lastPlayedAtEpochMs: Long,
    val lastPositionMs: Long,
    val durationMs: Long?,
    val seekable: Boolean,
    val completed: Boolean,
)
