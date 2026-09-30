package com.pinkiptv.app

import com.pinkiptv.app.model.CatalogCategory
import com.pinkiptv.app.model.CatalogError
import com.pinkiptv.app.model.CatalogResult
import com.pinkiptv.app.model.LiveStream
import com.pinkiptv.app.model.RuntimeProviderSessionStore
import com.pinkiptv.app.model.SeriesDetail
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


    @Test
    fun seriesInfoUsesExactSelectedIdAndStructuredAuthoritativeOrigin() = runTest {
        val store = RuntimeProviderSessionStore().apply {
            check(
                establish(
                    "fixture-user",
                    "fixture-pass", // pragma: allowlist secret
                    success("http://catalog.invalid:8080/"),
                ),
            )
        }
        val interceptor = RecordingInterceptor(
            """{"info":{"name":"Show"},"seasons":[],"episodes":{}}""",
        )
        val catalog = XtreamCatalogClient(store, testClient(interceptor))

        val result = catalog.seriesInfo("789")

        assertEquals("789", successValue<SeriesDetail>(result).seriesId)
        val request = requireNotNull(interceptor.request)
        assertEquals("http", request.url.scheme)
        assertEquals("catalog.invalid", request.url.host)
        assertEquals(8080, request.url.port)
        assertEquals("/player_api.php", request.url.encodedPath)
        assertEquals("get_series_info", request.url.queryParameter("action"))
        assertEquals("789", request.url.queryParameter("series_id"))
        assertEquals("fixture-user", request.url.queryParameter("username"))
        assertEquals("fixture-pass", request.url.queryParameter("password")) // pragma: allowlist secret
    }

    @Test
    fun parsesSeriesSeasonsGroupedEpisodesAndPracticalTypeVariants() = runTest {
        val body = """
            {
              "info":{
                "name":"Series Title",
                "plot":"Series plot",
                "cover":"https://art.invalid/cover.jpg",
                "genre":"Drama",
                "rating":9
              },
              "seasons":[
                {"season_number":1,"name":"Season One","episode_count":"2"},
                {"season_number":"2","name":"Season Two","episode_count":1}
              ],
              "episodes":{
                "1":[
                  {
                    "id":"1001",
                    "episode_num":1,
                    "title":"Pilot",
                    "container_extension":"mkv",
                    "info":{
                      "movie_image":"https://art.invalid/e1.jpg",
                      "duration":"00:45:00",
                      "plot":"First"
                    }
                  },
                  {
                    "id":1002,
                    "episode_num":"2",
                    "title":"Second",
                    "container_extension":"mp4",
                    "info":{}
                  }
                ],
                "2":[
                  {
                    "episode_id":"2001",
                    "episode_number":1,
                    "name":"Third",
                    "container_extension":"mkv"
                  }
                ]
              }
            }
        """.trimIndent()
        val result = XtreamCatalogClient(
            activeStore(),
            testClient(RecordingInterceptor(body)),
        ).seriesInfo("789")
        val detail = successValue<SeriesDetail>(result)

        assertEquals("Series Title", detail.name)
        assertEquals(listOf("1", "2"), detail.seasons.map { it.seasonId })
        assertEquals(listOf("1001", "1002", "2001"), detail.episodes.map { it.episodeId })
        assertEquals(listOf("1", "2", "1"), detail.episodes.map { it.episodeNumber })
        assertEquals("00:45:00", detail.episodes.first().duration)
        assertEquals("mkv", detail.episodes.last().containerExtension)
    }

    @Test
    fun parsesArrayStyleEpisodesAndDerivesProviderSeasonKeysWithoutInventingSeasonOne() = runTest {
        val body = """
            {
              "info":{},
              "seasons":[],
              "episodes":[
                {
                  "id":3001,
                  "episode_num":4,
                  "title":"Episode",
                  "season":"7",
                  "container_extension":"mp4"
                }
              ]
            }
        """.trimIndent()
        val result = XtreamCatalogClient(
            activeStore(),
            testClient(RecordingInterceptor(body)),
        ).seriesInfo("789")
        val detail = successValue<SeriesDetail>(result)

        assertEquals(listOf("7"), detail.seasons.map { it.seasonId })
        assertEquals("7", detail.episodes.single().seasonId)
        assertEquals("3001", detail.episodes.single().episodeId)
    }

    @Test
    fun seriesInfoSkipsUnusableEpisodeIdentityWithoutCrashing() = runTest {
        val body = """
            {
              "info":{"name":"Show"},
              "seasons":[{"season_number":"1"}],
              "episodes":{
                "1":[
                  {"id":"../bad","episode_num":"1","container_extension":"mp4"},
                  {"id":"1001","episode_num":"2","container_extension":"mp4"}
                ]
              }
            }
        """.trimIndent()
        val result = XtreamCatalogClient(
            activeStore(),
            testClient(RecordingInterceptor(body)),
        ).seriesInfo("789")
        val detail = successValue<SeriesDetail>(result)

        assertEquals(listOf("1001"), detail.episodes.map { it.episodeId })
    }

    @Test
    fun seriesInfoToleratesMissingOptionalMetadataAndEmptyEpisodeGroups() = runTest {
        val body = """
            {
              "info":{"name":"Sparse"},
              "seasons":[{"season_number":"4"}],
              "episodes":{"4":[]}
            }
        """.trimIndent()
        val result = XtreamCatalogClient(
            activeStore(),
            testClient(RecordingInterceptor(body)),
        ).seriesInfo("44")
        val detail = successValue<SeriesDetail>(result)

        assertEquals("Sparse", detail.name)
        assertEquals(listOf("4"), detail.seasons.map { it.seasonId })
        assertTrue(detail.episodes.isEmpty())
    }

    @Test
    fun seriesInfoRejectsMalformedTopLevelAndInvalidSeriesIdentity() = runTest {
        val malformed = XtreamCatalogClient(
            activeStore(),
            testClient(RecordingInterceptor("[]")),
        ).seriesInfo("789")
        assertEquals(CatalogError.InvalidResponse, failureValue(malformed))

        val interceptor = RecordingInterceptor("""{"info":{},"seasons":[],"episodes":{}}""")
        val invalid = XtreamCatalogClient(
            activeStore(),
            testClient(interceptor),
        ).seriesInfo("../789")
        assertEquals(CatalogError.InvalidMetadata, failureValue(invalid))
        assertEquals(null, interceptor.request)
    }

    @Test
    fun seriesInfoHttpNetworkAndMissingSessionFailuresStayCredentialFree() = runTest {
        val http = XtreamCatalogClient(
            activeStore(),
            testClient(RecordingInterceptor("{}", code = 503)),
        ).seriesInfo("789")
        assertEquals(CatalogError.HttpFailure, failureValue(http))

        val timeout = XtreamCatalogClient(
            activeStore(),
            testClient(
                RecordingInterceptor(
                    "{}",
                    failure = SocketTimeoutException("fixture timeout"),
                ),
            ),
        ).seriesInfo("789")
        assertEquals(CatalogError.NetworkFailure, failureValue(timeout))

        val interceptor = RecordingInterceptor("{}")
        val missing = XtreamCatalogClient(
            RuntimeProviderSessionStore(),
            testClient(interceptor),
        ).seriesInfo("789")
        assertEquals(CatalogError.MissingSession, failureValue(missing))
        assertEquals(null, interceptor.request)

        val text = http.toString() + timeout.toString() + missing.toString()
        assertFalse(text.contains("fixture-user"))
        assertFalse(text.contains("fixture-pass")) // pragma: allowlist secret
        assertFalse(text.contains("catalog.invalid"))
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
