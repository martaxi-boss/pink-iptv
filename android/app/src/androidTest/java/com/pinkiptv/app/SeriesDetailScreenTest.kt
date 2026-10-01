package com.pinkiptv.app

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.pinkiptv.app.model.SeriesDetailPhase
import com.pinkiptv.app.model.SeriesDetailUiError
import com.pinkiptv.app.model.SeriesDetailUiState
import com.pinkiptv.app.model.SeriesEpisode
import com.pinkiptv.app.model.SeriesSeason
import com.pinkiptv.app.ui.screens.SeriesDetailScreen
import com.pinkiptv.app.ui.theme.PinkTheme
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class SeriesDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingErrorRetryAndEmptyStatesAreSafe() {
        composeRule.setContent {
            PinkTheme {
                SeriesDetailScreen(
                    state = SeriesDetailUiState(
                        phase = SeriesDetailPhase.Loading,
                        title = "Show",
                    ),
                    onRetry = {},
                    onSelectSeason = {},
                    onOpenEpisode = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("series_detail_loading").assertIsDisplayed()

        var retries = 0
        composeRule.setContent {
            PinkTheme {
                SeriesDetailScreen(
                    state = SeriesDetailUiState(
                        phase = SeriesDetailPhase.Error,
                        title = "Show",
                        error = SeriesDetailUiError.ProviderUnavailable,
                    ),
                    onRetry = { retries += 1 },
                    onSelectSeason = {},
                    onOpenEpisode = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("series_detail_error").assertIsDisplayed()
        composeRule.onNodeWithTag("series_detail_retry").performClick()
        composeRule.runOnIdle { assertEquals(1, retries) }

        composeRule.setContent {
            PinkTheme {
                SeriesDetailScreen(
                    state = SeriesDetailUiState(
                        phase = SeriesDetailPhase.Empty,
                        title = "Show",
                    ),
                    onRetry = {},
                    onSelectSeason = {},
                    onOpenEpisode = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("series_detail_empty").assertIsDisplayed()
    }

    @Test
    fun seasonsAndEpisodesExposeDeterministicTouchActions() {
        var season: String? = null
        var episode: String? = null
        composeRule.setContent {
            PinkTheme {
                SeriesDetailScreen(
                    state = contentState(),
                    onRetry = {},
                    onSelectSeason = { season = it },
                    onOpenEpisode = { episode = it.episodeId },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("series_season_2").performClick()
        composeRule.onNodeWithTag("series_episode_101").performClick()
        composeRule.runOnIdle {
            assertEquals("2", season)
            assertEquals("101", episode)
        }
    }

    @Test
    fun tvLoadingKeepsBackFocusableInsteadOfTrappingFocus() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val configuration = instrumentation.targetContext.resources.configuration
        assumeTrue(
            configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
                Configuration.UI_MODE_TYPE_TELEVISION,
        )

        composeRule.setContent {
            PinkTheme {
                SeriesDetailScreen(
                    state = SeriesDetailUiState(
                        phase = SeriesDetailPhase.Loading,
                        title = "Show",
                    ),
                    onRetry = {},
                    onSelectSeason = {},
                    onOpenEpisode = {},
                    onBack = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("series_detail_back").assertIsFocused()
    }

    @Test
    fun tvDpadMovesFromSeasonToEpisodeAndOkActivates() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val configuration = instrumentation.targetContext.resources.configuration
        assumeTrue(
            configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
                Configuration.UI_MODE_TYPE_TELEVISION,
        )

        var season: String? = null
        var episode: String? = null
        composeRule.setContent {
            PinkTheme {
                SeriesDetailScreen(
                    state = contentState(),
                    onRetry = {},
                    onSelectSeason = { season = it },
                    onOpenEpisode = { episode = it.episodeId },
                    onBack = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("series_season_1").assertIsFocused()

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("series_season_2").assertIsFocused()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        composeRule.runOnIdle { assertEquals("2", season) }

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("series_episode_101").assertIsFocused()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        composeRule.runOnIdle { assertEquals("101", episode) }
    }

    private fun contentState() = SeriesDetailUiState(
        phase = SeriesDetailPhase.Content,
        selectedSeriesId = "77",
        title = "Show",
        seasons = listOf(
            SeriesSeason("1", "Season 1", 1, null),
            SeriesSeason("2", "Season 2", 1, null),
        ),
        selectedSeasonId = "1",
        episodes = listOf(
            SeriesEpisode(
                episodeId = "101",
                episodeNumber = "1",
                title = "Episode",
                seasonId = "1",
                containerExtension = "mp4",
                artworkUrl = null,
                duration = "00:45:00",
                plot = null,
            ),
        ),
    )
}
