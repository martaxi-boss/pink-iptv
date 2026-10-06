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


    private fun webView(host: android.view.View): android.webkit.WebView? {
        if (host is android.webkit.WebView) return host
        if (host is android.view.ViewGroup) {
            for (index in 0 until host.childCount) webView(host.getChildAt(index))?.let { return it }
        }
        return null
    }

    private var uiJsBoundary = "not_started"
    private var uiPulseAtEvaluation = 0L
    private var uiNativeRoute = "not_started"
    private var uiNativeProgress = -1
    private var uiLivePhase = "not_started"
    private fun js(activity: ActivityScenario<MainActivity>, script: String): String {
        val latch = java.util.concurrent.CountDownLatch(1)
        var result: String? = null
        uiJsBoundary = "host"
        activity.onActivity { host ->
            uiJsBoundary = "webview"
            val view = checkNotNull(webView(host.findViewById(android.R.id.content)))
            val path = try { android.net.Uri.parse(view.url).path } catch (_: Exception) { null }
            uiNativeRoute = if (path in setOf("/", "/login", "/tv", "/tv/login", "/livetv", "/tv/livetv")) path!! else "other"
            uiNativeProgress = view.progress.coerceIn(0, 100)
            uiPulseAtEvaluation = PinkWebBridge.rendererPulseForTests()
            view.evaluateJavascript(script) {
                result = it
                latch.countDown()
            }
        }
        uiJsBoundary = "callback"
        check(latch.await(10, java.util.concurrent.TimeUnit.SECONDS))
        uiJsBoundary = "decode"
        return org.json.JSONTokener(checkNotNull(result)).nextValue().toString()
    }

    private fun waitJs(activity: ActivityScenario<MainActivity>, expression: String, seconds: Int) {
        val deadline = System.currentTimeMillis() + seconds * 1000L
        while (System.currentTimeMillis() < deadline) {
            var loaded = false
            activity.onActivity { host ->
                loaded = webView(host.findViewById(android.R.id.content))?.progress == 100
            }
            if (!loaded) { Thread.sleep(250); continue }
            val raw = js(activity, """JSON.stringify({
                matched:document.readyState==='complete' && Boolean($expression),
                phase:(()=>{const value=document.documentElement?.dataset.pinkLivePhase;
                    return ['boot','account','preferences','categories','channels','response','reading','streaming','body','parsing','painting','painted','failed'].includes(value)?value:'absent'})()
            })""")
            // A document can be replaced between native readiness and evaluation.
            // WebView's null response is pending, never a successful checkpoint.
            if (raw == "null") { Thread.sleep(250); continue }
            val observation = JSONObject(raw)
            uiLivePhase = observation.getString("phase")
            if (observation.getBoolean("matched")) return
            Thread.sleep(250)
        }
        val flags = js(activity, """JSON.stringify({
            online:navigator.onLine===true,
            login:!!document.querySelector('[data-pink-login]'),
            live:!!document.querySelector('#viewport'),
            skeletons:document.querySelectorAll('#viewport [data-skeleton]').length,
            rows:document.querySelectorAll('#viewport .channel-row:not([data-skeleton])').length,
            nativeBridge:typeof window.PinkNative?.postMessage==='function',
            nativeReady:typeof window.PinkConnection?.ready==='function',
            loginPhase:(()=>{const value=document.querySelector('[data-pink-login]')?.dataset.pinkPhase;
                return ['idle','authenticating','loading_account','saving_account','navigating','failed'].includes(value)?value:'absent'})(),
            loginFailure:(()=>{const value=document.querySelector('[data-pink-login]')?.dataset.pinkFailure;
                return ['authenticating','loading_account','saving_account','navigating'].includes(value)?value:'none'})(),
            submitDisabled:document.querySelector('[data-pink-login] button')?.disabled===true,
            route:(()=>{const value=location.pathname;
                return ['/','/login','/tv','/tv/login','/livetv','/tv/livetv'].includes(value)?value:'other'})()
        })""")
        InstrumentationRegistry.getInstrumentation().sendStatus(2, android.os.Bundle().apply {
            putString("stream", "\nACTUAL_WEBVIEW_FIXED_FLAGS="+flags+"\n")
        })
        throw AssertionError("Protected WebView checkpoint unavailable")
    }

    private var uiCheckpoint = "not_started"
    private fun navigate(activity: ActivityScenario<MainActivity>, path: String) {
        check(path in setOf("/login", "/livetv"))
        // A navigation can dispose the evaluating document and its callback.
        // Completion is the next page checkpoint, not a callback from the old page.
        activity.onActivity { host ->
            checkNotNull(webView(host.findViewById(android.R.id.content)))
                .evaluateJavascript("window.__pinkProofNavigationPending=true;location.assign(" + JSONObject.quote(path) + ")", null)
        }
        // A form already present in the old /login document is not a destination
        // checkpoint. Only the new document loses this transient marker.
        waitJs(activity, "location.pathname===" + JSONObject.quote(path) +
            " && window.__pinkProofNavigationPending!==true", 45)
    }

    private fun uiLoginAndCatalog(activity: ActivityScenario<MainActivity>, fixture: JSONObject, report: (String) -> Unit) {
        uiCheckpoint = "navigate_login"
        navigate(activity, "/login")
        uiCheckpoint = "login_form"
        waitJs(activity, "document.querySelector('[data-pink-login]')", 45)
        // Runtime-only fixture values enter only the local form, never output/artifacts.
        val username = JSONObject.quote(fixture.getString("username"))
        val password = JSONObject.quote(fixture.getString("password"))
        uiCheckpoint = "submit_login"
        check(js(activity, """(()=>{
            const form=document.querySelector('[data-pink-login]');
            form.elements.namedItem('username').value=$username;
            form.elements.namedItem('password').value=$password;
            form.requestSubmit();
            return form.dataset.pinkPhase==='authenticating' && form.querySelector('button').disabled===true;
        })()""") == "true")
        uiCheckpoint = "login_home"
        waitJs(activity, "location.pathname==='/' && !document.querySelector('[data-pink-login]')", 90)
        report("ACTUAL_WEBVIEW_USERNAME_PASSWORD_LOGIN=PASS")
        uiCheckpoint = "navigate_livetv"
        navigate(activity, "/livetv")
        uiCheckpoint = "live_rows"
        waitJs(activity, "document.querySelector('#viewport .channel-row:not([data-skeleton])')", 90)
        // A main-thread roundtrip and category control, independent of native HTTP proof.
        uiCheckpoint = "category_control"
        check(js(activity, "Boolean(document.querySelector('#category-picker-trigger'))") == "true")
        uiCheckpoint = "category_roundtrip"
        js(activity, "document.querySelector('#category-picker-trigger').click();true")
        report("ACTUAL_WEBVIEW_TAURI_LIVE_CATALOG_AND_UI_ROUNDTRIP=PASS")
        report("WEBVIEW_ONLINE_FIXED_STATE="+js(activity, "navigator.onLine===true"))
        actualUiPlayback(activity, report)
    }

    private fun actualUiPlayback(activity: ActivityScenario<MainActivity>, report: (String) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(VideoActivity::class.java.name, null, false)
        var nativeVideo: VideoActivity? = null
        var decoded = false
        var transport = "none"
        try {
            uiCheckpoint = "live_channel_click"
            // Exercise the real row handler with production player preferences and
            // URL construction. No fixture URL, payload or forced native preference.
            check(js(activity, """(()=>{
                document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));
                const button=document.querySelector('#viewport .channel-row:not([data-skeleton]) [data-role="play"]');
                if (!button) return false;
                button.click();
                return true;
            })()""") == "true")
            uiCheckpoint = "live_channel_decode"
            val deadline = System.currentTimeMillis()+60000
            while (!decoded && System.currentTimeMillis()<deadline) {
                nativeVideo = monitor.lastActivity as? VideoActivity
                if (nativeVideo != null) {
                    instrumentation.runOnMainSync {
                        val host = checkNotNull(nativeVideo)
                        check(!host.isFinishing && !host.isDestroyed)
                        val field = VideoActivity::class.java.getDeclaredField("exoPlayer")
                        field.isAccessible = true
                        val player = field.get(host) as? ExoPlayer
                        check(player?.playerError == null)
                        decoded = player?.isPlaying == true && player.currentPosition>1000 &&
                            (player.videoDecoderCounters?.renderedOutputBufferCount ?: 0)>0 &&
                            (player.audioDecoderCounters?.renderedOutputBufferCount ?: 0)>0
                    }
                    transport = "native"
                } else {
                    decoded = js(activity, """(()=>{
                        const video=document.querySelector('#player-wrap video');
                        const quality=video?.getVideoPlaybackQuality?.();
                        return !!video && !video.paused && !video.error && video.currentTime>1 &&
                            ((quality?.totalVideoFrames || 0)-(quality?.droppedVideoFrames || 0))>0 &&
                            (video.webkitAudioDecodedByteCount || 0)>0;
                    })()""") == "true"
                    transport = "webview"
                }
                Thread.sleep(250)
            }
            if (!decoded && nativeVideo == null) {
                report("ACTUAL_UI_CHANNEL_MEDIA_FIXED_STATE="+js(activity, """JSON.stringify((()=>{
                    const video=document.querySelector('#player-wrap video');
                    const quality=video?.getVideoPlaybackQuality?.();
                    return {video:!!video,playing:video?.paused===false,clock:(video?.currentTime || 0)>1,
                        ready:video?.readyState || 0,error:video?.error?.code || 0,
                        presented:((quality?.totalVideoFrames || 0)-(quality?.droppedVideoFrames || 0))>0,
                        audio:(video?.webkitAudioDecodedByteCount || 0)>0,
                        audioMetric:typeof video?.webkitAudioDecodedByteCount==='number',
                        failurePanel:!!document.querySelector('[data-playback-failure]')};
                })())"""))
            }
            check(decoded && PinkVpnRuntime.isReady())
            report("ACTUAL_UI_CHANNEL_CLICK_VIDEO_AUDIO_DECODE=PASS;player="+transport)
        } finally {
            nativeVideo?.let { host -> instrumentation.runOnMainSync { host.finish() } }
            instrumentation.removeMonitor(monitor)
            instrumentation.waitForIdleSync()
        }
    }

    @Test fun authenticatedNativeLoginCatalogAndDecodedLiveAudioVideoUseWireGuard() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun report(message: String) = instrumentation.sendStatus(2, android.os.Bundle().apply { putString("stream", "\n"+message+"\n") })
        val context = instrumentation.targetContext
        var phase = "fixture"
        fun advance(next: String) {
            phase = next
            try { context.filesDir.resolve("pink055-live-phase-public.txt").writeText(android.os.Process.myPid().toString()+":"+next) } catch (_: Exception) { }
        }
        advance("fixture")
        val fixtureFile = context.filesDir.resolve("pink055-test-account.json")
        check(fixtureFile.isFile)
        val fixture = JSONObject(fixtureFile.readText())
        check(fixtureFile.delete())
        var enrolled = false
        val retainForRestart = InstrumentationRegistry.getArguments().getString("pinkRetainGrant") == "true"
        var primaryFailed = false
        try {
            ActivityScenario.launch(MainActivity::class.java).let { activity ->
                advance("normal-consent")
                val device = UiDevice.getInstance(instrumentation)
                val consentDeadline = System.currentTimeMillis()+60000
                var launcherDialogs = 0
                while (VpnService.prepare(context) != null && System.currentTimeMillis()<consentDeadline) {
                    if (device.hasObject(By.pkg("com.android.vpndialogs")) ||
                        device.hasObject(By.text("Connection request"))) {
                        device.findObject(By.res("android","button1"))?.click()
                    } else if (launcherDialogs < 2 && device.hasObject(By.text("Pixel Launcher isn't responding"))) {
                        device.findObject(By.res("android", "aerr_close"))?.let {
                            it.click()
                            launcherDialogs++
                            report("EMULATOR_LAUNCHER_ANR_NORMAL_CLOSE=OBSERVED")
                        }
                    }
                    Thread.sleep(100)
                }
                check(VpnService.prepare(context) == null)
                report("NORMAL_SYSTEM_CONSENT=PASS")
                advance("offline-capture")
                val runtime = PinkVpnRuntime.get(context)
                runtime.failClosureObserverForTests = { reason ->
                    context.filesDir.resolve("pink055-fail-closure-public.txt").writeText(android.os.Process.myPid().toString()+":"+reason)
                }
                val deadline = System.currentTimeMillis()+60000
                while (!runtime.hasCapturedRouteForTests() && System.currentTimeMillis()<deadline) Thread.sleep(100)
                check(runtime.hasCapturedRouteForTests())
                val publicIdentity = runBlocking { SecureVpnIdentityStore(AndroidKeystoreVpnIdentityCipher(),
                    DataStoreVpnIdentityPersistence(context)).loadIdentity() as VpnIdentityResult.Available }
                context.filesDir.resolve("pink055-peer-public.txt").writeText(publicIdentity.identity.publicKey)
                context.filesDir.resolve("pink055-process-public.txt").writeText(android.os.Process.myPid().toString())
                advance("protected-login-enrollment")
                val reply = runtime.resolve(fixture.getString("username"),fixture.getString("password"))
                check(reply.getString("code") == "SUCCESS")
                enrolled = true
                check(PinkVpnRuntime.isReady())
                check(runtime.initialServiceStopAcknowledgedForTests())
                val initialReplacementCount = runtime.initialPeerReplacementCountForTests()
                check(initialReplacementCount >= 1)
                report("INITIAL_OFFLINE_SERVICE_RETIRED_BEFORE_AUTHORIZED_TUNNEL=PASS")
                val origin = reply.getString("xtream_base_url").trimEnd('/')
                check(origin == fixture.getString("expected_origin").trimEnd('/'))
                val identity = runBlocking { SecureVpnIdentityStore(AndroidKeystoreVpnIdentityCipher(),
                    DataStoreVpnIdentityPersistence(context)).loadIdentity() }
                check(identity is VpnIdentityResult.Available)
                advance("authoritative-catalog")
                val query = "username="+encoded(fixture.getString("username"))+"&password="+encoded(fixture.getString("password"))
                val categories = catalog("$origin/player_api.php?$query&action=get_live_categories")
                val streams = catalog("$origin/player_api.php?$query&action=get_live_streams")
                check(categories.length()>0 && streams.length()>0)
                report("AUTHORITATIVE_USERNAME_PASSWORD_LOGIN_AND_CATALOG_VIA_WIREGUARD=PASS")
                advance("webview-login-catalog")
                val controlRequestsBeforeUi = runtime.controlPlaneRequestCountForTests()
                uiLoginAndCatalog(activity, fixture, ::report)
                check(runtime.controlPlaneRequestCountForTests() > controlRequestsBeforeUi)
                report("ACTUAL_UI_REAUTH_AND_ACCOUNT_CONTROL_USE_PINNED_HTTPS_CONTROL=PASS")
                check(runtime.initialPeerReplacementCountForTests() == initialReplacementCount)
                report("ACTUAL_UI_REAUTH_PRESERVES_ADMITTED_TUNNEL=PASS")
                check(context.getSharedPreferences("pink_account_v1",0).edit().clear().commit())
                report("UI_FIXTURE_VAULT_CLEARED_BEFORE_NO_CREDENTIAL_COLD_PROOF=PASS")
                advance("native-audio-video")
                var decoded = false
                for (index in 0 until minOf(3,streams.length())) {
                    val stream = streams.getJSONObject(index)
                    val id = stream.getString("stream_id")
                    check(Regex("[0-9]+").matches(id))
                    val url = "$origin/"+encoded(fixture.getString("username"))+"/"+
                        encoded(fixture.getString("password"))+"/$id"
                    NativePlayerPayload.setChannels(JSONArray().put(JSONObject().put("id",id)
                        .put("name","Teste PINK").put("streamUrl",url).put("ua",ua)).toString())
                    val intent = Intent(context,VideoActivity::class.java)
                        .putExtra(VideoActivity.EXTRA_MODE,"live")
                        .putExtra(VideoActivity.EXTRA_INITIAL_CHANNEL_ID,id)
                        .putExtra(VideoActivity.EXTRA_TITLE,"Teste PINK")
                        .putExtra(VideoActivity.EXTRA_UA,ua)
                    // ActivityScenario.launch adds CLEAR_TASK and destroys the Tauri root.
                    // Exercise the production transition: open the native player from PINK.
                    val monitor = instrumentation.addMonitor(VideoActivity::class.java.name, null, false)
                    var video: VideoActivity? = null
                    try {
                        activity.onActivity { host ->
                            check(!host.isFinishing && !host.isDestroyed)
                            host.startActivity(intent)
                        }
                        video = instrumentation.waitForMonitorWithTimeout(monitor, 10000) as? VideoActivity
                        check(video != null)
                        instrumentation.waitForIdleSync()
                        val deadline = System.currentTimeMillis()+25000
                        var failed = false
                        while (!decoded && !failed && System.currentTimeMillis()<deadline) {
                            instrumentation.runOnMainSync {
                                val host = checkNotNull(video)
                                check(!host.isFinishing && !host.isDestroyed)
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
                    } finally {
                        video?.let { host -> instrumentation.runOnMainSync { host.finish() } }
                        instrumentation.removeMonitor(monitor)
                        instrumentation.waitForIdleSync()
                    }
                    activity.onActivity { host -> check(!host.isFinishing && !host.isDestroyed) }
                    report("NATIVE_PLAYER_PRESERVES_TAURI_ROOT=PASS")
                    if (decoded) break
                }
                check(decoded)
                check(PinkVpnRuntime.isReady())
                advance("activity-recreation")
                activity.recreate()
                instrumentation.waitForIdleSync()
                check(VpnService.prepare(context) == null && PinkVpnRuntime.isReady())
                val reloaded = runBlocking { SecureVpnIdentityStore(AndroidKeystoreVpnIdentityCipher(),
                    DataStoreVpnIdentityPersistence(context)).loadIdentity() }
                check(identity == reloaded)
                report("NATIVE_LIVE_AUDIO_VIDEO_DECODE_AND_SAME_INSTALLATION_RECREATION=PASS")
            }
        } catch (failure: Throwable) {
            primaryFailed = true
            report("PROTECTED_FLOW_FAILURE_PHASE="+phase)
            if (phase == "protected-login-enrollment") report("PROTECTED_ACTIVATION_DIAGNOSTIC="+PinkVpnRuntime.get(context).protectedDiagnosticForTests())
            if (phase == "webview-login-catalog") {
                val category = when (failure) {
                    is AssertionError -> "ASSERTION"
                    is IllegalStateException -> "STATE"
                    is org.json.JSONException -> "JSON"
                    else -> "OTHER"
                }
                report("ACTUAL_UI_FAILURE_CHECKPOINT="+uiCheckpoint+";js="+uiJsBoundary+";category="+category)
                report("ACTUAL_UI_LAST_FIXED_OBSERVATION=route="+uiNativeRoute+";progress="+uiNativeProgress+";livePhase="+uiLivePhase)
                report("ACTUAL_UI_NATIVE_LIVE_PHASE="+PinkWebBridge.livePhaseForTests())
                report("ACTUAL_UI_RENDERER_PULSE_DURING_CALLBACK="+
                    (if (PinkWebBridge.rendererPulseForTests()>uiPulseAtEvaluation) "ADVANCING" else "QUIET"))
                report("ACTUAL_UI_RUNTIME_FIXED_STATE="+PinkVpnRuntime.get(context).protectedDiagnosticForTests())
                val cacheState = try {
                    val raw = PinkVault(context).readValidated()
                    if (raw.isEmpty()) "EMPTY" else {
                        val account = JSONObject(raw)
                        val selected = account.optString("selectedId")
                        val entries = account.optJSONArray("entries")
                        if (selected.isNotBlank() && entries != null &&
                            (0 until entries.length()).any { entries.optJSONObject(it)?.optString("_id") == selected })
                            "VALID" else "EMPTY"
                    }
                } catch (_: Exception) { "MALFORMED" }
                val encrypted = context.getSharedPreferences("pink_account_v1", android.content.Context.MODE_PRIVATE).contains("account")
                report("ACTUAL_UI_ACCOUNT_FIXED_STATE=cache="+cacheState+";encrypted="+encrypted)
            }
            // Provider exceptions can contain credential-bearing URLs: never chain them.
            throw AssertionError("Protected real Android flow unavailable at "+phase)
        } finally {
            // Operational retain mode delegates every exit to the mandatory external own-peer cleanup trap.
            if (enrolled && !retainForRestart) {
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
                    report("TEMPORARY_INSTALLATION_AUTHORIZATION_REVOKED=PASS")
                } catch (_: Throwable) {
                    report("INSTRUMENTATION_PEER_CLEANUP=FAIL")
                    if (!primaryFailed) throw AssertionError("Temporary peer cleanup requires recovery")
                }
            }
        }
    }
}
