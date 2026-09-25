package com.pinkiptv.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SessionResolveRequest(
    val username: String,
    val password: String,
)

@Serializable
data class SessionResolveResponse(
    val code: SessionCode,
    @SerialName("session_token") val sessionToken: String? = null,
    @SerialName("session_expires_at") val sessionExpiresAt: String? = null,
    @SerialName("xtream_base_url") val xtreamBaseUrl: String? = null,
    @SerialName("account_expires_at") val accountExpiresAt: String? = null,
)

@Serializable
enum class SessionCode {
    SUCCESS,
    INVALID_CREDENTIALS,
    EXPIRED,
    DISABLED,
    DNS_UNREACHABLE,
    UPSTREAM_ERROR,
}

sealed interface SessionResult {
    data class Success(
        val sessionToken: String?,
        val sessionExpiresAt: String?,
        val xtreamBaseUrl: String?,
        val accountExpiresAt: String?,
    ) : SessionResult

    data object InvalidCredentials : SessionResult
    data object Expired : SessionResult
    data object Disabled : SessionResult
    data object TemporaryUnavailable : SessionResult
}

interface SessionRepository {
    suspend fun resolve(username: String, password: String): SessionResult
}

internal fun SessionResolveResponse.toSessionResult(): SessionResult =
    when (code) {
        SessionCode.SUCCESS -> SessionResult.Success(
            sessionToken = sessionToken,
            sessionExpiresAt = sessionExpiresAt,
            xtreamBaseUrl = xtreamBaseUrl,
            accountExpiresAt = accountExpiresAt,
        )
        SessionCode.INVALID_CREDENTIALS -> SessionResult.InvalidCredentials
        SessionCode.EXPIRED -> SessionResult.Expired
        SessionCode.DISABLED -> SessionResult.Disabled
        SessionCode.DNS_UNREACHABLE,
        SessionCode.UPSTREAM_ERROR,
        -> SessionResult.TemporaryUnavailable
    }
