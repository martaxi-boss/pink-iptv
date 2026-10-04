package com.pinkiptv.app.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pinkiptv.app.model.ContinueWatchingItem
import com.pinkiptv.app.model.FavoriteItem
import com.pinkiptv.app.model.FavoriteKind
import com.pinkiptv.app.model.HistoryItem
import com.pinkiptv.app.model.LibraryPhase
import com.pinkiptv.app.model.LibraryUiState
import com.pinkiptv.app.model.PlaybackKind
import com.pinkiptv.app.ui.theme.Ink
import com.pinkiptv.app.ui.theme.PinkSoft
import com.pinkiptv.app.ui.theme.SurfaceRaised

private enum class LibrarySection {
    Favorites,
    Continue,
    Recents,
}

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onOpenFavorite: (FavoriteItem) -> Unit,
    onRemoveFavorite: (FavoriteItem) -> Unit,
    onOpenContinue: (ContinueWatchingItem) -> Unit,
    onOpenRecent: (HistoryItem) -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isTv = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
        Configuration.UI_MODE_TYPE_TELEVISION
    val firstTabFocus = remember { FocusRequester() }
    var section by remember { mutableStateOf(LibrarySection.Favorites) }

    LaunchedEffect(isTv) {
        if (isTv) {
            withFrameNanos { }
            firstTabFocus.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .testTag("library_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Favoritos",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            LibraryActionButton(
                label = "Voltar",
                testTag = "library_back",
                onClick = onBack,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("library_tabs")
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LibraryTabButton(
                label = "FAVORITOS",
                selected = section == LibrarySection.Favorites,
                testTag = "library_tab_favorites",
                focusRequester = if (isTv) firstTabFocus else null,
                onClick = { section = LibrarySection.Favorites },
            )
            LibraryTabButton(
                label = "CONTINUAR",
                selected = section == LibrarySection.Continue,
                testTag = "library_tab_continue",
                onClick = { section = LibrarySection.Continue },
            )
            LibraryTabButton(
                label = "RECENTES",
                selected = section == LibrarySection.Recents,
                testTag = "library_tab_recents",
                onClick = { section = LibrarySection.Recents },
            )
        }

        when (state.phase) {
            LibraryPhase.Inactive,
            LibraryPhase.Loading,
            -> LibraryLoading()

            LibraryPhase.Error -> Text(
                text = "Não foi possível aceder à biblioteca local.",
                modifier = Modifier.testTag("library_error"),
            )

            LibraryPhase.Ready -> when (section) {
                LibrarySection.Favorites -> FavoritesSection(
                    items = state.favorites,
                    onOpen = onOpenFavorite,
                    onRemove = onRemoveFavorite,
                )
                LibrarySection.Continue -> ContinueSection(
                    items = state.continueWatching,
                    onOpen = onOpenContinue,
                )
                LibrarySection.Recents -> RecentsSection(
                    items = state.recents,
                    onOpen = onOpenRecent,
                    onClearHistory = onClearHistory,
                )
            }
        }
    }
}

@Composable
private fun LibraryLoading() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library_loading"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator()
        Text("A carregar biblioteca…")
    }
}

@Composable
private fun FavoritesSection(
    items: List<FavoriteItem>,
    onOpen: (FavoriteItem) -> Unit,
    onRemove: (FavoriteItem) -> Unit,
) {
    if (items.isEmpty()) {
        Text(
            text = "Ainda não tens favoritos.",
            modifier = Modifier.testTag("library_favorites_empty"),
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library_favorites_list"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items, key = { it.kind.name + "|" + it.providerId }) { item ->
            LibraryCard(
                title = item.title,
                subtitle = favoriteKindLabel(item.kind),
                testTag = "library_favorite_" + item.kind.name + "_" + item.providerId,
            ) {
                LibraryActionButton(
                    label = "ABRIR",
                    testTag = "library_favorite_open_" + item.kind.name + "_" + item.providerId,
                    onClick = { onOpen(item) },
                )
                LibraryActionButton(
                    label = "REMOVER",
                    testTag = "library_favorite_remove_" + item.kind.name + "_" + item.providerId,
                    onClick = { onRemove(item) },
                )
            }
        }
    }
}

@Composable
private fun ContinueSection(
    items: List<ContinueWatchingItem>,
    onOpen: (ContinueWatchingItem) -> Unit,
) {
    if (items.isEmpty()) {
        Text(
            text = "Nada para continuar.",
            modifier = Modifier.testTag("library_continue_empty"),
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library_continue_list"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items, key = { it.history.playbackKind.name + "|" + it.history.mediaId }) { item ->
            LibraryCard(
                title = item.history.title,
                subtitle = playbackKindLabel(item.history.playbackKind) +
                    " · " + item.progressPercent + "%",
                testTag = "library_continue_" + item.history.mediaId,
            ) {
                LibraryActionButton(
                    label = "CONTINUAR",
                    testTag = "library_continue_open_" + item.history.mediaId,
                    onClick = { onOpen(item) },
                )
            }
        }
    }
}

@Composable
private fun RecentsSection(
    items: List<HistoryItem>,
    onOpen: (HistoryItem) -> Unit,
    onClearHistory: () -> Unit,
) {
    if (items.isEmpty()) {
        Text(
            text = "Ainda não existe histórico.",
            modifier = Modifier.testTag("library_recents_empty"),
        )
        return
    }

    LibraryActionButton(
        label = "LIMPAR HISTÓRICO",
        testTag = "library_clear_history",
        onClick = onClearHistory,
    )
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library_recents_list"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items, key = { it.playbackKind.name + "|" + it.mediaId }) { item ->
            LibraryCard(
                title = item.title,
                subtitle = playbackKindLabel(item.playbackKind),
                testTag = "library_recent_" + item.playbackKind.name + "_" + item.mediaId,
            ) {
                LibraryActionButton(
                    label = "ABRIR",
                    testTag = "library_recent_open_" + item.playbackKind.name + "_" + item.mediaId,
                    onClick = { onOpen(item) },
                )
            }
        }
    }
}

@Composable
private fun LibraryCard(
    title: String,
    subtitle: String,
    testTag: String,
    actions: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actions()
            }
        }
    }
}

@Composable
private fun LibraryTabButton(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    var modifier = Modifier
        .testTag(testTag)
        .onFocusChanged { focused = it.isFocused }
    if (focusRequester != null) {
        modifier = modifier.focusRequester(focusRequester)
    }

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected || focused) {
                PinkSoft
            } else {
                MaterialTheme.colorScheme.primary
            },
            contentColor = Ink,
        ),
        border = BorderStroke(
            width = if (focused) 4.dp else 0.dp,
            color = PinkSoft,
        ),
        modifier = modifier,
    ) {
        Text(label, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun LibraryActionButton(
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
        Text(label, maxLines = 1, softWrap = false)
    }
}

private fun favoriteKindLabel(kind: FavoriteKind): String =
    when (kind) {
        FavoriteKind.Live -> "TV AO VIVO"
        FavoriteKind.Movie -> "FILME"
        FavoriteKind.Series -> "SÉRIE"
    }

private fun playbackKindLabel(kind: PlaybackKind): String =
    when (kind) {
        PlaybackKind.Live -> "TV AO VIVO"
        PlaybackKind.Vod -> "FILME"
        PlaybackKind.Series -> "EPISÓDIO"
        PlaybackKind.CatchUp -> "CATCH UP"
    }
