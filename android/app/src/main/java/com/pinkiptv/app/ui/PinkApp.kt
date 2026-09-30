package com.pinkiptv.app.ui

import androidx.compose.runtime.Composable
import com.pinkiptv.app.model.CatalogKind
import com.pinkiptv.app.model.CatalogUiState
import com.pinkiptv.app.state.AppUiState
import com.pinkiptv.app.state.RootScreen
import com.pinkiptv.app.ui.navigation.AuthenticatedShell
import com.pinkiptv.app.ui.screens.LoginScreen
import com.pinkiptv.app.ui.screens.SplashScreen

@Composable
fun PinkApp(
    state: AppUiState,
    liveCatalog: CatalogUiState,
    movieCatalog: CatalogUiState,
    seriesCatalog: CatalogUiState,
    onLogin: (String, String) -> Unit,
    onLogout: () -> Unit,
    onLoadCatalog: (CatalogKind) -> Unit,
    onSelectCatalogCategory: (CatalogKind, String?) -> Unit,
) {
    when (state.screen) {
        RootScreen.Splash -> SplashScreen()
        RootScreen.Login -> LoginScreen(
            loginInFlight = state.loginInFlight,
            loginError = state.loginError,
            onLogin = onLogin,
        )
        RootScreen.Home -> AuthenticatedShell(
            liveCatalog = liveCatalog,
            movieCatalog = movieCatalog,
            seriesCatalog = seriesCatalog,
            onLoadCatalog = onLoadCatalog,
            onSelectCatalogCategory = onSelectCatalogCategory,
            onLogout = onLogout,
        )
    }
}
