package com.pinkiptv.app

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pinkiptv.app.model.CatalogKind
import com.pinkiptv.app.model.CatalogPhase
import com.pinkiptv.app.model.CatalogUiState
import com.pinkiptv.app.ui.navigation.AuthenticatedShell
import com.pinkiptv.app.ui.theme.PinkTheme
import org.junit.Rule
import org.junit.Test

class NavigationShellTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun liveCatalogNavigatesAndBackReturnsHome() {
        var backDispatcher: OnBackPressedDispatcher? = null

        composeRule.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            PinkTheme {
                AuthenticatedShell(
                    liveCatalog = CatalogUiState(
                        kind = CatalogKind.Live,
                        phase = CatalogPhase.Loading,
                    ),
                    movieCatalog = CatalogUiState(CatalogKind.Movies),
                    seriesCatalog = CatalogUiState(CatalogKind.Series),
                    onLoadCatalog = {},
                    onSelectCatalogCategory = { _, _ -> },
                    onLogout = {},
                )
            }
        }

        composeRule.onNodeWithTag("home_live").performClick()
        composeRule.onNodeWithText("TV ao Vivo").assertExists()
        composeRule.onNodeWithTag("catalog_loading").assertIsDisplayed()

        composeRule.runOnIdle {
            requireNotNull(backDispatcher).onBackPressed()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_live").assertExists()
    }
}
