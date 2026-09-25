package com.pinkiptv.app

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.pinkiptv.app.ui.screens.LoginScreen
import com.pinkiptv.app.ui.theme.PinkTheme
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class TvLoginDpadTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loginWorksWithDpadAndKeyboardInput() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val configuration = instrumentation.targetContext.resources.configuration
        assumeTrue(
            configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
                Configuration.UI_MODE_TYPE_TELEVISION,
        )

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

        val username = composeRule.onNodeWithTag("login_username")
        val password = composeRule.onNodeWithTag("login_password")
        val action = composeRule.onNodeWithTag("login_action")

        composeRule.waitForIdle()
        username.assertIsFocused()
        username.performTextInput("tvfixtureuser")
        username.performImeAction()
        composeRule.waitForIdle()
        password.assertIsFocused()
        password.performTextInput("tvfixturepass")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitForIdle()
        password.assertIsFocused()

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        composeRule.waitForIdle()
        action.assertIsFocused()

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
        composeRule.runOnIdle {
            assertEquals(
                "tvfixtureuser" to "tvfixturepass",
                submitted,
            )
        }
    }
}
