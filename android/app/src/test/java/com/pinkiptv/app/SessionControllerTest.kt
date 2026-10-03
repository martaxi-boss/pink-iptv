package com.pinkiptv.app

import com.pinkiptv.app.model.RuntimeProviderSessionStore
import com.pinkiptv.app.model.SessionRepository
import com.pinkiptv.app.model.SessionResult
import com.pinkiptv.app.state.AppUiState
import com.pinkiptv.app.state.LoginError
import com.pinkiptv.app.state.RootScreen
import com.pinkiptv.app.state.SessionController
import com.pinkiptv.app.storage.CredentialStore
import com.pinkiptv.app.storage.StoredCredentials
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SessionControllerTest {
    @Test
    fun startupWithoutStoredCredentialsShowsLogin() = runTest {
        val store = FakeCredentialStore()
        val runtime = RuntimeProviderSessionStore()
        val controller = SessionController(
            repository = FakeSessionRepository(SessionResult.TemporaryUnavailable),
            credentialStore = store,
            scope = this,
            providerSessionStore = runtime,
        )

        advanceUntilIdle()

        assertEquals(RootScreen.Login, controller.state.value.screen)
        assertEquals(0, store.clearCount)
        assertNull(runtime.current())
    }

    @Test
    fun startupWithValidStoredCredentialsRebuildsRuntimeSession() = runTest {
        val store = FakeCredentialStore(
            stored = StoredCredentials(
                username = "stored-user",
                password = "stored-pass", // pragma: allowlist secret
            ),
        )
        val repository = FakeSessionRepository(success())
        val runtime = RuntimeProviderSessionStore()

        val controller = SessionController(repository, store, this, runtime)
        advanceUntilIdle()

        assertEquals(RootScreen.Home, controller.state.value.screen)
        assertEquals(1, repository.calls.size)
        assertEquals(0, store.clearCount)
        assertNotNull(runtime.current())
        assertEquals("https://catalog.invalid/", runtime.current()?.xtreamBaseUrl)
    }

    @Test
    fun invalidStoredCredentialsClearRuntimeAndStoredCredentials() = runTest {
        val store = FakeCredentialStore(
            stored = StoredCredentials(
                username = "stored-user",
                password = "stale-pass", // pragma: allowlist secret
            ),
        )
        val runtime = RuntimeProviderSessionStore()
        runtime.establish("old-user", "old-pass", success()) // pragma: allowlist secret
        val controller = SessionController(
            repository = FakeSessionRepository(SessionResult.InvalidCredentials),
            credentialStore = store,
            scope = this,
            providerSessionStore = runtime,
        )
        advanceUntilIdle()

        assertEquals(RootScreen.Login, controller.state.value.screen)
        assertEquals(LoginError.InvalidCredentials, controller.state.value.loginError)
        assertEquals(1, store.clearCount)
        assertNull(store.stored)
        assertNull(runtime.current())
    }

    @Test
    fun successfulLoginSavesCredentialsAndCreatesRuntimeSession() = runTest {
        val store = FakeCredentialStore()
        val runtime = RuntimeProviderSessionStore()
        val controller = SessionController(
            repository = FakeSessionRepository(success()),
            credentialStore = store,
            scope = this,
            providerSessionStore = runtime,
        )
        advanceUntilIdle()

        controller.login("fixture-user", "fixture-pass") // pragma: allowlist secret
        advanceUntilIdle()

        assertEquals(RootScreen.Home, controller.state.value.screen)
        assertEquals("fixture-user", store.stored?.username)
        assertEquals("fixture-pass", store.stored?.password) // pragma: allowlist secret
        assertEquals("fixture-user", runtime.current()?.username)
        assertEquals("https://catalog.invalid/", runtime.current()?.xtreamBaseUrl)
    }

    @Test
    fun backendSuccessWithoutValidXtreamOriginFailsClosed() = runTest {
        val store = FakeCredentialStore()
        val runtime = RuntimeProviderSessionStore()
        val controller = SessionController(
            repository = FakeSessionRepository(
                success(xtreamBaseUrl = "ftp://catalog.invalid/"),
            ),
            credentialStore = store,
            scope = this,
            providerSessionStore = runtime,
        )
        advanceUntilIdle()

        controller.login("fixture-user", "fixture-pass") // pragma: allowlist secret
        advanceUntilIdle()

        assertEquals(RootScreen.Login, controller.state.value.screen)
        assertEquals(LoginError.TemporaryUnavailable, controller.state.value.loginError)
        assertEquals(0, store.saveCount)
        assertNull(runtime.current())
    }

    @Test
    fun failedExpiredAndDisabledLoginsLeaveNoRuntimeSession() = runTest {
        for (result in listOf(
            SessionResult.InvalidCredentials,
            SessionResult.Expired,
            SessionResult.Disabled,
        )) {
            val runtime = RuntimeProviderSessionStore()
            runtime.establish("old-user", "old-pass", success()) // pragma: allowlist secret
            val controller = SessionController(
                repository = FakeSessionRepository(result),
                credentialStore = FakeCredentialStore(),
                scope = this,
                providerSessionStore = runtime,
            )
            advanceUntilIdle()

            controller.login("fixture-user", "fixture-pass") // pragma: allowlist secret
            advanceUntilIdle()

            assertNull(runtime.current())
        }
    }

    @Test
    fun temporaryFailureIsRecoverableAndDoesNotSaveCredentials() = runTest {
        val store = FakeCredentialStore()
        val runtime = RuntimeProviderSessionStore()
        val controller = SessionController(
            repository = FakeSessionRepository(SessionResult.TemporaryUnavailable),
            credentialStore = store,
            scope = this,
            providerSessionStore = runtime,
        )
        advanceUntilIdle()

        controller.login("fixture-user", "fixture-pass") // pragma: allowlist secret
        advanceUntilIdle()

        assertEquals(RootScreen.Login, controller.state.value.screen)
        assertEquals(LoginError.TemporaryUnavailable, controller.state.value.loginError)
        assertEquals(0, store.saveCount)
        assertNull(runtime.current())
    }

    @Test
    fun logoutClearsCredentialsRuntimeAndReturnsToLogin() = runTest {
        val store = FakeCredentialStore(
            stored = StoredCredentials(
                username = "stored-user",
                password = "stored-pass", // pragma: allowlist secret
            ),
        )
        val runtime = RuntimeProviderSessionStore()
        val controller = SessionController(
            repository = FakeSessionRepository(success()),
            credentialStore = store,
            scope = this,
            providerSessionStore = runtime,
        )
        advanceUntilIdle()
        assertEquals(RootScreen.Home, controller.state.value.screen)
        assertNotNull(runtime.current())

        controller.logout()
        advanceUntilIdle()

        assertEquals(RootScreen.Login, controller.state.value.screen)
        assertEquals(1, store.clearCount)
        assertNull(store.stored)
        assertNull(runtime.current())
    }

    @Test
    fun publicUiStateContainsNoPasswordField() {
        assertFalse(
            AppUiState::class.java.declaredFields.any {
                it.name.contains("password", ignoreCase = true)
            },
        )
    }


    @Test
    fun delayedLoginCannotRestoreSessionAfterLogout() = runTest {
        val pending = CompletableDeferred<SessionResult>()
        val repository = object : SessionRepository {
            override suspend fun resolve(username: String, password: String) = pending.await()
        }
        val store = FakeCredentialStore()
        val runtime = RuntimeProviderSessionStore()
        val controller = SessionController(repository, store, this, runtime)
        advanceUntilIdle()
        controller.login("old-user", "synthetic-value")
        runCurrent()
        controller.logout()
        runCurrent()
        pending.complete(success())
        advanceUntilIdle()
        assertEquals(RootScreen.Login, controller.state.value.screen)
        assertNull(runtime.current())
        assertNull(store.stored)
        assertEquals(0, store.saveCount)
    }

    @Test
    fun queuedDuplicateLoginStartsOnlyOneRequest() = runTest {
        val repository = FakeSessionRepository(success())
        val controller = SessionController(repository, FakeCredentialStore(), this)
        advanceUntilIdle()
        controller.login("first", "synthetic-value")
        controller.login("second", "synthetic-value")
        advanceUntilIdle()
        assertEquals(listOf("first"), repository.calls.map { it.first })
    }

    @Test
    fun delayedBootstrapCannotOverwriteNewLogin() = runTest {
        val pending = CompletableDeferred<SessionResult>()
        val repository = object : SessionRepository {
            override suspend fun resolve(username: String, password: String): SessionResult =
                if (username == "stored-user") pending.await() else success()
        }
        val store = FakeCredentialStore(StoredCredentials("stored-user", "synthetic-value"))
        val runtime = RuntimeProviderSessionStore()
        val controller = SessionController(repository, store, this, runtime)
        runCurrent()
        controller.login("new-user", "synthetic-value")
        runCurrent()
        pending.complete(SessionResult.InvalidCredentials)
        advanceUntilIdle()
        assertEquals(RootScreen.Home, controller.state.value.screen)
        assertEquals("new-user", runtime.current()?.username)
        assertEquals("new-user", store.stored?.username)
        assertEquals(0, store.clearCount)
    }

    @Test
    fun logoutWaitsForSuspendedSaveAndClearsItsResult() = runTest {
        val saveStarted = CompletableDeferred<Unit>()
        val finishSave = CompletableDeferred<Unit>()
        var persisted: StoredCredentials? = null
        val store = object : CredentialStore {
            override suspend fun load(): StoredCredentials? = null
            override suspend fun save(username: String, password: String) {
                saveStarted.complete(Unit)
                finishSave.await()
                persisted = StoredCredentials(username, password)
            }
            override suspend fun clear() { persisted = null }
        }
        val runtime = RuntimeProviderSessionStore()
        val controller = SessionController(FakeSessionRepository(success()), store, this, runtime)
        advanceUntilIdle()
        controller.login("old-user", "synthetic-value")
        runCurrent()
        assertEquals(true, saveStarted.isCompleted)
        controller.logout()
        runCurrent()
        finishSave.complete(Unit)
        advanceUntilIdle()
        assertNull(persisted)
        assertNull(runtime.current())
        assertEquals(RootScreen.Login, controller.state.value.screen)
    }

    @Test
    fun delayedStoredCredentialReadCannotRestoreLogout() = runTest {
        val pending = CompletableDeferred<StoredCredentials?>()
        var resolveCalls = 0
        val store = object : CredentialStore {
            override suspend fun load() = pending.await()
            override suspend fun save(username: String, password: String) = Unit
            override suspend fun clear() = Unit
        }
        val repository = object : SessionRepository {
            override suspend fun resolve(username: String, password: String): SessionResult {
                resolveCalls += 1
                return success()
            }
        }
        val runtime = RuntimeProviderSessionStore()
        val controller = SessionController(repository, store, this, runtime)
        runCurrent()
        controller.logout()
        pending.complete(StoredCredentials("stored-user", "synthetic-value"))
        advanceUntilIdle()
        assertEquals(0, resolveCalls)
        assertNull(runtime.current())
        assertEquals(RootScreen.Login, controller.state.value.screen)
    }

    @Test
    fun immediateFailedLoginCannotCancelLogoutCredentialCleanup() = runTest {
        val store = FakeCredentialStore(StoredCredentials("old-user", "synthetic-value"))
        val repository = FakeSessionRepository(success())
        val runtime = RuntimeProviderSessionStore()
        val controller = SessionController(repository, store, this, runtime)
        advanceUntilIdle()
        controller.logout()
        repository.result = SessionResult.InvalidCredentials
        controller.login("new-user", "synthetic-value")
        advanceUntilIdle()
        assertNull(store.stored)
        assertNull(runtime.current())
        assertEquals(LoginError.InvalidCredentials, controller.state.value.loginError)
        assertEquals(1, store.clearCount)
    }

    private fun success(
        xtreamBaseUrl: String = "https://catalog.invalid/",
    ) = SessionResult.Success(
        sessionToken = "fixture-session",
        sessionExpiresAt = null,
        xtreamBaseUrl = xtreamBaseUrl,
        accountExpiresAt = "2030-01-01T00:00:00Z",
    )

    private class FakeSessionRepository(
        var result: SessionResult,
    ) : SessionRepository {
        val calls = mutableListOf<Pair<String, String>>()

        override suspend fun resolve(username: String, password: String): SessionResult {
            calls += username to password
            return result
        }
    }

    private class FakeCredentialStore(
        var stored: StoredCredentials? = null,
    ) : CredentialStore {
        var saveCount = 0
        var clearCount = 0

        override suspend fun load(): StoredCredentials? = stored

        override suspend fun save(username: String, password: String) {
            saveCount += 1
            stored = StoredCredentials(username, password)
        }

        override suspend fun clear() {
            clearCount += 1
            stored = null
        }
    }
}
