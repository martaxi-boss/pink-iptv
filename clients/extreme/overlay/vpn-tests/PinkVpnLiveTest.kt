package com.pinkiptv.extreme

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Invoked only by the bounded operational proof with transient private fixture input. */
@androidx.media3.common.util.UnstableApi
class PinkVpnLiveTest {
    private val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private fun encoded(value: String): String = URLEncoder.encode(value,"UTF-8").replace("+","%20")

    private fun catalog(url: String): JSONArray {
        val request = URL(url).openConnection() as HttpURLConnection
        request.instanceFollowRedirects = false
        request.connectTimeout = 15000
        request.readTimeout = 15000
        request.setRequestProperty("User-Agent",ua)
        try {
            check(request.responseCode == 200)
            val output = java.io.ByteArrayOutputStream()
            request.inputStream.use { source ->
                val buffer = ByteArray(8192)
                while (output.size() <= 32*1024*1024) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    output.write(buffer,0,count)
                }
            }
            check(output.size() <= 32*1024*1024)
            return JSONArray(output.toString("UTF-8"))
        } finally { request.disconnect() }
    }

    @Test fun authenticatedNativeLoginCatalogAndDecodedLiveAudioVideoUseWireGuard() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixtureFile = context.filesDir.resolve("pink055-test-account.json")
        check(fixtureFile.isFile)
        val fixture = JSONObject(fixtureFile.readText())
        check(fixtureFile.delete())
        var enrolled = false
        try {
            ActivityScenario.launch(MainActivity::class.java).use { activity ->
                if (VpnService.prepare(context) != null) {
                    val device = UiDevice.getInstance(instrumentation)
                    check(device.wait(Until.hasObject(By.pkg("com.android.vpndialogs")),15000))
                    val accept = device.wait(Until.findObject(By.res("android","button1")),10000)
                    check(accept != null)
                    accept.click()
                }
                val runtime = PinkVpnRuntime.get(context)
                val deadline = System.currentTimeMillis()+20000
                while (!runtime.hasCapturedRouteForTests() && System.currentTimeMillis()<deadline) Thread.sleep(100)
                check(runtime.hasCapturedRouteForTests())
                val publicIdentity = runBlocking { SecureVpnIdentityStore(AndroidKeystoreVpnIdentityCipher(),
                    DataStoreVpnIdentityPersistence(context)).loadIdentity() as VpnIdentityResult.Available }
                context.filesDir.resolve("pink055-peer-public.txt").writeText(publicIdentity.identity.publicKey)
                val reply = runtime.resolve(fixture.getString("username"),fixture.getString("password"))
                check(reply.getString("code") == "SUCCESS")
                enrolled = true
                check(PinkVpnRuntime.isReady())
                val origin = reply.getString("xtream_base_url").trimEnd('/')
                check(origin == fixture.getString("expected_origin").trimEnd('/'))
                val identity = runBlocking { SecureVpnIdentityStore(AndroidKeystoreVpnIdentityCipher(),
                    DataStoreVpnIdentityPersistence(context)).loadIdentity() }
                check(identity is VpnIdentityResult.Available)
                val query = "username="+encoded(fixture.getString("username"))+"&password="+encoded(fixture.getString("password"))
                val categories = catalog("$origin/player_api.php?$query&action=get_live_categories")
                val streams = catalog("$origin/player_api.php?$query&action=get_live_streams")
                check(categories.length()>0 && streams.length()>0)
                println("AUTHORITATIVE_USERNAME_PASSWORD_LOGIN_AND_CATALOG_VIA_WIREGUARD=PASS")
                var decoded = false
                for (index in 0 until minOf(3,streams.length())) {
                    val stream = streams.getJSONObject(index)
                    val id = stream.getString("stream_id")
                    check(Regex("[0-9]+").matches(id))
                    for (extension in listOf("m3u8","ts")) {
                        val url = "$origin/live/"+encoded(fixture.getString("username"))+"/"+
                            encoded(fixture.getString("password"))+"/$id.$extension"
                        NativePlayerPayload.setChannels(JSONArray().put(JSONObject().put("id",id)
                            .put("name","Teste PINK").put("streamUrl",url).put("ua",ua)).toString())
                        val intent = Intent(context,VideoActivity::class.java)
                            .putExtra(VideoActivity.EXTRA_MODE,"live")
                            .putExtra(VideoActivity.EXTRA_INITIAL_CHANNEL_ID,id)
                            .putExtra(VideoActivity.EXTRA_TITLE,"Teste PINK")
                            .putExtra(VideoActivity.EXTRA_UA,ua)
                        ActivityScenario.launch<VideoActivity>(intent).use { video ->
                            val deadline = System.currentTimeMillis()+25000
                            var failed = false
                            while (!decoded && !failed && System.currentTimeMillis()<deadline) {
                                video.onActivity { host ->
                                    val field = VideoActivity::class.java.getDeclaredField("exoPlayer")
                                    field.isAccessible = true
                                    val player = field.get(host) as? ExoPlayer
                                    failed = player?.playerError != null
                                    decoded = player?.isPlaying == true && player.currentPosition>1000 &&
                                        (player.videoDecoderCounters?.renderedOutputBufferCount ?: 0)>0 &&
                                        (player.audioDecoderCounters?.renderedOutputBufferCount ?: 0)>0
                                }
                                Thread.sleep(250)
                            }
                        }
                        if (decoded) break
                    }
                    if (decoded) break
                }
                check(decoded)
                check(PinkVpnRuntime.isReady())
                activity.recreate()
                instrumentation.waitForIdleSync()
                check(VpnService.prepare(context) == null && PinkVpnRuntime.isReady())
                val reloaded = runBlocking { SecureVpnIdentityStore(AndroidKeystoreVpnIdentityCipher(),
                    DataStoreVpnIdentityPersistence(context)).loadIdentity() }
                check(identity == reloaded)
                println("NATIVE_LIVE_AUDIO_VIDEO_DECODE_AND_SAME_INSTALLATION_RECREATION=PASS")
            }
        } catch (_: Throwable) {
            // Provider exceptions can contain credential-bearing URLs: never chain them.
            throw AssertionError("Protected real Android flow unavailable")
        } finally {
            if (enrolled) {
                try {
                    val prefs = context.getSharedPreferences("pink_vpn_grant_v1",0)
                    val saved = JSONObject(AndroidKeystoreVpnIdentityCipher().decrypt(VpnEncryptedPayload(
                        checkNotNull(prefs.getString("iv",null)),checkNotNull(prefs.getString("ciphertext",null)))))
                    val identity = runBlocking { SecureVpnIdentityStore(AndroidKeystoreVpnIdentityCipher(),
                        DataStoreVpnIdentityPersistence(context)).loadIdentity() as VpnIdentityResult.Available }
                    val cm = context.getSystemService(ConnectivityManager::class.java)
                    val physical = cm.allNetworks.first { cm.getNetworkCapabilities(it)
                        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) == true }
                    val request = physical.openConnection(URL("https://pink-iptv.duckdns.org/v1/vpn/revoke")) as HttpURLConnection
                    request.requestMethod = "POST"
                    request.doOutput = true
                    request.connectTimeout = 10000
                    request.readTimeout = 10000
                    request.setRequestProperty("Content-Type","application/json")
                    try {
                        request.outputStream.use { it.write(JSONObject().put("public_key",identity.identity.publicKey)
                            .put("device_token",saved.getString("device_token")).toString().toByteArray()) }
                        check(request.responseCode == 204)
                    } finally { request.disconnect() }
                    check(prefs.edit().clear().commit())
                    println("TEMPORARY_INSTALLATION_AUTHORIZATION_REVOKED=PASS")
                } catch (_: Throwable) { throw AssertionError("Temporary peer cleanup requires recovery") }
            }
        }
    }
}
