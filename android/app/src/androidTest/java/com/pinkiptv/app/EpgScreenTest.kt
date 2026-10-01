package com.pinkiptv.app

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.pinkiptv.app.model.CatchUpPlaybackRef
import com.pinkiptv.app.model.EpgChannel
import com.pinkiptv.app.model.EpgPhase
import com.pinkiptv.app.model.EpgProgramme
import com.pinkiptv.app.model.EpgProgrammeUi
import com.pinkiptv.app.model.EpgUiError
import com.pinkiptv.app.model.EpgUiState
import com.pinkiptv.app.ui.screens.EpgScreen
import com.pinkiptv.app.ui.theme.PinkTheme
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class EpgScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingEmptyAndErrorRetryStatesAreSafe() {
        var current = EpgUiState(phase = EpgPhase.Loading)
        var retryCount = 0

        composeRule.setContent {
            PinkTheme {
                EpgScreen(
                    state = current,
                    onLoad = {},
                    onSelectChannel = {},
                    onRetry = { retryCount += 1 },
                    onOpenCatchUp = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("epg_loading").assertIsDisplayed()

        composeRule.runOnIdle {
            current = EpgUiState(phase = EpgPhase.Empty)
        }
    }

    @Test
    fun errorRetryIsActionable() {
        var retries = 0
        composeRule.setContent {
            PinkTheme {
                EpgScreen(
                    state = EpgUiState(
                        phase = EpgPhase.Error,
                        error = EpgUiError.ProviderUnavailable,
                    ),
                    onLoad = {},
                    onSelectChannel = {},
                    onRetry = { retries += 1 },
                    onOpenCatchUp = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("epg_error").assertIsDisplayed()
        composeRule.onNodeWithTag("epg_retry").performClick()
        composeRule.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun catchUpActionAppearsOnlyForEligibleProgramme() {
        var opened: CatchUpPlaybackRef? = null
        composeRule.setContent {
            PinkTheme {
                EpgScreen(
                    state = contentState(),
                    onLoad = {},
                    onSelectChannel = {},
                    onRetry = {},
                    onOpenCatchUp = { opened = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("epg_now_archived-0").assertDoesNotExist()
        composeRule.onNodeWithTag("epg_now_current-1").assertIsDisplayed()
        composeRule.onNodeWithTag("epg_catchup_archived-0").assertIsDisplayed()
        composeRule.onNodeWithTag("epg_catchup_current-1").assertDoesNotExist()
        composeRule.onNodeWithTag("epg_catchup_archived-0").performClick()
        composeRule.runOnIdle {
            assertEquals("10", opened?.streamId)
            assertEquals(30, opened?.durationMinutes)
        }
    }

    @Test
    fun tvDpadMovesChannelToProgrammeToCatchUpAndOkActivates() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val configuration = instrumentation.targetContext.resources.configuration
        assumeTrue(
            configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
                Configuration.UI_MODE_TYPE_TELEVISION,
        )

        var opened: CatchUpPlaybackRef? = null
        composeRule.setContent {
            PinkTheme {
                EpgScreen(
                    state = contentState(),
                    onLoad = {},
                    onSelectChannel = {},
                    onRetry = {},
                    onOpenCatchUp = { opened = it },
                    onBack = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("epg_channel_10").assertIsFocused()

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("epg_programme_archived-0").assertIsFocused()

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("epg_catchup_archived-0").assertIsFocused()

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        composeRule.runOnIdle { assertEquals("10", opened?.streamId) }
    }

    @Test
    fun tvLoadingWithoutChannelsKeepsBackFocusable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val configuration = instrumentation.targetContext.resources.configuration
        assumeTrue(
            configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
                Configuration.UI_MODE_TYPE_TELEVISION,
        )

        composeRule.setContent {
            PinkTheme {
                EpgScreen(
                    state = EpgUiState(phase = EpgPhase.Loading),
                    onLoad = {},
                    onSelectChannel = {},
                    onRetry = {},
                    onOpenCatchUp = {},
                    onBack = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("epg_back").assertIsFocused()
    }

    private fun contentState(): EpgUiState {
        val archivedRef = CatchUpPlaybackRef(
            streamId = "10",
            title = "Archived",
            providerStart = "2026-09-30:12-34",
            durationMinutes = 30,
        )
        val archived = EpgProgramme(
            programmeId = "archived",
            title = "Archived",
            description = "Past programme",
            startProvider = "2026-09-30 12:34:56",
            endProvider = "2026-09-30 13:04:56",
            startTimestamp = 1000L,
            stopTimestamp = 2800L,
            nowPlaying = false,
            hasArchive = true,
        )
        val current = EpgProgramme(
            programmeId = "current",
            title = "Current",
            description = null,
            startProvider = "2026-09-30 13:04:56",
            endProvider = "2026-09-30 13:34:56",
            startTimestamp = 3000L,
            stopTimestamp = 4800L,
            nowPlaying = true,
            hasArchive = true,
        )
        return EpgUiState(
            phase = EpgPhase.Content,
            channels = listOf(
                EpgChannel(
                    streamId = "10",
                    name = "Channel",
                    epgChannelId = "epg-10",
                    tvArchive = true,
                    tvArchiveDurationDays = 7,
                ),
            ),
            selectedChannelId = "10",
            nowNext = listOf(current),
            programmes = listOf(
                EpgProgrammeUi(
                    key = "archived-0",
                    programme = archived,
                    isCurrent = false,
                    catchUpRef = archivedRef,
                ),
                EpgProgrammeUi(
                    key = "current-1",
                    programme = current,
                    isCurrent = true,
                    catchUpRef = null,
                ),
            ),
        )
    }
}
