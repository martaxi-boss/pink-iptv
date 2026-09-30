package com.pinkiptv.app.ui.screens

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pinkiptv.app.model.CatalogCategory
import com.pinkiptv.app.model.CatalogPhase
import com.pinkiptv.app.model.CatalogUiError
import com.pinkiptv.app.model.CatalogUiItem
import com.pinkiptv.app.model.CatalogUiState
import com.pinkiptv.app.ui.theme.Ink
import com.pinkiptv.app.ui.theme.PinkSoft
import com.pinkiptv.app.ui.theme.SurfaceRaised

@Composable
fun CatalogScreen(
    @StringRes titleRes: Int,
    state: CatalogUiState,
    onLoad: () -> Unit,
    onSelectCategory: (String?) -> Unit,
    onBack: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isTv = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
        Configuration.UI_MODE_TYPE_TELEVISION
    val firstCategoryFocus = remember { FocusRequester() }
    var selectedItemName by rememberSaveable(state.kind) { mutableStateOf<String?>(null) }

    LaunchedEffect(state.phase) {
        if (state.phase == CatalogPhase.Idle) {
            onLoad()
        }
    }

    LaunchedEffect(isTv, state.phase) {
        if (isTv && state.phase == CatalogPhase.Content) {
            withFrameNanos { }
            firstCategoryFocus.requestFocus()
        }
    }

    LaunchedEffect(state.selectedCategoryId) {
        selectedItemName = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            CatalogActionButton(
                label = "Voltar",
                testTag = "catalog_back",
                onClick = onBack,
            )
        }

        when (state.phase) {
            CatalogPhase.Idle,
            CatalogPhase.Loading,
            -> LoadingCatalog()
            CatalogPhase.Empty -> EmptyCatalog()
            CatalogPhase.Error -> ErrorCatalog(
                error = state.error,
                onRetry = onLoad,
            )
            CatalogPhase.Content -> CatalogContent(
                state = state,
                firstCategoryFocus = firstCategoryFocus,
                selectedItemName = selectedItemName,
                onSelectCategory = onSelectCategory,
                onSelectItem = { selectedItemName = it.name },
            )
        }
    }
}

@Composable
private fun LoadingCatalog() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("catalog_loading"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text("A carregar catálogo…")
    }
}

@Composable
private fun EmptyCatalog() {
    Text(
        text = "Não existem itens disponíveis neste catálogo.",
        modifier = Modifier.testTag("catalog_empty"),
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun ErrorCatalog(
    error: CatalogUiError?,
    onRetry: () -> Unit,
) {
    val message = when (error) {
        CatalogUiError.SessionUnavailable -> "A sessão terminou. Volta a iniciar sessão."
        CatalogUiError.InvalidResponse -> "O fornecedor devolveu uma resposta inválida."
        CatalogUiError.ProviderUnavailable,
        null,
        -> "Não foi possível carregar o catálogo. Tenta novamente."
    }
    Column(
        modifier = Modifier.testTag("catalog_error"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message)
        CatalogActionButton(
            label = "Tentar novamente",
            testTag = "catalog_retry",
            onClick = onRetry,
        )
    }
}

@Composable
private fun ColumnScope.CatalogContent(
    state: CatalogUiState,
    firstCategoryFocus: FocusRequester,
    selectedItemName: String?,
    onSelectCategory: (String?) -> Unit,
    onSelectItem: (CatalogUiItem) -> Unit,
) {
    Text(
        text = "Categorias",
        style = MaterialTheme.typography.titleMedium,
    )
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item {
            CategoryChip(
                category = null,
                selected = state.selectedCategoryId == null,
                focusRequester = firstCategoryFocus,
                onClick = { onSelectCategory(null) },
            )
        }
        items(state.categories, key = { it.id }) { category ->
            CategoryChip(
                category = category,
                selected = state.selectedCategoryId == category.id,
                focusRequester = null,
                onClick = { onSelectCategory(category.id) },
            )
        }
    }

    if (selectedItemName != null) {
        Text(
            text = "Selecionado: " + selectedItemName,
            modifier = Modifier.testTag("catalog_selection"),
            color = PinkSoft,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Reprodução ainda não está ativa nesta fase.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    if (state.items.isEmpty()) {
        Text(
            text = "Sem itens nesta categoria.",
            modifier = Modifier.testTag("catalog_category_empty"),
        )
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 180.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("catalog_items"),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            gridItems(state.items, key = { it.id }) { item ->
                CatalogItemCard(
                    item = item,
                    onClick = { onSelectItem(item) },
                )
            }
        }
    }
}

@Composable
private fun CategoryChip(
    category: CatalogCategory?,
    selected: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val label = category?.name ?: "Todos"
    var modifier = Modifier
        .testTag("catalog_category_" + (category?.id ?: "all"))
        .onFocusChanged { focused = it.isFocused }
    if (focusRequester != null) {
        modifier = modifier.focusRequester(focusRequester)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected || focused) {
                MaterialTheme.colorScheme.primary
            } else {
                SurfaceRaised
            },
        ),
        border = BorderStroke(
            width = if (focused) 4.dp else 1.dp,
            color = if (focused) PinkSoft else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun CatalogItemCard(
    item: CatalogUiItem,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
        border = BorderStroke(
            width = if (focused) 4.dp else 1.dp,
            color = if (focused) PinkSoft else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp)
            .testTag("catalog_item_" + item.id)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
            )
            item.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CatalogActionButton(
    label: String,
    testTag: String,
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
        modifier = Modifier
            .testTag(testTag)
            .onFocusChanged { focused = it.isFocused },
    ) {
        Text(label)
    }
}
