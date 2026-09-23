package com.pinkiptv.app

import com.pinkiptv.app.model.SessionCode
import com.pinkiptv.app.model.SessionResolveRequest
import com.pinkiptv.app.model.SessionResolveResponse
import com.pinkiptv.app.model.SessionResult
import com.pinkiptv.app.model.toSessionResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionModelsTest {
    private val json = Json

    @Test
    fun requestSerializesOnlyUsernameAndPassword() {
        val encoded = json.encodeToString(
            SessionResolveRequest(
                username = "fixture-user",
                password = "fixture-pass", // pragma: allowlist secret
            ),
        )
        val keys = json.parseToJsonElement(encoded).jsonObject.keys

        assertEquals(setOf("username", "password"), keys)
        assertFalse(keys.contains("dns"))
        assertFalse(keys.contains("url"))
        assertFalse(keys.contains("token"))
    }

    @Test
    fun responseCodesMapToUiSafeResults() {
        assertTrue(response(SessionCode.SUCCESS) is SessionResult.Success)
        assertEquals(
            SessionResult.InvalidCredentials,
            response(SessionCode.INVALID_CREDENTIALS),
        )
        assertEquals(SessionResult.Expired, response(SessionCode.EXPIRED))
        assertEquals(SessionResult.Disabled, response(SessionCode.DISABLED))
        assertEquals(
            SessionResult.TemporaryUnavailable,
            response(SessionCode.DNS_UNREACHABLE),
        )
        assertEquals(
            SessionResult.TemporaryUnavailable,
            response(SessionCode.UPSTREAM_ERROR),
        )
    }

    private fun response(code: SessionCode): SessionResult =
        SessionResolveResponse(
            code = code,
            sessionToken = "fixture-session-token",
            sessionExpiresAt = "2026-09-23T16:00:00Z",
            xtreamBaseUrl = "https://fixture-provider.invalid",
            accountExpiresAt = "2026-12-01T00:00:00Z",
        ).toSessionResult()
}
