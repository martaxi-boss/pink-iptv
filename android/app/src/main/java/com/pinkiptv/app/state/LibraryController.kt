package com.pinkiptv.app.state

import com.pinkiptv.app.library.ActiveLibraryProfileStore
import com.pinkiptv.app.library.LocalLibraryRepository
import com.pinkiptv.app.library.LocalProfileKey
import com.pinkiptv.app.model.CatalogKind
import com.pinkiptv.app.model.CatalogUiItem
import com.pinkiptv.app.model.ContinueWatchingItem
import com.pinkiptv.app.model.FavoriteItem
import com.pinkiptv.app.model.FavoriteKind
import com.pinkiptv.app.model.LibraryError
import com.pinkiptv.app.model.LibraryPhase
import com.pinkiptv.app.model.LibraryUiState
import com.pinkiptv.app.model.RuntimeProviderSessionStore
import com.pinkiptv.app.model.VodPlaybackRef
import com.pinkiptv.app.model.isContinueWatchingEligible
import com.pinkiptv.app.model.progressPercent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class LibraryController(
    private val repository: LocalLibraryRepository,
    private val providerSessionStore: RuntimeProviderSessionStore,
    private val profileStore: ActiveLibraryProfileStore,
    private val scope: CoroutineScope,
    private val clockMs: () -> Long = System::currentTimeMillis,
) {
    private val mutableState = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = mutableState.asStateFlow()

    private var libraryJob: Job? = null

    init {
        scope.launch {
            providerSessionStore.available.collect { available ->
                if (!available) {
                    clearVisible()
                } else {
                    activateCurrentSession()
                }
            }
        }
    }

    fun clearVisible() {
        libraryJob?.cancel()
        libraryJob = null
        profileStore.clear()
        mutableState.value = LibraryUiState()
    }

    fun toggleFavorite(
        kind: CatalogKind,
        item: CatalogUiItem,
    ) {
        val profileKey = profileStore.current() ?: return
        val favorite = item.toFavorite(kind, clockMs()) ?: return
        scope.launch {
            runCatching {
                repository.toggleFavorite(profileKey, favorite)
            }.onFailure {
                storageError()
            }
        }
    }

    fun removeFavorite(item: FavoriteItem) {
        val profileKey = profileStore.current() ?: return
        scope.launch {
            runCatching {
                repository.removeFavorite(profileKey, item)
            }.onFailure {
                storageError()
            }
        }
    }

    fun clearHistory() {
        val profileKey = profileStore.current() ?: return
        scope.launch {
            runCatching {
                repository.clearHistory(profileKey)
            }.onFailure {
                storageError()
            }
        }
    }

    private fun activateCurrentSession() {
        val username = providerSessionStore.current()?.username ?: run {
            clearVisible()
            return
        }
        val profileKey = LocalProfileKey.derive(username)
        profileStore.activate(profileKey)
        libraryJob?.cancel()
        mutableState.value = LibraryUiState(phase = LibraryPhase.Loading)
        libraryJob = scope.launch {
            try {
                combine(
                    repository.observeFavorites(profileKey),
                    repository.observeHistory(profileKey),
                ) { favorites, history ->
                    LibraryUiState(
                        phase = LibraryPhase.Ready,
                        favorites = favorites,
                        continueWatching = history
                            .filter { it.isContinueWatchingEligible() }
                            .map { item ->
                                ContinueWatchingItem(
                                    history = item,
                                    progressPercent = item.progressPercent(),
                                )
                            },
                        recents = history.take(100),
                    )
                }.collect { next ->
                    if (profileStore.current() == profileKey) {
                        mutableState.value = next
                    }
                }
            } catch (_: Throwable) {
                if (profileStore.current() == profileKey) {
                    storageError()
                }
            }
        }
    }

    private fun storageError() {
        mutableState.value = mutableState.value.copy(
            phase = LibraryPhase.Error,
            error = LibraryError.StorageUnavailable,
        )
    }

    private fun CatalogUiItem.toFavorite(
        kind: CatalogKind,
        now: Long,
    ): FavoriteItem? {
        val favoriteKind = when (kind) {
            CatalogKind.Live -> FavoriteKind.Live
            CatalogKind.Movies -> FavoriteKind.Movie
            CatalogKind.Series -> FavoriteKind.Series
        }
        val extension = (playbackRef as? VodPlaybackRef)?.containerExtension
        return FavoriteItem(
            kind = favoriteKind,
            providerId = id,
            title = name,
            artworkUrl = artworkUrl,
            containerExtension = extension,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
    }
}
