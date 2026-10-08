package com.pinkiptv.extreme

import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*

/** Test-only loopback HTTP. No provider, vault, VPN-admission override or remote I/O.
 * Uses the *production Live native HTTP reader* for VOD categories and the
 * existing production disk/chunk reader for full VOD/Series. VPN authority
 * and bound Network acquisition are verified separately. */
class PinkCatalogTransportChecks {
    fun verify() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        server.soTimeout = 30000
        val io = Executors.newSingleThreadExecutor()
        val stages = PinkVodStageStore()
        val requests = mutableListOf<String>()
        val body = "[" + (1..5000).joinToString(",") { """{"stream_id":$it,"series_id":$it,"name":"Português 🎬 $it"}""" } + "]"
        val bytes = body.toByteArray(Charsets.UTF_8)
        val compressed = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()
        val served = CountDownLatch(1)
        var serverFailure: Throwable? = null
        val thread = Thread {
            try {
                repeat(4) { index ->
                    server.accept().use { socket ->
                        socket.soTimeout = 10000
                        val reader = socket.getInputStream().bufferedReader()
                        while (!reader.readLine().isNullOrEmpty()) { }
                        val out = socket.getOutputStream()
                        // Real Android transparent gzip and chunked response handling.
                        val payload = if (index % 2 == 0) bytes else compressed
                        val headers = if (index % 2 == 0) "Content-Length: ${payload.size}\r\n" else "Transfer-Encoding: chunked\r\nContent-Encoding: gzip\r\n"
                        out.write(("HTTP/1.1 200 OK\r\nConnection: close\r\nContent-Type: application/json\r\n" + headers + "\r\n").toByteArray())
                        for (offset in payload.indices step 4093) {
                            val count = minOf(4093, payload.size-offset)
                            if (index % 2 != 0) out.write((count.toString(16)+"\r\n").toByteArray())
                            out.write(payload, offset, count)
                            if (index % 2 != 0) out.write("\r\n".toByteArray())
                            out.flush()
                        }
                        if (index % 2 != 0) out.write("0\r\n\r\n".toByteArray())
                        out.flush()
                    }
                }
            } catch (failure: Throwable) { serverFailure = failure }
            finally { served.countDown() }
        }.apply { start() }
        lateinit var view: WebView
        val source = instrumentation.context.assets.open("pink-catalog-transport.js").bufferedReader().use { it.readText() }
        instrumentation.runOnMainSync {
            view = WebView(context)
            view.settings.javaScriptEnabled = true
            WebViewCompat.addWebMessageListener(view, "PinkNative", setOf("https://tauri.localhost")) { _, message, origin, mainFrame, reply ->
                if (!mainFrame || origin.host != "tauri.localhost") return@addWebMessageListener
                val request = JSONObject(message.data!!)
                io.execute {
                    val response = JSONObject().put("id", request.getString("id"))
                    try {
                        val payload = request.getJSONObject("payload")
                        val result: Any = when (request.getString("operation")) {
                            "liveCatalog" -> {
                                val action = payload.getString("action")
                                require(action in setOf("get_vod_categories", "get_series_categories"))
                                requests.add(action)
                                val connection = URL("http://127.0.0.1:${server.localPort}/fixture").openConnection() as HttpURLConnection
                                PinkCatalog.readHttp(connection, action) { true }
                            }
                            "vodCatalog" -> {
                                val action = payload.getString("action")
                                require(action in setOf("get_vod_streams", "get_series"))
                                requests.add(action)
                                val file = File.createTempFile("pink-http-fixture", ".json", context.cacheDir)
                                val connection = URL("http://127.0.0.1:${server.localPort}/fixture").openConnection() as HttpURLConnection
                                PinkCatalogTransfer.download(connection, file, PinkCatalogTransfer.policy(action)) { true }
                                stages.admit(action, "fixture", action, file)
                                JSONObject().put("token", action).put("size", file.length())
                            }
                            "vodCatalogChunk" -> stages.chunk(payload.getString("token"), "fixture")
                            "vodCatalogClose" -> {
                                stages.close(payload.getString("token"))
                            }
                            else -> error("Unexpected test operation")
                        }
                        response.put("ok", true).put("result", result)
                    } catch (failure: Exception) {
                        response.put("ok", false).put("code", if (failure is PinkCatalogFailure) failure.phase else "STAGE")
                    }
                    // This fixture WebView has no attached window: View.post() may never run.
                    // Dispatch on the main looper, matching the production bridge.
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        reply.postMessage(response.toString())
                    }
                }
            }
            view.loadDataWithBaseURL("https://tauri.localhost", """<script>$source
              window.result = null;
              (async () => {
                for (const action of ['get_vod_categories','get_vod_streams','get_series_categories','get_series']) {
                  const response = await fetchPinkVodCatalog(action, 'fixture');
                  const rows = await response.json();
                  if (rows.length !== 5000 || rows[0].name !== 'Português 🎬 1' || rows[4999].series_id !== 5000) throw Error('completeness');
                }
                window.result = {ok:true};
              })().catch(e => {window.result = {ok:false,code:e.code || 'JS'};});
            </script>""", "text/html", "UTF-8", null)
        }
        try {
            val deadline = System.currentTimeMillis()+45000
            var result: JSONObject? = null
            while (result == null && System.currentTimeMillis()<deadline) {
                val latch = CountDownLatch(1)
                instrumentation.runOnMainSync {
                    view.evaluateJavascript("JSON.stringify(window.result)") { encoded ->
                        val value = JSONTokener(encoded).nextValue()
                        if (value is String && value.startsWith("{")) result = JSONObject(value)
                        latch.countDown()
                    }
                }
                assertTrue(latch.await(3, TimeUnit.SECONDS))
                if (result == null) Thread.sleep(50)
            }
            assertNotNull("Native HTTP-stage-WebMessage fixture did not complete", result)
            assertTrue("Native HTTP-stage-WebMessage failed: " + result?.optString("code"), result!!.getBoolean("ok"))
            assertTrue(served.await(3, TimeUnit.SECONDS))
            assertNull(serverFailure)
            assertEquals(listOf("get_vod_categories","get_vod_streams","get_series_categories","get_series"), requests)
            assertEquals(0, stages.count())
            instrumentation.sendStatus(2, android.os.Bundle().apply { putString("stream", "\nPINK_ANDROID_HTTP_STAGE_WEBMESSAGE=PASS;ACTIONS=4;ROWS=20000;GZIP_CHUNKED=PASS;LIVE_PATH_CATEGORIES=PASS\n") })
        } finally {
            server.close(); thread.join(3000)
            io.shutdownNow(); io.awaitTermination(5, TimeUnit.SECONDS)
            stages.closeAll()
            instrumentation.runOnMainSync { view.destroy() }
        }
    }
}
