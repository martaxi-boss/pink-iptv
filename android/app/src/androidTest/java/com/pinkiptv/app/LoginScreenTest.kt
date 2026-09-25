package com.pinkiptv.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.pinkiptv.app.ui.screens.LoginScreen
import com.pinkiptv.app.ui.theme.PinkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LoginScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loginContainsOnlyExpectedCredentialControlsAndSubmits() {
        var submitted: Pair<String, String>? = null

        composeRule.setContent {
            PinkTheme {
                LoginScreen(
                    loginInFlight = false,
                    loginError = null,
                    onLogin = { username, password ->
                        submitted = username to password
                    },
                )
            }
        }

        composeRule.onNodeWithText("USERNAME").assertExists()
        composeRule.onNodeWithText("PASSWORD").assertExists()
        composeRule.onNodeWithText("ENTRAR").assertExists()
        composeRule.onNodeWithTag("login_password").assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Password),
        )

        composeRule.onNodeWithText("DNS").assertDoesNotExist()
        composeRule.onNodeWithText("M3U").assertDoesNotExist()
        composeRule.onNodeWithText("PORTAL").assertDoesNotExist()

        composeRule.onNodeWithTag("login_username").performTextInput("fixture-user")
        composeRule.onNodeWithTag("login_password")
            .performTextInput("fixture-pass") // pragma: allowlist secret
        composeRule.onNodeWithTag("login_action").performClick()

        composeRule.runOnIdle {
            assertEquals(
                "fixture-user" to "fixture-pass", // pragma: allowlist secret
                submitted,
            )
        }
    }
}
