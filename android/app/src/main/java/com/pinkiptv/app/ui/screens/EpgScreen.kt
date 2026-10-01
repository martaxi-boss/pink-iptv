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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.pinkiptv.app.model.CatchUpPlaybackRef
import com.pinkiptv.app.model.EpgChannel
import com.pinkiptv.app.model.EpgPhase
import com.pinkiptv.app.model.EpgProgramme
import com.pinkiptv.app.model.EpgProgrammeUi
import com.pinkiptv.app.model.EpgUiError
import com.pinkiptv.app.model.EpgUiState
import com.pinkiptv.app.ui.theme.Ink
import com.pinkiptv.app.ui.theme.PinkSoft
import com.pinkiptv.app.ui.theme.SurfaceRaised
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EpgScreen(
    state: EpgUiState,
    onLoad: () -> Unit,
    onSelectChannel: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenCatchUp: (CatchUpPlaybackRef) -> Unit,
    onBack: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isTv = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
        Configuration.UI_MODE_TYPE_TELEVISION
    val backFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }

    LaunchedEffect(state.phase) {
        if (state.phase == EpgPhase.Idle) {
            onLoad()
        }
    }

    LaunchedEffect(isTv, state.phase, state.channels.size) {
        if (!isTv) return@LaunchedEffect
        when {
            state.phase == EpgPhase.Error -> {
                withFrameNanos { }
                retryFocus.requestFocus()
            }
            state.channels.isEmpty() -> {
                withFrameNanos { }
                backFocus.requestFocus()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .testTag("epg_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "EPG",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            EpgActionButton(
                label = "Voltar",
                testTag = "epg_back",
                focusRequester = if (isTv) backFocus else null,
                onClick = onBack,
            )
        }

        if (state.channels.isNotEmpty()) {
            ChannelSelector(
                channels = state.channels,
                selectedChannelId = state.selectedChannelId,
                requestInitialFocus = isTv,
                programmeDownTargetAvailable = state.programmes.isNotEmpty(),
                onSelectChannel = onSelectChannel,
            )
        }

        if (state.nowNext.isNotEmpty()) {
            NowNextSummary(state.nowNext)
        }

        when (state.phase) {
            EpgPhase.Idle,
            EpgPhase.Loading,
            -> LoadingEpg()
            EpgPhase.Content -> ProgrammeList(
                programmes = state.programmes,
                selectedChannelId = state.selectedChannelId,
                onOpenCatchUp = onOpenCatchUp,
            )
            EpgPhase.Empty -> EmptyEpg(hasChannels = state.channels.isNotEmpty())
            EpgPhase.Error -> ErrorEpg(
                error = state.error,
                focusRequester = if (isTv) retryFocus else null,
                onRetry = onRetry,
            )
        }
    }
}

@Composable
private fun ChannelSelector(
    channels: List<EpgChannel>,
    selectedChannelId: String?,
    requestInitialFocus: Boolean,
    programmeDownTargetAvailable: Boolean,
    onSelectChannel: (String) -> Unit,
) {
    val ids = channels.map { it.streamId }
    val requesters = remember(ids) { List(ids.size) { FocusRequester() } }
    val selectedIndex = channels.indexOfFirst { it.streamId == selectedChannelId }
        .let { if (it < 0) 0 else it }

    LaunchedEffect(requestInitialFocus, selectedIndex, ids) {
        if (requestInitialFocus && requesters.isNotEmpty()) {
            withFrameNanos { }
            requesters[selectedIndex.coerceAtMost(requesters.lastIndex)].requestFocus()
        }
    }

    Text("Canais", style = MaterialTheme.typography.titleMedium)
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("epg_channels"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(channels, key = { _, channel -> channel.streamId }) { index, channel ->
            ChannelChip(
                channel = channel,
                selected = channel.streamId == selectedChannelId,
                focusRequester = requesters[index],
                left = requesters.getOrNull(index - 1),
                right = requesters.getOrNull(index + 1),
                onClick = { onSelectChannel(channel.streamId) },
                programmeDownTargetAvailable = programmeDownTargetAvailable,
            )
        }
    }
}

