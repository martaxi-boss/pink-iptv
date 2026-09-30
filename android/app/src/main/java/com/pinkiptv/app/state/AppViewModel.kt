package com.pinkiptv.app.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pinkiptv.app.model.CatalogKind
import com.pinkiptv.app.model.CatalogRepository
import com.pinkiptv.app.model.CatalogUiItem
import com.pinkiptv.app.model.RuntimeProviderSessionStore
import com.pinkiptv.app.model.SessionRepository
import com.pinkiptv.app.storage.CredentialStore

class AppViewModel(
    repository: SessionRepository,
    credentialStore: CredentialStore,
    catalogRepository: CatalogRepository,
    providerSessionStore: RuntimeProviderSessionStore,
) : ViewModel() {
    private val sessionController = SessionController(
        repository = repository,
        credentialStore = credentialStore,
        scope = viewModelScope,
        providerSessionStore = providerSessionStore,
    )
    private val catalogController = CatalogController(
        repository = catalogRepository,
        scope = viewModelScope,
    )
    private val playbackSelectionController = PlaybackSelectionController()

    val uiState = sessionController.state
    val liveCatalog = catalogController.live
    val movieCatalog = catalogController.movies
    val seriesCatalog = catalogController.series
    val selectedPlayback = playbackSelectionController.selection

    fun login(username: String, password: String) {
        sessionController.login(username, password)
    }

    fun logout() {
        playbackSelectionController.clear()
        catalogController.clear()
        sessionController.logout()
    }

    fun loadCatalog(kind: CatalogKind) {
        catalogController.load(kind)
    }

    fun selectCatalogCategory(kind: CatalogKind, categoryId: String?) {
        catalogController.selectCategory(kind, categoryId)
    }

    fun selectPlayback(item: CatalogUiItem) {
        playbackSelectionController.select(item)
    }

    fun clearPlayback() {
        playbackSelectionController.clear()
    }

    class Factory(
        private val repository: SessionRepository,
        private val credentialStore: CredentialStore,
        private val catalogRepository: CatalogRepository,
        private val providerSessionStore: RuntimeProviderSessionStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AppViewModel::class.java))
            return AppViewModel(
                repository = repository,
                credentialStore = credentialStore,
                catalogRepository = catalogRepository,
                providerSessionStore = providerSessionStore,
            ) as T
        }
    }
}
