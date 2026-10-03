package com.pinkiptv.app.state

import com.pinkiptv.app.model.RuntimeProviderSessionStore
import com.pinkiptv.app.model.SessionRepository
import com.pinkiptv.app.model.SessionResult
import com.pinkiptv.app.storage.CredentialStore
import com.pinkiptv.app.storage.StoredCredentials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    private var generation = 0L
    private val credentialMutex = Mutex()
    private var credentialCleanup: Job? = null

    init {
        val requestGeneration = generation
        scope.launch { bootstrap(requestGeneration) }
    }

    fun login(username: String, password: String) {
        if (mutableState.value.loginInFlight) return
        val requestGeneration = ++generation
        if (username.isBlank() || password.isBlank()) {
            providerSessionStore.clear()
            mutableState.value = loginState(LoginError.Required)
            return
        }

        mutableState.value = AppUiState(
            screen = RootScreen.Login,
            loginInFlight = true,
        )
        val pendingCleanup = credentialCleanup
        scope.launch {
            pendingCleanup?.join()
            if (requestGeneration != generation) return@launch
            val result = repository.resolve(username, password)
            if (requestGeneration != generation) return@launch
            when (result) {
                is SessionResult.Success -> {
                    // Validate the origin before persisting credentials or publishing a session.
                    val valid = try {
                        val origin = result.xtreamBaseUrl
                        if (origin == null) false else {
                            com.pinkiptv.app.model.XtreamOriginValidator.validate(origin)
                            true
                        }
                    } catch (_: IllegalArgumentException) {
                        false
                    }
                    if (!valid) {
                        failLogin(LoginError.TemporaryUnavailable)
                        return@launch
                    }
                    credentialMutex.withLock {
                        if (requestGeneration == generation) {
                            credentialStore.save(username, password)
                        }
                    }
                    if (requestGeneration != generation) return@launch
                    if (providerSessionStore.establish(username, password, result)) {
                        mutableState.value = AppUiState(screen = RootScreen.Home)
                    } else {
                        failLogin(LoginError.TemporaryUnavailable)
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
        generation += 1
        providerSessionStore.clear()
        mutableState.value = AppUiState(screen = RootScreen.Login)
        val previousCleanup = credentialCleanup
        credentialCleanup = scope.launch {
            previousCleanup?.join()
            credentialMutex.withLock { credentialStore.clear() }
        }
    }

    private suspend fun bootstrap(requestGeneration: Long) {
        val credentials = credentialMutex.withLock {
            if (requestGeneration != generation) return
            credentialStore.load()
        }
        if (requestGeneration != generation) return
        if (credentials == null) {
            providerSessionStore.clear()
            mutableState.value = AppUiState(screen = RootScreen.Login)
        } else {
            reauthenticate(credentials, requestGeneration)
        }
    }

    private suspend fun reauthenticate(
        credentials: StoredCredentials,
        requestGeneration: Long,
    ) {
        val result = repository.resolve(credentials.username, credentials.password)
        if (requestGeneration != generation) return
        when (result) {
            is SessionResult.Success -> {
                if (providerSessionStore.establish(
                        credentials.username,
                        credentials.password,
                        result,
                    )
                ) {
                    mutableState.value = AppUiState(screen = RootScreen.Home)
                } else {
                    failLogin(LoginError.TemporaryUnavailable)
                }
            }
            SessionResult.InvalidCredentials,
            SessionResult.Expired,
            SessionResult.Disabled,
            -> {
                credentialMutex.withLock {
                    if (requestGeneration == generation) credentialStore.clear()
                }
                if (requestGeneration != generation) return
                val error = when (result) {
                    SessionResult.InvalidCredentials -> LoginError.InvalidCredentials
                    SessionResult.Expired -> LoginError.Expired
                    else -> LoginError.Disabled
                }
                failLogin(error)
            }
            SessionResult.TemporaryUnavailable -> failLogin(LoginError.TemporaryUnavailable)
        }
    }

    private fun failLogin(error: LoginError) {
        providerSessionStore.clear()
        mutableState.value = loginState(error)
    }

    private fun loginState(error: LoginError) =
        AppUiState(screen = RootScreen.Login, loginError = error)
}
