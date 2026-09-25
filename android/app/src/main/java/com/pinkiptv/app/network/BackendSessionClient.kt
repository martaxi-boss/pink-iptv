package com.pinkiptv.app.network

import com.pinkiptv.app.model.SessionCode
import com.pinkiptv.app.model.SessionRepository
import com.pinkiptv.app.model.SessionResolveRequest
import com.pinkiptv.app.model.SessionResolveResponse
import com.pinkiptv.app.model.SessionResult
import com.pinkiptv.app.model.toSessionResult
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class BackendSessionClient(
    baseUrl: String,
    private val client: OkHttpClient,
    allowCleartextForTesting: Boolean = false,
) : SessionRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val endpoint: HttpUrl = validateBaseUrl(baseUrl, allowCleartextForTesting)
        .newBuilder()
        .addPathSegments("v1/session/resolve")
        .build()

    override suspend fun resolve(username: String, password: String): SessionResult =
        withContext(Dispatchers.IO) {
            val body = json.encodeToString(
                SessionResolveRequest(username = username, password = password),
            ).toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url(endpoint)
                .post(body)
                .header("Accept", "application/json")
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext SessionResult.TemporaryUnavailable
                    }
                    json.decodeFromString<SessionResolveResponse>(response.body.string())
                        .toSessionResult()
                }
            } catch (_: IOException) {
                SessionResult.TemporaryUnavailable
            } catch (_: SerializationException) {
                SessionResult.TemporaryUnavailable
            } catch (_: IllegalArgumentException) {
                SessionResult.TemporaryUnavailable
            }
        }

    private fun validateBaseUrl(raw: String, allowCleartextForTesting: Boolean): HttpUrl {
        val parsed = raw.toHttpUrl()
        require(parsed.username.isEmpty() && parsed.password.isEmpty()) {
            "PINK backend URL must not contain credentials"
        }
        require(parsed.query == null && parsed.fragment == null) {
            "PINK backend URL must be an origin/base URL"
        }
        require(parsed.encodedPath == "/") {
            "PINK backend URL must not contain an application path"
        }
        require(parsed.isHttps || allowCleartextForTesting) {
            "PINK backend requires HTTPS"
        }
        return parsed
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
