package com.pinkiptv.app

import com.pinkiptv.app.vpn.EncryptedVpnIdentityRecord
import com.pinkiptv.app.vpn.SecureVpnIdentityStore
import com.pinkiptv.app.vpn.VpnEncryptedPayload
import com.pinkiptv.app.vpn.VpnIdentityCipher
import com.pinkiptv.app.vpn.VpnIdentityError
import com.pinkiptv.app.vpn.VpnIdentityPersistence
import com.pinkiptv.app.vpn.VpnIdentityResult
import com.wireguard.crypto.KeyPair
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnIdentityStoreTest {
    @Test
    fun absentIdentityIsCreatedOnceAndReused() = runTest {
        val cipher = FakeCipher()
        val persistence = FakePersistence()
        val store = SecureVpnIdentityStore(cipher, persistence)

        assertEquals(VpnIdentityResult.Absent, store.loadIdentity())

        val created = store.ensureIdentity() as VpnIdentityResult.Available
        val reloaded = store.ensureIdentity() as VpnIdentityResult.Available

        assertEquals(created.identity.publicKey, reloaded.identity.publicKey)
        assertEquals(1, persistence.writeCount)
        assertNotEquals(created.identity.publicKey, persistence.record?.ciphertextHex)
    }

    @Test
    fun concurrentEnsureIdentityCreatesExactlyOneStableIdentity() = runTest {
        val persistence = FakePersistence(yieldAfterSnapshotRead = true)
        val store = SecureVpnIdentityStore(FakeCipher(), persistence)

        val first = async { store.ensureIdentity() as VpnIdentityResult.Available }
        val second = async { store.ensureIdentity() as VpnIdentityResult.Available }

        val firstIdentity = first.await().identity
        val secondIdentity = second.await().identity

        assertEquals(firstIdentity.publicKey, secondIdentity.publicKey)
        assertEquals(1, persistence.writeCount)
    }

    @Test
    fun storedPrivateKeyDerivesSamePublicIdentity() = runTest {
        val pair = KeyPair()
        val cipher = FakeCipher(decryptedOverride = pair.privateKey.toBase64())
        val persistence = FakePersistence(
            record = EncryptedVpnIdentityRecord(
                version = SecureVpnIdentityStore.FORMAT_VERSION,
                ivHex = "iv",
                ciphertextHex = "cipher",
            ),
        )
        val store = SecureVpnIdentityStore(cipher, persistence)

        val loaded = store.loadIdentity() as VpnIdentityResult.Available

        assertEquals(pair.publicKey.toBase64(), loaded.identity.publicKey)
    }

    @Test
    fun decryptAndParseFailuresFailClosedWithoutRotation() = runTest {
        val decryptPersistence = FakePersistence(validRecord())
        val decryptStore = SecureVpnIdentityStore(
            cipher = FakeCipher(throwOnDecrypt = true),
            persistence = decryptPersistence,
        )

        val decryptResult = decryptStore.ensureIdentity()
        assertEquals(
            VpnIdentityError.Decrypt,
            (decryptResult as VpnIdentityResult.Failure).error,
        )
        assertEquals(0, decryptPersistence.writeCount)

        val parsePersistence = FakePersistence(validRecord())
        val parseStore = SecureVpnIdentityStore(
            cipher = FakeCipher(decryptedOverride = "not-a-wireguard-key"),
            persistence = parsePersistence,
        )

        val parseResult = parseStore.ensureIdentity()
        assertEquals(
            VpnIdentityError.Parse,
            (parseResult as VpnIdentityResult.Failure).error,
        )
        assertEquals(0, parsePersistence.writeCount)
    }

    @Test
    fun storageFailuresAreSafeAndPublicModelContainsNoSecrets() = runTest {
        val readFailure = SecureVpnIdentityStore(
            cipher = FakeCipher(),
            persistence = FakePersistence(throwOnRead = true),
        ).loadIdentity()
        assertEquals(
            VpnIdentityError.Storage,
            (readFailure as VpnIdentityResult.Failure).error,
        )

        val writeFailure = SecureVpnIdentityStore(
            cipher = FakeCipher(),
            persistence = FakePersistence(throwOnWrite = true),
        ).ensureIdentity()
        assertEquals(
            VpnIdentityError.Storage,
            (writeFailure as VpnIdentityResult.Failure).error,
        )

        val publicFields = com.pinkiptv.app.vpn.VpnPublicIdentity::class.java.declaredFields
            .map { it.name.lowercase() }
        assertTrue(
            publicFields.none {
                it.contains("private") ||
                    it.contains("psk") ||
                    it.contains("password")
            },
        )
    }

    private fun validRecord() = EncryptedVpnIdentityRecord(
        version = SecureVpnIdentityStore.FORMAT_VERSION,
        ivHex = "iv",
        ciphertextHex = "cipher",
    )

    private class FakeCipher(
        private val decryptedOverride: String? = null,
        private val throwOnDecrypt: Boolean = false,
    ) : VpnIdentityCipher {
        private var lastEncryptedPlaintext: String? = null

        override fun encrypt(privateKeyBase64: String): VpnEncryptedPayload {
            lastEncryptedPlaintext = privateKeyBase64
            return VpnEncryptedPayload(
                ivHex = "iv",
                ciphertextHex = "encrypted",
            )
        }

        override fun decrypt(payload: VpnEncryptedPayload): String {
            if (throwOnDecrypt) error("synthetic decrypt failure")
            return decryptedOverride
                ?: requireNotNull(lastEncryptedPlaintext) {
                    "Fake cipher has no encrypted fixture to decrypt"
                }
        }
    }

    private class FakePersistence(
        var record: EncryptedVpnIdentityRecord? = null,
        private val throwOnRead: Boolean = false,
        private val throwOnWrite: Boolean = false,
        private val yieldAfterSnapshotRead: Boolean = false,
    ) : VpnIdentityPersistence {
        var writeCount = 0

        override suspend fun read(): EncryptedVpnIdentityRecord? {
            if (throwOnRead) error("synthetic read failure")
            val snapshot = record
            if (yieldAfterSnapshotRead) yield()
            return snapshot
        }

        override suspend fun write(record: EncryptedVpnIdentityRecord) {
            if (throwOnWrite) error("synthetic write failure")
            writeCount += 1
            this.record = record
        }
    }
}
