package com.pinkiptv.app

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.pinkiptv.app.ui.screens.HomeScreen
import com.pinkiptv.app.ui.theme.PinkTheme
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeSupportsDpadFocusAndNavigation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val configuration = instrumentation.targetContext.resources.configuration
        assumeTrue(
            configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
                Configuration.UI_MODE_TYPE_TELEVISION,
        )

        var selectedRoute: String? = null

        composeRule.setContent {
            PinkTheme {
                HomeScreen(onNavigate = { selectedRoute = it })
            }
        }

        val live = composeRule.onNodeWithTag("home_live")
        val movies = composeRule.onNodeWithTag("home_movies")

        composeRule.waitForIdle()
        live.assertIsFocused()

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        composeRule.waitForIdle()
        movies.assertIsFocused()

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
        composeRule.runOnIdle {
            assertEquals("movies", selectedRoute)
        }
    }

    @Test
    fun homeHasSevenTilesAndSearchIsDpadReachable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val configuration = instrumentation.targetContext.resources.configuration
        assumeTrue(
            configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
                Configuration.UI_MODE_TYPE_TELEVISION,
        )

        var selectedRoute: String? = null
        composeRule.setContent {
            PinkTheme {
                Box(
                    modifier = Modifier
                        .width(1200.dp)
                        .height(900.dp),
                ) {
                    HomeScreen(onNavigate = { selectedRoute = it })
                }
            }
        }

        composeRule.onNodeWithTag("home_live").assertIsDisplayed()
        composeRule.onNodeWithTag("home_movies").assertIsDisplayed()
        composeRule.onNodeWithTag("home_series").assertIsDisplayed()
        composeRule.onNodeWithTag("home_epg").assertIsDisplayed()
        composeRule.onNodeWithTag("home_favorites").assertIsDisplayed()
        composeRule.onNodeWithTag("home_search").assertIsDisplayed()
        composeRule.onNodeWithTag("home_settings").assertIsDisplayed()

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_live").assertIsFocused()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_search").assertIsFocused()

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
        composeRule.runOnIdle {
            assertEquals("search", selectedRoute)
        }
    }
}
