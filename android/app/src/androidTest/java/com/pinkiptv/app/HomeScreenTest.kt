package com.pinkiptv.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.pinkiptv.app.ui.screens.HomeScreen
import com.pinkiptv.app.ui.theme.PinkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeSupportsDpadFocusAndNavigation() {
        var selectedRoute: String? = null

        composeRule.setContent {
            PinkTheme {
                HomeScreen(onNavigate = { selectedRoute = it })
            }
        }

        val live = composeRule.onNodeWithTag("home_live")
        val movies = composeRule.onNodeWithTag("home_movies")

        live.requestFocus().assertIsFocused()
        live.performKeyInput { pressKey(Key.DirectionRight) }
        movies.assertIsFocused()

        movies.performClick()
        composeRule.runOnIdle {
            assertEquals("movies", selectedRoute)
        }
    }
}
