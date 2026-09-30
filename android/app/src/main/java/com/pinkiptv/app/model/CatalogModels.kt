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

enum class CatalogError {
    MissingSession,
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
}
