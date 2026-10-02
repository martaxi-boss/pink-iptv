package com.pinkiptv.app.vpn

import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair

data class VpnPublicIdentity(
    val identityAvailable: Boolean,
    val publicKey: String,
)

enum class VpnIdentityError {
    Storage,
    Decrypt,
    Parse,
}

sealed interface VpnIdentityResult {
    data object Absent : VpnIdentityResult

    data class Available(
        val identity: VpnPublicIdentity,
    ) : VpnIdentityResult

    data class Failure(
        val error: VpnIdentityError,
    ) : VpnIdentityResult
}

data class VpnEncryptedPayload(
    val ivHex: String,
    val ciphertextHex: String,
)

data class EncryptedVpnIdentityRecord(
    val version: Int,
    val ivHex: String,
    val ciphertextHex: String,
)

interface VpnIdentityCipher {
    fun encrypt(privateKeyBase64: String): VpnEncryptedPayload
    fun decrypt(payload: VpnEncryptedPayload): String
}

interface VpnIdentityPersistence {
    suspend fun read(): EncryptedVpnIdentityRecord?
    suspend fun write(record: EncryptedVpnIdentityRecord)
}

interface VpnIdentityStore {
    suspend fun loadIdentity(): VpnIdentityResult
    suspend fun ensureIdentity(): VpnIdentityResult
}

class SecureVpnIdentityStore(
    private val cipher: VpnIdentityCipher,
    private val persistence: VpnIdentityPersistence,
) : VpnIdentityStore {
    override suspend fun loadIdentity(): VpnIdentityResult {
        val record = try {
            persistence.read()
        } catch (_: Exception) {
            return VpnIdentityResult.Failure(VpnIdentityError.Storage)
        } ?: return VpnIdentityResult.Absent

        if (record.version != FORMAT_VERSION) {
            return VpnIdentityResult.Failure(VpnIdentityError.Parse)
        }

        val privateKeyBase64 = try {
            cipher.decrypt(
                VpnEncryptedPayload(
                    ivHex = record.ivHex,
                    ciphertextHex = record.ciphertextHex,
                ),
            )
        } catch (_: Exception) {
            return VpnIdentityResult.Failure(VpnIdentityError.Decrypt)
        }

        return try {
            val privateKey = Key.fromBase64(privateKeyBase64)
            val keyPair = KeyPair(privateKey)
            VpnIdentityResult.Available(
                VpnPublicIdentity(
                    identityAvailable = true,
                    publicKey = keyPair.publicKey.toBase64(),
                ),
            )
        } catch (_: Exception) {
            VpnIdentityResult.Failure(VpnIdentityError.Parse)
        }
    }

    override suspend fun ensureIdentity(): VpnIdentityResult {
        return when (val existing = loadIdentity()) {
            VpnIdentityResult.Absent -> createIdentity()
            is VpnIdentityResult.Available -> existing
            is VpnIdentityResult.Failure -> existing
        }
    }

    private suspend fun createIdentity(): VpnIdentityResult {
        val keyPair = try {
            KeyPair()
        } catch (_: Exception) {
            return VpnIdentityResult.Failure(VpnIdentityError.Storage)
        }

        val encrypted = try {
            cipher.encrypt(keyPair.privateKey.toBase64())
        } catch (_: Exception) {
            return VpnIdentityResult.Failure(VpnIdentityError.Storage)
        }

        val record = EncryptedVpnIdentityRecord(
            version = FORMAT_VERSION,
            ivHex = encrypted.ivHex,
            ciphertextHex = encrypted.ciphertextHex,
        )

        return try {
            persistence.write(record)
            VpnIdentityResult.Available(
                VpnPublicIdentity(
                    identityAvailable = true,
                    publicKey = keyPair.publicKey.toBase64(),
                ),
            )
        } catch (_: Exception) {
            VpnIdentityResult.Failure(VpnIdentityError.Storage)
        }
    }

    companion object {
        const val FORMAT_VERSION = 1
    }
}
