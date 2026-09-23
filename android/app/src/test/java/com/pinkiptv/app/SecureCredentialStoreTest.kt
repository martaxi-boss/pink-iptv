package com.pinkiptv.app

import com.pinkiptv.app.storage.CredentialCipher
import com.pinkiptv.app.storage.CredentialPersistence
import com.pinkiptv.app.storage.EncryptedCredentialRecord
import com.pinkiptv.app.storage.EncryptedPayload
import com.pinkiptv.app.storage.SecureCredentialStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureCredentialStoreTest {
    @Test
    fun persistedRecordNeverContainsPlaintextPassword() = runTest {
        val cipher = FakeCipher()
        val persistence = FakePersistence()
        val store = SecureCredentialStore(cipher, persistence)

        store.save("fixture-user", "plain-fixture") // pragma: allowlist secret

        val record = requireNotNull(persistence.record)
        assertEquals("fixture-user", record.username)
        assertFalse(record.ciphertextHex.contains("plain-fixture"))
        assertEquals("ciphertext-fixture", record.ciphertextHex)
        assertEquals("iv-fixture", record.ivHex)
    }

    @Test
    fun loadDecryptsOnlyInMemory() = runTest {
        val cipher = FakeCipher()
        val persistence = FakePersistence(
            record = EncryptedCredentialRecord(
                username = "fixture-user",
                ivHex = "iv-fixture",
                ciphertextHex = "ciphertext-fixture",
            ),
        )
        val store = SecureCredentialStore(cipher, persistence)

        val credentials = requireNotNull(store.load())

        assertEquals("fixture-user", credentials.username)
        assertEquals("plain-fixture", credentials.password) // pragma: allowlist secret
    }

    @Test
    fun clearRemovesRecordAndDestroysInstallationKey() = runTest {
        val cipher = FakeCipher()
        val persistence = FakePersistence(
            record = EncryptedCredentialRecord(
                username = "fixture-user",
                ivHex = "iv-fixture",
                ciphertextHex = "ciphertext-fixture",
            ),
        )
        val store = SecureCredentialStore(cipher, persistence)

        store.clear()

        assertNull(persistence.record)
        assertTrue(cipher.keyDestroyed)
    }

    private class FakeCipher : CredentialCipher {
        var keyDestroyed = false

        override fun encrypt(plaintext: String): EncryptedPayload =
            EncryptedPayload(
                ivHex = "iv-fixture",
                ciphertextHex = "ciphertext-fixture",
            )

        override fun decrypt(payload: EncryptedPayload): String =
            "plain-fixture" // pragma: allowlist secret

        override fun destroyKey() {
            keyDestroyed = true
        }
    }

    private class FakePersistence(
        var record: EncryptedCredentialRecord? = null,
    ) : CredentialPersistence {
        override suspend fun read(): EncryptedCredentialRecord? = record

        override suspend fun write(record: EncryptedCredentialRecord) {
            this.record = record
        }

        override suspend fun clear() {
            record = null
        }
    }
}
