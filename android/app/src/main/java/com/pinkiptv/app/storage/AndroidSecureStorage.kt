package com.pinkiptv.app.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.first

private val Context.credentialDataStore by preferencesDataStore(
    name = "pink_secure_credentials",
)

class AndroidKeystoreCredentialCipher : CredentialCipher {
    override fun encrypt(plaintext: String): EncryptedPayload {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return EncryptedPayload(
            ivHex = cipher.iv.toHex(),
            ciphertextHex = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)).toHex(),
        )
    }

    override fun decrypt(payload: EncryptedPayload): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_BITS, payload.ivHex.hexToByteArray())
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
        return cipher.doFinal(payload.ciphertextHex.hexToByteArray())
            .toString(Charsets.UTF_8)
    }

    override fun destroyKey() {
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
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "pink_iptv_credentials_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}

class DataStoreCredentialPersistence(
    private val context: Context,
) : CredentialPersistence {
    override suspend fun read(): EncryptedCredentialRecord? {
        val preferences = context.credentialDataStore.data.first()
        val username = preferences[USERNAME] ?: return null
        val iv = preferences[IV] ?: return null
        val ciphertext = preferences[CIPHERTEXT] ?: return null
        return EncryptedCredentialRecord(
            username = username,
            ivHex = iv,
            ciphertextHex = ciphertext,
        )
    }

    override suspend fun write(record: EncryptedCredentialRecord) {
        context.credentialDataStore.edit { preferences ->
            preferences[USERNAME] = record.username
            preferences[IV] = record.ivHex
            preferences[CIPHERTEXT] = record.ciphertextHex
        }
    }

    override suspend fun clear() {
        context.credentialDataStore.edit { preferences ->
            preferences.remove(USERNAME)
            preferences.remove(IV)
            preferences.remove(CIPHERTEXT)
        }
    }

    private companion object {
        val USERNAME = stringPreferencesKey("username")
        val IV = stringPreferencesKey("password_iv")
        val CIPHERTEXT = stringPreferencesKey("password_ciphertext")
    }
}

internal fun ByteArray.toHex(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte) }

internal fun String.hexToByteArray(): ByteArray {
    require(length % 2 == 0) { "Invalid hex length" }
    return chunked(2)
        .map { pair -> pair.toInt(16).toByte() }
        .toByteArray()
}
