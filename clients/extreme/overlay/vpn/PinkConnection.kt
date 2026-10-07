package com.pinkiptv.extreme

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Process
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

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
    @Volatile private var boundVpn: Network? = null
    @Volatile private var captureAddress = "10.66.0.254"
    @Volatile private var latestHealthDiagnostic = "not_started"
    @Volatile private var protectedHealthDiagnostic = "not_started"
    @Volatile private var admitted = false
    @Volatile private var accepting = false
    @Volatile private var consentRequested = false
    private var config: Config? = null
    private var sealed = false
    private var failures = 0
    private var nextAttempt = 0L
    @Volatile private var startupStage = "awaiting_consent"
    @Volatile private var failureCategory = "none"
    @Volatile private var protectedStage = "not_started"
    @Volatile private var protectedFailure = "none"
    @Volatile private var activationStage = "not_started"
    @Volatile private var protectedActivation = "not_started"
    @Volatile private var initialServiceStopAcknowledged = false
    @Volatile private var initialPeerReplacements = 0
    @Volatile private var protectedControlFailure = "none"
    @Volatile private var controlPlaneRequests = 0
    // Inactive outside instrumentation; only fixed lifecycle categories are observed.
    @Volatile internal var failClosureObserverForTests: ((String) -> Unit)? = null

    private fun terminateForFailClosure(reason: String) {
        admitted = false
        try { failClosureObserverForTests?.invoke(reason) } catch (_: Exception) {
            // A diagnostic failure must never prevent fail closure.
        } finally { Process.killProcess(Process.myPid()) }
    }

    override fun getName(): String = "pink"
    override fun onStateChange(state: Tunnel.State) {
        live = state == Tunnel.State.UP
        if (!live && admitted) {
            // Service revocation/replacement must stop Rust, WebView and native player
            // networking together. No direct retry survives this process boundary.
            terminateForFailClosure("VPN_LOST")
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
        work.execute { maintain() }
    }

    fun resume() { work.execute { failures = 0; nextAttempt = 0; maintain() } }
    internal fun hasCapturedRouteForTests(): Boolean = live && boundVpn != null
    internal fun initialServiceStopAcknowledgedForTests(): Boolean = initialServiceStopAcknowledged
    internal fun initialPeerReplacementCountForTests(): Int = initialPeerReplacements
    internal fun controlPlaneRequestCountForTests(): Int = controlPlaneRequests
    // Only fixed stages and exception class names; no exception messages, keys,
    // grants, account data or provider origins may enter diagnostic output.
    internal fun startupDiagnosticForTests(): String =
        "stage=$startupStage;failure=$failureCategory;live=$live;bound=${boundVpn != null};accepting=$accepting"
    internal fun protectedDiagnosticForTests(): String =
        "stage=$protectedStage;activation=$protectedActivation;failure=$protectedFailure;live=$live;bound=${boundVpn != null};admitted=$admitted;saved=" + prefs.contains("ciphertext") + ";serviceStopAck=$initialServiceStopAcknowledged;initialReplacements=$initialPeerReplacements;control=$protectedControlFailure;controlPlaneRequests=$controlPlaneRequests;health=$protectedHealthDiagnostic"

    private fun bindCapturedNetwork() {
        // Binding also covers future native sockets and DNS. If Android removes
        // this network, they fail rather than select a physical default route.
        // Only the fixed control calls explicitly use a physical Network.
        repeat(200) {
            val network = cm.allNetworks.firstOrNull {
                cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true &&
                    cm.getLinkProperties(it)?.let { link ->
                        link.interfaceName?.isNotBlank() == true && ownsCapturedAddress(link.linkAddresses.map { it.address }, captureAddress)
                    } == true
            }
            if (network != null && cm.bindProcessToNetwork(network) && defaultRoutesSelect(network)) {
                boundVpn = network
                return
            }
            Thread.sleep(100)
        }
        throw IllegalStateException("Connection unavailable")
    }

    private fun defaultRoutesSelect(network: Network): Boolean {
        return try {
        // Network visibility precedes effective UID routing on some Android builds.
        // UDP connect selects a local source address without sending a datagram.
        // Never admit sockets merely because VpnService reports the TUN as UP.
        val expected = cm.getLinkProperties(network)?.linkAddresses?.map { it.address } ?: return false
        listOf("1.1.1.1", "2606:4700:4700::1111").all { target ->
            DatagramSocket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getByName(target), 443))
                expected.any { it == socket.localAddress }
            }
        }
        } catch (_: Exception) { false }
    }

    init {
        // The official GoBackend keeps the TUN established and its UDP socket roams.
        // Do not cycle DOWN/UP on Wi-Fi/mobile changes: doing so opens a route gap.
        cm.registerNetworkCallback(NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(),
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { work.execute { failures = 0; nextAttempt = 0; maintain() } }
            })
        work.scheduleWithFixedDelay({ maintain() }, 30, 30, TimeUnit.SECONDS)
    }

    fun resolve(username: String, password: String): JSONObject {
        require(username.isNotBlank() && username.length <= 256 && password.length <= 4096)
        check(permission.await(45, TimeUnit.SECONDS) && accepting)
        return work.submit<JSONObject> {
            try {
                protectedStage = "identity"
                protectedFailure = "none"
                protectedControlFailure = "none"
                protectedHealthDiagnostic = "not_started"
                ensureIdentity()
                protectedStage = "session_resolve"
                val reply = control("/v1/session/resolve", JSONObject()
                    .put("username", username).put("password", password))
                if (reply.optString("code") != "SUCCESS") return@submit reply

                val payload = JSONObject().put("public_key", pair().publicKey.toBase64())
                readGrant()?.optString("device_token")?.takeIf { it.isNotBlank() }?.let {
                    payload.put("device_token", it)
                }
                protectedStage = "vpn_enroll"
                val grant = control("/v1/vpn/enroll", payload, reply.getString("session_token"))
                protectedStage = "save_grant"
                saveGrant(grant)
                protectedStage = "activate_tunnel"
                connect(grant)
                protectedStage = "ready"
                // Session/enrollment bearer never enters JavaScript or account storage.
                JSONObject().put("code", "SUCCESS").put("xtream_base_url", reply.getString("xtream_base_url"))
            } catch (failure: Exception) {
                protectedActivation = activationStage
                protectedHealthDiagnostic = latestHealthDiagnostic
                protectedFailure = failure.javaClass.simpleName
                throw failure
            }
        }.get(90, TimeUnit.SECONDS)
    }

    private fun ensureIdentity() = runBlocking {
        check(identity.ensureIdentity() is VpnIdentityResult.Available)
    }

    private fun sealBeforeEnrollment() {
        if (live) {
            if (boundVpn == null) bindCapturedNetwork()
            return
        }
        // A fresh installation has no authenticated peer yet. Capture its own UID
        // into an offline official TUN while the fixed native HTTPS control path
        // obtains authorization. No IPTV packet can use a direct startup route.
        val keys = pair()
        val sink = KeyPair().publicKey.toBase64()
        val offline = Config.parse(("[Interface]\nPrivateKey = ${keys.privateKey.toBase64()}\n" +
            "Address = 10.66.0.254/32, fd66:7069:6e6b::fe/128\nDNS = 1.1.1.1\n" +
            "MTU = 1380\nIncludedApplications = ${app.packageName}\n" +
            "[Peer]\nPublicKey = $sink\nAllowedIPs = 0.0.0.0/0, ::/0\n").byteInputStream())
        captureAddress = "10.66.0.254"
        startupStage = "offline_backend"
        check(backend.setState(this, Tunnel.State.UP, offline) == Tunnel.State.UP)
        // Remember the installed offline configuration even if Android's route
        // registration is delayed. Recovery must bind it, not replace or admit it.
        sealed = true
        startupStage = "offline_binding"
        bindCapturedNetwork()
        startupStage = "offline_captured"
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

    @Suppress("DEPRECATION")
    private fun awaitRetiredBackendService() {
        // GoBackend1.0.20260102 stops the previous service asynchronously.
        // Its onDestroy resets the SDK service future only after retiring the
        // old tunnel. Never attach a new tunnel to that retiring service.
        repeat(100) {
            val stopped = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    backend.isAlwaysOn() // Status query only; never enables always-on.
                    false
                } catch (_: TimeoutException) { true }
            } else {
                // Android26-28 retain this documented query for our own services.
                app.getSystemService(ActivityManager::class.java).getRunningServices(Int.MAX_VALUE)
                    .none { it.service.packageName == app.packageName &&
                        it.service.className == GoBackend.VpnService::class.java.name }
            }
            if (stopped) {
                initialServiceStopAcknowledged = true
                return
            }
            Thread.sleep(100)
        }
        throw IllegalStateException("Connection unavailable")
    }

    private fun connect(grant: JSONObject) {
        activationStage = "validate_grant"
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
        if (!live || sealed) {
            // No application flow is admitted during the initial offline->authorized change.
            check(!admitted)
            // Keep the old (then dead) captured binding while the SDK retires
            // its service. No application flow is admitted during this boundary.
            activationStage = "retire_offline_backend"
            check(backend.setState(this, Tunnel.State.DOWN, null) == Tunnel.State.DOWN)
            activationStage = "await_offline_service_shutdown"
            awaitRetiredBackendService()
            initialPeerReplacements++
            // The existing initial binding transition remains before admission.
            // Never clear this binding after admission or when its VPN is lost.
            activationStage = "clear_initial_binding"
            check(cm.bindProcessToNetwork(null))
            boundVpn = null
            captureAddress = address.substringBefore('/')
            activationStage = "authorized_backend_up"
            check(backend.setState(this, Tunnel.State.UP, config) == Tunnel.State.UP)
            activationStage = "bind_authorized_route"
            bindCapturedNetwork()
            sealed = false
        }
        if (boundVpn == null) bindCapturedNetwork()
        activationStage = "protected_health"
        check(probe())
        admitted = true
        activationStage = "ready"
        failures = 0
    }

    private fun probe(): Boolean {
        var phase = "open_connection"
        var status = -1
        var bodyMatches = false
        var category = "none"
        return try {
            // Default protected app route only. Keep the original 4s deadlines.
            val connection = URL("http://10.66.0.1:51821/health").openConnection() as HttpURLConnection
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.instanceFollowRedirects = false
            try {
                phase = "headers"
                status = connection.responseCode
                if (status != 200) false else {
                    phase = "body"
                    bodyMatches = connection.inputStream.bufferedReader().use {
                        it.readLine() == "PINK_VPN_READY"
                    }
                    bodyMatches
                }
            } finally { connection.disconnect() }
        } catch (failure: Exception) {
            category = healthFailureKind(failure)
            false
        } finally {
            val captureMatches = try { ownsCapturedAddress(
                boundVpn?.let { cm.getLinkProperties(it)?.linkAddresses?.map { link -> link.address } }, captureAddress) } catch (_: Exception) { false }
            // Fixed enums/status/booleans only, copied on failed enrollment.
            latestHealthDiagnostic = "$phase:$category;status=$status;bodyMatches=$bodyMatches;captureMatches=$captureMatches"
        }
    }

    private fun maintain() {
        if (!accepting || failures >= 6 || System.currentTimeMillis() < nextAttempt) return
        try {
            startupStage = "identity"
            ensureIdentity()
            // Fresh installations also recover bounded startup failures. There
            // need not be a saved account/grant to establish the offline capture.
            if (!live || boundVpn == null) sealBeforeEnrollment()
            startupStage = if (sealed) "offline_captured" else "authorized_capture"
            failureCategory = "none"
            val saved = readGrant() ?: run {
                failures = 0
                nextAttempt = 0
                return
            }
            if (!Instant.parse(saved.getString("expires_at")).isAfter(Instant.now())) {
                if (admitted) terminateForFailClosure("GRANT_EXPIRED")
                return
            }
            if (live && !sealed) {
                val renewed = control("/v1/vpn/refresh", deviceRequest(saved))
                renewed.put("device_token", saved.getString("device_token"))
                saveGrant(renewed)
                // Losing a handshake keeps the full app route captured and retries bounded.
                if (!probe()) throw IllegalStateException("Connection unavailable")
                admitted = true
            } else restore(saved)
            failures = 0
        } catch (failure: Exception) {
            failureCategory = failure.javaClass.simpleName
            failures = (failures + 1).coerceAtMost(6)
            if (failures == 6 && admitted) {
                terminateForFailClosure("RECOVERY_EXHAUSTED")
            }
            nextAttempt = System.currentTimeMillis() + (5_000L shl failures).coerceAtMost(300_000L)
        }
    }

    private fun control(path: String, payload: JSONObject, session: String? = null): JSONObject {
        check(path in setOf("/v1/session/resolve", "/v1/vpn/enroll", "/v1/vpn/refresh"))
        // Fixed PINK HTTPS endpoints are the control plane that creates/repairs
        // the WireGuard data plane. Keep them on an explicitly selected physical
        // Network so recovery never depends circularly on the tunnel it is
        // repairing. Provider/catalog/media traffic still has no physical fallback:
        // the process remains bound to the captured VPN Network once admitted.
        val candidates = cm.allNetworks.filter {
            val capabilities = cm.getNetworkCapabilities(it)
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        }
        // Android's VALIDATED flag describes general Internet access, not DNS
        // reachability for this fixed control host. Enumeration order can pick
        // an unusable cellular underlay even while Wi-Fi resolves the host.
        // Probe DNS on the candidate Network itself, before any POST. This
        // never moves provider traffic or the process off the captured VPN.
        val controlNetwork = selectPinkControlNetwork(candidates, cm.activeNetwork,
            { cm.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true },
            { network ->
                try { network.getAllByName("pink-iptv.duckdns.org").isNotEmpty() }
                catch (_: java.net.UnknownHostException) { false }
            }) ?: throw java.net.UnknownHostException("Protected control DNS unavailable")
        val route = "PINNED_HTTPS_CONTROL"
        val capabilities = cm.getNetworkCapabilities(controlNetwork)
        val transport = when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WIFI"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "CELLULAR"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ETHERNET"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "VPN"
            else -> "OTHER"
        }
        val validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val selectedDefault = cm.activeNetwork == controlNetwork
        val link = cm.getLinkProperties(controlNetwork)
        val v4 = link?.linkAddresses?.any { it.address is java.net.Inet4Address } == true
        val v6 = link?.linkAddresses?.any { it.address is java.net.Inet6Address } == true
        var phase = "open_connection"
        var status = -1
        try {
            controlPlaneRequests++
            val connection = controlNetwork.openConnection(URL("https://pink-iptv.duckdns.org$path")) as javax.net.ssl.HttpsURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            session?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            try {
                phase = "connect_write"
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                phase = "response_headers"
                status = connection.responseCode
                check(status == 200)
                phase = "response_body"
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
        } catch (failure: Exception) {
            if (path != "/v1/vpn/refresh") {
                val role = when (path) { "/v1/session/resolve" -> "SESSION"; "/v1/vpn/enroll" -> "ENROLL"; else -> "REFRESH" }
                var cause: Throwable? = failure
                var errno = 0
                repeat(8) {
                    if (cause is android.system.ErrnoException) errno = (cause as android.system.ErrnoException).errno
                    cause = cause?.cause
                }
                val message = failure.message.orEmpty()
                val category = when {
                    failure is java.net.UnknownHostException -> "DNS"
                    failure is java.net.SocketTimeoutException -> "TIMEOUT"
                    message.contains("unreachable", true) -> "UNREACHABLE"
                    message.contains("reset", true) -> "RESET"
                    message.contains("abort", true) -> "ABORT"
                    message.contains("closed", true) -> "CLOSED"
                    message.contains("refused", true) -> "REFUSED"
                    failure is javax.net.ssl.SSLException -> "TLS"
                    failure is java.net.SocketException -> "SOCKET_OTHER"
                    else -> "OTHER"
                }
                // Fixed enums/booleans/OS errno and HTTP status only; never message/URL/account.
                protectedControlFailure = "$role:$route:$phase:$category;errno=$errno;status=$status;transport=$transport;validated=$validated;selectedDefault=$selectedDefault;v4=$v4;v6=$v6"
            }
            throw failure
        }
    }

    companion object {
        internal fun ownsCapturedAddress(addresses: List<InetAddress>?, expected: String): Boolean =
            addresses?.any { it.hostAddress == expected } == true

        internal fun healthFailureKind(failure: Exception): String = when (failure) {
            is java.net.UnknownHostException -> "DNS"
            is java.net.SocketTimeoutException -> "TIMEOUT"
            is javax.net.ssl.SSLException -> "TLS"
            is java.net.SocketException -> "SOCKET"
            is java.io.IOException -> "IO"
            else -> "OTHER"
        }

        @Volatile private var instance: PinkVpnRuntime? = null
        @Synchronized fun get(context: Context): PinkVpnRuntime = instance ?: PinkVpnRuntime(context).also { instance = it }
        fun isReady(): Boolean = instance?.let { it.live && it.admitted && it.accepting && it.boundVpn != null } == true
    }
}
