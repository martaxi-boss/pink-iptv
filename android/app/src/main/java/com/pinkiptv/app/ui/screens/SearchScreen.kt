package com.pinkiptv.app.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.dp
import com.pinkiptv.app.model.SearchItem
import com.pinkiptv.app.model.SearchKind
import com.pinkiptv.app.model.SearchPhase
import com.pinkiptv.app.model.SearchSourceStatus
import com.pinkiptv.app.model.SearchUiState
import com.pinkiptv.app.model.favoriteKey
import com.pinkiptv.app.ui.theme.Ink
import com.pinkiptv.app.ui.theme.PinkSoft
import com.pinkiptv.app.ui.theme.SurfaceRaised

@Composable
fun SearchScreen(
    state: SearchUiState,
    favoriteKeys: Set<String>,
    onLoad: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectKind: (SearchKind) -> Unit,
    onRetry: () -> Unit,
    onOpenItem: (SearchItem) -> Unit,
    onToggleFavorite: (SearchItem) -> Unit,
    onBack: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isTv = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
        Configuration.UI_MODE_TYPE_TELEVISION

    val queryFocus = remember { FocusRequester() }
    val filterKinds = remember {
        listOf(
            SearchKind.All,
            SearchKind.Live,
            SearchKind.Movies,
            SearchKind.Series,
        )
    }
    val filterRequesters = remember {
        List(filterKinds.size) { FocusRequester() }
    }
    val resultKeys = state.results.map { it.favoriteKey() }
    val resultRequesters = remember(resultKeys) {
        List(resultKeys.size) { FocusRequester() }
    }
    val favoriteRequesters = remember(resultKeys) {
        List(resultKeys.size) { FocusRequester() }
    }

    LaunchedEffect(
        state.liveSource.status,
        state.movieSource.status,
        state.seriesSource.status,
    ) {
        val neverLoaded = listOf(
            state.liveSource,
            state.movieSource,
            state.seriesSource,
        ).all { it.status == SearchSourceStatus.Idle }
        if (neverLoaded) {
            onLoad()
        }
    }

    LaunchedEffect(isTv) {
        if (isTv) {
            withFrameNanos { }
            queryFocus.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .testTag("search_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Pesquisa",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            SearchActionButton(
                label = "Voltar",
                testTag = "search_back",
                onClick = onBack,
            )
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            singleLine = true,
            label = { Text("Pesquisar") },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.testTag("search_clear"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Limpar pesquisa",
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PinkSoft,
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_query")
                .focusRequester(queryFocus)
                .focusProperties {
                    filterRequesters.firstOrNull()?.let { down = it }
                },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_filters"),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            filterKinds.forEachIndexed { index, kind ->
                SearchFilterChip(
                    kind = kind,
                    selected = state.selectedKind == kind,
                    focusRequester = filterRequesters[index],
                    left = filterRequesters.getOrNull(index - 1),
                    right = filterRequesters.getOrNull(index + 1),
                    up = queryFocus,
                    down = resultRequesters.firstOrNull(),
                    onClick = { onSelectKind(kind) },
                )
            }
        }

        when (state.phase) {
            SearchPhase.LoadingCatalog -> LoadingSearch()
            SearchPhase.FullError -> FullSearchError(onRetry)
            SearchPhase.PartialError -> {
                PartialSearchError(onRetry)
                SearchQueryContent(
                    state = state,
                    favoriteKeys = favoriteKeys,
                    resultRequesters = resultRequesters,
                    favoriteRequesters = favoriteRequesters,
                    filterUp = filterRequesters.getOrNull(
                        filterKinds.indexOf(state.selectedKind).coerceAtLeast(0),
                    ),
                    onOpenItem = onOpenItem,
                    onToggleFavorite = onToggleFavorite,
                )
            }
            SearchPhase.Inactive -> SearchPrompt()
            SearchPhase.NoMatches -> NoSearchMatches()
            SearchPhase.Ready -> SearchResults(
                items = state.results,
                favoriteKeys = favoriteKeys,
                resultRequesters = resultRequesters,
                favoriteRequesters = favoriteRequesters,
                filterUp = filterRequesters.getOrNull(
                    filterKinds.indexOf(state.selectedKind).coerceAtLeast(0),
                ),
                onOpenItem = onOpenItem,
                onToggleFavorite = onToggleFavorite,
            )
        }
    }
}

@Composable
private fun SearchQueryContent(
    state: SearchUiState,
    favoriteKeys: Set<String>,
    resultRequesters: List<FocusRequester>,
    favoriteRequesters: List<FocusRequester>,
    filterUp: FocusRequester?,
    onOpenItem: (SearchItem) -> Unit,
    onToggleFavorite: (SearchItem) -> Unit,
) {
    when {
        !state.effectiveQuery -> SearchPrompt()
        state.results.isEmpty() -> NoSearchMatches()
        else -> SearchResults(
            items = state.results,
            favoriteKeys = favoriteKeys,
            resultRequesters = resultRequesters,
            favoriteRequesters = favoriteRequesters,
            filterUp = filterUp,
            onOpenItem = onOpenItem,
            onToggleFavorite = onToggleFavorite,
        )
    }
}

@Composable
private fun LoadingSearch() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_loading"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator()
        Text("A carregar TV, filmes e séries…")
    }
}

