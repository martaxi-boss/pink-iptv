package com.pinkiptv.app

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pinkiptv.app.library.FavoriteEntity
import com.pinkiptv.app.library.HistoryEntity
import com.pinkiptv.app.library.PinkLibraryDatabase
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LibraryRoomDatabaseTest {
    private lateinit var context: Context
    private lateinit var database: PinkLibraryDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(
            context,
            PinkLibraryDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun databaseVersionOneAndFavoriteCompositeIdentityAreStable() = runBlocking {
        assertEquals(1, database.openHelper.readableDatabase.version)

        val dao = database.favoriteDao()
        dao.upsert(favorite("profile-a", "Live", "1", "First"))
        dao.upsert(favorite("profile-a", "Live", "1", "Updated"))
        dao.upsert(favorite("profile-a", "Movie", "1", "Movie"))
        dao.upsert(favorite("profile-b", "Live", "1", "Other account"))

        val profileA = dao.observeForProfile("profile-a").first()
        assertEquals(2, profileA.size)
        assertEquals(1, profileA.count { it.contentKind == "Live" && it.providerId == "1" })
        assertTrue(profileA.any { it.contentKind == "Movie" && it.providerId == "1" })
        assertTrue(profileA.any { it.title == "Updated" })

        dao.delete("profile-a", "Live", "1")
        assertEquals(1, dao.observeForProfile("profile-a").first().size)
        assertEquals(1, dao.observeForProfile("profile-b").first().size)
    }

    @Test
    fun historyIsNewestFirstPrunedToOneHundredAndClearIsProfileLocal() = runBlocking {
        val dao = database.historyDao()
        repeat(105) { index ->
            dao.upsertAndPrune(
                history(
                    profileKey = "profile-a",
                    id = index.toString(),
                    timestamp = index.toLong(),
                ),
            )
        }
        dao.upsertAndPrune(history("profile-b", "b", 999L))

        val profileA = dao.observeForProfile("profile-a").first()
        assertEquals(100, profileA.size)
        assertEquals("104", profileA.first().providerMediaId)
        assertEquals("5", profileA.last().providerMediaId)

        dao.clearProfile("profile-a")
        assertTrue(dao.observeForProfile("profile-a").first().isEmpty())
        assertEquals(1, dao.observeForProfile("profile-b").first().size)
    }


    @Test
    fun replayUpdatesTimestampAndMovesExistingIdentityToTopWithoutDuplicate() = runBlocking {
        val dao = database.historyDao()
        dao.upsertAndPrune(history("profile-a", "same", 1L))
        dao.upsertAndPrune(history("profile-a", "other", 2L))
        dao.upsertAndPrune(history("profile-a", "same", 3L))

        val rows = dao.observeForProfile("profile-a").first()
        assertEquals(2, rows.size)
        assertEquals("same", rows.first().providerMediaId)
        assertEquals(3L, rows.first().lastPlayedAtEpochMs)
        assertEquals(1, rows.count { it.providerMediaId == "same" })
    }

    @Test
    fun favoriteFlowInvalidatesAndHistoryClearDoesNotDeleteFavorites() = runBlocking {
        val favoriteDao = database.favoriteDao()
        val historyDao = database.historyDao()
        val emissions = mutableListOf<List<FavoriteEntity>>()
        val collector = async(start = CoroutineStart.UNDISPATCHED) {
            favoriteDao.observeForProfile("profile-a")
                .take(2)
                .toList(emissions)
        }

        favoriteDao.upsert(favorite("profile-a", "Series", "7", "Series"))
        collector.await()

        assertEquals(2, emissions.size)
        assertTrue(emissions.first().isEmpty())
        assertEquals("7", emissions.last().single().providerId)

        historyDao.upsertAndPrune(history("profile-a", "movie", 1L))
        historyDao.clearProfile("profile-a")
        assertTrue(historyDao.observeForProfile("profile-a").first().isEmpty())
        assertEquals(1, favoriteDao.observeForProfile("profile-a").first().size)
    }

    private fun favorite(
        profileKey: String,
        kind: String,
        id: String,
        title: String,
    ) = FavoriteEntity(
        profileKey = profileKey,
        contentKind = kind,
        providerId = id,
        title = title,
        artworkUrl = null,
        containerExtension = if (kind == "Movie") "mp4" else null,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 2L,
    )

    private fun history(
        profileKey: String,
        id: String,
        timestamp: Long,
    ) = HistoryEntity(
        profileKey = profileKey,
        playbackKind = "Vod",
        providerMediaId = id,
        title = "Movie " + id,
        artworkUrl = null,
        containerExtension = "mp4",
        lastPlayedAtEpochMs = timestamp,
        lastPositionMs = 30_000L,
        durationMs = 120_000L,
        seekable = true,
        completed = false,
    )
}
