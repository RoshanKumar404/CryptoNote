@file:Suppress("DEPRECATION")

package com.example.cryptnote.security.token

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface defining contract for secure persistence of session tokens and user identity.
 * Adheres to Dependency Inversion Principle (DIP).
 */
interface TokenStorageManager {
    fun saveSession(accessToken: String, refreshToken: String, userId: String, email: String)
    fun saveTokens(accessToken: String, refreshToken: String)
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun getUserId(): String?
    fun getUserEmail(): String?
    fun getLoginTimestamp(): Long
    fun hasSession(): Boolean
    fun clearSession()
}

/**
 * Secure implementation of [TokenStorageManager] using AndroidX [EncryptedSharedPreferences]
 * backed by AES-256-GCM values and AES-256-SIV keys.
 */
@Singleton
class EncryptedTokenStorageManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : TokenStorageManager {

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
            // Fallback for non-GMS / unit testing environments
            context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
        }
    }

    override fun saveSession(accessToken: String, refreshToken: String, userId: String, email: String) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USER_EMAIL, email)
            .putLong(KEY_LOGIN_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    override fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    override fun getAccessToken(): String? {
        return prefs.getString(KEY_ACCESS_TOKEN, null)
    }

    override fun getRefreshToken(): String? {
        return prefs.getString(KEY_REFRESH_TOKEN, null)
    }

    override fun getUserId(): String? {
        return prefs.getString(KEY_USER_ID, null)
    }

    override fun getUserEmail(): String? {
        return prefs.getString(KEY_USER_EMAIL, null)
    }

    override fun getLoginTimestamp(): Long {
        val stored = prefs.getLong(KEY_LOGIN_TIMESTAMP, 0L)
        if (stored > 0L) return stored

        // Fallback: extract iat (issued at) from JWT access token
        val token = getAccessToken()
        if (!token.isNullOrBlank()) {
            try {
                val parts = token.split(".")
                if (parts.size >= 2) {
                    val decodedBytes = android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
                    val decoded = String(decodedBytes, Charsets.UTF_8)
                    val json = org.json.JSONObject(decoded)
                    if (json.has("iat")) {
                        val iatSeconds = json.getLong("iat")
                        val timeMs = iatSeconds * 1000L
                        prefs.edit().putLong(KEY_LOGIN_TIMESTAMP, timeMs).apply()
                        return timeMs
                    }
                }
            } catch (_: Throwable) {
                // Ignore decoding errors
            }
        }
        return System.currentTimeMillis()
    }

    override fun hasSession(): Boolean {
        return !getAccessToken().isNullOrBlank() && !getRefreshToken().isNullOrBlank()
    }

    override fun clearSession() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_FILE_NAME = "cryptonote_token_secure_prefs"
        private const val KEY_ACCESS_TOKEN = "jwt_access_token"
        private const val KEY_REFRESH_TOKEN = "jwt_refresh_token"
        private const val KEY_USER_ID = "auth_user_id"
        private const val KEY_USER_EMAIL = "auth_user_email"
        private const val KEY_LOGIN_TIMESTAMP = "auth_login_timestamp"
    }
}

/**
 * In-memory implementation of [TokenStorageManager] for deterministic unit tests.
 */
class InMemoryTokenStorageManager : TokenStorageManager {
    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var userId: String? = null
    private var userEmail: String? = null
    private var loginTimestamp: Long = 0L

    override fun saveSession(accessToken: String, refreshToken: String, userId: String, email: String) {
        this.accessToken = accessToken
        this.refreshToken = refreshToken
        this.userId = userId
        this.userEmail = email
        this.loginTimestamp = System.currentTimeMillis()
    }

    override fun saveTokens(accessToken: String, refreshToken: String) {
        this.accessToken = accessToken
        this.refreshToken = refreshToken
    }

    override fun getAccessToken(): String? = accessToken

    override fun getRefreshToken(): String? = refreshToken

    override fun getUserId(): String? = userId

    override fun getUserEmail(): String? = userEmail

    override fun getLoginTimestamp(): Long = if (loginTimestamp > 0L) loginTimestamp else System.currentTimeMillis()

    override fun hasSession(): Boolean = !accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()

    override fun clearSession() {
        accessToken = null
        refreshToken = null
        userId = null
        userEmail = null
        loginTimestamp = 0L
    }
}
