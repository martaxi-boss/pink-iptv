package com.pinkiptv.app.library

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query(
        """
        SELECT * FROM favorites
        WHERE profileKey = :profileKey
        ORDER BY updatedAtEpochMs DESC
        """,
    )
    fun observeForProfile(profileKey: String): Flow<List<FavoriteEntity>>

    @Query(
        """
        SELECT * FROM favorites
        WHERE profileKey = :profileKey
          AND contentKind = :contentKind
          AND providerId = :providerId
        LIMIT 1
        """,
    )
    suspend fun find(
        profileKey: String,
        contentKind: String,
        providerId: String,
    ): FavoriteEntity?

    @Upsert
    suspend fun upsert(entity: FavoriteEntity)

    @Query(
        """
        DELETE FROM favorites
        WHERE profileKey = :profileKey
          AND contentKind = :contentKind
          AND providerId = :providerId
        """,
    )
    suspend fun delete(
        profileKey: String,
        contentKind: String,
        providerId: String,
    )
}

@Dao
interface HistoryDao {
    @Query(
        """
        SELECT * FROM history
        WHERE profileKey = :profileKey
        ORDER BY lastPlayedAtEpochMs DESC
        LIMIT 100
        """,
    )
    fun observeForProfile(profileKey: String): Flow<List<HistoryEntity>>

    @Upsert
    suspend fun upsert(entity: HistoryEntity)

    @Query(
        """
        SELECT * FROM history
        WHERE profileKey = :profileKey
        ORDER BY lastPlayedAtEpochMs DESC
        LIMIT -1 OFFSET 100
        """,
    )
    suspend fun overflow(profileKey: String): List<HistoryEntity>

    @Query(
        """
        DELETE FROM history
        WHERE profileKey = :profileKey
          AND playbackKind = :playbackKind
          AND providerMediaId = :providerMediaId
        """,
    )
    suspend fun deleteIdentity(
        profileKey: String,
        playbackKind: String,
        providerMediaId: String,
    )

    @Query("DELETE FROM history WHERE profileKey = :profileKey")
    suspend fun clearProfile(profileKey: String)

    @Transaction
    suspend fun upsertAndPrune(entity: HistoryEntity) {
        upsert(entity)
        overflow(entity.profileKey).forEach { stale ->
            deleteIdentity(
                profileKey = stale.profileKey,
                playbackKind = stale.playbackKind,
                providerMediaId = stale.providerMediaId,
            )
        }
    }
}