@Composable
private fun ChannelChip(
    channel: EpgChannel,
    selected: Boolean,
    focusRequester: FocusRequester,
    left: FocusRequester?,
    right: FocusRequester?,
    onClick: () -> Unit,
    programmeDownTargetAvailable: Boolean,
) {
    var focused by remember { mutableStateOf(false) }

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
            .testTag("epg_channel_" + channel.streamId)
            .focusRequester(focusRequester)
            .focusProperties {
                left?.let { this.left = it }
                right?.let { this.right = it }
                if (!programmeDownTargetAvailable) {
                    down = FocusRequester.Cancel
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(channel.name)
            if (channel.tvArchive) {
                Text(
                    text = channel.tvArchiveDurationDays?.let {
                        "Catch Up · " + it + " dias"
                    } ?: "Catch Up",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun NowNextSummary(programmes: List<EpgProgramme>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("epg_now_next"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        programmes.take(2).forEachIndexed { index, programme ->
            Text(
                text = (if (index == 0) "AGORA · " else "A SEGUIR · ") + programme.title,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun LoadingEpg() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("epg_loading"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator()
        Text("A carregar programação…")
    }
}

@Composable
private fun EmptyEpg(hasChannels: Boolean) {
    Text(
        text = if (hasChannels) {
            "Não existem programas disponíveis para este canal."
        } else {
            "Não existem canais Live disponíveis."
        },
        modifier = Modifier.testTag("epg_empty"),
    )
}

@Composable
private fun ErrorEpg(
    error: EpgUiError?,
    focusRequester: FocusRequester?,
    onRetry: () -> Unit,
) {
    val message = when (error) {
        EpgUiError.SessionUnavailable -> "A sessão terminou. Volta a iniciar sessão."
        EpgUiError.InvalidResponse -> "A programação recebida é inválida."
        EpgUiError.ProviderUnavailable,
        null,
        -> "Não foi possível carregar a programação. Tenta novamente."
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("epg_error"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message)
        EpgActionButton(
            label = "Tentar novamente",
            testTag = "epg_retry",
            focusRequester = focusRequester,
            onClick = onRetry,
        )
    }
}

@Composable
private fun ProgrammeList(
    programmes: List<EpgProgrammeUi>,
    selectedChannelId: String?,
    onOpenCatchUp: (CatchUpPlaybackRef) -> Unit,
) {
    val rowRequesters = remember(programmes.map { it.key }) {
        List(programmes.size) { FocusRequester() }
    }
    val catchUpRequesters = remember(programmes.map { it.key }) {
        List(programmes.size) { FocusRequester() }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("epg_programmes"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(programmes, key = { _, item -> item.key }) { index, item ->
            ProgrammeCard(
                item = item,
                selectedChannelId = selectedChannelId,
                rowFocusRequester = rowRequesters[index],
                catchUpFocusRequester = catchUpRequesters[index],
                up = rowRequesters.getOrNull(index - 1),
                down = rowRequesters.getOrNull(index + 1),
                onOpenCatchUp = onOpenCatchUp,
            )
        }
    }
}

@Composable
private fun ProgrammeCard(
    item: EpgProgrammeUi,
    selectedChannelId: String?,
    rowFocusRequester: FocusRequester,
    catchUpFocusRequester: FocusRequester,
    up: FocusRequester?,
    down: FocusRequester?,
    onOpenCatchUp: (CatchUpPlaybackRef) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val programme = item.programme
    val catchUp = item.catchUpRef

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
        border = BorderStroke(
            width = if (focused) 4.dp else 1.dp,
            color = if (focused) PinkSoft else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 104.dp)
            .testTag("epg_programme_" + item.key)
            .focusRequester(rowFocusRequester)
            .focusProperties {
                up?.let { this.up = it }
                down?.let { this.down = it }
                if (catchUp != null) {
                    right = catchUpFocusRequester
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = {}),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = programme.title,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (item.isCurrent) {
                    Text(
                        text = "AGORA",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("epg_now_" + item.key),
                    )
                }
            }
            Text(
                text = displayTimeRange(programme),
                style = MaterialTheme.typography.bodySmall,
            )
            programme.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            if (catchUp != null && selectedChannelId == catchUp.streamId) {
                var buttonFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = { onOpenCatchUp(catchUp) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (buttonFocused) {
                            PinkSoft
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        contentColor = Ink,
                    ),
                    border = BorderStroke(
                        width = if (buttonFocused) 4.dp else 0.dp,
                        color = PinkSoft,
                    ),
                    modifier = Modifier
                        .testTag("epg_catchup_" + item.key)
                        .focusRequester(catchUpFocusRequester)
                        .focusProperties {
                            left = rowFocusRequester
                            down?.let { this.down = it }
                            up?.let { this.up = it }
                        }
                        .onFocusChanged { buttonFocused = it.isFocused },
                ) {
                    Text("VER EM CATCH UP")
                }
            }
        }
    }
}

private fun displayTimeRange(programme: EpgProgramme): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    fun format(epochSeconds: Long?): String? =
        epochSeconds?.takeIf { it > 0L }?.let {
            formatter.format(Date(it * 1000L))
        }

    val start = format(programme.startTimestamp) ?: programme.startProvider
    val end = format(programme.stopTimestamp) ?: programme.endProvider
    return listOfNotNull(start, end).joinToString(" – ")
}

@Composable
private fun EpgActionButton(
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
