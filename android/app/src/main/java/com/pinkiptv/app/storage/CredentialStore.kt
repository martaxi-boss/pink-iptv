package com.pinkiptv.app.storage

data class StoredCredentials(
    val username: String,
    val password: String,
)

data class EncryptedPayload(
    val ivHex: String,
    val ciphertextHex: String,
)

data class EncryptedCredentialRecord(
    val username: String,
    val ivHex: String,
    val ciphertextHex: String,
)

interface CredentialStore {
    suspend fun load(): StoredCredentials?
    suspend fun save(username: String, password: String)
    suspend fun clear()
}

interface CredentialCipher {
    fun encrypt(plaintext: String): EncryptedPayload
    fun decrypt(payload: EncryptedPayload): String
    fun destroyKey()
}

interface CredentialPersistence {
    suspend fun read(): EncryptedCredentialRecord?
    suspend fun write(record: EncryptedCredentialRecord)
    suspend fun clear()
}

class SecureCredentialStore(
    private val cipher: CredentialCipher,
    private val persistence: CredentialPersistence,
) : CredentialStore {
    override suspend fun load(): StoredCredentials? {
        val record = persistence.read() ?: return null
        return try {
            StoredCredentials(
                username = record.username,
                password = cipher.decrypt(
                    EncryptedPayload(
                        ivHex = record.ivHex,
                        ciphertextHex = record.ciphertextHex,
                    ),
                ),
            )
        } catch (_: Exception) {
            clear()
            null
        }
    }

    override suspend fun save(username: String, password: String) {
        val encrypted = cipher.encrypt(password)
        persistence.write(
            EncryptedCredentialRecord(
                username = username,
                ivHex = encrypted.ivHex,
                ciphertextHex = encrypted.ciphertextHex,
            ),
        )
    }

    override suspend fun clear() {
        persistence.clear()
        cipher.destroyKey()
    }
}
