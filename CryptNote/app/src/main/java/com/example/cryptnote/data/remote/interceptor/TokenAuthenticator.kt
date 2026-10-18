package com.example.cryptnote.data.remote.interceptor

import com.example.cryptnote.data.remote.api.AuthApi
import com.example.cryptnote.data.remote.model.RefreshRequestDto
import com.example.cryptnote.security.token.AuthEventBus
import com.example.cryptnote.security.token.TokenStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * OkHttp [Authenticator] that handles 401 Unauthorized responses.
 * Refreshes the JWT token pair synchronously using a lock to prevent concurrent refresh races,
 * updates [TokenStorageManager], retries the failed request, or emits [AuthEventBus.emitForceLogout] on failure.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenStorageManager: TokenStorageManager,
    private val authApiProvider: Provider<AuthApi>,
    private val authEventBus: AuthEventBus
) : Authenticator {

    private val refreshLock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Prevent infinite loops: cap retries to 2 attempts per request
        if (responseCount(response) >= 3) {
            return null
        }

        val requestHeader = response.request.header("Authorization")
        val requestToken = requestHeader?.removePrefix("Bearer ")?.trim()

        synchronized(refreshLock) {
            val currentAccessToken = tokenStorageManager.getAccessToken()

            // 1. If another thread already refreshed the token while this request was failing,
            // retry immediately with the updated access token without calling /auth/refresh again.
            if (!currentAccessToken.isNullOrBlank() && currentAccessToken != requestToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentAccessToken")
                    .build()
            }

            // 2. Fetch the stored refresh token
            val currentRefreshToken = tokenStorageManager.getRefreshToken()
            if (currentRefreshToken.isNullOrBlank()) {
                handleSessionFailure()
                return null
            }

            // 3. Perform synchronous token refresh call
            return try {
                val refreshResult = runBlocking(Dispatchers.IO) {
                    authApiProvider.get().refresh(RefreshRequestDto(refreshToken = currentRefreshToken))
                }

                if (refreshResult.isSuccessful && refreshResult.body() != null) {
                    val tokens = refreshResult.body()!!
                    tokenStorageManager.saveTokens(
                        accessToken = tokens.accessToken,
                        refreshToken = tokens.refreshToken
                    )

                    // Rebuild the request with the new access token
                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${tokens.accessToken}")
                        .build()
                } else {
                    handleSessionFailure()
                    null
                }
            } catch (_: Exception) {
                handleSessionFailure()
                null
            }
        }
    }

    private fun handleSessionFailure() {
        tokenStorageManager.clearSession()
        authEventBus.emitForceLogout()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
