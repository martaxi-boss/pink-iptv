package com.pinkiptv.extreme

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Process
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** Runs after a real enrolled process has been force-stopped, without an account fixture. */
class PinkVpnRestoreTest {
    @Test fun coldProcessRestoresSameIdentityAndAuthorizedTunnelWithoutCredentials() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        fun report(message: String) = instrumentation.sendStatus(2, android.os.Bundle().apply {
            putString("stream", "\n"+message+"\n")
        })
        val expectedKey = context.filesDir.resolve("pink055-peer-public.txt").readText().trim()
        val previousPid = context.filesDir.resolve("pink055-process-public.txt").readText().trim().toInt()
        check(previousPid != Process.myPid())
        check(!context.filesDir.resolve("pink055-test-account.json").exists())
        check(VpnService.prepare(context) == null)
        ActivityScenario.launch(MainActivity::class.java).let {
            fun health(): Boolean = try {
                val request = URL("http://10.66.0.1:51821/health").openConnection() as HttpURLConnection
                request.connectTimeout = 3000
                request.readTimeout = 3000
                request.instanceFollowRedirects = false
                try { request.responseCode == 200 && request.inputStream.bufferedReader().use {
                    source -> source.readLine() == "PINK_VPN_READY"
                } } finally { request.disconnect() }
            } catch (_: Exception) { false }
            fun ready() {
                val deadline = System.currentTimeMillis()+90000
                while ((!PinkVpnRuntime.isReady() || !health()) && System.currentTimeMillis()<deadline) Thread.sleep(250)
                check(PinkVpnRuntime.isReady() && health())
            }
            ready()
            val stored = runBlocking { SecureVpnIdentityStore(AndroidKeystoreVpnIdentityCipher(),
                DataStoreVpnIdentityPersistence(context)).loadIdentity() as VpnIdentityResult.Available }
            check(stored.identity.publicKey == expectedKey)
            report("COLD_PROCESS_SAME_KEY_NO_CREDENTIALS_AUTHORIZED_TUNNEL=PASS")
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val wifi = cm.allNetworks.firstOrNull { network -> cm.getNetworkCapabilities(network)?.let { caps ->
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } == true }
            if (wifi != null) {
                fun command(value: String) {
                    instrumentation.uiAutomation.executeShellCommand(value).use { descriptor ->
                        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { stream ->
                            while (stream.read() != -1) { }
                        }
                    }
                }
                try {
                    command("svc wifi disable")
                    val deadline = System.currentTimeMillis()+30000
                    while (cm.allNetworks.any { it == wifi } && System.currentTimeMillis()<deadline) Thread.sleep(250)
                    check(cm.allNetworks.none { it == wifi })
                    val mobile = cm.allNetworks.any { network -> cm.getNetworkCapabilities(network)?.let { caps ->
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    } == true }
                    if (mobile) {
                        ready()
                        report("SAME_PROCESS_WIFI_TO_CELLULAR_WIREGUARD_ROAM=PASS")
                    } else report("EMULATOR_CELLULAR_ROAM_UNAVAILABLE")
                } finally { command("svc wifi enable") }
                ready()
                check(PinkVpnRuntime.get(context).hasCapturedRouteForTests())
                report("WIFI_RECONNECT_CAPTURE_AND_DEFAULT_TUNNEL_HEALTH=PASS")
            } else report("EMULATOR_WIFI_CHANGE_UNAVAILABLE")
        }
    }
}
