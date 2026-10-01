package com.pinkiptv.app.network

import com.pinkiptv.app.model.CatalogCategory
import com.pinkiptv.app.model.CatalogError
import com.pinkiptv.app.model.CatalogRepository
import com.pinkiptv.app.model.CatalogResult
import com.pinkiptv.app.model.LiveStream
import com.pinkiptv.app.model.RuntimeProviderSessionStore
import com.pinkiptv.app.model.SeriesDetail
import com.pinkiptv.app.model.SeriesEpisode
import com.pinkiptv.app.model.SeriesItem
import com.pinkiptv.app.model.SeriesSeason
import com.pinkiptv.app.model.VodItem
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

const val PINK_XTREAM_USER_AGENT = "PINK-IPTV/0.1"

fun buildXtreamHttpClient(): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

class XtreamCatalogClient(
    private val sessionStore: RuntimeProviderSessionStore,
    private val client: OkHttpClient,
) : CatalogRepository {
    init {
        require(!client.followRedirects && !client.followSslRedirects) {
            "Xtream redirects must remain disabled"
        }
    }

    override suspend fun liveCategories(): CatalogResult<List<CatalogCategory>> =
        requestList("get_live_categories", XtreamJsonParser::categories)

    override suspend fun liveStreams(): CatalogResult<List<LiveStream>> =
        requestList("get_live_streams", XtreamJsonParser::liveStreams)

    override suspend fun vodCategories(): CatalogResult<List<CatalogCategory>> =
        requestList("get_vod_categories", XtreamJsonParser::categories)

    override suspend fun vodStreams(): CatalogResult<List<VodItem>> =
        requestList("get_vod_streams", XtreamJsonParser::vodItems)

    override suspend fun seriesCategories(): CatalogResult<List<CatalogCategory>> =
        requestList("get_series_categories", XtreamJsonParser::categories)

    override suspend fun series(): CatalogResult<List<SeriesItem>> =
        requestList("get_series", XtreamJsonParser::seriesItems)

    override suspend fun seriesInfo(seriesId: String): CatalogResult<SeriesDetail> {
        if (!PROVIDER_ID.matches(seriesId)) {
            return CatalogResult.Failure(CatalogError.InvalidMetadata)
        }

        return requestValue(
            action = "get_series_info",
            extraQuery = mapOf("series_id" to seriesId),
        ) { body ->
            XtreamJsonParser.seriesDetail(seriesId, body)
        }
    }

    private suspend fun <T> requestList(
        action: String,
        parser: (String) -> List<T>,
    ): CatalogResult<List<T>> =
        requestValue(action = action, extraQuery = emptyMap(), parser = parser)

    private suspend fun <T> requestValue(
        action: String,
        extraQuery: Map<String, String>,
        parser: (String) -> T,
    ): CatalogResult<T> = withContext(Dispatchers.IO) {
        val session = sessionStore.current()
            ?: return@withContext CatalogResult.Failure(CatalogError.MissingSession)

        val builder = session.origin.newBuilder()
            .addPathSegment("player_api.php")
            .addQueryParameter("username", session.username)
            .addQueryParameter("password", session.password)
            .addQueryParameter("action", action)
        extraQuery.forEach { (name, value) ->
            builder.addQueryParameter(name, value)
        }

        val request = Request.Builder()
            .url(builder.build())
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", PINK_XTREAM_USER_AGENT)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext CatalogResult.Failure(CatalogError.HttpFailure)
                }

                try {
                    CatalogResult.Success(parser(response.body.string()))
                } catch (_: SerializationException) {
                    CatalogResult.Failure(CatalogError.InvalidResponse)
                } catch (_: IllegalArgumentException) {
                    CatalogResult.Failure(CatalogError.InvalidResponse)
                }
            }
        } catch (_: SocketTimeoutException) {
            CatalogResult.Failure(CatalogError.NetworkFailure)
        } catch (_: IOException) {
            CatalogResult.Failure(CatalogError.NetworkFailure)
        }
    }

    private companion object {
        val PROVIDER_ID = Regex("^[A-Za-z0-9_-]{1,64}$")
    }
}

internal object XtreamJsonParser {
    private val providerMediaId = Regex("^[A-Za-z0-9_-]{1,64}$")
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun categories(body: String): List<CatalogCategory> =
        parseArray(body) { item ->
            val id = item.text("category_id") ?: return@parseArray null
            val name = item.text("category_name") ?: return@parseArray null
            CatalogCategory(id = id, name = name)
        }

    fun liveStreams(body: String): List<LiveStream> =
        parseArray(body) { item ->
            val id = item.text("stream_id") ?: return@parseArray null
            val name = item.text("name") ?: return@parseArray null
            LiveStream(
                streamId = id,
                name = name,
                categoryId = item.text("category_id"),
                artworkUrl = item.text("stream_icon"),
                streamType = item.text("stream_type"),
            )
        }

    fun vodItems(body: String): List<VodItem> =
        parseArray(body) { item ->
            val id = item.text("stream_id") ?: return@parseArray null
            val name = item.text("name") ?: return@parseArray null
            VodItem(
                streamId = id,
                name = name,
                categoryId = item.text("category_id"),
                artworkUrl = item.text("stream_icon"),
                containerExtension = item.rawText("container_extension"),
            )
        }

