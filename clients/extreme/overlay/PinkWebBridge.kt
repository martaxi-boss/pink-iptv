package com.pinkiptv.extreme

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.concurrent.Executors
import org.json.JSONObject

/** The account vault and protected login are exposed only to the local main document. */
object PinkWebBridge {
    private val main = Handler(Looper.getMainLooper())
    private val workers = Executors.newSingleThreadExecutor()
    private val catalogWorkers = Executors.newSingleThreadExecutor()
    private val vodCatalogWorkers = Executors.newSingleThreadExecutor()
    private var vodOwner: PinkVodCatalog? = null
    @Volatile private var livePhase = "absent"
    @Volatile private var rendererPulse = 0L
    internal fun rendererPulseForTests(): Long = rendererPulse
    internal fun livePhaseForTests(): String = livePhase
    private val livePhases = setOf("boot", "account", "preferences", "categories", "channels",
        "response", "reading",
        "body", "parsing", "painting", "painted", "failed")
    fun attach(context: Context, webView: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))
            throw IllegalStateException("Connection unavailable")
        livePhase = "absent"
        rendererPulse = 0L
        val app = context.applicationContext
        val vault = PinkVault(app)
        val vodCatalog = PinkVodCatalog(app, vault, PinkVpnRuntime.get(app))
        vodOwner?.dispose()
        vodOwner = vodCatalog
        WebViewCompat.addWebMessageListener(webView, "PinkNative",
            setOf("http://tauri.localhost", "https://tauri.localhost")) { _, message, sourceOrigin, mainFrame, reply ->
            if (!mainFrame || sourceOrigin.host != "tauri.localhost" ||
                sourceOrigin.scheme !in setOf("http", "https")) return@addWebMessageListener
            val request = try {
                val data = checkNotNull(message.data)
                check(data.length <= 200000)
                JSONObject(data).also { check(Regex("[0-9]{1,16}").matches(it.getString("id"))) }
            } catch (_: Exception) { return@addWebMessageListener }
            if (request.optString("operation") == "livePulse") {
                rendererPulse++
                return@addWebMessageListener
            }
            if (request.optString("operation") == "livePhase") {
                val value = request.optJSONObject("payload")?.optString("phase")
                livePhase = if (value in livePhases) value!! else "absent"
                return@addWebMessageListener
            }
            val id = request.getString("id")
            // A bounded catalog read must not queue readiness/vault requests
            // behind network I/O or block account/control-plane operations.
            val executor = when (request.optString("operation")) {
                "liveCatalog" -> catalogWorkers
                "vodCatalog", "vodCatalogChunk", "vodCatalogClose" -> vodCatalogWorkers
                else -> workers
            }
            executor.execute {
                val response = JSONObject().put("id", id)
                try {
                    val payload = request.getJSONObject("payload")
                    val result: Any = when (request.getString("operation")) {
                        "vodCatalog" -> vodCatalog.open(payload.getString("action"), payload.getString("entryId"))
                        "vodCatalogChunk" -> vodCatalog.chunk(payload.getString("token"), payload.getString("entryId"))
                        "vodCatalogClose" -> vodCatalog.close(payload.getString("token"))
                        "ready" -> PinkVpnRuntime.isReady()
                        "liveCatalog" -> PinkCatalog.read(vault, PinkVpnRuntime.get(app),
                            payload.getString("action"), payload.getString("entryId"))
                        "resolve" -> PinkVpnRuntime.get(app).resolve(
                            payload.getString("username"), payload.getString("password")).toString()
                        "vaultRead" -> vault.read()
                        "vaultReadValidated" -> vault.readValidated()
                        "vaultMarkValidated" -> vault.markValidated(payload.getString("value"))
                        "vaultWrite" -> vault.write(payload.getString("value"))
                        else -> throw IllegalArgumentException("Unavailable operation")
                    }
                    response.put("ok", true).put("result", result)
                } catch (error: Exception) {
                    response.put("ok", false)
                    if (request.optString("operation") in setOf("vodCatalog", "vodCatalogChunk", "vodCatalogClose")) {
                        response.put("code", if (error is PinkCatalogFailure) error.phase else "STAGE")
                        if (error is PinkCatalogFailure) {
                            error.bytes?.let { response.put("bytes", it) }
                            error.elapsedMs?.let { response.put("elapsedMs", it) }
                            error.httpStatus?.let { response.put("httpStatus", it) }
                        }
                    }
                }
                val encodedResponse = response.toString()
                main.post {
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                        try { reply.postMessage(encodedResponse) } catch (_: Exception) { }
                    }
                }
            }
        }
    }
}
