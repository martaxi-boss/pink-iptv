package com.pinkiptv.app.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

object XtreamOriginValidator {
    fun validate(raw: String): HttpUrl {
        val parsed = try {
            raw.toHttpUrl()
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Unsupported Xtream origin")
        }

        require(parsed.scheme == "http" || parsed.scheme == "https") {
            "Unsupported Xtream origin scheme"
        }
        require(parsed.host.isNotBlank()) {
            "Xtream origin requires a hostname"
        }
        require(parsed.username.isEmpty() && parsed.password.isEmpty()) {
            "Xtream origin must not contain credentials"
        }
        require(parsed.query == null && parsed.fragment == null) {
            "Xtream origin must not contain query or fragment"
        }
        require(parsed.encodedPath == "/") {
            "Xtream origin must use root semantics"
        }
        return parsed
    }
}

class AuthenticatedProviderSession internal constructor(
    val username: String,
    internal val password: String,
    val xtreamBaseUrl: String,
    internal val origin: HttpUrl,
    val accountExpiresAt: String?,
) {
    override fun toString(): String =
        "AuthenticatedProviderSession(username=<redacted>, origin=<redacted>, accountExpiresAt=" +
            accountExpiresAt + ")"
}

class RuntimeProviderSessionStore {
    @Volatile
    private var activeSession: AuthenticatedProviderSession? = null
    private val mutableAvailable = MutableStateFlow(false)
    val available: StateFlow<Boolean> = mutableAvailable.asStateFlow()

    fun establish(
        username: String,
        password: String,
        result: SessionResult.Success,
    ): Boolean {
        val rawOrigin = result.xtreamBaseUrl ?: return false
        val validated = try {
            XtreamOriginValidator.validate(rawOrigin)
        } catch (_: IllegalArgumentException) {
            return false
        }

        activeSession = AuthenticatedProviderSession(
            username = username,
            password = password,
            xtreamBaseUrl = rawOrigin,
            origin = validated,
            accountExpiresAt = result.accountExpiresAt,
        )
        mutableAvailable.value = true
        return true
    }

    fun current(): AuthenticatedProviderSession? = activeSession

    fun clear() {
        activeSession = null
        mutableAvailable.value = false
    }
}
