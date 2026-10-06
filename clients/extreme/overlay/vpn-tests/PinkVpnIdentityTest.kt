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
}