@Composable
private fun SearchPrompt() {
    Text(
        text = "Escreve pelo menos 2 caracteres para pesquisar.",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.testTag("search_inactive"),
    )
}

@Composable
private fun NoSearchMatches() {
    Text(
        text = "Sem resultados para esta pesquisa.",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.testTag("search_no_results"),
    )
}

@Composable
private fun PartialSearchError(
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_partial_error"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Parte do catálogo não está disponível. Os resultados carregados continuam utilizáveis.",
            modifier = Modifier.weight(1f),
        )
        SearchActionButton(
            label = "Repetir",
            testTag = "search_partial_retry",
            onClick = onRetry,
        )
    }
}

@Composable
private fun FullSearchError(
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_error"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Não foi possível carregar a pesquisa. Tenta novamente.")
        SearchActionButton(
            label = "Tentar novamente",
            testTag = "search_retry",
            onClick = onRetry,
        )
    }
}

@Composable
private fun SearchResults(
    items: List<SearchItem>,
    favoriteKeys: Set<String>,
    resultRequesters: List<FocusRequester>,
    favoriteRequesters: List<FocusRequester>,
    filterUp: FocusRequester?,
    onOpenItem: (SearchItem) -> Unit,
    onToggleFavorite: (SearchItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_results"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(
            items = items,
            key = { _, item -> item.favoriteKey() },
        ) { index, item ->
            SearchResultCard(
                item = item,
                favorite = item.favoriteKey() in favoriteKeys,
                focusRequester = resultRequesters.getOrNull(index),
                favoriteFocusRequester = favoriteRequesters.getOrNull(index),
                up = if (index == 0) filterUp else favoriteRequesters.getOrNull(index - 1),
                next = resultRequesters.getOrNull(index + 1),
                onOpen = { onOpenItem(item) },
                onToggleFavorite = { onToggleFavorite(item) },
            )
        }
    }
}

@Composable
private fun SearchFilterChip(
    kind: SearchKind,
    selected: Boolean,
    focusRequester: FocusRequester,
    left: FocusRequester?,
    right: FocusRequester?,
    up: FocusRequester,
    down: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val label = when (kind) {
        SearchKind.All -> "TODOS"
        SearchKind.Live -> "TV AO VIVO"
        SearchKind.Movies -> "FILMES"
        SearchKind.Series -> "SÉRIES"
    }
    val tag = when (kind) {
        SearchKind.All -> "all"
        SearchKind.Live -> "live"
        SearchKind.Movies -> "movies"
        SearchKind.Series -> "series"
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
        modifier = Modifier
            .testTag("search_filter_" + tag)
            .focusRequester(focusRequester)
            .focusProperties {
                left?.let { this.left = it }
                right?.let { this.right = it }
                this.up = up
                down?.let { this.down = it }
            }
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun SearchResultCard(
    item: SearchItem,
    favorite: Boolean,
    focusRequester: FocusRequester?,
    favoriteFocusRequester: FocusRequester?,
    up: FocusRequester?,
    next: FocusRequester?,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var cardModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 104.dp)
        .testTag("search_result_" + item.kind.name + "_" + item.providerId)
        .onFocusChanged { focused = it.isFocused }
        .focusProperties {
            up?.let { this.up = it }
            favoriteFocusRequester?.let { this.down = it }
        }
    if (focusRequester != null) {
        cardModifier = cardModifier.focusRequester(focusRequester)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
        border = BorderStroke(
            width = if (focused) 4.dp else 1.dp,
            color = if (focused) PinkSoft else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = cardModifier.clickable(onClick = onOpen),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = when (item.kind) {
                    SearchKind.Live -> "TV AO VIVO"
                    SearchKind.Movies -> "FILME"
                    SearchKind.Series -> "SÉRIE"
                    SearchKind.All -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SearchFavoriteButton(
                favorite = favorite,
                testTag = "search_favorite_" + item.kind.name + "_" + item.providerId,
                focusRequester = favoriteFocusRequester,
                up = focusRequester,
                down = next,
                onClick = onToggleFavorite,
            )
        }
    }
}

@Composable
private fun SearchFavoriteButton(
    favorite: Boolean,
    testTag: String,
    focusRequester: FocusRequester?,
    up: FocusRequester?,
    down: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var modifier = Modifier
        .testTag(testTag)
        .onFocusChanged { focused = it.isFocused }
        .focusProperties {
            up?.let { this.up = it }
            down?.let { this.down = it }
        }
    if (focusRequester != null) {
        modifier = modifier.focusRequester(focusRequester)
    }
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
        modifier = modifier,
    ) {
        Text(if (favorite) "♥ FAVORITO" else "♡ FAVORITO")
    }
}

@Composable
private fun SearchActionButton(
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
