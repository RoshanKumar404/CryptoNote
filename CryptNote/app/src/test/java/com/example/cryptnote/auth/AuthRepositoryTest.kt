package com.example.cryptnote.auth

import com.example.cryptnote.data.remote.api.AuthApi
import com.example.cryptnote.data.remote.api.KeysApi
import com.example.cryptnote.data.remote.model.AuthResponseDto
import com.example.cryptnote.data.remote.model.GetKeysResponseDto
import com.example.cryptnote.data.remote.model.LoginRequestDto
import com.example.cryptnote.data.remote.model.PutKeysRequestDto
import com.example.cryptnote.data.remote.model.PutKeysResponseDto
import com.example.cryptnote.data.remote.model.RefreshRequestDto
import com.example.cryptnote.data.remote.model.RegisterRequestDto
import com.example.cryptnote.data.remote.model.TokenPairDto
import com.example.cryptnote.data.remote.model.UserDto
import com.example.cryptnote.data.remote.model.UserResponseDto
import com.example.cryptnote.data.remote.model.WrappedKeyRecordDto
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
import org.junit.Assert.assertNotNull
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
            cryptoManager = cryptoManager,
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
    fun login_failure_serverRejection_failsAndRemainsLocked() = runBlocking {
        val email = "bob@example.com"
        val wrongPassphrase = "wrong-passphrase-999".toCharArray()
        fakeAuthApi.loginResult = Response.error(401, "{\"message\":\"Invalid email or password\"}".toResponseBody("application/json".toMediaType()))

        val result = authRepository.login(email, wrongPassphrase)

        assertTrue(result.isFailure)
        assertFalse(keySessionManager.isUnlocked.value)
        org.junit.Assert.assertNotNull(fakeAuthApi.lastLoginRequest)
        assertEquals(email, fakeAuthApi.lastLoginRequest?.email)
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

    @Test
    fun register_withKeysApi_backsUpWrappedKeysToServer() = runBlocking {
        val email = "alice-keys@example.com"
        val passphrase = "super-secret-keys-passphrase-2026"

        fakeAuthApi.registerResult = Response.success(
            AuthResponseDto(
                accessToken = "access_jwt_123",
                refreshToken = "refresh_jwt_456",
                user = UserDto(id = "user_uuid_keys", email = email)
            )
        )

        val fakeKeysApi = FakeKeysApi()
        val repoWithKeys = AuthRepositoryImpl(
            authApi = fakeAuthApi,
            tokenStorageManager = tokenStorage,
            keySessionManager = keySessionManager,
            keyStorageManager = keyStorage,
            cryptoManager = cryptoManager,
            syncScheduler = fakeSyncScheduler,
            authEventBus = authEventBus,
            json = json,
            database = null,
            keysApi = fakeKeysApi,
            keyLifecycleManager = null
        )

        val result = repoWithKeys.register(email, passphrase.toCharArray())

        assertTrue(result.isSuccess)
        assertNotNull(fakeKeysApi.lastPutRequest)
        assertNotNull(fakeKeysApi.lastPutRequest?.passWrappedMk)
        assertNotNull(fakeKeysApi.lastPutRequest?.passSalt)
        assertNotNull(fakeKeysApi.lastPutRequest?.kcv)
    }

    @Test
    fun login_withKeysApi_kcvMismatch_rejectsAndClearsVault() = runBlocking {
        val email = "bob-keys@example.com"
        val passphrase = "valid-passphrase-123".toCharArray()

        fakeAuthApi.loginResult = Response.success(
            AuthResponseDto(
                accessToken = "login_access_token",
                refreshToken = "login_refresh_token",
                user = UserDto(id = "bob_keys_id", email = email)
            )
        )

        val fakeKeysApi = FakeKeysApi()
        // Provide a remote key record with a mismatched KCV
        val mismatchedKcvBase64 = java.util.Base64.getEncoder().encodeToString(ByteArray(8) { 0xFF.toByte() })
        fakeKeysApi.getKeysResult = Response.success(
            GetKeysResponseDto(
                exists = true,
                latest = WrappedKeyRecordDto(
                    keyVersion = 1,
                    passWrappedMk = "dummyWrapped",
                    passSalt = "dummySalt",
                    recoveryWrappedMk = "dummyRec",
                    kcv = mismatchedKcvBase64
                )
            )
        )

        val repoWithKeys = AuthRepositoryImpl(
            authApi = fakeAuthApi,
            tokenStorageManager = tokenStorage,
            keySessionManager = keySessionManager,
            keyStorageManager = keyStorage,
            cryptoManager = cryptoManager,
            syncScheduler = fakeSyncScheduler,
            authEventBus = authEventBus,
            json = json,
            database = null,
            keysApi = fakeKeysApi,
            keyLifecycleManager = null
        )

        val result = repoWithKeys.login(email, passphrase)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("key check") == true)
        assertFalse(keySessionManager.isUnlocked.value)
        assertFalse(tokenStorage.hasSession())
        assertTrue(repoWithKeys.authState.value is AuthState.Unauthenticated)
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

    private class FakeKeysApi : KeysApi {
        var getKeysResult: Response<GetKeysResponseDto>? = null
        var lastPutRequest: PutKeysRequestDto? = null
        var putKeysResult: Response<PutKeysResponseDto> = Response.success(
            PutKeysResponseDto(keyVersion = 1, status = "created")
        )

        override suspend fun getKeys(): Response<GetKeysResponseDto> {
            return getKeysResult ?: Response.success(GetKeysResponseDto(exists = false))
        }

        override suspend fun putKeys(request: PutKeysRequestDto): Response<PutKeysResponseDto> {
            lastPutRequest = request
            return putKeysResult
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

        override suspend fun resetAccount(request: LoginRequestDto): Response<AuthResponseDto> {
            lastLoginRequest = request
            return loginResult ?: throw IllegalStateException("loginResult not configured")
        }

        override suspend fun refresh(request: RefreshRequestDto): Response<TokenPairDto> =
            throw NotImplementedError()

        override suspend fun getMe(): Response<UserResponseDto> =
            throw NotImplementedError()
    }
}
