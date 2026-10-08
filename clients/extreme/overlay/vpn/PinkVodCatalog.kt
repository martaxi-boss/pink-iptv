package com.pinkiptv.extreme

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.Base64
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
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

// Only fixed phase codes cross the bridge; never exception messages or provider data.
internal class PinkCatalogFailure(val phase: String) : IllegalStateException(phase)

/** Bounded disk transfer; no full native String or JSONArray for large catalogs. */
internal object PinkCatalogTransfer {
    private val deadlines = Executors.newSingleThreadScheduledExecutor { job ->
        Thread(job, "pink-catalog-deadline").apply { isDaemon = true }
    }
    private fun watch(seconds: Long, stop: () -> Unit): () -> Unit {
        val task = deadlines.schedule(Runnable { stop() }, seconds, TimeUnit.SECONDS)
        return { task.cancel(false); Unit }
    }
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
    // Restore the historical native client's default idle budget (20s).
    // Socket idle and total body duration are different bounds: progress resets
    // only idle, never the total deadline. Live's transport is unchanged.
    fun download(request: HttpURLConnection, file: File, policy: Policy,
                 now: () -> Long = System::nanoTime,
                 watchdog: (Long, () -> Unit) -> (() -> Unit) = ::watch,
                 ready: () -> Boolean) {
        request.connectTimeout = 15000
        request.readTimeout = 20000
        request.instanceFollowRedirects = false
        request.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        val expired = AtomicBoolean(false)
        // Hard wall limit includes headers and a blocked socket. This timer is
        // transfer protection, never a timer used to advance the catalog queue.
        val cancelDeadline = watchdog(policy.seconds + 35) {
            expired.set(true)
            request.disconnect()
        }
        fun admitted() {
            if (expired.get()) throw PinkCatalogFailure("TOTAL_DEADLINE")
            if (!ready()) throw PinkCatalogFailure("VPN_LOST")
        }
        var phase = "CONNECT"
        try {
            admitted()
            val status = request.responseCode
            admitted()
            if (status != 200) throw PinkCatalogFailure("HTTP_STATUS")
            if (request.contentLengthLong > policy.maxBytes) throw PinkCatalogFailure("MAX_BYTES")
            // Like Live, the body deadline starts after the response headers.
            val deadline = now() + TimeUnit.SECONDS.toNanos(policy.seconds)
            val idleBudget = TimeUnit.SECONDS.toNanos(20)
            var lastProgress = now()
            var total = 0L
            phase = "READ"
            request.inputStream.use { input ->
                phase = "STAGE"
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        admitted()
                        val time = now()
                        if (time >= deadline) throw PinkCatalogFailure("TOTAL_DEADLINE")
                        val idleRemaining = idleBudget - (time - lastProgress)
                        if (idleRemaining <= 0) throw PinkCatalogFailure("READ_IDLE")
                        request.readTimeout = maxOf(1, TimeUnit.NANOSECONDS.toMillis(minOf(idleRemaining, deadline - time)).toInt())
                        phase = "READ"
                        val count = try { input.read(buffer) }
                        catch (_: SocketTimeoutException) {
                            admitted()
                            throw PinkCatalogFailure(if (now() >= deadline) "TOTAL_DEADLINE" else "READ_IDLE")
                        }
                        admitted()
                        if (now() >= deadline) throw PinkCatalogFailure("TOTAL_DEADLINE")
                        if (now() - lastProgress >= idleBudget) throw PinkCatalogFailure("READ_IDLE")
                        if (count < 0) break
                        if (count == 0) continue
                        lastProgress = now()
                        total += count
                        if (total > policy.maxBytes) throw PinkCatalogFailure("MAX_BYTES")
                        phase = "STAGE"
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (total == 0L) throw PinkCatalogFailure("STAGE")
            // Actual EOF size is the bridge integrity bound. Provider length is
            // not a JSON validation rule; the Worker validates the whole body.
            // This also admits UTF-8 BOM exactly as the streaming decoder does.
        } catch (error: Exception) {
            file.delete()
            if (error is PinkCatalogFailure) throw error
            throw PinkCatalogFailure(if (expired.get()) "TOTAL_DEADLINE" else if (!ready()) "VPN_LOST" else phase)
        } finally { cancelDeadline(); request.disconnect() }
    }
}
