package com.pinkiptv.app

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
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
    fun preparationLogoutAndBackHaveDeterministicDpadOrder() {
        var prepareClicks = 0

        composeRule.setContent {
            val phoneConfiguration = LocalConfiguration.current
            val tvConfiguration = Configuration(phoneConfiguration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_TYPE_MASK.inv()) or
                    Configuration.UI_MODE_TYPE_TELEVISION
            }

            CompositionLocalProvider(LocalConfiguration provides tvConfiguration) {
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

        composeRule.waitForIdle()

        val prepare = composeRule.onNodeWithTag("settings_vpn_prepare")
        prepare.assertIsFocused()

        prepare.performKeyInput { pressKey(Key.Enter) }
        composeRule.runOnIdle { assertEquals(1, prepareClicks) }

        prepare.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("settings_logout").assertIsFocused()

        composeRule.onNodeWithTag("settings_logout")
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("settings_back").assertIsFocused()

        composeRule.onNodeWithTag("settings_back")
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("settings_logout").assertIsFocused()
    }
}
