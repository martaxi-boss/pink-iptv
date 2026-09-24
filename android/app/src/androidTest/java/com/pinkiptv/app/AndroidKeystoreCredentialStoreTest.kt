package com.pinkiptv.app

import androidx.test.platform.app.InstrumentationRegistry
import com.pinkiptv.app.storage.AndroidKeystoreCredentialCipher
import com.pinkiptv.app.storage.DataStoreCredentialPersistence
import com.pinkiptv.app.storage.SecureCredentialStore
import java.security.KeyStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidKeystoreCredentialStoreTest {
    @Test
    fun realKeystoreEncryptsPersistsClearsAndRecreates() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val persistence = DataStoreCredentialPersistence(context)
        val store = SecureCredentialStore(
            cipher = AndroidKeystoreCredentialCipher(),
            persistence = persistence,
        )

        val username = "runtime-fixture-user"
        val password = "runtime-fixture-pass" // pragma: allowlist secret

        store.clear()
        try {
            store.save(username, password)

            val loaded = requireNotNull(store.load())
            assertEquals(username, loaded.username)
            assertEquals(password, loaded.password)
            assertTrue(keyExists())

            val dataStoreFile = context.filesDir
                .resolve("datastore")
                .resolve("pink_secure_credentials.preferences_pb")
            assertTrue(dataStoreFile.exists())
            assertFalse(
                dataStoreFile.readBytes()
                    .toString(Charsets.ISO_8859_1)
                    .contains(password),
            )

            store.clear()
            assertNull(persistence.read())
            assertFalse(keyExists())

            store.save(username, password)
            assertTrue(keyExists())
            assertEquals(password, requireNotNull(store.load()).password)
        } finally {
            store.clear()
        }
    }

    private fun keyExists(): Boolean {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }
        return keyStore.containsAlias("pink_iptv_credentials_v1")
    }
}
