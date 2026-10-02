package com.pinkiptv.app

import androidx.test.platform.app.InstrumentationRegistry
import com.pinkiptv.app.storage.AndroidKeystoreCredentialCipher
import com.pinkiptv.app.vpn.AndroidKeystoreVpnIdentityCipher
import com.pinkiptv.app.vpn.DataStoreVpnIdentityPersistence
import com.pinkiptv.app.vpn.EncryptedVpnIdentityRecord
import com.pinkiptv.app.vpn.SecureVpnIdentityStore
import com.pinkiptv.app.vpn.VpnEncryptedPayload
import com.pinkiptv.app.vpn.VpnIdentityResult
import com.pinkiptv.app.vpn.VpnPermissionCheck
import com.pinkiptv.app.vpn.VpnPermissionGateway
import com.pinkiptv.app.vpn.VpnPreparationController
import java.security.KeyStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WireGuardIdentityStoreTest {
    @Test
    fun realKeystoreAndDataStorePersistIdentityFailClosedAndSurviveLogout() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val persistence = DataStoreVpnIdentityPersistence(context)
        val cipher = AndroidKeystoreVpnIdentityCipher()
        val store = SecureVpnIdentityStore(cipher, persistence)
        val credentialCipher = AndroidKeystoreCredentialCipher()

        persistence.clearForTests()
        cipher.destroyKeyForTests()
        credentialCipher.destroyKey()

        try {
            val created = store.ensureIdentity() as VpnIdentityResult.Available
            val reloaded = store.loadIdentity() as VpnIdentityResult.Available

            assertEquals(created.identity.publicKey, reloaded.identity.publicKey)
            assertTrue(keyExists(AndroidKeystoreVpnIdentityCipher.KEY_ALIAS))
            assertFalse(keyExists(CREDENTIAL_ALIAS))
            assertFalse(AndroidKeystoreVpnIdentityCipher.KEY_ALIAS == CREDENTIAL_ALIAS)

            val record = requireNotNull(persistence.read())
            val privateKeyBase64 = cipher.decrypt(
                VpnEncryptedPayload(
                    ivHex = record.ivHex,
                    ciphertextHex = record.ciphertextHex,
                ),
            )
            val dataStoreFile = context.filesDir
                .resolve("datastore")
                .resolve("${DataStoreVpnIdentityPersistence.DATASTORE_NAME}.preferences_pb")
            assertTrue(dataStoreFile.exists())
            assertFalse(
                dataStoreFile.readBytes()
                    .toString(Charsets.ISO_8859_1)
                    .contains(privateKeyBase64),
            )

            credentialCipher.encrypt("synthetic-credential-material")
            assertTrue(keyExists(CREDENTIAL_ALIAS))
            assertTrue(keyExists(AndroidKeystoreVpnIdentityCipher.KEY_ALIAS))
            credentialCipher.destroyKey()
            assertFalse(keyExists(CREDENTIAL_ALIAS))
            assertTrue(keyExists(AndroidKeystoreVpnIdentityCipher.KEY_ALIAS))

            val controller = VpnPreparationController(
                identityStore = store,
                permissionGateway = object : VpnPermissionGateway {
                    override fun prepare(): VpnPermissionCheck =
                        VpnPermissionCheck.SystemPermissionRequired
                },
                scope = this,
            )
            yield()
            controller.onIptvLogout()
            yield()

            val afterLogout = store.loadIdentity() as VpnIdentityResult.Available
            assertEquals(created.identity.publicKey, afterLogout.identity.publicKey)

            val corrupt = EncryptedVpnIdentityRecord(
                version = record.version,
                ivHex = record.ivHex,
                ciphertextHex = "00",
            )
            persistence.write(corrupt)

            assertTrue(store.loadIdentity() is VpnIdentityResult.Failure)
            val beforeEnsure = requireNotNull(persistence.read())
            assertTrue(store.ensureIdentity() is VpnIdentityResult.Failure)
            val afterEnsure = requireNotNull(persistence.read())
            assertEquals(beforeEnsure, afterEnsure)
        } finally {
            persistence.clearForTests()
            cipher.destroyKeyForTests()
            credentialCipher.destroyKey()
        }
    }

    private fun keyExists(alias: String): Boolean {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return keyStore.containsAlias(alias)
    }

    private companion object {
        const val CREDENTIAL_ALIAS = "pink_iptv_credentials_v1"
    }
}
