package com.pinkiptv.extreme

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.Base64
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/** Movies/Series only: fixed provider reads through the existing admitted VPN. */
internal class PinkVodCatalog(context: Context, private val vault: PinkVault, private val runtime: PinkVpnRuntime) {
    private val directory = File(context.cacheDir, "pink-catalog").apply { mkdirs() }
    private data class Stage(val entryId: String, val file: File, val reader: RandomAccessFile)
    private val stages = mutableMapOf<String, Stage>()
    private val lease = PinkCatalogTransfer.Lease()
    init { directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 300000 }?.forEach { it.delete() } }
    private fun selected(entryId: String): JSONObject {
        check(lease.active() && PinkVpnRuntime.isReady())
        val account = JSONObject(vault.readValidated())
        check(account.getString("selectedId") == entryId)
        val entries = account.getJSONArray("entries")
        val entry = (0 until entries.length()).map { entries.getJSONObject(it) }.single { it.getString("_id") == entryId }
        check(entry.getString("type") == "xtream")
        return entry
    }
    fun open(action: String, entryId: String): JSONObject {
        val policy = PinkCatalogTransfer.policy(action)
        val entry = selected(entryId)
        val url = requestUrl(entry.getString("serverUrl"), entry.getString("username"), entry.getString("password"), action)
        val token = UUID.randomUUID().toString()
        val file = File(directory, token)
        try {
            val request = runtime.openProtectedConnection(url)
            try {
                lease.attach(request)
                PinkCatalogTransfer.download(request, file, policy) { lease.active() && PinkVpnRuntime.isReady() }
            } finally { request.disconnect(); lease.release() }
            selected(entryId)
            synchronized(stages) {
                check(stages.size < 4)
                stages[token] = Stage(entryId, file, RandomAccessFile(file, "r"))
            }
            cleanup.schedule(Runnable { close(token) }, 300, TimeUnit.SECONDS)
            return JSONObject().put("token", token).put("size", file.length())
        } catch (error: Exception) { file.delete(); throw error }
    }
    fun chunk(token: String, entryId: String): JSONObject {
        try {
            selected(entryId)
            return synchronized(stages) {
                val stage = checkNotNull(stages[token])
                check(stage.entryId == entryId)
                val buffer = ByteArray(128 * 1024)
                val count = stage.reader.read(buffer)
                check(lease.active() && PinkVpnRuntime.isReady())
                if (count < 0) { close(token); JSONObject().put("done", true) }
                else JSONObject().put("data", Base64.getEncoder().encodeToString(buffer.copyOf(count)))
            }
        } catch (error: Exception) { close(token); throw error }
    }
    fun close(token: String): Boolean = synchronized(stages) {
        stages.remove(token)?.let { try { it.reader.close() } finally { it.file.delete() } }
        true
    }
    fun dispose() {
        lease.invalidate()
        cleanup.execute {
            lease.close()
            synchronized(stages) { stages.keys.toList().forEach { close(it) } }
        }
    }
    companion object {
        private val cleanup = Executors.newSingleThreadScheduledExecutor()
        internal fun requestUrl(origin: String, username: String, password: String, action: String): URL {
            PinkCatalogTransfer.policy(action)
            val base = URI(origin)
            require(base.scheme in setOf("http", "https") && !base.host.isNullOrBlank())
            require(base.rawUserInfo == null && base.rawQuery == null && base.rawFragment == null)
            require(base.path.isNullOrEmpty() || base.path == "/")
            require(username.isNotBlank() && password.isNotBlank())
            fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
            return URL(origin.trimEnd('/') + "/player_api.php?username=" + encode(username) + "&password=" + encode(password) + "&action=" + action)
        }
    }
}

/** Bounded disk transfer; no full native String or JSONArray for large catalogs. */
internal object PinkCatalogTransfer {
    // A new document cancels the previous owner's I/O rather than waiting for
    // its timeout. No pending transfer/token is inherited after recreation.
    class Lease {
        @Volatile private var alive = true
        private var request: HttpURLConnection? = null
        fun active() = alive
        fun invalidate() { alive = false }
        @Synchronized fun attach(value: HttpURLConnection) { check(alive); request = value }
        @Synchronized fun release() { request = null }
        @Synchronized fun close() { alive = false; request?.disconnect(); request = null }
    }
    data class Policy(val maxBytes: Long, val seconds: Long)
    fun policy(action: String): Policy = when (action) {
        "get_vod_categories", "get_series_categories" -> Policy(8L * 1024 * 1024, 30)
        "get_vod_streams", "get_series" -> Policy(256L * 1024 * 1024, 180)
        else -> throw IllegalArgumentException("Invalid catalog action")
    }
    fun download(request: HttpURLConnection, file: File, policy: Policy, ready: () -> Boolean) {
        request.connectTimeout = 15000
        request.readTimeout = 15000
        request.instanceFollowRedirects = false
        request.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(policy.seconds)
        try {
            check(ready() && request.responseCode == 200)
            check(request.contentLengthLong <= policy.maxBytes)
            var total = 0L
            var sawArray = false
            request.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        check(ready() && System.nanoTime() < deadline)
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (!sawArray) {
                            for (i in 0 until count) {
                                val byte = buffer[i].toInt() and 255
                                if (byte == 9 || byte == 10 || byte == 13 || byte == 32) continue
                                check(byte == 91 || byte == 123); sawArray = true; break
                            }
                        }
                        total += count
                        check(total <= policy.maxBytes)
                        output.write(buffer, 0, count)
                    }
                }
            }
            check(request.contentLengthLong < 0 || request.contentLengthLong == total)
            check(sawArray && ready() && System.nanoTime() < deadline)
        } catch (error: Exception) { file.delete(); throw error }
        finally { request.disconnect() }
    }
}
