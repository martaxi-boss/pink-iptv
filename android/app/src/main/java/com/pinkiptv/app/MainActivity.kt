package com.pinkiptv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.pinkiptv.app.state.AppViewModel
import com.pinkiptv.app.ui.PinkApp
import com.pinkiptv.app.ui.theme.PinkTheme

class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels {
        val container = (application as PinkApplication).container
        AppViewModel.Factory(
            repository = container.sessionRepository,
            credentialStore = container.credentialStore,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PinkTheme {
                val state by appViewModel.uiState.collectAsStateWithLifecycle()
                PinkApp(
                    state = state,
                    onLogin = appViewModel::login,
                    onLogout = appViewModel::logout,
                )
            }
        }
    }
}
