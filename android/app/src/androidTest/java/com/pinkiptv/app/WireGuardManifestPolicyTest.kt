package com.pinkiptv.app

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import com.wireguard.android.backend.GoBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class WireGuardManifestPolicyTest {
    @Suppress("DEPRECATION")
    @Test
    fun mergedManifestUsesOfficialVpnServiceAndDisablesAlwaysOn() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val serviceInfo = context.packageManager.getServiceInfo(
            ComponentName(context, GoBackend.VpnService::class.java),
            PackageManager.GET_META_DATA,
        )

        assertEquals("android.permission.BIND_VPN_SERVICE", serviceInfo.permission)
        assertFalse(serviceInfo.exported)
        assertNotNull(serviceInfo.metaData)
        assertFalse(
            serviceInfo.metaData.getBoolean(
                "android.net.VpnService.SUPPORTS_ALWAYS_ON",
                true,
            ),
        )
    }
}
