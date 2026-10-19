package com.example.cryptnote.auth

import com.example.cryptnote.data.remote.api.AuthApi
import com.example.cryptnote.data.remote.interceptor.TokenAuthenticator
import com.example.cryptnote.data.remote.model.AuthResponseDto
import com.example.cryptnote.data.remote.model.LoginRequestDto
import com.example.cryptnote.data.remote.model.RefreshRequestDto
import com.example.cryptnote.data.remote.model.RegisterRequestDto
import com.example.cryptnote.data.remote.model.TokenPairDto
import com.example.cryptnote.data.remote.model.UserResponseDto
import com.example.cryptnote.security.token.AuthEventBus
import com.example.cryptnote.security.token.AuthSessionEvent
import com.example.cryptnote.security.token.InMemoryTokenStorageManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Response as RetrofitResponse

class TokenAuthenticatorTest {

    private lateinit var tokenStorage: InMemoryTokenStorageManager
    private lateinit var fakeAuthApi: FakeAuthApi
    private lateinit var authEventBus: AuthEventBus
    private lateinit var authenticator: TokenAuthenticator

    @Before
    fun setUp() {
        tokenStorage = InMemoryTokenStorageManager()
        fakeAuthApi = FakeAuthApi()
        authEventBus = AuthEventBus()
        authenticator = TokenAuthenticator(
            tokenStorageManager = tokenStorage,
            authApiProvider = { fakeAuthApi },
            authEventBus = authEventBus
        )
    }

    @Test
    fun authenticate_refreshesTokens_and_retriesRequestWithNewBearerHeader() {
        tokenStorage.saveTokens(accessToken = "expired_token", refreshToken = "valid_refresh")
        fakeAuthApi.refreshResult = RetrofitResponse.success(
            TokenPairDto(accessToken = "new_access_token", refreshToken = "new_refresh_token")
        )

        val failedRequest = Request.Builder()
            .url("http://10.0.2.2:3000/sync/pull?since=0")
            .header("Authorization", "Bearer expired_token")
            .build()

        val response401 = Response.Builder()
            .request(failedRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()

        val retryRequest = authenticator.authenticate(null, response401)

        assertNotNull(retryRequest)
        assertEquals("Bearer new_access_token", retryRequest?.header("Authorization"))
        assertEquals("new_access_token", tokenStorage.getAccessToken())
        assertEquals("new_refresh_token", tokenStorage.getRefreshToken())
    }

    @Test
    fun authenticate_reusesToken_whenAlreadyRefreshedByAnotherThread() {
        // Token in storage has already been advanced by another thread
        tokenStorage.saveTokens(accessToken = "already_refreshed_access", refreshToken = "new_refresh")

        val failedRequest = Request.Builder()
            .url("http://10.0.2.2:3000/sync/push")
            .header("Authorization", "Bearer old_expired_token")
            .build()

        val response401 = Response.Builder()
            .request(failedRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()

        val retryRequest = authenticator.authenticate(null, response401)

        assertNotNull(retryRequest)
        assertEquals("Bearer already_refreshed_access", retryRequest?.header("Authorization"))
        // refresh API should not be called since stored token was already newer
        assertEquals(0, fakeAuthApi.refreshCallCount)
    }

    @Test
    fun authenticate_clearsSession_and_emitsForceLogout_whenRefreshFails() = runBlocking {
        tokenStorage.saveTokens(accessToken = "expired_token", refreshToken = "bad_refresh")
        fakeAuthApi.refreshResult = RetrofitResponse.error(
            401,
            "{\"message\":\"Token expired\"}".toResponseBody("application/json".toMediaType())
        )

        val failedRequest = Request.Builder()
            .url("http://10.0.2.2:3000/sync/push")
            .header("Authorization", "Bearer expired_token")
            .build()

        val response401 = Response.Builder()
            .request(failedRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()

        var logoutEventReceived: AuthSessionEvent? = null
        val eventJob = launch {
            logoutEventReceived = authEventBus.events.first()
        }

        val retryRequest = authenticator.authenticate(null, response401)

        assertNull(retryRequest)
        assertNull(tokenStorage.getAccessToken())
        assertNull(tokenStorage.getRefreshToken())

        withTimeoutOrNull(500) {
            eventJob.join()
        }
        assertEquals(AuthSessionEvent.ForceLogout, logoutEventReceived)
    }

    @Test
    fun authenticate_capsRetries_toPreventInfiniteLoop() {
        tokenStorage.saveTokens(accessToken = "expired_token", refreshToken = "valid_refresh")
        fakeAuthApi.refreshResult = RetrofitResponse.success(
            TokenPairDto(accessToken = "new_token", refreshToken = "new_refresh")
        )

        val request = Request.Builder()
            .url("http://10.0.2.2:3000/sync/push")
            .header("Authorization", "Bearer expired_token")
            .build()

        val response1 = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .build()

        val response2 = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .priorResponse(response1)
            .build()

        val response3 = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .priorResponse(response2)
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()

        val retryRequest = authenticator.authenticate(null, response3)
        assertNull(retryRequest)
    }

    private class FakeAuthApi : AuthApi {
        var refreshResult: RetrofitResponse<TokenPairDto>? = null
        var refreshCallCount = 0

        override suspend fun register(request: RegisterRequestDto): RetrofitResponse<AuthResponseDto> =
            throw NotImplementedError()

        override suspend fun login(request: LoginRequestDto): RetrofitResponse<AuthResponseDto> =
            throw NotImplementedError()

        override suspend fun resetAccount(request: LoginRequestDto): RetrofitResponse<AuthResponseDto> =
            throw NotImplementedError()

        override suspend fun refresh(request: RefreshRequestDto): RetrofitResponse<TokenPairDto> {
            refreshCallCount++
            return refreshResult ?: throw IllegalStateException("refreshResult not configured")
        }

        override suspend fun getMe(): RetrofitResponse<UserResponseDto> =
            throw NotImplementedError()
    }
}
