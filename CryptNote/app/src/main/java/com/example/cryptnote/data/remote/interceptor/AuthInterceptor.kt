package com.example.cryptnote.data.remote.interceptor

import com.example.cryptnote.security.token.TokenStorageManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp [Interceptor] that dynamically attaches the Bearer JWT access token to outbound requests.
 * Exempts unauthenticated endpoints like /auth/register, /auth/login, and /auth/refresh.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStorageManager: TokenStorageManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val path = originalRequest.url.encodedPath

        // Do not attach authorization header to public authentication endpoints
        if (isUnauthenticatedPath(path)) {
            return chain.proceed(originalRequest)
        }

        val token = tokenStorageManager.getAccessToken()
        return if (!token.isNullOrBlank() && originalRequest.header("Authorization") == null) {
            val authenticatedRequest = originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
            chain.proceed(authenticatedRequest)
        } else {
            chain.proceed(originalRequest)
        }
    }

    private fun isUnauthenticatedPath(path: String): Boolean {
        return path.endsWith("/auth/register") ||
                path.endsWith("/auth/login") ||
                path.endsWith("/auth/refresh")
    }
}
