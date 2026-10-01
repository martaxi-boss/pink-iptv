package com.pinkiptv.app

import com.pinkiptv.app.model.CatalogResult
import com.pinkiptv.app.model.EpgError
import com.pinkiptv.app.model.EpgProgramme
import com.pinkiptv.app.model.EpgResult
import com.pinkiptv.app.model.LiveStream
import com.pinkiptv.app.model.RuntimeProviderSessionStore
import com.pinkiptv.app.model.SessionResult
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgNetworkAndParserTest {
    @Test
    fun liveArchiveMetadataNormalizesNumericStringNullAndOmitsDirectSource() = runTest {
        val body = """
            [
              {
                "stream_id":101,
                "name":"One",
                "epg_channel_id":"epg-one",
                "tv_archive":1,
                "tv_archive_duration":7,
                "direct_source":"https://direct.invalid/secret"
              },
              {
                "stream_id":"102",
                "name":"Two",
                "epg_channel_id":"epg-two",
                "tv_archive":"true",
                "tv_archive_duration":"3"
              },
              {
                "stream_id":"103",
                "name":"Three",
                "tv_archive":0,
                "tv_archive_duration":null
              },
              {
                "stream_id":"104",
                "name":"Four",
                "tv_archive":"false"
              }
            ]
        """.trimIndent()
        val streams = successCatalog<List<LiveStream>>(
            XtreamCatalogClient(
                activeStore(),
                testClient(RecordingInterceptor(body)),
            ).liveStreams(),
        )

        assertEquals("epg-one", streams[0].epgChannelId)
        assertTrue(streams[0].tvArchive)
        assertEquals(7, streams[0].tvArchiveDurationDays)
        assertTrue(streams[1].tvArchive)
        assertEquals(3, streams[1].tvArchiveDurationDays)
        assertFalse(streams[2].tvArchive)
        assertNull(streams[2].tvArchiveDurationDays)
        assertFalse(streams[3].tvArchive)
        assertNull(streams[3].tvArchiveDurationDays)

        val fields = LiveStream::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse(fields.any { it.contains("direct") })
        assertFalse(streams.joinToString().contains("direct.invalid"))
    }

    @Test
    fun shortEpgUsesExactStreamIdBoundedLimitAndHttpOrigin() = runTest {
        val interceptor = RecordingInterceptor("""{"epg_listings":[]}""")
        val client = XtreamCatalogClient(
            activeStore("http://catalog.invalid:8080/"),
            testClient(interceptor),
        )

        assertTrue(client.shortEpg("123", 2) is EpgResult.Success)
        val request = requireNotNull(interceptor.request)
        assertEquals("http", request.url.scheme)
        assertEquals("catalog.invalid", request.url.host)
        assertEquals(8080, request.url.port)
        assertEquals("/player_api.php", request.url.encodedPath)
        assertEquals("get_short_epg", request.url.queryParameter("action"))
        assertEquals("123", request.url.queryParameter("stream_id"))
        assertEquals("2", request.url.queryParameter("limit"))
        assertEquals("fixture-user", request.url.queryParameter("username"))
        assertEquals("fixture-pass", request.url.queryParameter("password")) // pragma: allowlist secret
    }

    @Test
    fun shortEpgRejectsUnsafeStreamIdAndUnboundedLimitBeforeNetwork() = runTest {
        val interceptor = RecordingInterceptor("""{"epg_listings":[]}""")
        val client = XtreamCatalogClient(activeStore(), testClient(interceptor))

        for (streamId in listOf("../1", "1/2", "1\\2", "1?2", "1#2", "1 2")) {
            assertEquals(EpgError.InvalidMetadata, failure(client.shortEpg(streamId, 2)))
        }
        assertEquals(EpgError.InvalidMetadata, failure(client.shortEpg("123", 0)))
        assertEquals(EpgError.InvalidMetadata, failure(client.shortEpg("123", 21)))
        assertNull(interceptor.request)
    }

    @Test
    fun simpleDataTablePreservesHttpsExplicitPortAndRedirectsOff() = runTest {
        val interceptor = RecordingInterceptor("""{"epg_listings":[]}""")
        val client = XtreamCatalogClient(
            activeStore("https://catalog.invalid:8443/"),
            testClient(interceptor),
        )

        assertTrue(client.simpleDataTable("456") is EpgResult.Success)
        val request = requireNotNull(interceptor.request)
        assertEquals("https", request.url.scheme)
        assertEquals("catalog.invalid", request.url.host)
        assertEquals(8443, request.url.port)
        assertEquals("get_simple_data_table", request.url.queryParameter("action"))
        assertEquals("456", request.url.queryParameter("stream_id"))

        val policy = buildXtreamHttpClient()
        assertFalse(policy.followRedirects)
        assertFalse(policy.followSslRedirects)
    }

    @Test
    fun epgFailuresAreSafeAndCredentialFree() = runTest {
        val http = XtreamCatalogClient(
            activeStore(),
            testClient(RecordingInterceptor("{}", code = 503)),
        ).simpleDataTable("1")
        assertEquals(EpgError.HttpFailure, failure(http))

        val timeout = XtreamCatalogClient(
            activeStore(),
            testClient(
                RecordingInterceptor(
                    "{}",
                    failure = SocketTimeoutException("fixture timeout"),
                ),
            ),
        ).shortEpg("1", 2)
        assertEquals(EpgError.NetworkFailure, failure(timeout))

        val interceptor = RecordingInterceptor("""{"epg_listings":[]}""")
        val missing = XtreamCatalogClient(
            RuntimeProviderSessionStore(),
            testClient(interceptor),
        ).simpleDataTable("1")
        assertEquals(EpgError.MissingSession, failure(missing))
        assertNull(interceptor.request)

        val text = http.toString() + timeout.toString() + missing.toString()
        assertFalse(text.contains("fixture-user"))
        assertFalse(text.contains("fixture-pass"))
        assertFalse(text.contains("catalog.invalid"))
    }

    @Test
    fun parserDecodesBase64PreservesPlainTextFlagsAndOrdersTimestamps() = runTest {
        val body = """
            {
              "epg_listings":[
                {
                  "id":2,
                  "title":"TmV3cw==",
                  "description":"RGVzY3JpcHRpb24=",
                  "start":"2026-09-30 12:10:00",
                  "end":"2026-09-30 12:20:00",
                  "start_timestamp":"200",
                  "stop_timestamp":260,
                  "now_playing":"true",
                  "has_archive":"1"
                },
                {
                  "id":"1",
                  "title":"News",
                  "description":"not@@base64",
                  "start":"2026-09-30 12:00:00",
                  "end":"2026-09-30 12:10:00",
                  "start_timestamp":100,
                  "stop_timestamp":"160",
                  "now_playing":0,
                  "has_archive":true
                },
                {
                  "epg_id":"3",
                  "title":"Plain programme title",
                  "description":null,
                  "start":"later",
                  "end":"later",
                  "now_playing":"false",
                  "has_archive":"false"
                }
              ]
            }
        """.trimIndent()

        val programmes = successEpg(
            XtreamCatalogClient(
                activeStore(),
                testClient(RecordingInterceptor(body)),
            ).simpleDataTable("1"),
        )

        assertEquals(listOf("1", "2", "3"), programmes.map { it.programmeId })
        assertEquals("News", programmes[0].title)
        assertEquals("not@@base64", programmes[0].description)
        assertEquals("News", programmes[1].title)
        assertEquals("Description", programmes[1].description)
        assertEquals(100L, programmes[0].startTimestamp)
        assertEquals(260L, programmes[1].stopTimestamp)
        assertTrue(programmes[1].nowPlaying)
        assertTrue(programmes[0].hasArchive)
        assertFalse(programmes[2].hasArchive)
        assertNull(programmes[2].description)
        assertNull(programmes[2].startTimestamp)
    }

    @Test
    fun parserKeepsInvalidBase64AsPlainText() = runTest {
        val programmes = successEpg(
            XtreamCatalogClient(
                activeStore(),
                testClient(
                    RecordingInterceptor(
                        """{"epg_listings":[{"id":"1","title":"%%%%","description":"bad@@"}]}""",
                    ),
                ),
            ).simpleDataTable("1"),
        )

        assertEquals("%%%%", programmes.single().title)
        assertEquals("bad@@", programmes.single().description)
    }

    @Test
    fun emptyEpgIsSuccessAndMalformedRootFailsClosed() = runTest {
        val empty = XtreamCatalogClient(
            activeStore(),
            testClient(RecordingInterceptor("""{"epg_listings":[]}""")),
        ).simpleDataTable("1")
        assertTrue(empty is EpgResult.Success)
        assertTrue(successEpg(empty).isEmpty())

        val malformedArray = XtreamCatalogClient(
            activeStore(),
            testClient(RecordingInterceptor("[]")),
        ).simpleDataTable("1")
        assertEquals(EpgError.InvalidResponse, failure(malformedArray))

        val missingField = XtreamCatalogClient(
            activeStore(),
            testClient(RecordingInterceptor("{}")),
        ).shortEpg("1", 2)
        assertEquals(EpgError.InvalidResponse, failure(missingField))
    }

    private fun activeStore(
        origin: String = "https://catalog.invalid/",
    ): RuntimeProviderSessionStore =
        RuntimeProviderSessionStore().apply {
            check(
                establish(
                    "fixture-user",
                    "fixture-pass", // pragma: allowlist secret
                    success(origin),
                ),
            )
        }

    private fun success(origin: String) = SessionResult.Success(
        sessionToken = "fixture-session",
        sessionExpiresAt = null,
        xtreamBaseUrl = origin,
        accountExpiresAt = null,
    )

    private fun <T> successCatalog(result: CatalogResult<T>): T {
        assertTrue(result is CatalogResult.Success)
        @Suppress("UNCHECKED_CAST")
        return (result as CatalogResult.Success<T>).value
    }

    private fun successEpg(
        result: EpgResult<List<EpgProgramme>>,
    ): List<EpgProgramme> {
        assertTrue(result is EpgResult.Success)
        return (result as EpgResult.Success<List<EpgProgramme>>).value
    }

    private fun failure(result: EpgResult<*>): EpgError {
        assertTrue(result is EpgResult.Failure)
        return (result as EpgResult.Failure).error
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
