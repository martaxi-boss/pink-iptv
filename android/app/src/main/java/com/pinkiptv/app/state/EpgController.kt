package com.pinkiptv.app.state

import com.pinkiptv.app.model.CatalogError
import com.pinkiptv.app.model.CatalogRepository
import com.pinkiptv.app.model.CatalogResult
import com.pinkiptv.app.model.EpgChannel
import com.pinkiptv.app.model.EpgError
import com.pinkiptv.app.model.EpgPhase
import com.pinkiptv.app.model.EpgProgramme
import com.pinkiptv.app.model.EpgProgrammeUi
import com.pinkiptv.app.model.EpgResult
import com.pinkiptv.app.model.EpgUiError
import com.pinkiptv.app.model.EpgUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EpgController(
    private val repository: CatalogRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000L },
) {
    private val mutableState = MutableStateFlow(EpgUiState())
    val state: StateFlow<EpgUiState> = mutableState.asStateFlow()

    private var generation = 0

    fun open() {
        if (mutableState.value.phase == EpgPhase.Loading) return
        val requestGeneration = ++generation
        mutableState.value = EpgUiState(phase = EpgPhase.Loading)

        scope.launch {
            when (val live = repository.liveStreams()) {
                is CatalogResult.Failure -> {
                    if (requestGeneration != generation) return@launch
                    mutableState.value = EpgUiState(
                        phase = EpgPhase.Error,
                        error = live.error.toEpgUiError(),
                    )
                }
                is CatalogResult.Success -> {
                    if (requestGeneration != generation) return@launch
                    val channels = live.value.map { stream ->
                        EpgChannel(
                            streamId = stream.streamId,
                            name = stream.name,
                            epgChannelId = stream.epgChannelId,
                            tvArchive = stream.tvArchive,
                            tvArchiveDurationDays = stream.tvArchiveDurationDays,
                        )
                    }
                    if (channels.isEmpty()) {
                        mutableState.value = EpgUiState(phase = EpgPhase.Empty)
                    } else {
                        loadChannel(channels.first().streamId, channels)
                    }
                }
            }
        }
    }

    fun selectChannel(streamId: String) {
        val channels = mutableState.value.channels
        if (channels.none { it.streamId == streamId }) return
        if (mutableState.value.selectedChannelId == streamId &&
            mutableState.value.phase == EpgPhase.Loading
        ) {
            return
        }
        loadChannel(streamId, channels)
    }

    fun retry() {
        val current = mutableState.value
        val streamId = current.selectedChannelId
        if (streamId == null || current.channels.none { it.streamId == streamId }) {
            open()
        } else {
            loadChannel(streamId, current.channels)
        }
    }

    fun clear() {
        generation += 1
        mutableState.value = EpgUiState()
    }

    private fun loadChannel(
        streamId: String,
        channels: List<EpgChannel>,
    ) {
        val selected = channels.firstOrNull { it.streamId == streamId } ?: return
        val requestGeneration = ++generation
        mutableState.value = EpgUiState(
            phase = EpgPhase.Loading,
            channels = channels,
            selectedChannelId = streamId,
        )

        scope.launch {
            val programmesResult = repository.simpleDataTable(streamId)
            if (requestGeneration != generation) return@launch

            if (programmesResult is EpgResult.Failure) {
                mutableState.value = EpgUiState(
                    phase = EpgPhase.Error,
                    channels = channels,
                    selectedChannelId = streamId,
                    error = programmesResult.error.toUiError(),
                )
                return@launch
            }

            programmesResult as EpgResult.Success
            val shortResult = repository.shortEpg(streamId, 2)
            if (requestGeneration != generation) return@launch

            val now = clock()
            val programmes = programmesResult.value.mapIndexed { index, programme ->
                EpgProgrammeUi(
                    key = (programme.programmeId ?: "row") + "-" + index,
                    programme = programme,
                    isCurrent = programme.nowPlaying || programme.isCurrentAt(now),
                    catchUpRef = CatchUpPolicy.createRef(
                        channel = selected,
                        programme = programme,
                        nowEpochSeconds = now,
                    ),
                )
            }
            val nowNext = when (shortResult) {
                is EpgResult.Success -> shortResult.value
                is EpgResult.Failure -> emptyList()
            }

            mutableState.value = EpgUiState(
                phase = if (programmes.isEmpty()) EpgPhase.Empty else EpgPhase.Content,
                channels = channels,
                selectedChannelId = streamId,
                nowNext = nowNext,
                programmes = programmes,
            )
        }
    }

    private fun EpgProgramme.isCurrentAt(now: Long): Boolean {
        val start = startTimestamp ?: return false
        val stop = stopTimestamp ?: return false
        return start <= now && now < stop
    }

    private fun CatalogError.toEpgUiError(): EpgUiError =
        when (this) {
            CatalogError.MissingSession -> EpgUiError.SessionUnavailable
            CatalogError.InvalidMetadata,
            CatalogError.InvalidResponse,
            -> EpgUiError.InvalidResponse
            CatalogError.HttpFailure,
            CatalogError.NetworkFailure,
            -> EpgUiError.ProviderUnavailable
        }

    private fun EpgError.toUiError(): EpgUiError =
        when (this) {
            EpgError.MissingSession -> EpgUiError.SessionUnavailable
            EpgError.InvalidMetadata,
            EpgError.InvalidResponse,
            -> EpgUiError.InvalidResponse
            EpgError.HttpFailure,
            EpgError.NetworkFailure,
            -> EpgUiError.ProviderUnavailable
        }
}
