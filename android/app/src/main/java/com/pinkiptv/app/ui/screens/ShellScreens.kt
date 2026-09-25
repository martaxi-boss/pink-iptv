package com.pinkiptv.app.ui.screens

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pinkiptv.app.R
import com.pinkiptv.app.ui.theme.Ink
import com.pinkiptv.app.ui.theme.PinkSoft

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
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    ShellLayout(titleRes = R.string.settings) {
        Text(
            text = stringResource(R.string.settings_local_session),
            style = MaterialTheme.typography.titleMedium,
        )
        FocusButton(
            label = stringResource(R.string.logout),
            onClick = onLogout,
        )
        FocusButton(
            label = stringResource(R.string.back),
            onClick = onBack,
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
            .padding(32.dp),
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
) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (focused) PinkSoft else MaterialTheme.colorScheme.primary,
            contentColor = Ink,
        ),
        border = BorderStroke(
            width = if (focused) 4.dp else 0.dp,
            color = PinkSoft,
        ),
        modifier = Modifier.onFocusChanged { focused = it.isFocused },
    ) {
        Text(label)
    }
}
