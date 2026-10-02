package com.pinkiptv.app.ui.screens

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pinkiptv.app.R
import com.pinkiptv.app.ui.theme.Ink
import com.pinkiptv.app.ui.theme.PinkSoft
import com.pinkiptv.app.vpn.VpnPreparationPhase
import com.pinkiptv.app.vpn.VpnPreparationState

@Composable
fun ShellScreen(
    @StringRes titleRes: Int,
    onBack: () -> Unit,
) {
    ShellLayout(titleRes = titleRes) {
        Text(
            text = stringResource(R.string.next_phase),
            style = MaterialTheme.typography.titleLarge,
        )
        FocusButton(
            label = stringResource(R.string.back),
            onClick = onBack,
        )
    }
}

@Composable
fun SettingsScreen(
    vpnState: VpnPreparationState,
    onPrepareVpn: () -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isTv = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
        Configuration.UI_MODE_TYPE_TELEVISION
    val prepareFocus = remember { FocusRequester() }
    val logoutFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }

    LaunchedEffect(isTv) {
        if (isTv) {
            withFrameNanos { }
            prepareFocus.requestFocus()
        }
    }

    val status = when (vpnState.phase) {
        VpnPreparationPhase.NOT_PREPARED -> R.string.vpn_status_not_prepared
        VpnPreparationPhase.LOCAL_IDENTITY_READY -> R.string.vpn_status_permission_needed
        VpnPreparationPhase.AWAITING_SYSTEM_PERMISSION -> R.string.vpn_status_awaiting_permission
        VpnPreparationPhase.READY_FOR_TUNNEL_STAGE -> R.string.vpn_status_ready
        VpnPreparationPhase.PERMISSION_DENIED -> R.string.vpn_status_permission_denied
        VpnPreparationPhase.ERROR -> R.string.vpn_status_error
    }

    ShellLayout(titleRes = R.string.settings) {
        Text(
            text = stringResource(R.string.settings_local_session),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.wireguard_vpn),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(status),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag("settings_vpn_status"),
        )
        FocusButton(
            label = stringResource(R.string.vpn_prepare),
            onClick = onPrepareVpn,
            enabled = vpnState.phase != VpnPreparationPhase.AWAITING_SYSTEM_PERMISSION,
            modifier = Modifier
                .testTag("settings_vpn_prepare")
                .focusRequester(prepareFocus)
                .focusProperties {
                    up = backFocus
                    down = logoutFocus
                },
        )
        FocusButton(
            label = stringResource(R.string.logout),
            onClick = onLogout,
            modifier = Modifier
                .testTag("settings_logout")
                .focusRequester(logoutFocus)
                .focusProperties {
                    up = prepareFocus
                    down = backFocus
                },
        )
        FocusButton(
            label = stringResource(R.string.back),
            onClick = onBack,
            modifier = Modifier
                .testTag("settings_back")
                .focusRequester(backFocus)
                .focusProperties {
                    up = logoutFocus
                    down = prepareFocus
                },
        )
    }
}

@Composable
private fun ShellLayout(
    @StringRes titleRes: Int,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .testTag(if (titleRes == R.string.settings) "settings_screen" else "shell_screen"),
        verticalArrangement = Arrangement.spacedBy(
            space = 24.dp,
            alignment = Alignment.CenterVertically,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

@Composable
private fun FocusButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (focused) PinkSoft else MaterialTheme.colorScheme.primary,
            contentColor = Ink,
        ),
        border = BorderStroke(
            width = if (focused) 4.dp else 0.dp,
            color = PinkSoft,
        ),
        modifier = modifier.onFocusChanged { focused = it.isFocused },
    ) {
        Text(label)
    }
}
