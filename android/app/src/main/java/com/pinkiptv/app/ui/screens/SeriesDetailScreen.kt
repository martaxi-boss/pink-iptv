package com.pinkiptv.app.ui.screens

import android.content.res.Configuration
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pinkiptv.app.model.SeriesDetailPhase
import com.pinkiptv.app.model.SeriesDetailUiError
import com.pinkiptv.app.model.SeriesDetailUiState
import com.pinkiptv.app.model.SeriesEpisode
import com.pinkiptv.app.model.SeriesSeason
import com.pinkiptv.app.ui.theme.Ink
import com.pinkiptv.app.ui.theme.PinkSoft
import com.pinkiptv.app.ui.theme.SurfaceRaised

@Composable
fun SeriesDetailScreen(
    state: SeriesDetailUiState,
    onRetry: () -> Unit,
    onSelectSeason: (String) -> Unit,
    onOpenEpisode: (SeriesEpisode) -> Unit,
    onBack: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isTv = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
        Configuration.UI_MODE_TYPE_TELEVISION
    val primaryFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }

    LaunchedEffect(isTv, state.phase) {
        if (!isTv) return@LaunchedEffect
        when (state.phase) {
            SeriesDetailPhase.Error -> {
                withFrameNanos { }
                primaryFocus.requestFocus()
            }
            SeriesDetailPhase.Content -> Unit
            SeriesDetailPhase.Idle,
            SeriesDetailPhase.Loading,
            SeriesDetailPhase.Empty,
            -> {
                withFrameNanos { }
                backFocus.requestFocus()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .testTag("series_detail_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.title ?: "Série",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("series_detail_title"),
            )
            SeriesActionButton(
                label = "Voltar",
                testTag = "series_detail_back",
                focusRequester = if (isTv) backFocus else null,
                onClick = onBack,
            )
        }

        when (state.phase) {
            SeriesDetailPhase.Idle,
            SeriesDetailPhase.Loading,
            -> LoadingSeriesDetail()
            SeriesDetailPhase.Empty -> EmptySeriesDetail(state)
            SeriesDetailPhase.Error -> ErrorSeriesDetail(
                error = state.error,
                focusRequester = if (isTv) primaryFocus else null,
                onRetry = onRetry,
            )
            SeriesDetailPhase.Content -> SeriesDetailContent(
                state = state,
                requestInitialFocus = isTv,
                onSelectSeason = onSelectSeason,
                onOpenEpisode = onOpenEpisode,
            )
        }
    }
}

@Composable
private fun LoadingSeriesDetail() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("series_detail_loading"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text("A carregar episódios…")
    }
}

@Composable
private fun EmptySeriesDetail(state: SeriesDetailUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("series_detail_empty"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Esta série não tem episódios disponíveis.")
        state.plot?.takeIf { it.isNotBlank() }?.let { Text(it) }
    }
}

@Composable
private fun ErrorSeriesDetail(
    error: SeriesDetailUiError?,
    focusRequester: FocusRequester?,
    onRetry: () -> Unit,
) {
    val message = when (error) {
        SeriesDetailUiError.SessionUnavailable ->
            "A sessão terminou. Volta a iniciar sessão."
        SeriesDetailUiError.InvalidResponse ->
            "Os dados desta série são inválidos."
        SeriesDetailUiError.ProviderUnavailable,
        null,
        -> "Não foi possível carregar esta série. Tenta novamente."
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("series_detail_error"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message)
        SeriesActionButton(
            label = "Tentar novamente",
            testTag = "series_detail_retry",
            focusRequester = focusRequester,
            onClick = onRetry,
        )
    }
}

