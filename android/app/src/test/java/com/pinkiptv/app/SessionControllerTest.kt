package com.pinkiptv.app

import com.pinkiptv.app.model.SessionRepository
import com.pinkiptv.app.model.SessionResult
import com.pinkiptv.app.state.LoginError
import com.pinkiptv.app.state.RootScreen
import com.pinkiptv.app.state.SessionController
import com.pinkiptv.app.storage.CredentialStore
import com.pinkiptv.app.storage.StoredCredentials
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionControllerTest {
    @Test
    fun startupWithoutStoredCredentialsShowsLogin() = runTest {
        val store = FakeCredentialStore()
        val controller = SessionController(
            repository = FakeSessionRepository(SessionResult.TemporaryUnavailable),
            credentialStore = store,
            scope = this,
        )

        advanceUntilIdle()

        assertEquals(RootScreen.Login, controller.state.value.screen)
        assertEquals(0, store.clearCount)
    }

    @Test
    fun startupWithValidStoredCredentialsShowsHome() = runTest {
        val store = FakeCredentialStore(
            stored = StoredCredentials(
                username = "stored-user",
                password = "stored-pass", // pragma: allowlist secret
            ),
        )
        val repository = FakeSessionRepository(success())

        val controller = SessionController(repository, store, this)
        advanceUntilIdle()

        assertEquals(RootScreen.Home, controller.state.value.screen)
        assertEquals(1, repository.calls.size)
        assertEquals(0, store.clearCount)
    }

    @Test
    fun invalidStoredCredentialsAreClearedBeforeLogin() = runTest {
        val store = FakeCredentialStore(
            stored = StoredCredentials(
                username = "stored-user",
                password = "stale-pass", // pragma: allowlist secret
            ),
        )
        val controller = SessionController(
            repository = FakeSessionRepository(SessionResult.InvalidCredentials),
            credentialStore = store,
            scope = this,
        )
        advanceUntilIdle()

        assertEquals(RootScreen.Login, controller.state.value.screen)
        assertEquals(LoginError.InvalidCredentials, controller.state.value.loginError)
        assertEquals(1, store.clearCount)
        assertNull(store.stored)
    }

    @Test
    fun successfulLoginSavesCredentialsAndShowsHome() = runTest {
        val store = FakeCredentialStore()
        val controller = SessionController(
            repository = FakeSessionRepository(success()),
            credentialStore = store,
            scope = this,
        )
        advanceUntilIdle()

        controller.login("fixture-user", "fixture-pass") // pragma: allowlist secret
        advanceUntilIdle()

        assertEquals(RootScreen.Home, controller.state.value.screen)
        assertEquals("fixture-user", store.stored?.username)
        assertEquals("fixture-pass", store.stored?.password) // pragma: allowlist secret
    }

    @Test
    fun failedLoginNeverSavesCredentials() = runTest {
        val store = FakeCredentialStore()
        val controller = SessionController(
            repository = FakeSessionRepository(SessionResult.InvalidCredentials),
            credentialStore = store,
            scope = this,
        )
        advanceUntilIdle()

        controller.login("fixture-user", "bad-fixture") // pragma: allowlist secret
        advanceUntilIdle()

        assertEquals(RootScreen.Login, controller.state.value.screen)
        assertEquals(LoginError.InvalidCredentials, controller.state.value.loginError)
        assertEquals(0, store.saveCount)
        assertNull(store.stored)
    }

    @Test
    fun temporaryFailureIsRecoverableAndDoesNotSaveCredentials() = runTest {
        val store = FakeCredentialStore()
        val controller = SessionController(
            repository = FakeSessionRepository(SessionResult.TemporaryUnavailable),
            credentialStore = store,
            scope = this,
        )
        advanceUntilIdle()

        controller.login("fixture-user", "fixture-pass") // pragma: allowlist secret
        advanceUntilIdle()

        assertEquals(RootScreen.Login, controller.state.value.screen)
        assertEquals(LoginError.TemporaryUnavailable, controller.state.value.loginError)
        assertEquals(0, store.saveCount)
    }

    @Test
    fun logoutClearsCredentialsAndReturnsToLogin() = runTest {
        val store = FakeCredentialStore(
            stored = StoredCredentials(
                username = "stored-user",
                password = "stored-pass", // pragma: allowlist secret
            ),
        )
        val controller = SessionController(
            repository = FakeSessionRepository(success()),
            credentialStore = store,
            scope = this,
        )
        advanceUntilIdle()
        assertEquals(RootScreen.Home, controller.state.value.screen)

        controller.logout()
        advanceUntilIdle()

        assertEquals(RootScreen.Login, controller.state.value.screen)
        assertEquals(1, store.clearCount)
        assertNull(store.stored)
    }

    private fun success() = SessionResult.Success(
        sessionToken = "fixture-session",
        sessionExpiresAt = null,
        xtreamBaseUrl = null,
        accountExpiresAt = null,
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
