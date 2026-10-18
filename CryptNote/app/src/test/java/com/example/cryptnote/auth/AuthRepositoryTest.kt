package com.example.cryptnote.auth

import com.example.cryptnote.data.remote.api.AuthApi
import com.example.cryptnote.data.remote.model.AuthResponseDto
import com.example.cryptnote.data.remote.model.LoginRequestDto
import com.example.cryptnote.data.remote.model.RefreshRequestDto
import com.example.cryptnote.data.remote.model.RegisterRequestDto
import com.example.cryptnote.data.remote.model.TokenPairDto
import com.example.cryptnote.data.remote.model.UserDto
import com.example.cryptnote.data.remote.model.UserResponseDto
import com.example.cryptnote.data.repository.AuthRepository
import com.example.cryptnote.data.repository.AuthRepositoryImpl
import com.example.cryptnote.data.repository.AuthState
import com.example.cryptnote.security.CryptoManager
import com.example.cryptnote.security.CryptoManagerImpl
import com.example.cryptnote.security.InMemoryKeyStorageManager
import com.example.cryptnote.security.JvmKeyWrapper
import com.example.cryptnote.security.KeySessionManager
import com.example.cryptnote.security.KeySessionManagerImpl
import com.example.cryptnote.security.KeyStorageManager
import com.example.cryptnote.security.KeyWrapper
import com.example.cryptnote.security.token.AuthEventBus
import com.example.cryptnote.security.token.InMemoryTokenStorageManager
import com.example.cryptnote.sync.SyncScheduler
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class AuthRepositoryTest {

    private lateinit var tokenStorage: InMemoryTokenStorageManager
    private lateinit var keyStorage: KeyStorageManager
    private lateinit var cryptoManager: CryptoManager
    private lateinit var keyWrapper: KeyWrapper
    private lateinit var keySessionManager: KeySessionManager
    private lateinit var fakeAuthApi: FakeAuthApi
    private lateinit var fakeSyncScheduler: FakeSyncScheduler
    private lateinit var authEventBus: AuthEventBus
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var authRepository: AuthRepository

    @Before
    fun setUp() {
        tokenStorage = InMemoryTokenStorageManager()
        keyStorage = InMemoryKeyStorageManager()
        cryptoManager = CryptoManagerImpl()
        keyWrapper = JvmKeyWrapper()
        keySessionManager = KeySessionManagerImpl(
            cryptoManager = cryptoManager,
            keyWrapper = keyWrapper,
            keyStorageManager = keyStorage
        )
        fakeAuthApi = FakeAuthApi()
        fakeSyncScheduler = FakeSyncScheduler()
        authEventBus = AuthEventBus()

        authRepository = AuthRepositoryImpl(
            authApi = fakeAuthApi,
            tokenStorageManager = tokenStorage,
            keySessionManager = keySessionManager,
            keyStorageManager = keyStorage,
            syncScheduler = fakeSyncScheduler,
            authEventBus = authEventBus,
            json = json
        )
    }

    @Test
    fun register_success_derivesZeroKnowledgeAuthKey_storesTokens_and_emitsAuthenticated() = runBlocking {
        val email = "alice@example.com"
        val rawPassphrase = "super-secret-user-passphrase-2026"

        fakeAuthApi.registerResult = Response.success(
            AuthResponseDto(
                accessToken = "access_jwt_123",
                refreshToken = "refresh_jwt_456",
                user = UserDto(id = "user_uuid_999", email = email)
            )
        )

        val result = authRepository.register(email, rawPassphrase.toCharArray())

        assertTrue(result.isSuccess)
        assertEquals("access_jwt_123", tokenStorage.getAccessToken())
        assertEquals("refresh_jwt_456", tokenStorage.getRefreshToken())
        assertEquals("user_uuid_999", tokenStorage.getUserId())
        assertTrue(keySessionManager.isUnlocked.value)
        assertTrue(keyStorage.isSetup())
        assertTrue(fakeSyncScheduler.immediateSyncScheduled)

        // Verify zero-knowledge guarantee: server received 64-char hex auth key, never raw passphrase
        val sentPassword = fakeAuthApi.lastRegisterRequest?.password.orEmpty()
        assertEquals(64, sentPassword.length)
        assertFalse(sentPassword.contains("super-secret"))
        assertTrue(sentPassword.matches(Regex("^[0-9a-f]{64}$")))

        val currentState = authRepository.authState.value
        assertTrue(currentState is AuthState.Authenticated)
        assertEquals("user_uuid_999", (currentState as AuthState.Authenticated).userId)
    }

    @Test
    fun register_failure_cleansUpLocalVault_and_returnsError() = runBlocking {
        val email = "existing@example.com"
        fakeAuthApi.registerResult = Response.error(
            409,
            "{\"message\":\"An account with this email address already exists\"}"
                .toResponseBody("application/json".toMediaType())
        )

        val result = authRepository.register(email, "passphrase123".toCharArray())

        assertTrue(result.isFailure)
        assertEquals("An account with this email address already exists", result.exceptionOrNull()?.message)
        // Verify local vault was rolled back completely
        assertFalse(keyStorage.isSetup())
        assertFalse(keySessionManager.isUnlocked.value)
        assertFalse(tokenStorage.hasSession())
        assertTrue(authRepository.authState.value is AuthState.Unauthenticated)
    }

    @Test
    fun login_success_existingVault_unlocksAndSavesSession() = runBlocking {
        val email = "bob@example.com"
        val passphrase = "valid-passphrase-123".toCharArray()

        // Setup existing vault
        keySessionManager.setupPassphrase(passphrase)
        keySessionManager.lock()
        assertFalse(keySessionManager.isUnlocked.value)

        fakeAuthApi.loginResult = Response.success(
            AuthResponseDto(
                accessToken = "login_access_token",
                refreshToken = "login_refresh_token",
                user = UserDto(id = "bob_id", email = email)
            )
        )

        val result = authRepository.login(email, passphrase)

        assertTrue(result.isSuccess)
        assertTrue(keySessionManager.isUnlocked.value)
        assertEquals("login_access_token", tokenStorage.getAccessToken())
        assertEquals("bob_id", tokenStorage.getUserId())
        assertTrue(fakeSyncScheduler.immediateSyncScheduled)
        assertTrue(authRepository.authState.value is AuthState.Authenticated)
    }

    @Test
    fun login_failure_invalidPassphrase_failsWithoutCallingServer() = runBlocking {
        val email = "bob@example.com"
        val realPassphrase = "correct-passphrase-123".toCharArray()
        keySessionManager.setupPassphrase(realPassphrase)
        keySessionManager.lock()

        val wrongPassphrase = "wrong-passphrase-999".toCharArray()
        val result = authRepository.login(email, wrongPassphrase)

        assertTrue(result.isFailure)
        assertFalse(keySessionManager.isUnlocked.value)
        assertNull(fakeAuthApi.lastLoginRequest)
    }

    @Test
    fun logout_clearsSession_locksVault_and_cancelsSync() = runBlocking {
        tokenStorage.saveSession("token", "refresh", "id", "email@test.com")
        keySessionManager.setupPassphrase("my-passphrase-123".toCharArray())
        assertTrue(keySessionManager.isUnlocked.value)

        authRepository.logout()

        assertFalse(tokenStorage.hasSession())
        assertFalse(keySessionManager.isUnlocked.value)
        assertTrue(fakeSyncScheduler.cancelAllCalled)
        assertTrue(authRepository.authState.value is AuthState.Unauthenticated)
    }

    private class FakeSyncScheduler : SyncScheduler {
        var periodicScheduled = false
        var immediateSyncScheduled = false
        var cancelAllCalled = false

        override fun schedulePeriodic() {
            periodicScheduled = true
        }

        override fun scheduleImmediate() {
            immediateSyncScheduled = true
        }

        override fun cancelAll() {
            cancelAllCalled = true
        }
    }

    private class FakeAuthApi : AuthApi {
        var registerResult: Response<AuthResponseDto>? = null
        var lastRegisterRequest: RegisterRequestDto? = null

        var loginResult: Response<AuthResponseDto>? = null
        var lastLoginRequest: LoginRequestDto? = null

        override suspend fun register(request: RegisterRequestDto): Response<AuthResponseDto> {
            lastRegisterRequest = request
            return registerResult ?: throw IllegalStateException("registerResult not configured")
        }

        override suspend fun login(request: LoginRequestDto): Response<AuthResponseDto> {
            lastLoginRequest = request
            return loginResult ?: throw IllegalStateException("loginResult not configured")
        }

        override suspend fun refresh(request: RefreshRequestDto): Response<TokenPairDto> =
            throw NotImplementedError()

        override suspend fun getMe(): Response<UserResponseDto> =
            throw NotImplementedError()
    }
}
