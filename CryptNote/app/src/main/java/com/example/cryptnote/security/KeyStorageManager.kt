@file:Suppress("DEPRECATION")

package com.example.cryptnote.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface managing persistence of wrapped keys, salt, and IVs.
 */
interface KeyStorageManager {
    fun isSetup(): Boolean
    fun storeVault(salt: ByteArray, wrappedKey: ByteArray, iv: ByteArray, kcv: ByteArray? = null, keyVersion: Int = 1)
    fun getSalt(): ByteArray?
    fun getWrappedKey(): ByteArray?
    fun getWrapperIv(): ByteArray?
    fun getKcv(): ByteArray?
    fun getKeyVersion(): Int
    fun storeBiometricVault(wrappedKey: ByteArray, iv: ByteArray)
    fun getBiometricWrappedKey(): ByteArray?
    fun getBiometricIv(): ByteArray?
    fun isBiometricEnabled(): Boolean
    fun setBiometricEnabled(enabled: Boolean)
    fun clearBiometric()
    fun clear()
}

/**
 * Secure persistent storage for vault metadata backed by [EncryptedSharedPreferences].
 */
@Singleton
class KeyStorageManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : KeyStorageManager {

    private val prefs: SharedPreferences by lazy {
        createEncryptedPrefs()
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            // Fallback for tests / non-GMS environments
            context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
        }
    }

    override fun isSetup(): Boolean {
        return getSalt() != null && getWrappedKey() != null && getWrapperIv() != null
    }

    override fun storeVault(salt: ByteArray, wrappedKey: ByteArray, iv: ByteArray, kcv: ByteArray?, keyVersion: Int) {
        val editor = prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_WRAPPED_KEY, Base64.encodeToString(wrappedKey, Base64.NO_WRAP))
            .putString(KEY_WRAPPER_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .putInt(KEY_KEY_VERSION, keyVersion)

        if (kcv != null) {
            editor.putString(KEY_KCV, Base64.encodeToString(kcv, Base64.NO_WRAP))
        } else {
            editor.remove(KEY_KCV)
        }
        editor.apply()
    }

    override fun getSalt(): ByteArray? {
        val encoded = prefs.getString(KEY_SALT, null) ?: return null
        return try {
            Base64.decode(encoded, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    override fun getWrappedKey(): ByteArray? {
        val encoded = prefs.getString(KEY_WRAPPED_KEY, null) ?: return null
        return try {
            Base64.decode(encoded, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    override fun getWrapperIv(): ByteArray? {
        val encoded = prefs.getString(KEY_WRAPPER_IV, null) ?: return null
        return try {
            Base64.decode(encoded, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    override fun getKcv(): ByteArray? {
        val encoded = prefs.getString(KEY_KCV, null) ?: return null
        return try {
            Base64.decode(encoded, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    override fun getKeyVersion(): Int {
        return prefs.getInt(KEY_KEY_VERSION, 1)
    }

    override fun storeBiometricVault(wrappedKey: ByteArray, iv: ByteArray) {
        prefs.edit()
            .putString(KEY_BIOMETRIC_WRAPPED_KEY, Base64.encodeToString(wrappedKey, Base64.NO_WRAP))
            .putString(KEY_BIOMETRIC_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .putBoolean(KEY_BIOMETRIC_ENABLED, true)
            .apply()
    }

    override fun getBiometricWrappedKey(): ByteArray? {
        val encoded = prefs.getString(KEY_BIOMETRIC_WRAPPED_KEY, null) ?: return null
        return try {
            Base64.decode(encoded, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    override fun getBiometricIv(): ByteArray? {
        val encoded = prefs.getString(KEY_BIOMETRIC_IV, null) ?: return null
        return try {
            Base64.decode(encoded, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    override fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false) &&
                getBiometricWrappedKey() != null &&
                getBiometricIv() != null
    }

    override fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    override fun clearBiometric() {
        prefs.edit()
            .remove(KEY_BIOMETRIC_WRAPPED_KEY)
            .remove(KEY_BIOMETRIC_IV)
            .putBoolean(KEY_BIOMETRIC_ENABLED, false)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_FILE_NAME = "cryptonote_vault_secure_prefs"
        private const val KEY_SALT = "vault_salt"
        private const val KEY_WRAPPED_KEY = "vault_wrapped_key"
        private const val KEY_WRAPPER_IV = "vault_wrapper_iv"
        private const val KEY_KCV = "vault_kcv"
        private const val KEY_KEY_VERSION = "vault_key_version"
        private const val KEY_BIOMETRIC_WRAPPED_KEY = "vault_biometric_wrapped_key"
        private const val KEY_BIOMETRIC_IV = "vault_biometric_iv"
        private const val KEY_BIOMETRIC_ENABLED = "vault_biometric_enabled"
    }
}

/**
 * In-memory implementation of [KeyStorageManager] for unit testing.
 */
class InMemoryKeyStorageManager : KeyStorageManager {
    private var salt: ByteArray? = null
    private var wrappedKey: ByteArray? = null
    private var iv: ByteArray? = null
    private var kcv: ByteArray? = null
    private var keyVersion: Int = 1
    private var biometricWrappedKey: ByteArray? = null
    private var biometricIv: ByteArray? = null
    private var biometricEnabled: Boolean = false

    override fun isSetup(): Boolean = salt != null && wrappedKey != null && iv != null

    override fun storeVault(salt: ByteArray, wrappedKey: ByteArray, iv: ByteArray, kcv: ByteArray?, keyVersion: Int) {
        this.salt = salt.copyOf()
        this.wrappedKey = wrappedKey.copyOf()
        this.iv = iv.copyOf()
        this.kcv = kcv?.copyOf()
        this.keyVersion = keyVersion
    }

    override fun getSalt(): ByteArray? = salt?.copyOf()

    override fun getWrappedKey(): ByteArray? = wrappedKey?.copyOf()

    override fun getWrapperIv(): ByteArray? = iv?.copyOf()

    override fun getKcv(): ByteArray? = kcv?.copyOf()

    override fun getKeyVersion(): Int = keyVersion

    override fun storeBiometricVault(wrappedKey: ByteArray, iv: ByteArray) {
        this.biometricWrappedKey = wrappedKey.copyOf()
        this.biometricIv = iv.copyOf()
        this.biometricEnabled = true
    }

    override fun getBiometricWrappedKey(): ByteArray? = biometricWrappedKey?.copyOf()

    override fun getBiometricIv(): ByteArray? = biometricIv?.copyOf()

    override fun isBiometricEnabled(): Boolean = biometricEnabled && biometricWrappedKey != null && biometricIv != null

    override fun setBiometricEnabled(enabled: Boolean) {
        this.biometricEnabled = enabled
    }

    override fun clearBiometric() {
        biometricWrappedKey = null
        biometricIv = null
        biometricEnabled = false
    }

    override fun clear() {
        salt = null
        wrappedKey = null
        iv = null
        clearBiometric()
    }
}
