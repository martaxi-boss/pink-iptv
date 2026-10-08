package com.pinkiptv.extreme

import androidx.test.platform.app.InstrumentationRegistry
import com.wireguard.android.backend.GoBackend
import com.wireguard.config.Config
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PinkVpnIdentityTest {
    @Test fun keystoreIdentityIsEncryptedPersistentAndAccountIndependent() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val persistence = DataStoreVpnIdentityPersistence(context)
        val cipher = AndroidKeystoreVpnIdentityCipher()
        val store = SecureVpnIdentityStore(cipher, persistence)
        persistence.clearForTests()
        cipher.destroyKeyForTests()
        try {
            val first = store.ensureIdentity() as VpnIdentityResult.Available
            assertEquals(first, store.loadIdentity())
            val record = checkNotNull(persistence.read())
            val plain = cipher.decrypt(VpnEncryptedPayload(record.ivHex, record.ciphertextHex))
            assertFalse(context.filesDir.resolve("datastore/pink_wireguard_identity.preferences_pb")
                .readBytes().toString(Charsets.ISO_8859_1).contains(plain))
            val accountBlob = "{\"entries\":[],\"selectedId\":\"fixture-account\"}"
            val firstVault = PinkVault(context)
            assertTrue(firstVault.write(accountBlob))
            assertEquals(accountBlob, firstVault.readValidated())
            assertEquals(accountBlob, PinkVault(context).readValidated())
            assertEquals(first, store.ensureIdentity())
            cipher.destroyKeyForTests()
            assertTrue(store.ensureIdentity() is VpnIdentityResult.Failure)
        } finally {
            PinkVault(context).markValidated("")
            context.getSharedPreferences("pink_account_v1", android.content.Context.MODE_PRIVATE)
                .edit().clear().commit()
            persistence.clearForTests()
            cipher.destroyKeyForTests()
        }
    }

    @Test fun officialBackendAndMergedVpnServiceAreAvailable() {
        assertStaleOfflineOrOtherVpnCannotMatchTheAuthorizedCapture()
        assertProtectedHealthFailuresEmitFixedKindsWithoutExceptionMessages()
        assertCapacityErrorNeedsExactAuthenticatedHttp429()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(GoBackend(context).version.isNotBlank())
        @Suppress("DEPRECATION")
        val service = context.packageManager.getServiceInfo(
            android.content.ComponentName(context, GoBackend.VpnService::class.java),
            android.content.pm.PackageManager.GET_META_DATA)
        assertFalse(service.exported)
        assertEquals("android.permission.BIND_VPN_SERVICE", service.permission)
        assertFalse(service.metaData.getBoolean("android.net.VpnService.SUPPORTS_ALWAYS_ON", true))
    }

    private fun assertCapacityErrorNeedsExactAuthenticatedHttp429() {
        val fixed = "ENROLL:PINNED_HTTPS_CONTROL:response_headers:OTHER;errno=0;status=429;transport=WIFI;validated=true"
        assertEquals("VPN_LIMIT", PinkVpnRuntime.classifyLoginFailure("vpn_enroll", fixed, true))
        assertEquals("VPN_PERMISSION", PinkVpnRuntime.classifyLoginFailure("vpn_enroll", fixed, false))
        assertEquals("VPN_ENROLL", PinkVpnRuntime.classifyLoginFailure(
            "vpn_enroll", fixed.replace("status=429", "status=503"), true))
        assertEquals("VPN_ENROLL", PinkVpnRuntime.classifyLoginFailure(
            "vpn_enroll", "ENROLL:PINNED_HTTPS_CONTROL:connect_write:TIMEOUT;errno=0;status=-1;", true))
        assertEquals("VPN_ENROLL", PinkVpnRuntime.classifyLoginFailure(
            "vpn_enroll", "PRIVATE:status=429;password=fixture", true))
        assertEquals("CONTROL_HTTPS", PinkVpnRuntime.classifyLoginFailure("session_resolve", fixed, true))
        assertEquals("VPN_ACTIVATION", PinkVpnRuntime.classifyLoginFailure("activate_tunnel", fixed, true))
    }

    private fun assertStaleOfflineOrOtherVpnCannotMatchTheAuthorizedCapture() {
        // Public Java numeric-address API only. Android LinkProperties mutation
        // and its LinkAddress(String) constructor are hidden from the app SDK.
        fun addresses(vararg values: String) = values.map { java.net.InetAddress.getByName(it) }
        val grant = "10.66.0.3"
        assertFalse(PinkVpnRuntime.ownsCapturedAddress(null, grant))
        assertFalse(PinkVpnRuntime.ownsCapturedAddress(emptyList(), grant))
        assertFalse(PinkVpnRuntime.ownsCapturedAddress(
            addresses("10.66.0.254", "fd66:7069:6e6b::fe"), grant))
        assertFalse(PinkVpnRuntime.ownsCapturedAddress(addresses("10.66.0.4"), grant))
        assertTrue(PinkVpnRuntime.ownsCapturedAddress(
            addresses("10.66.0.3", "fd66:7069:6e6b::3"), grant))
        assertTrue(PinkVpnRuntime.ownsCapturedAddress(
            addresses("10.66.0.254", "fd66:7069:6e6b::fe"), "10.66.0.254"))
    }

    private fun assertProtectedHealthFailuresEmitFixedKindsWithoutExceptionMessages() {
        val privateMessage = "fixture_user:fixture_password@private-provider"
        val fixtures = listOf(
            java.net.UnknownHostException(privateMessage) to "DNS",
            java.net.SocketTimeoutException(privateMessage) to "TIMEOUT",
            javax.net.ssl.SSLException(privateMessage) to "TLS",
            java.net.SocketException(privateMessage) to "SOCKET",
            java.io.IOException(privateMessage) to "IO",
            IllegalStateException(privateMessage) to "OTHER")
        fixtures.forEach { (failure, expected) ->
            val actual = PinkVpnRuntime.healthFailureKind(failure)
            assertEquals(expected, actual)
            assertFalse(actual.contains("fixture"))
            assertFalse(actual.contains("provider"))
        }
    }
}
