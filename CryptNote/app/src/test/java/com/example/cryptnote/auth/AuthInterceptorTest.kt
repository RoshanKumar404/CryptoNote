package com.example.cryptnote.auth

import com.example.cryptnote.data.remote.interceptor.AuthInterceptor
import com.example.cryptnote.security.token.InMemoryTokenStorageManager
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class AuthInterceptorTest {

    private lateinit var tokenStorage: InMemoryTokenStorageManager
    private lateinit var authInterceptor: AuthInterceptor

    @Before
    fun setUp() {
        tokenStorage = InMemoryTokenStorageManager()
        authInterceptor = AuthInterceptor(tokenStorage)
    }

    @Test
    fun intercept_attachesBearerToken_whenTokenAvailableAndPathAuthenticated() {
        tokenStorage.saveTokens(accessToken = "my_jwt_token_123", refreshToken = "refresh_456")

        val request = Request.Builder()
            .url("http://10.0.2.2:3000/sync/push")
            .build()

        val chain = FakeChain(request)
        authInterceptor.intercept(chain)

        val executedRequest = chain.executedRequest
        assertEquals("Bearer my_jwt_token_123", executedRequest?.header("Authorization"))
    }

    @Test
    fun intercept_skipsAuthorizationHeader_whenPathIsLogin() {
        tokenStorage.saveTokens(accessToken = "my_jwt_token_123", refreshToken = "refresh_456")

        val request = Request.Builder()
            .url("http://10.0.2.2:3000/auth/login")
            .build()

        val chain = FakeChain(request)
        authInterceptor.intercept(chain)

        val executedRequest = chain.executedRequest
        assertNull(executedRequest?.header("Authorization"))
    }

    @Test
    fun intercept_skipsAuthorizationHeader_whenPathIsRegister() {
        tokenStorage.saveTokens(accessToken = "my_jwt_token_123", refreshToken = "refresh_456")

        val request = Request.Builder()
            .url("http://10.0.2.2:3000/auth/register")
            .build()

        val chain = FakeChain(request)
        authInterceptor.intercept(chain)

        assertNull(chain.executedRequest?.header("Authorization"))
    }

    @Test
    fun intercept_skipsAuthorizationHeader_whenPathIsRefresh() {
        tokenStorage.saveTokens(accessToken = "my_jwt_token_123", refreshToken = "refresh_456")

        val request = Request.Builder()
            .url("http://10.0.2.2:3000/auth/refresh")
            .build()

        val chain = FakeChain(request)
        authInterceptor.intercept(chain)

        assertNull(chain.executedRequest?.header("Authorization"))
    }

    @Test
    fun intercept_proceedsWithoutHeader_whenTokenNotStored() {
        tokenStorage.clearSession()

        val request = Request.Builder()
            .url("http://10.0.2.2:3000/sync/pull?since=0")
            .build()

        val chain = FakeChain(request)
        authInterceptor.intercept(chain)

        assertNull(chain.executedRequest?.header("Authorization"))
    }

    private class FakeChain(private val request: Request) : Interceptor.Chain {
        var executedRequest: Request? = null

        override fun request(): Request = request

        override fun proceed(request: Request): Response {
            executedRequest = request
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }

        override fun connection(): Connection? = null
        override fun call(): okhttp3.Call = throw NotImplementedError()
        override fun connectTimeoutMillis(): Int = 0
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun readTimeoutMillis(): Int = 0
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun writeTimeoutMillis(): Int = 0
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }
}
