package com.pinkiptv.app.model

import java.text.Normalizer
import java.util.Locale

enum class SearchKind {
    All,
    Live,
    Movies,
    Series,
}

enum class SearchPhase {
    Inactive,
    LoadingCatalog,
    Ready,
    NoMatches,
    PartialError,
    FullError,
}

enum class SearchSourceStatus {
    Idle,
    Loading,
    Ready,
    Error,
}

enum class SearchUiError {
    SessionUnavailable,
    ProviderUnavailable,
    InvalidResponse,
}

data class SearchSourceState(
    val status: SearchSourceStatus = SearchSourceStatus.Idle,
    val error: SearchUiError? = null,
)

data class SearchItem(
    val kind: SearchKind,
    val providerId: String,
    val title: String,
    val artworkUrl: String?,
    val playbackRef: PlaybackRef?,
) {
    init {
        require(kind != SearchKind.All) {
            "Search result kind must identify a concrete catalog source"
        }
    }
}

data class SearchUiState(
    val phase: SearchPhase = SearchPhase.Inactive,
    val query: String = "",
    val selectedKind: SearchKind = SearchKind.All,
    val results: List<SearchItem> = emptyList(),
    val liveSource: SearchSourceState = SearchSourceState(),
    val movieSource: SearchSourceState = SearchSourceState(),
    val seriesSource: SearchSourceState = SearchSourceState(),
) {
    val effectiveQuery: Boolean
        get() = isEffectiveSearchQuery(query)

    val hasPartialFailure: Boolean
        get() {
            val states = listOf(liveSource, movieSource, seriesSource)
            return states.any { it.status == SearchSourceStatus.Error } &&
                states.any { it.status == SearchSourceStatus.Ready }
        }
}

fun normalizeSearchText(value: String): String {
    val trimmed = value.trim()
    val decomposed = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
    return decomposed
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
}

fun isEffectiveSearchQuery(value: String): Boolean {
    val trimmed = value.trim()
    return Character.codePointCount(trimmed, 0, trimmed.length) >= 2
}

fun filterSearchItems(
    items: List<SearchItem>,
    query: String,
    selectedKind: SearchKind,
    limitPerKind: Int = 100,
): List<SearchItem> {
    require(limitPerKind > 0) { "Search result limit must be positive" }
    if (!isEffectiveSearchQuery(query)) return emptyList()

    val normalizedQuery = normalizeSearchText(query)
    data class Candidate(
        val item: SearchItem,
        val normalizedTitle: String,
        val rank: Int,
    )

    val candidates = items.asSequence()
        .filter { item ->
            selectedKind == SearchKind.All || item.kind == selectedKind
        }
        .map { item ->
            val normalizedTitle = normalizeSearchText(item.title)
            val rank = when {
                normalizedTitle == normalizedQuery -> 0
                normalizedTitle.startsWith(normalizedQuery) -> 1
                normalizedTitle.contains(normalizedQuery) -> 2
                else -> 3
            }
            Candidate(item, normalizedTitle, rank)
        }
        .filter { it.rank < 3 }
        .sortedWith(
            compareBy<Candidate> { it.rank }
                .thenBy { it.normalizedTitle }
                .thenBy { it.item.providerId }
                .thenBy { it.item.kind.ordinal },
        )

    val counts = mutableMapOf<SearchKind, Int>()
    val output = mutableListOf<SearchItem>()
    for (candidate in candidates) {
        val kind = candidate.item.kind
        val current = counts[kind] ?: 0
        if (current >= limitPerKind) continue
        counts[kind] = current + 1
        output += candidate.item
    }
    return output
}

fun SearchKind.toCatalogKindOrNull(): CatalogKind? =
    when (this) {
        SearchKind.All -> null
        SearchKind.Live -> CatalogKind.Live
        SearchKind.Movies -> CatalogKind.Movies
        SearchKind.Series -> CatalogKind.Series
    }

fun SearchItem.toCatalogUiItem(): CatalogUiItem =
    CatalogUiItem(
        id = providerId,
        name = title,
        categoryId = null,
        artworkUrl = artworkUrl,
        subtitle = (playbackRef as? VodPlaybackRef)?.containerExtension,
        playbackRef = playbackRef,
    )

fun SearchItem.favoriteKey(): String =
    kind.name + ":" + providerId

fun FavoriteItem.searchFavoriteKey(): String {
    val searchKind = when (kind) {
        FavoriteKind.Live -> SearchKind.Live
        FavoriteKind.Movie -> SearchKind.Movies
        FavoriteKind.Series -> SearchKind.Series
    }
    return searchKind.name + ":" + providerId
}
