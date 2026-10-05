package com.pinkiptv.extreme

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Process
import android.webkit.JavascriptInterface
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/** Only attached to the local Tauri app WebView. No configuration/key API is exposed. */
class PinkConnection(private val context: Context) {
    @JavascriptInterface fun resolve(username: String, password: String): String = try {
        PinkVpnRuntime.get(context).resolve(username, password).toString()
    } catch (_: Exception) { JSONObject().put("code", "VPN_UNAVAILABLE").toString() }
}

/** Process-wide installation lifecycle, independent of IPTV login/logout or Activity recreation. */
class PinkVpnRuntime private constructor(context: Context) : Tunnel {
    private val app = context.applicationContext
    private val work = Executors.newSingleThreadScheduledExecutor()
    private val cm = app.getSystemService(ConnectivityManager::class.java)
    private val cipher = AndroidKeystoreVpnIdentityCipher()
    private val persistence = DataStoreVpnIdentityPersistence(app)
    private val identity = SecureVpnIdentityStore(cipher, persistence)
    private val prefs = app.getSharedPreferences("pink_vpn_grant_v1", Context.MODE_PRIVATE)
    private val permission = CountDownLatch(1)
    private val backend by lazy { GoBackend(app) }
    @Volatile private var live = false
    @Volatile private var admitted = false
    @Volatile private var accepting = false
    @Volatile private var consentRequested = false
    private var config: Config? = null
    private var failures = 0
    private var nextAttempt = 0L

    override fun getName(): String = "pink"
    override fun onStateChange(state: Tunnel.State) {
        live = state == Tunnel.State.UP
        if (!live && admitted) {
            // Service revocation/replacement must stop Rust, WebView and native player
            // networking together. No direct retry survives this process boundary.
            admitted = false
            Process.killProcess(Process.myPid())
        }
    }

    fun startup(activity: Activity, launcher: ActivityResultLauncher<Intent>) {
        if (consentRequested) return
        consentRequested = true
        val intent = VpnService.prepare(app)
        if (intent != null) launcher.launch(intent) else permissionResult(true)
    }

    fun permissionResult(granted: Boolean) {
        accepting = granted && VpnService.prepare(app) == null
        permission.countDown()
        if (!accepting) return
        ContextCompat.startForegroundService(app, Intent(app, PinkVpnRuntimeService::class.java))
        work.execute {
            try {
                ensureIdentity()
                readGrant()?.let { restore(it) }
            } catch (_: Exception) { /* Login stays closed; never log identity or grant. */ }
        }
    }

    fun resume() { work.execute { nextAttempt = 0; maintain() } }