    fun seriesItems(body: String): List<SeriesItem> =
        parseArray(body) { item ->
            val id = item.providerId("series_id") ?: return@parseArray null
            val name = item.text("name") ?: return@parseArray null
            SeriesItem(
                seriesId = id,
                name = name,
                categoryId = item.text("category_id"),
                artworkUrl = item.text("cover"),
            )
        }

    fun seriesDetail(seriesId: String, body: String): SeriesDetail {
        val root = json.parseToJsonElement(body) as? JsonObject
            ?: throw SerializationException("Unexpected Xtream series response")
        if (
            !root.containsKey("info") &&
            !root.containsKey("seasons") &&
            !root.containsKey("episodes")
        ) {
            throw SerializationException("Missing Xtream series fields")
        }

        val info = root["info"] as? JsonObject
        val episodes = parseEpisodes(root["episodes"])
        val declaredSeasons = parseSeasons(root["seasons"])
        val known = declaredSeasons.map { it.seasonId }.toSet()
        val derivedSeasons = episodes
            .map { it.seasonId }
            .distinct()
            .filterNot(known::contains)
            .map { seasonId ->
                SeriesSeason(
                    seasonId = seasonId,
                    displayName = "Temporada " + seasonId,
                    episodeCount = episodes.count { it.seasonId == seasonId },
                    artworkUrl = null,
                )
            }

        return SeriesDetail(
            seriesId = seriesId,
            name = info?.text("name"),
            plot = info?.text("plot"),
            artworkUrl = info?.text("cover"),
            genre = info?.text("genre"),
            rating = info?.text("rating"),
            seasons = declaredSeasons + derivedSeasons,
            episodes = episodes,
        )
    }

    private fun parseSeasons(element: JsonElement?): List<SeriesSeason> {
        if (element == null || element is JsonNull) return emptyList()
        val array = element as? JsonArray
            ?: throw SerializationException("Unexpected seasons shape")
        return array.mapNotNull { seasonElement ->
            val item = seasonElement as? JsonObject ?: return@mapNotNull null
            val seasonId = item.text("season_number")
                ?: item.text("id")
                ?: return@mapNotNull null
            SeriesSeason(
                seasonId = seasonId,
                displayName = item.text("name") ?: "Temporada " + seasonId,
                episodeCount = item.text("episode_count")?.toIntOrNull(),
                artworkUrl = item.text("cover") ?: item.text("cover_big"),
            )
        }
    }

    private fun parseEpisodes(element: JsonElement?): List<SeriesEpisode> {
        if (element == null || element is JsonNull) return emptyList()
        return when (element) {
            is JsonObject -> element.entries.flatMap { (seasonKey, group) ->
                val array = group as? JsonArray
                    ?: throw SerializationException("Unexpected episode group")
                array.mapNotNull { episode ->
                    parseEpisode(episode as? JsonObject, seasonKey)
                }
            }
            is JsonArray -> element.mapNotNull { episode ->
                parseEpisode(episode as? JsonObject, null)
            }
            else -> throw SerializationException("Unexpected episodes shape")
        }
    }

    private fun parseEpisode(
        item: JsonObject?,
        groupedSeasonId: String?,
    ): SeriesEpisode? {
        item ?: return null
        val episodeId = item.providerId("id")
            ?: item.providerId("episode_id")
            ?: return null
        val seasonId = groupedSeasonId
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it != "null" }
            ?: item.text("season")
            ?: item.text("season_number")
            ?: return null
        val episodeNumber = item.text("episode_num")
            ?: item.text("episode_number")
        val metadata = item["info"] as? JsonObject
        val title = item.text("title")
            ?: item.text("name")
            ?: episodeNumber?.let { "Episódio " + it }
            ?: "Episódio"

        return SeriesEpisode(
            episodeId = episodeId,
            episodeNumber = episodeNumber,
            title = title,
            seasonId = seasonId,
            containerExtension = item.rawText("container_extension"),
            artworkUrl = metadata?.text("movie_image")
                ?: item.text("movie_image"),
            duration = metadata?.text("duration")
                ?: item.text("duration"),
            plot = metadata?.text("plot")
                ?: item.text("plot"),
        )
    }

    private fun <T> parseArray(
        body: String,
        mapper: (JsonObject) -> T?,
    ): List<T> {
        val root = json.parseToJsonElement(body)
        val array = root as? JsonArray
            ?: throw SerializationException("Unexpected Xtream response shape")
        return array.mapNotNull { element ->
            (element as? JsonObject)?.let(mapper)
        }
    }

    private fun JsonObject.providerId(name: String): String? {
        val value = rawText(name) ?: return null
        return value.takeIf(providerMediaId::matches)
    }

    private fun JsonObject.rawText(name: String): String? {
        val primitive = this[name] as? JsonPrimitive ?: return null
        return primitive.contentOrNull
            ?.takeIf { value -> value.isNotEmpty() && value != "null" }
    }

    private fun JsonObject.text(name: String): String? =
        rawText(name)
            ?.trim()
            ?.takeIf { value -> value.isNotEmpty() && value != "null" }
}
