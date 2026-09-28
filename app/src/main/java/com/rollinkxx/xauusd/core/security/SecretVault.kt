package com.rollinkxx.xauusd.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.rollinkxx.xauusd.domain.model.MarketProviderId
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecretVault(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("secure_secrets", Context.MODE_PRIVATE)
    private val alias = "xauusd_user_provider_key_v1"

    fun saveApiKey(provider: MarketProviderId, value: String) {
        if (value.isBlank()) { clearApiKey(provider); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + encrypted
        check(prefs.edit().putString(keyFor(provider), Base64.encodeToString(packed, Base64.NO_WRAP)).commit()) {
            "Could not store encrypted ${provider.displayName} API key."
        }
    }

    fun loadApiKey(provider: MarketProviderId): String? {
        val encoded = prefs.getString(keyFor(provider), null) ?: return null
        return try {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            require(packed.size > IV_LENGTH)
            val iv = packed.copyOfRange(0, IV_LENGTH)
            val ciphertext = packed.copyOfRange(IV_LENGTH, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        } catch (error: Exception) {
            clearApiKey(provider)
            throw IllegalStateException("Stored ${provider.displayName} key could not be decrypted. Please enter it again.", error)
        }
    }

    fun clearApiKey(provider: MarketProviderId) { prefs.edit().remove(keyFor(provider)).apply() }

    /** Removes the pre-provider-selection secret so it cannot be sent to an unintended service after upgrade. */
    fun clearLegacyApiKey(): Boolean {
        val existed = prefs.contains(LEGACY_KEY)
        if (existed) check(prefs.edit().remove(LEGACY_KEY).commit()) { "Could not remove the legacy provider key." }
        return existed
    }

    private fun keyFor(provider: MarketProviderId) = "provider_api_key_${provider.preferenceKey}_ciphertext"

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    private companion object {
        const val LEGACY_KEY = "twelvedata_api_key_ciphertext"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
    }
}
