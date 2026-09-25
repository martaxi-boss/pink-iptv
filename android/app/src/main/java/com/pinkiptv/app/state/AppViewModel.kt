package com.pinkiptv.app.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pinkiptv.app.model.SessionRepository
import com.pinkiptv.app.storage.CredentialStore

class AppViewModel(
    repository: SessionRepository,
    credentialStore: CredentialStore,
) : ViewModel() {
    private val controller = SessionController(
        repository = repository,
        credentialStore = credentialStore,
        scope = viewModelScope,
    )

    val uiState = controller.state

    fun login(username: String, password: String) {
        controller.login(username, password)
    }

    fun logout() {
        controller.logout()
    }

    class Factory(
        private val repository: SessionRepository,
        private val credentialStore: CredentialStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AppViewModel::class.java))
            return AppViewModel(repository, credentialStore) as T
        }
    }
}
