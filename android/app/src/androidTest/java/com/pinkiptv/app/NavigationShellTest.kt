package com.pinkiptv.app

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pinkiptv.app.model.CatalogKind
import com.pinkiptv.app.model.CatalogPhase
import com.pinkiptv.app.model.CatalogUiItem
import com.pinkiptv.app.model.CatalogUiState
import com.pinkiptv.app.model.LivePlaybackRef
import com.pinkiptv.app.model.PlaybackKind
import com.pinkiptv.app.model.PlaybackRef
import com.pinkiptv.app.model.PlayerPhase
import com.pinkiptv.app.model.PlayerUiState
import com.pinkiptv.app.model.VodPlaybackRef
import com.pinkiptv.app.ui.navigation.AuthenticatedShell
import com.pinkiptv.app.ui.theme.PinkTheme
import org.junit.Rule
import org.junit.Test

class NavigationShellTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun liveCatalogNavigatesAndBackReturnsHome() {
        var backDispatcher: OnBackPressedDispatcher? = null

        composeRule.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            PinkTheme {
                AuthenticatedShell(
                    liveCatalog = CatalogUiState(
                        kind = CatalogKind.Live,
                        phase = CatalogPhase.Loading,
                    ),
                    movieCatalog = CatalogUiState(CatalogKind.Movies),
                    seriesCatalog = CatalogUiState(CatalogKind.Series),
                    onLoadCatalog = {},
                    onSelectCatalogCategory = { _, _ -> },
                    onLogout = {},
                )
            }
        }

        composeRule.onNodeWithTag("home_live").performClick()
        composeRule.onNodeWithText("TV ao Vivo").assertExists()
        composeRule.onNodeWithTag("catalog_loading").assertIsDisplayed()

        composeRule.runOnIdle {
            requireNotNull(backDispatcher).onBackPressed()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_live").assertExists()
    }

    @Test
    fun liveAndMovieOpenPlayerBackReturnsAndSeriesStaysBrowseOnly() {
        val factory = TestPlaybackFacadeFactory(
            PlayerUiState(
                phase = PlayerPhase.Playing,
                title = "Playback",
                kind = PlaybackKind.Live,
                isPlaying = true,
            ),
        )

        composeRule.setContent {
            var selected by remember { mutableStateOf<PlaybackRef?>(null) }
            PinkTheme {
                AuthenticatedShell(
                    liveCatalog = contentState(
                        kind = CatalogKind.Live,
                        item = CatalogUiItem(
                            id = "live-1",
                            name = "Live One",
                            categoryId = null,
                            artworkUrl = null,
                            subtitle = "live",
                            playbackRef = LivePlaybackRef("101", "Live One"),
                        ),
                    ),
                    movieCatalog = contentState(
                        kind = CatalogKind.Movies,
                        item = CatalogUiItem(
                            id = "movie-1",
                            name = "Movie One",
                            categoryId = null,
                            artworkUrl = null,
                            subtitle = "mp4",
                            playbackRef = VodPlaybackRef("202", "Movie One", "mp4"),
                        ),
                    ),
                    seriesCatalog = contentState(
                        kind = CatalogKind.Series,
                        item = CatalogUiItem(
                            id = "series-1",
                            name = "Series One",
                            categoryId = null,
                            artworkUrl = null,
                            subtitle = null,
                            playbackRef = null,
                        ),
                    ),
                    selectedPlayback = selected,
                    playbackFacadeFactory = factory,
                    onLoadCatalog = {},
                    onSelectCatalogCategory = { _, _ -> },
                    onSelectPlayback = { selected = it.playbackRef },
                    onClearPlayback = { selected = null },
                    onLogout = {},
                )
            }
        }

        composeRule.onNodeWithTag("home_live").performClick()
        composeRule.onNodeWithTag("catalog_item_live-1").performClick()
        composeRule.onNodeWithTag("player_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("player_play_pause").assertIsDisplayed()
        composeRule.onNodeWithTag("player_back").performClick()
        composeRule.onNodeWithTag("catalog_item_live-1").assertIsDisplayed()

        composeRule.onNodeWithTag("catalog_back").performClick()
        composeRule.onNodeWithTag("home_movies").performClick()
        composeRule.onNodeWithTag("catalog_item_movie-1").performClick()
        composeRule.onNodeWithTag("player_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("player_back").performClick()
        composeRule.onNodeWithTag("catalog_item_movie-1").assertIsDisplayed()

        composeRule.onNodeWithTag("catalog_back").performClick()
        composeRule.onNodeWithTag("home_series").performClick()
        composeRule.onNodeWithTag("catalog_item_series-1").performClick()
        composeRule.onNodeWithTag("player_screen").assertDoesNotExist()
        composeRule.onNodeWithTag("series_future_playback").assertIsDisplayed()
    }

    private fun contentState(
        kind: CatalogKind,
        item: CatalogUiItem,
    ) = CatalogUiState(
        kind = kind,
        phase = CatalogPhase.Content,
        items = listOf(item),
    )
}
