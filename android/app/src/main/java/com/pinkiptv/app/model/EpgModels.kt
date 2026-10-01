package com.pinkiptv.app.model

data class EpgChannel(
    val streamId: String,
    val name: String,
    val epgChannelId: String?,
    val tvArchive: Boolean,
    val tvArchiveDurationDays: Int?,
)

data class EpgProgramme(
    val programmeId: String?,
    val title: String,
    val description: String?,
    val startProvider: String?,
    val endProvider: String?,
    val startTimestamp: Long?,
    val stopTimestamp: Long?,
    val nowPlaying: Boolean,
    val hasArchive: Boolean,
)

data class EpgProgrammeUi(
    val key: String,
    val programme: EpgProgramme,
    val isCurrent: Boolean,
    val catchUpRef: CatchUpPlaybackRef?,
)

enum class EpgPhase {
    Idle,
    Loading,
    Content,
    Empty,
    Error,
}

enum class EpgUiError {
    SessionUnavailable,
    ProviderUnavailable,
    InvalidResponse,
}

data class EpgUiState(
    val phase: EpgPhase = EpgPhase.Idle,
    val channels: List<EpgChannel> = emptyList(),
    val selectedChannelId: String? = null,
    val nowNext: List<EpgProgramme> = emptyList(),
    val programmes: List<EpgProgrammeUi> = emptyList(),
    val error: EpgUiError? = null,
)

enum class EpgError {
    MissingSession,
    InvalidMetadata,
    InvalidResponse,
    HttpFailure,
    NetworkFailure,
}

sealed interface EpgResult<out T> {
    data class Success<T>(val value: T) : EpgResult<T>
    data class Failure(val error: EpgError) : EpgResult<Nothing>
}
