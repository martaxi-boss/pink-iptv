package com.pinkiptv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pinkiptv.app.state.AppViewModel
import com.pinkiptv.app.ui.PinkApp
import com.pinkiptv.app.ui.theme.PinkTheme

class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels {
        val container = (application as PinkApplication).container
        AppViewModel.Factory(
            repository = container.sessionRepository,
            credentialStore = container.credentialStore,
            catalogRepository = container.catalogRepository,
            providerSessionStore = container.providerSessionStore,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PinkTheme {
                val state by appViewModel.uiState.collectAsStateWithLifecycle()
                val liveCatalog by appViewModel.liveCatalog.collectAsStateWithLifecycle()
                val movieCatalog by appViewModel.movieCatalog.collectAsStateWithLifecycle()
                val seriesCatalog by appViewModel.seriesCatalog.collectAsStateWithLifecycle()

                PinkApp(
                    state = state,
                    liveCatalog = liveCatalog,
                    movieCatalog = movieCatalog,
                    seriesCatalog = seriesCatalog,
                    onLogin = appViewModel::login,
                    onLogout = appViewModel::logout,
                    onLoadCatalog = appViewModel::loadCatalog,
                    onSelectCatalogCategory = appViewModel::selectCatalogCategory,
                )
            }
        }
    }
}
