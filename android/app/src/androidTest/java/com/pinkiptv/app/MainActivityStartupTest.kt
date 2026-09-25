package com.pinkiptv.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class MainActivityStartupTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun freshStartReachesLoginWithoutTechnicalFields() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("USERNAME")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithText("USERNAME").assertExists()
        composeRule.onNodeWithText("PASSWORD").assertExists()
        composeRule.onNodeWithText("ENTRAR").assertExists()

        composeRule.onNodeWithText("DNS").assertDoesNotExist()
        composeRule.onNodeWithText("M3U").assertDoesNotExist()
        composeRule.onNodeWithText("PORTAL").assertDoesNotExist()
    }
}
