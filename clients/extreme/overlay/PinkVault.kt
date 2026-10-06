package com.pinkiptv.extreme

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Local account blob: AES-GCM, non-exportable Android Keystore key, backups disabled. */
class PinkVault(private val context: Context) {
  companion object {
    @Volatile private var validatedProcessBlob: String? = null
  }
  private val alias = "pink.extreme.account.v1"
  private val prefs = context.getSharedPreferences("pink_account_v1", Context.MODE_PRIVATE)
  @Synchronized private fun key(): SecretKey {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (store.getKey(alias, null) as? SecretKey)?.let { return it }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setRandomizedEncryptionRequired(true).build())
    return generator.generateKey()
  }
  @Synchronized fun read(): String {
    val encoded = prefs.getString("account", null) ?: return ""
    return try {
      val bytes = Base64.decode(encoded, Base64.NO_WRAP)
      require(bytes.size > 28)
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
      String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    } catch (_: Exception) { throw IllegalStateException("Saved account unavailable") }
  }
  @Synchronized fun readValidated(): String = validatedProcessBlob ?: ""
  @Synchronized fun markValidated(value: String): Boolean {
    require(value.length <= 131072)
    validatedProcessBlob = value
    return true
  }
  @Synchronized fun write(value: String): Boolean {
    require(value.length <= 131072)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key())
    val bytes = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
    val committed = prefs.edit().putString("account", Base64.encodeToString(bytes, Base64.NO_WRAP)).commit()
    if (committed) validatedProcessBlob = value
    return committed
  }
}
