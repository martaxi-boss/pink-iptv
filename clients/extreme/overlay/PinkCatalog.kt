package com.pinkiptv.extreme

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/** Live TV and small VOD categories on the same admitted, VPN-owned Network. */
object PinkCatalog {
    // Full VOD/Series item lists remain on the separate bounded disk/chunk path.
    private val smallCategories = setOf("get_vod_categories", "get_series_categories")
    private val fixedActions = smallCategories + setOf("get_live_categories", "get_live_streams")
    internal fun maxResponseBytes(action: String): Int {
        require(action in fixedActions)
        return if (action in smallCategories) 8 * 1024 * 1024 else 32 * 1024 * 1024
    }
    internal fun requestUrl(origin: String, username: String, password: String, action: String): URL {
        maxResponseBytes(action)
        val base = URI(origin)
        require(base.scheme in setOf("http", "https") && !base.host.isNullOrBlank())
        require(base.rawUserInfo == null && base.rawQuery == null && base.rawFragment == null)
        require(base.path.isNullOrEmpty() || base.path == "/")
        require(username.isNotBlank() && password.isNotBlank())
        fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        return URL(origin.trimEnd('/') + "/player_api.php?username=" + encode(username) +
            "&password=" + encode(password) + "&action=" + action)
    }

    fun read(vault: PinkVault, runtime: PinkVpnRuntime, action: String, entryId: String): String {
        check(PinkVpnRuntime.isReady())
        val account = JSONObject(vault.readValidated())
        check(entryId == account.getString("selectedId"))
        val entries = account.getJSONArray("entries")
        val entry = (0 until entries.length()).map { entries.getJSONObject(it) }
            .single { it.getString("_id") == entryId }
        check(entry.getString("type") == "xtream")
        val url = requestUrl(entry.getString("serverUrl"), entry.getString("username"),
            entry.getString("password"), action)
        return readHttp(runtime.openProtectedConnection(url), action) { PinkVpnRuntime.isReady() }
    }

    /** Exactly Live's proven HTTP transport; native caller supplies only the owned VPN Network. */
    internal fun readHttp(request: HttpURLConnection, action: String, ready: () -> Boolean): String {
        val limit = maxResponseBytes(action)
        request.connectTimeout = 15000
        request.readTimeout = 15000
        request.instanceFollowRedirects = false
        request.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        try {
            check(ready())
            check(request.responseCode == 200)
            val bytes = java.io.ByteArrayOutputStream()
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30)
            request.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    check(System.nanoTime() < deadline && ready())
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(bytes.size() + count <= limit)
                    bytes.write(buffer, 0, count)
                }
            }
            val text = bytes.toString("UTF-8")
            JSONArray(text) // Reject non-catalog responses before returning to the page.
            check(ready())
            return text
        } finally { request.disconnect() }
    }
}
