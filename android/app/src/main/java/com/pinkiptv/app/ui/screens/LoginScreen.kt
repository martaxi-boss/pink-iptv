package com.pinkiptv.app.ui.screens

import android.content.res.Configuration
import android.view.KeyEvent as AndroidKeyEvent
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pinkiptv.app.R
import com.pinkiptv.app.state.LoginError
import com.pinkiptv.app.ui.theme.Ink
import com.pinkiptv.app.ui.theme.PinkSoft

@Composable
fun LoginScreen(
    loginInFlight: Boolean,
    loginError: LoginError?,
    onLogin: (String, String) -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var usernameFocused by remember { mutableStateOf(false) }
    var passwordFocused by remember { mutableStateOf(false) }
    var pendingButtonFocus by remember { mutableStateOf(false) }
    var buttonFocused by remember { mutableStateOf(false) }
    val usernameFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val buttonFocus = remember { FocusRequester() }
    val configuration = LocalConfiguration.current
    val isTv = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
        Configuration.UI_MODE_TYPE_TELEVISION
    val view = LocalView.current
    val currentUsernameFocused by rememberUpdatedState(usernameFocused)
    val currentPasswordFocused by rememberUpdatedState(passwordFocused)
    val currentButtonFocused by rememberUpdatedState(buttonFocused)

    DisposableEffect(view, isTv) {
        if (!isTv) {
            onDispose { }
        } else {
            val listener = View.OnKeyListener { _, keyCode, event ->
                if (event.action != AndroidKeyEvent.ACTION_DOWN) {
                    false
                } else {
                    when {
                        keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN &&
                            currentUsernameFocused -> {
                            passwordFocus.requestFocus()
                            true
                        }
                        keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN &&
                            currentPasswordFocused -> {
                            pendingButtonFocus = true
                            true
                        }
                        keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP &&
                            currentPasswordFocused -> {
                            usernameFocus.requestFocus()
                            true
                        }
                        keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP &&
                            currentButtonFocused -> {
                            passwordFocus.requestFocus()
                            true
                        }
                        else -> false
                    }
                }
            }
            view.setOnKeyListener(listener)
            onDispose { view.setOnKeyListener(null) }
        }
    }

    LaunchedEffect(isTv) {
        if (isTv) usernameFocus.requestFocus()
    }

    LaunchedEffect(pendingButtonFocus) {
        if (pendingButtonFocus) {
            withFrameNanos { }
            buttonFocus.requestFocus()
            pendingButtonFocus = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (isTv && event.type == KeyEventType.KeyDown) {
                    when {
                        event.key == Key.DirectionDown && usernameFocused -> {
                            passwordFocus.requestFocus()
                            true
                        }
                        event.key == Key.DirectionDown && passwordFocused -> {
                            pendingButtonFocus = true
                            true
                        }
                        event.key == Key.DirectionUp && passwordFocused -> {
                            usernameFocus.requestFocus()
                            true
                        }
                        event.key == Key.DirectionUp && buttonFocused -> {
                            passwordFocus.requestFocus()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(
            20.dp,
            alignment = Alignment.CenterVertically,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(stringResource(R.string.username_label)) },
            singleLine = true,
            enabled = !loginInFlight,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(
                onNext = { passwordFocus.requestFocus() },
            ),
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .testTag("login_username")
                .focusRequester(usernameFocus)
                .focusProperties { down = passwordFocus }
                .onFocusChanged { usernameFocused = it.isFocused },
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.password_label)) },
            singleLine = true,
            enabled = !loginInFlight,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { onLogin(username, password) },
            ),
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .testTag("login_password")
                .focusRequester(passwordFocus)
                .onPreviewKeyEvent { event ->
                    if (isTv && event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionUp -> {
                                usernameFocus.requestFocus()
                                true
                            }
                            Key.DirectionDown -> {
                                buttonFocus.requestFocus()
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
                .focusProperties {
                    up = usernameFocus
                    down = buttonFocus
                }
                .onFocusChanged { passwordFocused = it.isFocused },
        )

        loginError?.let { error ->
            Text(
                text = stringResource(error.messageRes()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        Button(
            onClick = { onLogin(username, password) },
            enabled = !loginInFlight,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (buttonFocused) PinkSoft else MaterialTheme.colorScheme.primary,
                contentColor = Ink,
            ),
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .testTag("login_action")
                .focusRequester(buttonFocus)
                .focusProperties { up = passwordFocus }
                .onFocusChanged { buttonFocused = it.isFocused },
        ) {
            if (loginInFlight) {
                CircularProgressIndicator(
                    color = Ink,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(R.string.login_action))
            }
        }
    }
}

private fun LoginError.messageRes(): Int =
    when (this) {
        LoginError.Required -> R.string.login_error_required
        LoginError.InvalidCredentials -> R.string.login_error_invalid
        LoginError.Expired -> R.string.login_error_expired
        LoginError.Disabled -> R.string.login_error_disabled
        LoginError.TemporaryUnavailable -> R.string.login_error_temporary
    }
