package com.example.cryptnote.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Abstraction for wrapping and unwrapping the master encryption key.
 */
interface KeyWrapper {
    fun wrapKey(rawKey: ByteArray): WrappedKeyResult
    fun unwrapKey(wrappedKey: ByteArray, iv: ByteArray): ByteArray
}

/**
 * Hardware-backed key wrapping using Android Keystore.
 * Generates an AES-256 key inside Android Keystore (alias: "cryptonote_master_key")
 * and wraps/unwraps the master key with AES-GCM.
 */
@Singleton
class AndroidKeystoreWrapper @Inject constructor() : KeyWrapper {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    private fun getOrCreateWrappingKey(): SecretKey {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val keyGenSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()

            keyGenerator.init(keyGenSpec)
            keyGenerator.generateKey()
        }

        val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
        return entry.secretKey
    }

    override fun wrapKey(rawKey: ByteArray): WrappedKeyResult {
        val wrappingKey = getOrCreateWrappingKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey)
        val iv = cipher.iv
        val wrappedBytes = cipher.doFinal(rawKey)
        return WrappedKeyResult(wrappedKey = wrappedBytes, iv = iv)
    }

    override fun unwrapKey(wrappedKey: ByteArray, iv: ByteArray): ByteArray {
        val wrappingKey = getOrCreateWrappingKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey, spec)
        return cipher.doFinal(wrappedKey)
    }

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "cryptonote_master_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
    }
}

/**
 * Pure JVM AES-GCM key wrapper for unit tests and local environments where
 * Android Keystore is unavailable.
 */
class JvmKeyWrapper(
    private val keyBytes: ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }
) : KeyWrapper {

    private val secretKey: SecretKey = SecretKeySpec(keyBytes, "AES")
    private val secureRandom = SecureRandom()

    override fun wrapKey(rawKey: ByteArray): WrappedKeyResult {
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val wrapped = cipher.doFinal(rawKey)
        return WrappedKeyResult(wrappedKey = wrapped, iv = iv)
    }

    override fun unwrapKey(wrappedKey: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(wrappedKey)
    }
}
