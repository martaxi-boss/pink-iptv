package com.pinkiptv.app

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.pinkiptv.app.model.LivePlaybackRef
import com.pinkiptv.app.model.SearchItem
import com.pinkiptv.app.model.SearchKind
import com.pinkiptv.app.model.SearchPhase
import com.pinkiptv.app.model.SearchSourceState
import com.pinkiptv.app.model.SearchSourceStatus
import com.pinkiptv.app.model.SearchUiError
import com.pinkiptv.app.model.SearchUiState
import com.pinkiptv.app.model.VodPlaybackRef
import com.pinkiptv.app.ui.screens.SearchScreen
import com.pinkiptv.app.ui.theme.PinkTheme
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class SearchScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyQueryTypingClearAndFiltersAreDeterministic() {
        var selectedKind = SearchKind.All

        composeRule.setContent {
            var state by remember {
                mutableStateOf(
                    readySourcesState(
                        phase = SearchPhase.Inactive,
                        query = "",
                        results = emptyList(),
                    ),
                )
            }
            PinkTheme {
                SearchScreen(
                    state = state,
                    favoriteKeys = emptySet(),
                    onLoad = {},
                    onQueryChange = { value ->
                        state = state.copy(
                            query = value,
                            phase = if (value.trim().length >= 2) {
                                SearchPhase.Ready
                            } else {
                                SearchPhase.Inactive
                            },
                            results = if (value.trim().length >= 2) searchItems() else emptyList(),
                        )
                    },
                    onSelectKind = {
                        selectedKind = it
                        state = state.copy(selectedKind = it)
                    },
                    onRetry = {},
                    onOpenItem = {},
                    onToggleFavorite = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("search_inactive").assertIsDisplayed()
        composeRule.onNodeWithTag("search_query").performTextInput("one")
        composeRule.onNodeWithTag("search_query").assertTextContains("one")
        composeRule.onNodeWithTag("search_results").assertIsDisplayed()

        composeRule.onNodeWithTag("search_filter_live").performClick()
        composeRule.runOnIdle {
            assertEquals(SearchKind.Live, selectedKind)
        }
        composeRule.onNodeWithTag("search_filter_movies").performClick()
        composeRule.onNodeWithTag("search_filter_series").performClick()
        composeRule.onNodeWithTag("search_filter_all").performClick()
        composeRule.runOnIdle {
            assertEquals(SearchKind.All, selectedKind)
        }

        composeRule.onNodeWithTag("search_clear").performClick()
        composeRule.onNodeWithTag("search_inactive").assertIsDisplayed()
    }

    @Test
    fun liveMovieSeriesResultsAndFavoriteActionStaySeparate() {
        var opened: SearchItem? = null
        var favorite: SearchItem? = null
        val state = readySourcesState(
            phase = SearchPhase.Ready,
            query = "one",
            results = searchItems(),
        )

        composeRule.setContent {
            PinkTheme {
                SearchScreen(
                    state = state,
                    favoriteKeys = setOf("Live:101"),
                    onLoad = {},
                    onQueryChange = {},
                    onSelectKind = {},
                    onRetry = {},
                    onOpenItem = { opened = it },
                    onToggleFavorite = { favorite = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("search_result_Live_101").assertIsDisplayed()
        composeRule.onNodeWithTag("search_result_Movies_202").assertIsDisplayed()
        composeRule.onNodeWithTag("search_result_Series_301").assertIsDisplayed()

        composeRule.onNodeWithTag("search_favorite_Live_101").performClick()
        composeRule.runOnIdle {
            assertEquals("101", favorite?.providerId)
            assertEquals(null, opened)
        }

        composeRule.onNodeWithTag("search_result_Movies_202").performClick()
        composeRule.runOnIdle {
            assertEquals("202", opened?.providerId)
        }
    }

    @Test
    fun noMatchesPartialErrorAndFullErrorRetryHaveDistinctStates() {
        var retries = 0

        composeRule.setContent {
            PinkTheme {
                SearchScreen(
                    state = readySourcesState(
                        phase = SearchPhase.NoMatches,
                        query = "missing",
                        results = emptyList(),
                    ),
                    favoriteKeys = emptySet(),
                    onLoad = {},
                    onQueryChange = {},
                    onSelectKind = {},
                    onRetry = { retries += 1 },
                    onOpenItem = {},
                    onToggleFavorite = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("search_no_results").assertIsDisplayed()

        composeRule.setContent {
            PinkTheme {
                SearchScreen(
                    state = SearchUiState(
                        phase = SearchPhase.PartialError,
                        query = "one",
                        results = searchItems().drop(1),
                        liveSource = SearchSourceState(
                            SearchSourceStatus.Error,
                            SearchUiError.ProviderUnavailable,
                        ),
                        movieSource = SearchSourceState(SearchSourceStatus.Ready),
                        seriesSource = SearchSourceState(SearchSourceStatus.Ready),
                    ),
                    favoriteKeys = emptySet(),
                    onLoad = {},
                    onQueryChange = {},
                    onSelectKind = {},
                    onRetry = { retries += 1 },
                    onOpenItem = {},
                    onToggleFavorite = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("search_partial_error").assertIsDisplayed()
        composeRule.onNodeWithTag("search_result_Movies_202").assertIsDisplayed()
        composeRule.onNodeWithTag("search_partial_retry").performClick()

        composeRule.setContent {
            PinkTheme {
                SearchScreen(
                    state = SearchUiState(
                        phase = SearchPhase.FullError,
                        liveSource = SearchSourceState(
                            SearchSourceStatus.Error,
                            SearchUiError.ProviderUnavailable,
                        ),
                        movieSource = SearchSourceState(
                            SearchSourceStatus.Error,
                            SearchUiError.ProviderUnavailable,
                        ),
                        seriesSource = SearchSourceState(
                            SearchSourceStatus.Error,
                            SearchUiError.ProviderUnavailable,
                        ),
                    ),
                    favoriteKeys = emptySet(),
                    onLoad = {},
                    onQueryChange = {},
                    onSelectKind = {},
                    onRetry = { retries += 1 },
                    onOpenItem = {},
                    onToggleFavorite = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("search_error").assertIsDisplayed()
        composeRule.onNodeWithTag("search_retry").performClick()
        composeRule.runOnIdle {
            assertEquals(2, retries)
        }
    }

    @Test
    fun idleSourcesTriggerOneLoadRequest() {
        var loads = 0
        composeRule.setContent {
            PinkTheme {
                SearchScreen(
                    state = SearchUiState(),
                    favoriteKeys = emptySet(),
                    onLoad = { loads += 1 },
                    onQueryChange = {},
                    onSelectKind = {},
                    onRetry = {},
                    onOpenItem = {},
                    onToggleFavorite = {},
                    onBack = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(1, loads)
        }
    }

    @Test
    fun tvFocusMovesQueryToFiltersToResultToFavorite() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val configuration = instrumentation.targetContext.resources.configuration
        assumeTrue(
            configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
                Configuration.UI_MODE_TYPE_TELEVISION,
        )

        composeRule.setContent {
            PinkTheme {
                SearchScreen(
                    state = readySourcesState(
                        phase = SearchPhase.Ready,
                        query = "one",
                        results = searchItems(),
                    ),
                    favoriteKeys = emptySet(),
                    onLoad = {},
                    onQueryChange = {},
                    onSelectKind = {},
                    onRetry = {},
                    onOpenItem = {},
                    onToggleFavorite = {},
                    onBack = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("search_query").assertIsFocused()

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("search_filter_all").assertIsFocused()

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("search_result_Live_101").assertIsFocused()

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("search_favorite_Live_101").assertIsFocused()
    }

    private fun readySourcesState(
        phase: SearchPhase,
        query: String,
        results: List<SearchItem>,
    ) = SearchUiState(
        phase = phase,
        query = query,
        results = results,
        liveSource = SearchSourceState(SearchSourceStatus.Ready),
        movieSource = SearchSourceState(SearchSourceStatus.Ready),
        seriesSource = SearchSourceState(SearchSourceStatus.Ready),
    )

    private fun searchItems() = listOf(
        SearchItem(
            kind = SearchKind.Live,
            providerId = "101",
            title = "Live One",
            artworkUrl = null,
            playbackRef = LivePlaybackRef("101", "Live One"),
        ),
        SearchItem(
            kind = SearchKind.Movies,
            providerId = "202",
            title = "Movie One",
            artworkUrl = null,
            playbackRef = VodPlaybackRef("202", "Movie One", "mp4"),
        ),
        SearchItem(
            kind = SearchKind.Series,
            providerId = "301",
            title = "Series One",
            artworkUrl = null,
            playbackRef = null,
        ),
    )
}
