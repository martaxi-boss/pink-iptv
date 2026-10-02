package com.pinkiptv.app

import androidx.test.platform.app.InstrumentationRegistry
import com.wireguard.android.backend.GoBackend
import org.junit.Assert.assertTrue
import org.junit.Test

class WireGuardNativeSmokeTest {
    @Test
    fun officialGoBackendLoadsAndReportsVersionWithoutEstablishingTunnel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val backend = GoBackend(context.applicationContext)
        val version = backend.version

        assertTrue("WireGuard userspace backend version must be non-empty", version.isNotBlank())
    }
}
