package com.pinkiptv.app

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.pinkiptv.app.model.CatalogCategory
import com.pinkiptv.app.model.CatalogKind
import com.pinkiptv.app.model.CatalogPhase
import com.pinkiptv.app.model.CatalogUiError
import com.pinkiptv.app.model.CatalogUiItem
import com.pinkiptv.app.model.CatalogUiState
import com.pinkiptv.app.ui.screens.CatalogScreen
import com.pinkiptv.app.ui.theme.PinkTheme
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class CatalogScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun liveCatalogSupportsContentAndCategorySelection() {
        var selected: String? = "unset"

        composeRule.setContent {
            PinkTheme {
                CatalogScreen(
                    titleRes = R.string.live_tv,
                    state = contentState(CatalogKind.Live),
                    onLoad = {},
                    onSelectCategory = { selected = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("catalog_items").assertIsDisplayed()
        composeRule.onNodeWithTag("catalog_category_news").performClick()
        composeRule.runOnIdle {
            assertEquals("news", selected)
        }
    }


    @Test
    fun catalogFavoriteActionIsSeparateAndReflectsFavoriteState() {
        var toggled: CatalogUiItem? = null
        val state = contentState(CatalogKind.Movies)

        composeRule.setContent {
            PinkTheme {
                CatalogScreen(
                    titleRes = R.string.movies,
                    state = state,
                    onLoad = {},
                    onSelectCategory = {},
                    onBack = {},
                    favoriteIds = setOf("item-1"),
                    onToggleFavorite = { toggled = it },
                )
            }
        }

        composeRule.onNodeWithTag("catalog_favorite_item-1").assertIsDisplayed()
        composeRule.onNodeWithTag("catalog_favorite_item-1").performClick()
        composeRule.runOnIdle {
            assertEquals("item-1", toggled?.id)
        }
    }

    @Test
    fun moviesErrorOffersRetryAndSeriesEmptyIsStable() {
        var retries = 0
        composeRule.setContent {
            PinkTheme {
                CatalogScreen(
                    titleRes = R.string.movies,
                    state = CatalogUiState(
                        kind = CatalogKind.Movies,
                        phase = CatalogPhase.Error,
                        error = CatalogUiError.ProviderUnavailable,
                    ),
                    onLoad = { retries += 1 },
                    onSelectCategory = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("catalog_error").assertIsDisplayed()
        composeRule.onNodeWithTag("catalog_retry").performClick()
        composeRule.runOnIdle { assertEquals(1, retries) }

        composeRule.setContent {
            PinkTheme {
                CatalogScreen(
                    titleRes = R.string.series,
                    state = CatalogUiState(
                        kind = CatalogKind.Series,
                        phase = CatalogPhase.Empty,
                    ),
                    onLoad = {},
                    onSelectCategory = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("catalog_empty").assertIsDisplayed()
    }

    @Test
    fun tvContentStartsWithPredictableCategoryFocus() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val configuration = instrumentation.targetContext.resources.configuration
        assumeTrue(
            configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
                Configuration.UI_MODE_TYPE_TELEVISION,
        )

        composeRule.setContent {
            PinkTheme {
                CatalogScreen(
                    titleRes = R.string.live_tv,
                    state = contentState(CatalogKind.Live),
                    onLoad = {},
                    onSelectCategory = {},
                    onBack = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("catalog_category_all").assertIsFocused()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("catalog_category_news").assertIsFocused()
    }

    private fun contentState(kind: CatalogKind) = CatalogUiState(
        kind = kind,
        phase = CatalogPhase.Content,
        categories = listOf(
            CatalogCategory("news", "Notícias"),
            CatalogCategory("sports", "Desporto"),
        ),
        items = listOf(
            CatalogUiItem(
                id = "item-1",
                name = "Canal 1",
                categoryId = "news",
                artworkUrl = null,
                subtitle = "live",
            ),
        ),
    )
}
