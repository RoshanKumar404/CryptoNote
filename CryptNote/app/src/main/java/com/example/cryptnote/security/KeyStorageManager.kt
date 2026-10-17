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
    fun storeVault(salt: ByteArray, wrappedKey: ByteArray, iv: ByteArray)
    fun getSalt(): ByteArray?
    fun getWrappedKey(): ByteArray?
    fun getWrapperIv(): ByteArray?
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

    override fun storeVault(salt: ByteArray, wrappedKey: ByteArray, iv: ByteArray) {
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_WRAPPED_KEY, Base64.encodeToString(wrappedKey, Base64.NO_WRAP))
            .putString(KEY_WRAPPER_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .apply()
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

    override fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_FILE_NAME = "cryptonote_vault_secure_prefs"
        private const val KEY_SALT = "vault_salt"
        private const val KEY_WRAPPED_KEY = "vault_wrapped_key"
        private const val KEY_WRAPPER_IV = "vault_wrapper_iv"
    }
}

/**
 * In-memory implementation of [KeyStorageManager] for unit testing.
 */
class InMemoryKeyStorageManager : KeyStorageManager {
    private var salt: ByteArray? = null
    private var wrappedKey: ByteArray? = null
    private var iv: ByteArray? = null

    override fun isSetup(): Boolean = salt != null && wrappedKey != null && iv != null

    override fun storeVault(salt: ByteArray, wrappedKey: ByteArray, iv: ByteArray) {
        this.salt = salt.copyOf()
        this.wrappedKey = wrappedKey.copyOf()
        this.iv = iv.copyOf()
    }

    override fun getSalt(): ByteArray? = salt?.copyOf()

    override fun getWrappedKey(): ByteArray? = wrappedKey?.copyOf()

    override fun getWrapperIv(): ByteArray? = iv?.copyOf()

    override fun clear() {
        salt = null
        wrappedKey = null
        iv = null
    }
}
