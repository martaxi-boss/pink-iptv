package com.pinkiptv.extreme

import android.net.VpnService
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.net.URL
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
