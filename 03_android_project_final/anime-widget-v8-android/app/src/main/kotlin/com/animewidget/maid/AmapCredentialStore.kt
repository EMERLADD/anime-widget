package com.animewidget.maid

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores Amap Web Service credentials encrypted by an app-private Android Keystore key. */
object AmapCredentialStore {
    data class Credentials(val apiKey: String, val privateKey: String)

    private const val ALIAS = "anime_widget_amap_credentials_v1"
    private const val PREFS = "amap_secure_credentials"
    private const val KEY_API = "api_key_ciphertext"
    private const val KEY_PRIVATE = "private_key_ciphertext"

    fun save(context: Context, apiKey: String, privateKey: String) {
        require(apiKey.isNotBlank()) { "Web Service Key 不能为空" }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_API, encrypt(apiKey.trim()))
            .putString(KEY_PRIVATE, encrypt(privateKey.trim()))
            .apply()
    }

    fun load(context: Context): Credentials? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val api = prefs.getString(KEY_API, null) ?: return null
        val privateKey = prefs.getString(KEY_PRIVATE, null)
        return runCatching { Credentials(decrypt(api), privateKey?.let(::decrypt).orEmpty()) }.getOrNull()
            ?.takeIf { it.apiKey.isNotBlank() }
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        require(packed.size > 12) { "凭据密文损坏" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
        return String(cipher.doFinal(packed.copyOfRange(12, packed.size)), StandardCharsets.UTF_8)
    }
}
