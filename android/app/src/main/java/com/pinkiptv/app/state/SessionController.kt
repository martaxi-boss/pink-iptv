package com.pinkiptv.app.state

import com.pinkiptv.app.model.RuntimeProviderSessionStore
import com.pinkiptv.app.model.SessionRepository
import com.pinkiptv.app.model.SessionResult
import com.pinkiptv.app.storage.CredentialStore
import com.pinkiptv.app.storage.StoredCredentials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RootScreen { Splash, Login, Home }

enum class LoginError {
    Required,
    InvalidCredentials,
    Expired,
    Disabled,
    TemporaryUnavailable,
}

data class AppUiState(
    val screen: RootScreen = RootScreen.Splash,
    val loginInFlight: Boolean = false,
    val loginError: LoginError? = null,
)

class SessionController(
    private val repository: SessionRepository,
    private val credentialStore: CredentialStore,
    private val scope: CoroutineScope,
    private val providerSessionStore: RuntimeProviderSessionStore = RuntimeProviderSessionStore(),
) {
    private val mutableState = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()

    init {
        scope.launch { bootstrap() }
    }

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            providerSessionStore.clear()
            mutableState.value = AppUiState(
                screen = RootScreen.Login,
                loginError = LoginError.Required,
            )
            return
        }
        if (mutableState.value.loginInFlight) return

        scope.launch {
            mutableState.value = AppUiState(
                screen = RootScreen.Login,
                loginInFlight = true,
            )
            when (val result = repository.resolve(username, password)) {
                is SessionResult.Success -> {
                    if (providerSessionStore.establish(username, password, result)) {
                        credentialStore.save(username, password)
                        mutableState.value = AppUiState(screen = RootScreen.Home)
                    } else {
                        providerSessionStore.clear()
                        mutableState.value = loginState(LoginError.TemporaryUnavailable)
                    }
                }
                SessionResult.InvalidCredentials -> failLogin(LoginError.InvalidCredentials)
                SessionResult.Expired -> failLogin(LoginError.Expired)
                SessionResult.Disabled -> failLogin(LoginError.Disabled)
                SessionResult.TemporaryUnavailable -> failLogin(LoginError.TemporaryUnavailable)
            }
        }
    }

    fun logout() {
        scope.launch {
            providerSessionStore.clear()
            credentialStore.clear()
            mutableState.value = AppUiState(screen = RootScreen.Login)
        }
    }

    private suspend fun bootstrap() {
        val credentials = credentialStore.load()
        if (credentials == null) {
            providerSessionStore.clear()
            mutableState.value = AppUiState(screen = RootScreen.Login)
        } else {
            reauthenticate(credentials)
        }
    }

    private suspend fun reauthenticate(credentials: StoredCredentials) {
        when (val result = repository.resolve(credentials.username, credentials.password)) {
            is SessionResult.Success -> {
                if (providerSessionStore.establish(
                        credentials.username,
                        credentials.password,
                        result,
                    )
                ) {
                    mutableState.value = AppUiState(screen = RootScreen.Home)
                } else {
                    providerSessionStore.clear()
                    mutableState.value = loginState(LoginError.TemporaryUnavailable)
                }
            }
            SessionResult.InvalidCredentials -> {
                providerSessionStore.clear()
                credentialStore.clear()
                mutableState.value = loginState(LoginError.InvalidCredentials)
            }
            SessionResult.Expired -> {
                providerSessionStore.clear()
                credentialStore.clear()
                mutableState.value = loginState(LoginError.Expired)
            }
            SessionResult.Disabled -> {
                providerSessionStore.clear()
                credentialStore.clear()
                mutableState.value = loginState(LoginError.Disabled)
            }
            SessionResult.TemporaryUnavailable -> {
                providerSessionStore.clear()
                mutableState.value = loginState(LoginError.TemporaryUnavailable)
            }
        }
    }

    private fun failLogin(error: LoginError) {
        providerSessionStore.clear()
        mutableState.value = loginState(error)
    }

    private fun loginState(error: LoginError) =
        AppUiState(screen = RootScreen.Login, loginError = error)
}
