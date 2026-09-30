package com.pinkiptv.app

import android.content.res.Configuration
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.pinkiptv.app.model.LivePlaybackRef
import com.pinkiptv.app.model.PlaybackKind
import com.pinkiptv.app.model.PlayerError
import com.pinkiptv.app.model.PlayerPhase
import com.pinkiptv.app.model.PlayerUiState
import com.pinkiptv.app.model.VodPlaybackRef
import com.pinkiptv.app.ui.screens.PlayerScreen
import com.pinkiptv.app.ui.theme.PinkTheme
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class PlayerScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingAndErrorRetryAreDeterministic() {
        val loadingFactory = TestPlaybackFacadeFactory(
            PlayerUiState(
                phase = PlayerPhase.Preparing,
                title = "Channel",
                kind = PlaybackKind.Live,
            ),
        )
        composeRule.setContent {
            PinkTheme {
                PlayerScreen(
                    playbackRef = LivePlaybackRef("1", "Channel"),
                    facadeFactory = loadingFactory,
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("player_loading").assertIsDisplayed()

        val errorFactory = TestPlaybackFacadeFactory(
            PlayerUiState(
                phase = PlayerPhase.Error,
                title = "Channel",
                kind = PlaybackKind.Live,
                error = PlayerError.Network,
            ),
        )
        composeRule.setContent {
            PinkTheme {
                PlayerScreen(
                    playbackRef = LivePlaybackRef("1", "Channel"),
                    facadeFactory = errorFactory,
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("player_error").assertIsDisplayed()
        composeRule.onNodeWithTag("player_retry").performClick()
        composeRule.runOnIdle {
            assertEquals(1, errorFactory.lastFacade.retryCount)
        }
    }

    @Test
    fun vodSeekControlsAppearButNonSeekableLiveDoesNotExposeThem() {
        val vodFactory = TestPlaybackFacadeFactory(
            PlayerUiState(
                phase = PlayerPhase.Paused,
                title = "Movie",
                kind = PlaybackKind.Vod,
                durationMs = 60_000L,
                positionMs = 10_000L,
                seekable = true,
            ),
        )
        composeRule.setContent {
            PinkTheme {
                PlayerScreen(
                    playbackRef = VodPlaybackRef("2", "Movie", "mp4"),
                    facadeFactory = vodFactory,
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("player_seek_back").assertIsDisplayed()
        composeRule.onNodeWithTag("player_seek_forward").assertIsDisplayed()
        composeRule.onNodeWithTag("player_position").assertIsDisplayed()

        val liveFactory = TestPlaybackFacadeFactory(
            PlayerUiState(
                phase = PlayerPhase.Playing,
                title = "Live",
                kind = PlaybackKind.Live,
                isPlaying = true,
                seekable = false,
            ),
        )
        composeRule.setContent {
            PinkTheme {
                PlayerScreen(
                    playbackRef = LivePlaybackRef("3", "Live"),
                    facadeFactory = liveFactory,
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("player_play_pause").assertIsDisplayed()
        composeRule.onNodeWithTag("player_seek_back").assertDoesNotExist()
        composeRule.onNodeWithTag("player_seek_forward").assertDoesNotExist()
    }

    @Test
    fun backClosesPlayer() {
        var backCount = 0
        val factory = TestPlaybackFacadeFactory(
            PlayerUiState(
                phase = PlayerPhase.Playing,
                title = "Live",
                kind = PlaybackKind.Live,
                isPlaying = true,
            ),
        )
        composeRule.setContent {
            PinkTheme {
                PlayerScreen(
                    playbackRef = LivePlaybackRef("4", "Live"),
                    facadeFactory = factory,
                    onBack = { backCount += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("player_back").performClick()
        composeRule.runOnIdle {
            assertEquals(1, backCount)
            assertEquals(1, factory.lastFacade.closeCount)
        }
    }

    @Test
    fun tvPlayerStartsWithPrimaryControlFocused() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val configuration = instrumentation.targetContext.resources.configuration
        assumeTrue(
            configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
                Configuration.UI_MODE_TYPE_TELEVISION,
        )

        val factory = TestPlaybackFacadeFactory(
            PlayerUiState(
                phase = PlayerPhase.Playing,
                title = "Live",
                kind = PlaybackKind.Live,
                isPlaying = true,
            ),
        )
        composeRule.setContent {
            PinkTheme {
                PlayerScreen(
                    playbackRef = LivePlaybackRef("5", "Live"),
                    facadeFactory = factory,
                    onBack = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player_play_pause").assertIsFocused()
    }
}
