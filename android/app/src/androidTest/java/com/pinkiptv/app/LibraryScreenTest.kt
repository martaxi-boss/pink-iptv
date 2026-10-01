package com.pinkiptv.app

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.pinkiptv.app.model.ContinueWatchingItem
import com.pinkiptv.app.model.FavoriteItem
import com.pinkiptv.app.model.FavoriteKind
import com.pinkiptv.app.model.HistoryItem
import com.pinkiptv.app.model.LibraryPhase
import com.pinkiptv.app.model.LibraryUiState
import com.pinkiptv.app.model.PlaybackKind
import com.pinkiptv.app.ui.screens.LibraryScreen
import com.pinkiptv.app.ui.theme.PinkTheme
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class LibraryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun favoritesSectionSupportsOpenRemoveAndEmptyState() {
        var opened: FavoriteItem? = null
        var removed: FavoriteItem? = null
        val favorite = favorite(FavoriteKind.Live, "10", "Canal")

        composeRule.setContent {
            PinkTheme {
                LibraryScreen(
                    state = LibraryUiState(
                        phase = LibraryPhase.Ready,
                        favorites = listOf(favorite),
                    ),
                    onOpenFavorite = { opened = it },
                    onRemoveFavorite = { removed = it },
                    onOpenContinue = {},
                    onOpenRecent = {},
                    onClearHistory = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("library_favorites_list").assertIsDisplayed()
        composeRule.onNodeWithTag("library_favorite_open_Live_10").performClick()
        composeRule.onNodeWithTag("library_favorite_remove_Live_10").performClick()
        composeRule.runOnIdle {
            assertEquals(favorite, opened)
            assertEquals(favorite, removed)
        }

        composeRule.setContent {
            PinkTheme {
                LibraryScreen(
                    state = LibraryUiState(phase = LibraryPhase.Ready),
                    onOpenFavorite = {},
                    onRemoveFavorite = {},
                    onOpenContinue = {},
                    onOpenRecent = {},
                    onClearHistory = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("library_favorites_empty").assertIsDisplayed()
    }

    @Test
    fun continueAndRecentSectionsExposeResumeOpenAndClearHistory() {
        val movie = history(
            kind = PlaybackKind.Vod,
            id = "20",
            title = "Filme",
            position = 30_000L,
            duration = 120_000L,
            seekable = true,
        )
        val recentLive = history(
            kind = PlaybackKind.Live,
            id = "30",
            title = "Direto",
            position = 0L,
            duration = null,
            seekable = false,
        )
        var continued: ContinueWatchingItem? = null
        var openedRecent: HistoryItem? = null
        var clears = 0

        composeRule.setContent {
            PinkTheme {
                LibraryScreen(
                    state = LibraryUiState(
                        phase = LibraryPhase.Ready,
                        continueWatching = listOf(
                            ContinueWatchingItem(movie, 25),
                        ),
                        recents = listOf(movie, recentLive),
                    ),
                    onOpenFavorite = {},
                    onRemoveFavorite = {},
                    onOpenContinue = { continued = it },
                    onOpenRecent = { openedRecent = it },
                    onClearHistory = { clears += 1 },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("library_tab_continue").performClick()
        composeRule.onNodeWithTag("library_continue_list").assertIsDisplayed()
        composeRule.onNodeWithTag("library_continue_open_20").performClick()
        composeRule.runOnIdle { assertEquals("20", continued?.history?.mediaId) }

        composeRule.onNodeWithTag("library_tab_recents").performClick()
        composeRule.onNodeWithTag("library_recents_list").assertIsDisplayed()
        composeRule.onNodeWithTag("library_recent_open_Live_30").performClick()
        composeRule.onNodeWithTag("library_clear_history").performClick()
        composeRule.runOnIdle {
            assertEquals("30", openedRecent?.mediaId)
            assertEquals(1, clears)
        }
    }

    @Test
    fun loadingAndErrorKeepNavigationControlsAvailable() {
        composeRule.setContent {
            PinkTheme {
                LibraryScreen(
                    state = LibraryUiState(phase = LibraryPhase.Loading),
                    onOpenFavorite = {},
                    onRemoveFavorite = {},
                    onOpenContinue = {},
                    onOpenRecent = {},
                    onClearHistory = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("library_loading").assertIsDisplayed()
        composeRule.onNodeWithTag("library_back").assertIsDisplayed()

        composeRule.setContent {
            PinkTheme {
                LibraryScreen(
                    state = LibraryUiState(phase = LibraryPhase.Error),
                    onOpenFavorite = {},
                    onRemoveFavorite = {},
                    onOpenContinue = {},
                    onOpenRecent = {},
                    onClearHistory = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("library_error").assertIsDisplayed()
        composeRule.onNodeWithTag("library_tab_favorites").assertIsDisplayed()
    }

    @Test
    fun tvStartsOnFavoritesTabAndDpadMovesAcrossSections() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val configuration = instrumentation.targetContext.resources.configuration
        assumeTrue(
            configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
                Configuration.UI_MODE_TYPE_TELEVISION,
        )

        composeRule.setContent {
            PinkTheme {
                LibraryScreen(
                    state = LibraryUiState(phase = LibraryPhase.Ready),
                    onOpenFavorite = {},
                    onRemoveFavorite = {},
                    onOpenContinue = {},
                    onOpenRecent = {},
                    onClearHistory = {},
                    onBack = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("library_tab_favorites").assertIsFocused()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("library_tab_continue").assertIsFocused()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("library_tab_recents").assertIsFocused()
    }

    private fun favorite(
        kind: FavoriteKind,
        id: String,
        title: String,
    ) = FavoriteItem(
        kind = kind,
        providerId = id,
        title = title,
        artworkUrl = null,
        containerExtension = if (kind == FavoriteKind.Movie) "mp4" else null,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
    )

    private fun history(
        kind: PlaybackKind,
        id: String,
        title: String,
        position: Long,
        duration: Long?,
        seekable: Boolean,
    ) = HistoryItem(
        playbackKind = kind,
        mediaId = id,
        title = title,
        artworkUrl = null,
        containerExtension = if (kind == PlaybackKind.Vod || kind == PlaybackKind.Series) {
            "mp4"
        } else {
            null
        },
        lastPlayedAtEpochMs = 1L,
        lastPositionMs = position,
        durationMs = duration,
        seekable = seekable,
        completed = false,
    )
}
