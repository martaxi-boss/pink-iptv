package com.pinkiptv.extreme

import android.net.VpnService
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.net.URL
import java.net.Socket
import java.net.InetSocketAddress
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PinkVpnStartupTest {
    @Test fun normalConsentCapturesOwnAppOfflineAndSurvivesActivityRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            if (VpnService.prepare(context) != null) {
                assertTrue(device.wait(Until.hasObject(By.pkg("com.android.vpndialogs")), 15000))
                val accept = device.wait(Until.findObject(By.res("android", "button1")), 10000)
                assertNotNull(accept)
                accept.click()
            }
            val runtime = PinkVpnRuntime.get(context)
            val deadline = System.currentTimeMillis()+20000
            while (!runtime.hasCapturedRouteForTests() && System.currentTimeMillis()<deadline) Thread.sleep(100)
            assertNull(VpnService.prepare(context))
            assertTrue(runtime.hasCapturedRouteForTests())
            assertFalse(PinkVpnRuntime.isReady())
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            val captured = connectivity.boundNetworkForProcess
            assertNotNull(captured)
            assertTrue(connectivity.getNetworkCapabilities(captured!!)!!
                .hasTransport(NetworkCapabilities.TRANSPORT_VPN))
            val routes = connectivity.getLinkProperties(captured)!!.routes
            println("OFFLINE_DIAGNOSTIC_UID="+android.os.Process.myUid()+";NETWORK="+captured+
                ";INTERFACE="+connectivity.getLinkProperties(captured)!!.interfaceName)
            assertTrue(routes.any { it.destination.toString() == "0.0.0.0/0" })
            assertTrue(routes.any { it.destination.prefixLength == 0 && it.destination.address is java.net.Inet6Address })
            for (address in listOf("1.1.1.1", "2606:4700:4700::1111")) {
                var blockedIp = false
                Socket().use { socket ->
                    try {
                        socket.connect(InetSocketAddress(address, 443), 2000)
                        println("OFFLINE_IP_CONNECT="+address+";LOCAL="+socket.localAddress.hostAddress+
                            ";BOUND="+connectivity.boundNetworkForProcess)
                    } catch (_: java.io.IOException) {
                        blockedIp = true
                        println("OFFLINE_IP_BLOCKED="+address)
                    }
                }
                assertTrue("Unadmitted IP traffic escaped its capture", blockedIp)
            }
            val store = SecureVpnIdentityStore(AndroidKeystoreVpnIdentityCipher(), DataStoreVpnIdentityPersistence(context))
            val identity = runBlocking { store.loadIdentity() }
            assertTrue(identity is VpnIdentityResult.Available)
            // Default app HTTPS must never escape the offline full-route capture.
            val request = URL("https://api.github.com/").openConnection() as HttpsURLConnection
            request.connectTimeout = 2000
            request.readTimeout = 2000
            var blocked = false
            try { request.responseCode } catch (_: java.io.IOException) { blocked = true }
            finally { request.disconnect() }
            assertTrue("Unadmitted application traffic escaped its capture", blocked)
            activity.recreate()
            instrumentation.waitForIdleSync()
            assertNull(VpnService.prepare(context))
            assertTrue(runtime.hasCapturedRouteForTests())
            assertEquals(identity, runBlocking { store.loadIdentity() })
            assertFalse(PinkVpnRuntime.isReady())
        }
    }
}
