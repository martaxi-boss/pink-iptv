package com.pinkiptv.app.ui.screens

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import com.pinkiptv.app.model.PlaybackRef
import com.pinkiptv.app.model.PlayerError
import com.pinkiptv.app.model.PlayerPhase
import com.pinkiptv.app.player.PlaybackFacadeFactory
import com.pinkiptv.app.ui.theme.Ink
import com.pinkiptv.app.ui.theme.PinkSoft

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    playbackRef: PlaybackRef?,
    facadeFactory: PlaybackFacadeFactory,
    onBack: () -> Unit,
) {
    val facade = remember(facadeFactory) { facadeFactory.create() }
    val state by facade.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val isTv = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
        Configuration.UI_MODE_TYPE_TELEVISION
    val playFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }

    DisposableEffect(facade) {
        val previousKeepScreenOn = view.keepScreenOn
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = previousKeepScreenOn
            facade.close()
        }
    }

    LaunchedEffect(playbackRef) {
        if (playbackRef != null) {
            facade.prepare(playbackRef)
        }
    }

    val showingError = state.phase == PlayerPhase.Error
    LaunchedEffect(isTv, playbackRef, showingError) {
        if (isTv && playbackRef != null) {
            withFrameNanos { }
            if (showingError) {
                retryFocus.requestFocus()
            } else {
                playFocus.requestFocus()
            }
        }
    }

    BackHandler {
        facade.close()
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("player_screen"),
    ) {
        PlayerSurface(
            player = facade.media3Player,
            modifier = Modifier
                .fillMaxSize()
                .testTag("player_surface"),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.title ?: playbackRef?.title ?: "PINK IPTV",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.testTag("player_title"),
                )
                PlayerActionButton(
                    label = "Voltar",
                    testTag = "player_back",
                    onClick = {
                        facade.close()
                        onBack()
                    },
                )
            }

            when {
                playbackRef == null -> PlayerErrorPanel(
                    message = "O conteúdo selecionado já não está disponível.",
                    focusRequester = retryFocus,
                    onRetry = {},
                    retryEnabled = false,
                )
                state.phase == PlayerPhase.Error -> PlayerErrorPanel(
                    message = playerErrorMessage(state.error),
                    focusRequester = retryFocus,
                    onRetry = facade::retry,
                    retryEnabled = true,
                )
                state.phase == PlayerPhase.Preparing ||
                    state.phase == PlayerPhase.Buffering -> Column(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .testTag("player_loading"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "A carregar vídeo…",
                        color = Color.White,
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.seekable) {
                    Text(
                        text = formatPosition(state.positionMs) + " / " +
                            formatPosition(state.durationMs ?: 0L),
                        color = Color.White,
                        modifier = Modifier.testTag("player_position"),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("player_controls"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.seekable) {
                        PlayerActionButton(
                            label = "-10 s",
                            testTag = "player_seek_back",
                            onClick = { facade.seekBy(-10_000L) },
                        )
                    }
                    PlayerActionButton(
                        label = if (state.isPlaying) "Pausa" else "Reproduzir",
                        testTag = "player_play_pause",
                        focusRequester = if (state.phase != PlayerPhase.Error) {
                            playFocus
                        } else {
                            null
                        },
                        onClick = facade::togglePlayPause,
                    )
                    if (state.seekable) {
                        PlayerActionButton(
                            label = "+10 s",
                            testTag = "player_seek_forward",
                            onClick = { facade.seekBy(10_000L) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerErrorPanel(
    message: String,
    focusRequester: FocusRequester,
    onRetry: () -> Unit,
    retryEnabled: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("player_error"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = message,
            color = Color.White,
        )
        if (retryEnabled) {
            PlayerActionButton(
                label = "Tentar novamente",
                testTag = "player_retry",
                focusRequester = focusRequester,
                onClick = onRetry,
            )
        }
    }
}

private fun playerErrorMessage(error: PlayerError?): String =
    when (error) {
        PlayerError.SessionUnavailable ->
            "A sessão já não está disponível. Volta a iniciar sessão."
        PlayerError.InvalidStreamMetadata ->
            "Este conteúdo não tem dados de reprodução válidos."
        PlayerError.Network ->
            "Não foi possível ligar ao serviço de vídeo."
        PlayerError.SourceUnavailable ->
            "A fonte de vídeo não está disponível."
        PlayerError.UnsupportedFormat ->
            "Este formato de vídeo não é suportado neste dispositivo."
        PlayerError.PlaybackError,
        null,
        -> "Não foi possível reproduzir este conteúdo."
    }

private fun formatPosition(valueMs: Long): String {
    val totalSeconds = valueMs.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

@Composable
private fun PlayerActionButton(
    label: String,
    testTag: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    var modifier = Modifier
        .padding(horizontal = 5.dp)
        .testTag(testTag)
        .onFocusChanged { focused = it.isFocused }
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
        Text(label)
    }
}
