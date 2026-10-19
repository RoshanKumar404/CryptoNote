package com.example.cryptnote.security.biometric

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.inject.Inject
import javax.inject.Singleton

interface BiometricKeyManager {
    fun createEncryptCipher(): Cipher
    fun createDecryptCipher(iv: ByteArray): Cipher
    fun deleteBiometricKey()
}

@Singleton
class AndroidBiometricKeyManager @Inject constructor() : BiometricKeyManager {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    private fun getOrCreateBiometricKeyPair() {
        // If legacy SecretKey exists under this alias, remove it so RSA key pair can be stored
        if (keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
            val entry = try { keyStore.getEntry(BIOMETRIC_KEY_ALIAS, null) } catch (_: Exception) { null }
            if (entry !is KeyStore.PrivateKeyEntry) {
                keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS)
            }
        }

        if (!keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                ANDROID_KEYSTORE
            )

            val builder = KeyGenParameterSpec.Builder(
                BIOMETRIC_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            } else {
                @Suppress("DEPRECATION")
                builder.setUserAuthenticationRequired(true)
            }

            kpg.initialize(builder.build())
            kpg.generateKeyPair()
        }
    }

    private fun getBiometricPublicKey(): PublicKey {
        getOrCreateBiometricKeyPair()
        val cert = keyStore.getCertificate(BIOMETRIC_KEY_ALIAS)
            ?: throw IllegalStateException("Could not retrieve biometric certificate")
        return cert.publicKey
    }

    private fun getBiometricPrivateKey(): PrivateKey {
        getOrCreateBiometricKeyPair()
        val entry = keyStore.getEntry(BIOMETRIC_KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            ?: throw IllegalStateException("Could not retrieve biometric private key from Keystore")
        return entry.privateKey
    }

    override fun createEncryptCipher(): Cipher {
        val publicKey = getBiometricPublicKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA1,
            PSource.PSpecified.DEFAULT
        )
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, spec)
        return cipher
    }

    override fun createDecryptCipher(iv: ByteArray): Cipher {
        val privateKey = getBiometricPrivateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA1,
            PSource.PSpecified.DEFAULT
        )
        cipher.init(Cipher.DECRYPT_MODE, privateKey, spec)
        return cipher
    }

    override fun deleteBiometricKey() {
        if (keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
            keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS)
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val BIOMETRIC_KEY_ALIAS = "cryptnote_biometric_auth_key"
        private const val TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
    }
}
