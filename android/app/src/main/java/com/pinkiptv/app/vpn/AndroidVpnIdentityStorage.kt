package com.pinkiptv.app.vpn

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.VisibleForTesting
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.first

private val Context.wireGuardIdentityDataStore by preferencesDataStore(
    name = DataStoreVpnIdentityPersistence.DATASTORE_NAME,
)

class AndroidKeystoreVpnIdentityCipher : VpnIdentityCipher {
    override fun encrypt(privateKeyBase64: String): VpnEncryptedPayload {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return VpnEncryptedPayload(
            ivHex = cipher.iv.toHex(),
            ciphertextHex = cipher.doFinal(privateKeyBase64.toByteArray(Charsets.UTF_8)).toHex(),
        )
    }

    override fun decrypt(payload: VpnEncryptedPayload): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_BITS, payload.ivHex.hexToByteArray())
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
        return cipher.doFinal(payload.ciphertextHex.hexToByteArray())
            .toString(Charsets.UTF_8)
    }

    @VisibleForTesting
    fun destroyKeyForTests() {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .build(),
            )
            generateKey()
        }
    }

    companion object {
        const val KEY_ALIAS = "pink_iptv_wireguard_identity_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256

        private const val KEYSTORE = "AndroidKeyStore"
        private const val GCM_TAG_BITS = 128
    }
}

class DataStoreVpnIdentityPersistence(
    private val context: Context,
) : VpnIdentityPersistence {
    override suspend fun read(): EncryptedVpnIdentityRecord? {
        val preferences = context.wireGuardIdentityDataStore.data.first()
        val version = preferences[VERSION]
        val iv = preferences[IV]
        val ciphertext = preferences[CIPHERTEXT]

        if (version == null && iv == null && ciphertext == null) {
            return null
        }

        return EncryptedVpnIdentityRecord(
            version = requireNotNull(version) { "WireGuard identity version missing" },
            ivHex = requireNotNull(iv) { "WireGuard identity IV missing" },
            ciphertextHex = requireNotNull(ciphertext) { "WireGuard identity ciphertext missing" },
        )
    }

    override suspend fun write(record: EncryptedVpnIdentityRecord) {
        context.wireGuardIdentityDataStore.edit { preferences ->
            preferences[VERSION] = record.version
            preferences[IV] = record.ivHex
            preferences[CIPHERTEXT] = record.ciphertextHex
        }
    }

    @VisibleForTesting
    suspend fun clearForTests() {
        context.wireGuardIdentityDataStore.edit { preferences ->
            preferences.remove(VERSION)
            preferences.remove(IV)
            preferences.remove(CIPHERTEXT)
        }
    }

    companion object {
        const val DATASTORE_NAME = "pink_wireguard_identity"

        private val VERSION = intPreferencesKey("format_version")
        private val IV = stringPreferencesKey("private_key_iv")
        private val CIPHERTEXT = stringPreferencesKey("private_key_ciphertext")
    }
}

private fun ByteArray.toHex(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte) }

private fun String.hexToByteArray(): ByteArray {
    require(length % 2 == 0) { "Invalid hex length" }
    return chunked(2)
        .map { pair -> pair.toInt(16).toByte() }
        .toByteArray()
}
