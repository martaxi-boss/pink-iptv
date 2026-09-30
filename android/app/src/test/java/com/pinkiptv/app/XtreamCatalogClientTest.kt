package com.pinkiptv.app

import com.pinkiptv.app.model.CatalogCategory
import com.pinkiptv.app.model.CatalogError
import com.pinkiptv.app.model.CatalogResult
import com.pinkiptv.app.model.LiveStream
import com.pinkiptv.app.model.RuntimeProviderSessionStore
import com.pinkiptv.app.model.SeriesItem
import com.pinkiptv.app.model.SessionResult
import com.pinkiptv.app.model.VodItem
import com.pinkiptv.app.model.XtreamOriginValidator
import com.pinkiptv.app.network.PINK_XTREAM_USER_AGENT
import com.pinkiptv.app.network.XtreamCatalogClient
import com.pinkiptv.app.network.buildXtreamHttpClient
import java.net.SocketTimeoutException
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamCatalogClientTest {
    @Test
    fun authoritativeHttpAndHttpsOriginsAreAcceptedAndPreserved() {
        val http = XtreamOriginValidator.validate("http://catalog.invalid:8080/")
        val https = XtreamOriginValidator.validate("https://catalog.invalid:8443/")
        assertEquals("http", http.scheme)
        assertEquals(8080, http.port)
        assertEquals("https", https.scheme)
        assertEquals(8443, https.port)

        val store = RuntimeProviderSessionStore()
        assertTrue(
            store.establish(
                "fixture-user",
                "fixture-pass", // pragma: allowlist secret
                success("http://catalog.invalid:8080/"),
            ),
        )
        assertEquals("http://catalog.invalid:8080/", store.current()?.xtreamBaseUrl)
    }

    @Test
    fun malformedUnsupportedOrCredentialOriginsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            XtreamOriginValidator.validate("ftp://catalog.invalid/")
        }
        assertThrows(IllegalArgumentException::class.java) {
            XtreamOriginValidator.validate("https://fixture-user@catalog.invalid/")
        }
        assertThrows(IllegalArgumentException::class.java) {
            XtreamOriginValidator.validate("https://catalog.invalid/path")
        }
        assertThrows(IllegalArgumentException::class.java) {
            XtreamOriginValidator.validate("https://catalog.invalid/?x=1")
        }
    }

    @Test
    fun providerClientDisablesRedirects() {
        val client = buildXtreamHttpClient()
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
    }

    @Test
    fun liveCallsUseStableAgentAndStructuredPlayerApiUrl() = runTest {
        val interceptor = RecordingInterceptor(
            body = """[{"category_id":"7","category_name":"News"}]""",
        )
        val catalog = XtreamCatalogClient(activeStore(), testClient(interceptor))

        val result = catalog.liveCategories()

        successValue<List<CatalogCategory>>(result)
        val request = requireNotNull(interceptor.request)
        assertEquals(PINK_XTREAM_USER_AGENT, request.header("User-Agent"))
        assertEquals("/player_api.php", request.url.encodedPath)
        assertEquals("get_live_categories", request.url.queryParameter("action"))
        assertEquals("fixture-user", request.url.queryParameter("username"))
        assertEquals("fixture-pass", request.url.queryParameter("password")) // pragma: allowlist secret
    }

    @Test
    fun parsesLiveVodAndSeriesPayloadsDefensively() = runTest {
        val liveCategory = XtreamCatalogClient(
            activeStore(),
            testClient(RecordingInterceptor("""[{"category_id":7,"category_name":"News"}]""")),
        ).liveCategories()
        assertEquals("7", successValue<List<CatalogCategory>>(liveCategory).single().id)

        val live = XtreamCatalogClient(
            activeStore(),
            testClient(
                RecordingInterceptor(
                    """[{"stream_id":11,"name":"Channel","category_id":"7","stream_icon":"https://art.invalid/live.png","stream_type":"live"}]""",
                ),
            ),
        ).liveStreams()
        assertEquals("11", successValue<List<LiveStream>>(live).single().streamId)

        val vodCategory = XtreamCatalogClient(
            activeStore(),
            testClient(RecordingInterceptor("""[{"category_id":"2","category_name":"Films"}]""")),
        ).vodCategories()
        assertEquals("Films", successValue<List<CatalogCategory>>(vodCategory).single().name)

        val vod = XtreamCatalogClient(
            activeStore(),
            testClient(
                RecordingInterceptor(
                    """[{"stream_id":"22","name":"Movie","category_id":"2","stream_icon":null,"container_extension":"mkv"}]""",
                ),
            ),
        ).vodStreams()
        assertEquals("mkv", successValue<List<VodItem>>(vod).single().containerExtension)

        val seriesCategory = XtreamCatalogClient(
            activeStore(),
            testClient(RecordingInterceptor("""[{"category_id":"3","category_name":"Series"}]""")),
        ).seriesCategories()
        assertEquals("3", successValue<List<CatalogCategory>>(seriesCategory).single().id)

        val series = XtreamCatalogClient(
            activeStore(),
            testClient(
                RecordingInterceptor(
                    """[{"series_id":33,"name":"Show","category_id":"3","cover":"https://art.invalid/series.jpg"}]""",
                ),
            ),
        ).series()
        assertEquals("33", successValue<List<SeriesItem>>(series).single().seriesId)
    }

    @Test
    fun emptyPayloadIsSuccessfulAndMalformedTopLevelFailsClosed() = runTest {
        val empty = XtreamCatalogClient(
            activeStore(),
            testClient(RecordingInterceptor("[]")),
        ).liveStreams()
        assertTrue(successValue<List<LiveStream>>(empty).isEmpty())

        val malformed = XtreamCatalogClient(
            activeStore(),
            testClient(RecordingInterceptor("""{"user_info":{"auth":0}}""")),
        ).liveStreams()
        assertEquals(CatalogError.InvalidResponse, failureValue(malformed))
    }

    @Test
    fun httpAndNetworkFailuresAreSafeAndCredentialFree() = runTest {
        val httpFailure = XtreamCatalogClient(
            activeStore(),
            testClient(RecordingInterceptor("[]", code = 503)),
        ).vodStreams()
        assertEquals(CatalogError.HttpFailure, failureValue(httpFailure))

        val timeoutInterceptor = RecordingInterceptor(
            body = "[]",
            failure = SocketTimeoutException("fixture timeout"),
        )
        val timeout = XtreamCatalogClient(
            activeStore(),
            testClient(timeoutInterceptor),
        ).series()
        assertEquals(CatalogError.NetworkFailure, failureValue(timeout))

        val text = timeout.toString() + httpFailure.toString()
        assertFalse(text.contains("fixture-user"))
        assertFalse(text.contains("fixture-pass")) // pragma: allowlist secret
        assertFalse(text.contains("catalog.invalid"))
    }

    @Test
    fun missingRuntimeSessionFailsWithoutNetwork() = runTest {
        val interceptor = RecordingInterceptor("[]")
        val result = XtreamCatalogClient(
            RuntimeProviderSessionStore(),
            testClient(interceptor),
        ).liveCategories()
        assertEquals(CatalogError.MissingSession, failureValue(result))
        assertEquals(null, interceptor.request)
    }

    private fun activeStore(): RuntimeProviderSessionStore =
        RuntimeProviderSessionStore().apply {
            check(
                establish(
                    "fixture-user",
                    "fixture-pass", // pragma: allowlist secret
                    success("https://catalog.invalid/"),
                ),
            )
        }

    private fun success(origin: String) = SessionResult.Success(
        sessionToken = "fixture-session",
        sessionExpiresAt = null,
        xtreamBaseUrl = origin,
        accountExpiresAt = null,
    )

    private fun <T> successValue(result: CatalogResult<T>): T {
        assertTrue(result is CatalogResult.Success)
        @Suppress("UNCHECKED_CAST")
        return (result as CatalogResult.Success<T>).value
    }

    private fun failureValue(result: CatalogResult<*>): CatalogError {
        assertTrue(result is CatalogResult.Failure)
        return (result as CatalogResult.Failure).error
    }

    private fun testClient(interceptor: Interceptor): OkHttpClient =
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor(interceptor)
            .build()

    private class RecordingInterceptor(
        private val body: String,
        private val code: Int = 200,
        private val failure: Exception? = null,
    ) : Interceptor {
        var request: Request? = null

        override fun intercept(chain: Interceptor.Chain): Response {
            request = chain.request()
            failure?.let { throw it }
            return Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("fixture")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }
}
