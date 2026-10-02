package com.pinkiptv.app

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import com.pinkiptv.app.ui.screens.SettingsScreen
import com.pinkiptv.app.ui.theme.PinkTheme
import com.pinkiptv.app.vpn.VpnPreparationPhase
import com.pinkiptv.app.vpn.VpnPreparationState
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class VpnSettingsDpadTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun preparationLogoutAndBackHaveDeterministicDpadOrder() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val configuration = instrumentation.targetContext.resources.configuration
        assumeTrue(
            configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
                Configuration.UI_MODE_TYPE_TELEVISION,
        )

        var prepareClicks = 0
        var logoutClicks = 0
        var backClicks = 0

        composeRule.setContent {
            PinkTheme {
                SettingsScreen(
                    vpnState = VpnPreparationState(
                        phase = VpnPreparationPhase.NOT_PREPARED,
                    ),
                    onPrepareVpn = { prepareClicks += 1 },
                    onLogout = { logoutClicks += 1 },
                    onBack = { backClicks += 1 },
                )
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

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
        composeRule.runOnIdle { assertEquals(1, logoutClicks) }

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        composeRule.waitForIdle()
        back.assertIsFocused()

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
        composeRule.runOnIdle { assertEquals(1, backClicks) }
    }
}
