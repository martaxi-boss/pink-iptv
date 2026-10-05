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
    fun attach(context: Context, webView: WebView) {
        check(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))
        val app = context.applicationContext
        val vault = PinkVault(app)
        WebViewCompat.addWebMessageListener(webView, "PinkNative",
            setOf("http://tauri.localhost", "https://tauri.localhost")) { _, message, sourceOrigin, mainFrame, reply ->
            if (!mainFrame || sourceOrigin.host != "tauri.localhost" ||
                sourceOrigin.scheme !in setOf("http", "https")) return@addWebMessageListener
            val request = try {
                val data = checkNotNull(message.data)
                check(data.length <= 200000)
                JSONObject(data).also { check(Regex("[0-9]{1,16}").matches(it.getString("id"))) }
            } catch (_: Exception) { return@addWebMessageListener }
            val id = request.getString("id")
            workers.execute {
                val response = JSONObject().put("id", id)
                try {
                    val payload = request.getJSONObject("payload")
                    val result: Any = when (request.getString("operation")) {
                        "ready" -> PinkVpnRuntime.isReady()
                        "resolve" -> PinkVpnRuntime.get(app).resolve(
                            payload.getString("username"), payload.getString("password")).toString()
                        "vaultRead" -> vault.read()
                        "vaultWrite" -> vault.write(payload.getString("value"))
                        else -> throw IllegalArgumentException("Unavailable operation")
                    }
                    response.put("ok", true).put("result", result)
                } catch (_: Exception) { response.put("ok", false) }
                main.post { try { reply.postMessage(response.toString()) } catch (_: Exception) { } }
            }
        }
    }
}