@Composable
private fun ColumnScope.SeriesDetailContent(
    state: SeriesDetailUiState,
    requestInitialFocus: Boolean,
    onSelectSeason: (String) -> Unit,
    onOpenEpisode: (SeriesEpisode) -> Unit,
) {
    state.plot?.takeIf { it.isNotBlank() }?.let { plot ->
        Text(
            text = plot,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("series_detail_plot"),
        )
    }

    if (state.genre != null || state.rating != null) {
        Text(
            text = listOfNotNull(state.genre, state.rating?.let { "★ " + it }).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
        )
    }

    Text("Temporadas", style = MaterialTheme.typography.titleMedium)

    val seasonIds = state.seasons.map { it.seasonId }
    val seasonRequesters = remember(seasonIds) {
        List(seasonIds.size) { FocusRequester() }
    }
    val episodeIds = state.episodes.map { it.episodeId }
    val episodeRequesters = remember(episodeIds) {
        List(episodeIds.size) { FocusRequester() }
    }
    val selectedSeasonIndex = state.seasons.indexOfFirst {
        it.seasonId == state.selectedSeasonId
    }.coerceAtLeast(0)

    LaunchedEffect(requestInitialFocus, selectedSeasonIndex, seasonIds) {
        if (requestInitialFocus) {
            withFrameNanos { }
            if (seasonRequesters.isNotEmpty()) {
                seasonRequesters[
                    selectedSeasonIndex.coerceAtMost(seasonRequesters.lastIndex)
                ].requestFocus()
            } else {
                episodeRequesters.firstOrNull()?.requestFocus()
            }
        }
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("series_seasons"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(state.seasons, key = { _, season -> season.seasonId }) { index, season ->
            SeasonChip(
                season = season,
                selected = season.seasonId == state.selectedSeasonId,
                focusRequester = seasonRequesters.getOrNull(index),
                left = seasonRequesters.getOrNull(index - 1),
                right = seasonRequesters.getOrNull(index + 1),
                down = episodeRequesters.firstOrNull(),
                onClick = { onSelectSeason(season.seasonId) },
            )
        }
    }

    Text("Episódios", style = MaterialTheme.typography.titleMedium)

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .testTag("series_episodes"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(state.episodes, key = { _, episode -> episode.episodeId }) { index, episode ->
            EpisodeCard(
                episode = episode,
                focusRequester = episodeRequesters.getOrNull(index),
                up = if (index == 0) {
                    seasonRequesters.getOrNull(selectedSeasonIndex)
                } else {
                    episodeRequesters.getOrNull(index - 1)
                },
                down = episodeRequesters.getOrNull(index + 1),
                onClick = { onOpenEpisode(episode) },
            )
        }
    }
}

@Composable
private fun SeasonChip(
    season: SeriesSeason,
    selected: Boolean,
    focusRequester: FocusRequester?,
    left: FocusRequester?,
    right: FocusRequester?,
    down: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var modifier = Modifier
        .testTag("series_season_" + season.seasonId)
        .onFocusChanged { focused = it.isFocused }
        .focusProperties {
            left?.let { this.left = it }
            right?.let { this.right = it }
            down?.let { this.down = it }
        }
    if (focusRequester != null) modifier = modifier.focusRequester(focusRequester)

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
            text = season.displayName,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun EpisodeCard(
    episode: SeriesEpisode,
    focusRequester: FocusRequester?,
    up: FocusRequester?,
    down: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 88.dp)
        .testTag("series_episode_" + episode.episodeId)
        .onFocusChanged { focused = it.isFocused }
        .focusProperties {
            up?.let { this.up = it }
            down?.let { this.down = it }
        }
    if (focusRequester != null) modifier = modifier.focusRequester(focusRequester)

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
        border = BorderStroke(
            width = if (focused) 4.dp else 1.dp,
            color = if (focused) PinkSoft else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = episode.episodeNumber?.let { "E" + it + " · " + episode.title }
                    ?: episode.title,
                style = MaterialTheme.typography.titleMedium,
            )
            episode.duration?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            episode.plot?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SeriesActionButton(
    label: String,
    testTag: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    var modifier = Modifier
        .testTag(testTag)
        .onFocusChanged { focused = it.isFocused }
    if (focusRequester != null) modifier = modifier.focusRequester(focusRequester)

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
        Text(label)
    }
}
