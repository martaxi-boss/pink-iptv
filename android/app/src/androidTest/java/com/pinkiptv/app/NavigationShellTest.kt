package com.pinkiptv.app

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pinkiptv.app.ui.navigation.AuthenticatedShell
import com.pinkiptv.app.ui.theme.PinkTheme
import org.junit.Rule
import org.junit.Test

class NavigationShellTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun liveShellNavigatesAndBackReturnsHome() {
        composeRule.setContent {
            PinkTheme {
                AuthenticatedShell(onLogout = {})
            }
        }

        composeRule.onNodeWithTag("home_live").performClick()
        composeRule.onNodeWithText("TV ao Vivo").assertExists()
        composeRule.onNodeWithText("Disponível numa fase seguinte.").assertExists()

        composeRule.onNodeWithText("Voltar").performClick()
        composeRule.onNodeWithTag("home_live").assertExists()
    }
}
