package com.pinkiptv.app.model

data class CatalogCategory(
    val id: String,
    val name: String,
)

data class LiveStream(
    val streamId: String,
    val name: String,
    val categoryId: String?,
    val artworkUrl: String?,
    val streamType: String?,
    val epgChannelId: String? = null,
    val tvArchive: Boolean = false,
    val tvArchiveDurationDays: Int? = null,
)

data class VodItem(
    val streamId: String,
    val name: String,
    val categoryId: String?,
    val artworkUrl: String?,
    val containerExtension: String?,
)

data class SeriesItem(
    val seriesId: String,
    val name: String,
    val categoryId: String?,
    val artworkUrl: String?,
)

data class SeriesDetail(
    val seriesId: String,
    val name: String?,
    val plot: String?,
    val artworkUrl: String?,
    val genre: String?,
    val rating: String?,
    val seasons: List<SeriesSeason>,
    val episodes: List<SeriesEpisode>,
)

data class SeriesSeason(
    val seasonId: String,
    val displayName: String,
    val episodeCount: Int?,
    val artworkUrl: String?,
)

data class SeriesEpisode(
    val episodeId: String,
    val episodeNumber: String?,
    val title: String,
    val seasonId: String,
    val containerExtension: String?,
    val artworkUrl: String?,
    val duration: String?,
    val plot: String?,
)

enum class CatalogKind {
    Live,
    Movies,
    Series,
}

enum class CatalogPhase {
    Idle,
    Loading,
    Content,
    Empty,
    Error,
}

enum class CatalogUiError {
    SessionUnavailable,
    ProviderUnavailable,
    InvalidResponse,
}

data class CatalogUiItem(
    val id: String,
    val name: String,
    val categoryId: String?,
    val artworkUrl: String?,
    val subtitle: String?,
    val playbackRef: PlaybackRef? = null,
)

data class CatalogUiState(
    val kind: CatalogKind,
    val phase: CatalogPhase = CatalogPhase.Idle,
    val categories: List<CatalogCategory> = emptyList(),
    val selectedCategoryId: String? = null,
    val items: List<CatalogUiItem> = emptyList(),
    val error: CatalogUiError? = null,
)

enum class SeriesDetailPhase {
    Idle,
    Loading,
    Content,
    Empty,
    Error,
}

enum class SeriesDetailUiError {
    SessionUnavailable,
    ProviderUnavailable,
    InvalidResponse,
}

data class SeriesDetailUiState(
    val phase: SeriesDetailPhase = SeriesDetailPhase.Idle,
    val selectedSeriesId: String? = null,
    val title: String? = null,
    val plot: String? = null,
    val artworkUrl: String? = null,
    val genre: String? = null,
    val rating: String? = null,
    val seasons: List<SeriesSeason> = emptyList(),
    val selectedSeasonId: String? = null,
    val episodes: List<SeriesEpisode> = emptyList(),
    val error: SeriesDetailUiError? = null,
)

enum class CatalogError {
    MissingSession,
    InvalidMetadata,
    InvalidResponse,
    HttpFailure,
    NetworkFailure,
}

sealed interface CatalogResult<out T> {
    data class Success<T>(val value: T) : CatalogResult<T>
    data class Failure(val error: CatalogError) : CatalogResult<Nothing>
}

interface CatalogRepository {
    suspend fun liveCategories(): CatalogResult<List<CatalogCategory>>
    suspend fun liveStreams(): CatalogResult<List<LiveStream>>
    suspend fun vodCategories(): CatalogResult<List<CatalogCategory>>
    suspend fun vodStreams(): CatalogResult<List<VodItem>>
    suspend fun seriesCategories(): CatalogResult<List<CatalogCategory>>
    suspend fun series(): CatalogResult<List<SeriesItem>>
    suspend fun seriesInfo(seriesId: String): CatalogResult<SeriesDetail>

    suspend fun shortEpg(
        streamId: String,
        limit: Int = 2,
    ): EpgResult<List<EpgProgramme>> =
        EpgResult.Failure(EpgError.InvalidResponse)

    suspend fun simpleDataTable(
        streamId: String,
    ): EpgResult<List<EpgProgramme>> =
        EpgResult.Failure(EpgError.InvalidResponse)
}