    init {
        // The official GoBackend keeps the TUN established and its UDP socket roams.
        // Do not cycle DOWN/UP on Wi-Fi/mobile changes: doing so opens a route gap.
        cm.registerNetworkCallback(NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(),
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { work.execute { nextAttempt = 0 } }
            })
        work.scheduleWithFixedDelay({ maintain() }, 30, 30, TimeUnit.SECONDS)
    }

    fun resolve(username: String, password: String): JSONObject {
        require(username.isNotBlank() && username.length <= 256 && password.length <= 4096)
        check(permission.await(45, TimeUnit.SECONDS) && accepting)
        return work.submit<JSONObject> {
            ensureIdentity()
            val reply = control("/v1/session/resolve", JSONObject()
                .put("username", username).put("password", password))
            if (reply.optString("code") != "SUCCESS") return@submit reply
            val payload = JSONObject().put("public_key", pair().publicKey.toBase64())
            readGrant()?.optString("device_token")?.takeIf { it.isNotBlank() }?.let {
                payload.put("device_token", it)
            }
            val grant = control("/v1/vpn/enroll", payload, reply.getString("session_token"))
            saveGrant(grant)
            connect(grant)
            // Session/enrollment bearer never enters JavaScript or account storage.
            JSONObject().put("code", "SUCCESS").put("xtream_base_url", reply.getString("xtream_base_url"))
        }.get(90, TimeUnit.SECONDS)
    }

    private fun ensureIdentity() = runBlocking {
        check(identity.ensureIdentity() is VpnIdentityResult.Available)
    }

    private fun pair(): KeyPair = runBlocking {
        val record = checkNotNull(persistence.read())
        check(record.version == SecureVpnIdentityStore.FORMAT_VERSION)
        KeyPair(Key.fromBase64(cipher.decrypt(VpnEncryptedPayload(record.ivHex, record.ciphertextHex))))
    }

    private fun readGrant(): JSONObject? {
        val iv = prefs.getString("iv", null)
        val blob = prefs.getString("ciphertext", null)
        if (iv == null && blob == null) return null
        return JSONObject(cipher.decrypt(VpnEncryptedPayload(checkNotNull(iv), checkNotNull(blob))))
    }

    private fun saveGrant(grant: JSONObject) {
        val encrypted = cipher.encrypt(grant.toString())
        check(prefs.edit().putString("iv", encrypted.ivHex)
            .putString("ciphertext", encrypted.ciphertextHex).commit())
    }

    private fun deviceRequest(grant: JSONObject) = JSONObject()
        .put("public_key", pair().publicKey.toBase64()).put("device_token", grant.getString("device_token"))

    private fun restore(grant: JSONObject) {
        val renewed = control("/v1/vpn/refresh", deviceRequest(grant))
        renewed.put("device_token", grant.getString("device_token"))
        saveGrant(renewed)
        connect(renewed)
    }

    private fun connect(grant: JSONObject) {
        check(accepting && VpnService.prepare(app) == null)
        check(Instant.parse(grant.getString("expires_at")).isAfter(Instant.now()))
        check(grant.getString("endpoint") == "146.59.145.3:51820")
        val address = grant.getString("address")
        check(Regex("10\\.66\\.0\\.(?:[3-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-4])/32").matches(address))
        val v6 = "fd66:7069:6e6b::${address.substringBefore('/').substringAfterLast('.').toInt().toString(16)}/128"
        check(grant.getString("address_v6") == v6)
        val server = Key.fromBase64(grant.getString("server_public_key"))
        if (config == null) {
            config = Config.parse(("[Interface]\nPrivateKey = ${pair().privateKey.toBase64()}\n" +
                "Address = $address, $v6\nDNS = 1.1.1.1\nMTU = 1380\nIncludedApplications = ${app.packageName}\n" +
                "[Peer]\nPublicKey = ${server.toBase64()}\nEndpoint = 146.59.145.3:51820\n" +
                "AllowedIPs = 0.0.0.0/0, ::/0\nPersistentKeepalive = 25\n").byteInputStream())
        } else {
            // Live configuration cannot be cycled while admitted application flows exist.
            check(config!!.peers.single().publicKey == server)
            check(config!!.`interface`.addresses.any { it.toString() == address })
        }
        if (!live) check(backend.setState(this, Tunnel.State.UP, config) == Tunnel.State.UP)
        check(probe())
        admitted = true
        failures = 0
    }

    private fun probe(): Boolean = try {
        // Default app route, never the physical control path; source IP must be this peer.
        val connection = URL("http://10.66.0.1:51821/health").openConnection() as HttpURLConnection
        connection.connectTimeout = 4000
        connection.readTimeout = 4000
        connection.instanceFollowRedirects = false
        try { connection.responseCode == 200 && connection.inputStream.bufferedReader().use {
            it.readLine() == "PINK_VPN_READY"
        } } finally { connection.disconnect() }
    } catch (_: Exception) { false }

    private fun maintain() {
        if (!accepting || System.currentTimeMillis() < nextAttempt) return
        try {
            val saved = readGrant() ?: return
            if (!Instant.parse(saved.getString("expires_at")).isAfter(Instant.now())) {
                if (admitted) { admitted = false; Process.killProcess(Process.myPid()) }
                return
            }
            if (live) {
                val renewed = control("/v1/vpn/refresh", deviceRequest(saved))
                renewed.put("device_token", saved.getString("device_token"))
                saveGrant(renewed)
                // Losing a handshake keeps the full app route captured and retries bounded.
                if (!probe()) throw IllegalStateException("Connection unavailable")
                admitted = true
            } else restore(saved)
            failures = 0
        } catch (_: Exception) {
            failures = (failures + 1).coerceAtMost(6)
            if (failures == 6 && admitted) {
                admitted = false
                Process.killProcess(Process.myPid())
            }
            nextAttempt = System.currentTimeMillis() + (5_000L shl failures).coerceAtMost(300_000L)
        }
    }

    private fun control(path: String, payload: JSONObject, session: String? = null): JSONObject {
        check(path in setOf("/v1/session/resolve", "/v1/vpn/enroll", "/v1/vpn/refresh"))
        val physical = cm.allNetworks.firstOrNull {
            val capabilities = cm.getNetworkCapabilities(it)
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        } ?: throw IllegalStateException("Connection unavailable")
        val connection = physical.openConnection(URL("https://pink-iptv.duckdns.org$path")) as javax.net.ssl.HttpsURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        session?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            check(connection.responseCode == 200)
            val raw = connection.inputStream.use { source ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (output.size() <= 65536) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            check(raw.size <= 65536)
            return JSONObject(String(raw, Charsets.UTF_8))
        } finally { connection.disconnect() }
    }

    companion object {
        @Volatile private var instance: PinkVpnRuntime? = null
        @Synchronized fun get(context: Context): PinkVpnRuntime = instance ?: PinkVpnRuntime(context).also { instance = it }
        fun isReady(): Boolean = instance?.let { it.live && it.admitted && it.accepting } == true
    }
}
