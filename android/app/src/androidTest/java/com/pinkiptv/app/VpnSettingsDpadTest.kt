package com.pinkiptv.app

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import com.pinkiptv.app.ui.screens.SettingsScreen
import com.pinkiptv.app.ui.theme.PinkTheme
import com.pinkiptv.app.vpn.VpnPreparationPhase
import com.pinkiptv.app.vpn.VpnPreparationState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VpnSettingsDpadTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun touchPreparationInvokesOnlyThePreparationAction() {
        var prepareClicks = 0
        composeRule.setContent {
            PinkTheme {
                SettingsScreen(
                    vpnState = VpnPreparationState(),
                    onPrepareVpn = { prepareClicks += 1 },
                    onLogout = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("settings_vpn_status").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_vpn_prepare").performClick()
        composeRule.runOnIdle { assertEquals(1, prepareClicks) }
    }

    @Test
    fun preparationLogoutAndBackHaveDeterministicDpadOrder() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Exercise TV focus logic on the CI emulator, without claiming physical-TV proof.
        val configuration = Configuration(
            instrumentation.targetContext.resources.configuration,
        ).apply {
            uiMode = (uiMode and Configuration.UI_MODE_TYPE_MASK.inv()) or
                Configuration.UI_MODE_TYPE_TELEVISION
        }

        var prepareClicks = 0

        composeRule.setContent {
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                val inputModeManager = LocalInputModeManager.current
                SideEffect {
                    check(inputModeManager.requestInputMode(InputMode.Keyboard)) {
                        "Simulated TV proof requires keyboard input mode"
                    }
                }
                PinkTheme {
                    SettingsScreen(
                        vpnState = VpnPreparationState(
                            phase = VpnPreparationPhase.NOT_PREPARED,
                        ),
                        onPrepareVpn = { prepareClicks += 1 },
                        onLogout = {},
                        onBack = {},
                    )
                }
            }
        }

        val prepare = composeRule.onNodeWithTag("settings_vpn_prepare")
        val logout = composeRule.onNodeWithTag("settings_logout")
        val back = composeRule.onNodeWithTag("settings_back")

        composeRule.waitForIdle()
        prepare.assertIsFocused()

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
        composeRule.runOnIdle { assertEquals(1, prepareClicks) }

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        composeRule.waitForIdle()
        logout.assertIsFocused()

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        composeRule.waitForIdle()
        back.assertIsFocused()

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
        composeRule.waitForIdle()
        logout.assertIsFocused()
    }
}
