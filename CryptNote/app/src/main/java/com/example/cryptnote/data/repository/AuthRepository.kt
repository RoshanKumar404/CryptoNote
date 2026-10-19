package com.example.cryptnote.data.repository

import com.example.cryptnote.data.remote.api.AuthApi
import com.example.cryptnote.data.remote.model.AuthErrorResponseDto
import com.example.cryptnote.data.remote.model.LoginRequestDto
import com.example.cryptnote.data.remote.model.RegisterRequestDto
import com.example.cryptnote.security.KeySessionManager
import com.example.cryptnote.security.KeyStorageManager
import com.example.cryptnote.security.token.AuthEventBus
import com.example.cryptnote.security.token.AuthSessionEvent
import com.example.cryptnote.security.token.TokenStorageManager
import com.example.cryptnote.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthState {
    data object Unauthenticated : AuthState
    data object Loading : AuthState
    data class Authenticated(val userId: String, val email: String) : AuthState
}

interface AuthRepository {
    val authState: StateFlow<AuthState>
    fun hasActiveSession(): Boolean
    suspend fun register(email: String, passphrase: CharArray): Result<Unit>
    suspend fun login(email: String, passphrase: CharArray): Result<Unit>
    suspend fun resetAccount(email: String, passphrase: CharArray): Result<Unit>
    suspend fun logout()
    suspend fun checkSession(): Result<Unit>
}

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStorageManager: TokenStorageManager,
    private val keySessionManager: KeySessionManager,
    private val keyStorageManager: KeyStorageManager,
    private val cryptoManager: com.example.cryptnote.security.CryptoManager,
    private val syncScheduler: SyncScheduler,
    private val authEventBus: AuthEventBus,
    private val json: Json
) : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(
        if (tokenStorageManager.hasSession()) {
            AuthState.Authenticated(
                userId = tokenStorageManager.getUserId().orEmpty(),
                email = tokenStorageManager.getUserEmail().orEmpty()
            )
        } else {
            AuthState.Unauthenticated
        }
    )
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    init {
        // Listen to force logout events (e.g. from TokenAuthenticator when refresh fails)
        repositoryScope.launch {
            authEventBus.events.collect { event ->
                when (event) {
                    is AuthSessionEvent.ForceLogout -> {
                        logout()
                    }
                }
            }
        }
    }

    override fun hasActiveSession(): Boolean {
        return tokenStorageManager.hasSession()
    }

    private fun deriveDeterministicUserSalt(email: String): ByteArray {
        val normalized = email.trim().lowercase()
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest("CryptoNote:v1:user-salt:$normalized".toByteArray(Charsets.UTF_8))
            .copyOf(16)
    }

    override suspend fun register(email: String, passphrase: CharArray): Result<Unit> =
        kotlinx.coroutines.withContext(Dispatchers.Default) {
            val normalizedEmail = email.trim().lowercase()
            _authState.value = AuthState.Loading

            try {
                val userSalt = deriveDeterministicUserSalt(normalizedEmail)

                // 1. Initialize local cryptographic vault with deterministic user salt
                val vaultSetupResult = keySessionManager.setupPassphrase(passphrase, userSalt)
                if (vaultSetupResult.isFailure) {
                    _authState.value = AuthState.Unauthenticated
                    return@withContext Result.failure(
                        vaultSetupResult.exceptionOrNull()
                            ?: IllegalStateException("Failed to initialize vault")
                    )
                }

                // 2. Extract zero-knowledge auth_key derived via HKDF-SHA256 split
                val authKeyBytes = keySessionManager.getAuthKey()
                val authKeyHex = authKeyBytes.toHex()

                // 3. Register user with backend using derived auth_key as password
                val response = authApi.register(
                    RegisterRequestDto(email = normalizedEmail, password = authKeyHex)
                )

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    tokenStorageManager.saveSession(
                        accessToken = body.accessToken,
                        refreshToken = body.refreshToken,
                        userId = body.user.id,
                        email = body.user.email
                    )

                    _authState.value = AuthState.Authenticated(
                        userId = body.user.id,
                        email = body.user.email
                    )

                    // Trigger immediate initial synchronization
                    syncScheduler.scheduleImmediate()

                    Result.success(Unit)
                } else {
                    keyStorageManager.clear()
                    keySessionManager.lock()
                    _authState.value = AuthState.Unauthenticated
                    Result.failure(extractErrorMessage(response))
                }
            } catch (e: Exception) {
                keyStorageManager.clear()
                keySessionManager.lock()
                _authState.value = AuthState.Unauthenticated
                Result.failure(mapException(e))
            }
        }

    override suspend fun login(email: String, passphrase: CharArray): Result<Unit> =
        kotlinx.coroutines.withContext(Dispatchers.Default) {
            val normalizedEmail = email.trim().lowercase()
            _authState.value = AuthState.Loading

            try {
                val userSalt = deriveDeterministicUserSalt(normalizedEmail)
                val masterKey = cryptoManager.deriveMasterKey(passphrase, userSalt)
                val derived = cryptoManager.splitMasterKey(masterKey)
                val authKeyHex = derived.authKey.toHex()

                var response = authApi.login(
                    LoginRequestDto(email = normalizedEmail, password = authKeyHex)
                )
                var activeMasterKey = masterKey
                var activeSalt = userSalt

                // Fallback attempt: if backend returns 401 and device has an older random salt stored locally, try it
                if (!response.isSuccessful && keyStorageManager.isSetup()) {
                    val localSalt = keyStorageManager.getSalt()
                    if (localSalt != null && !java.security.MessageDigest.isEqual(localSalt, userSalt)) {
                        val fallbackMasterKey = cryptoManager.deriveMasterKey(passphrase, localSalt)
                        val fallbackDerived = cryptoManager.splitMasterKey(fallbackMasterKey)
                        val fallbackAuthHex = fallbackDerived.authKey.toHex()
                        val fallbackResp = authApi.login(
                            LoginRequestDto(email = normalizedEmail, password = fallbackAuthHex)
                        )
                        if (fallbackResp.isSuccessful) {
                            response = fallbackResp
                            activeMasterKey = fallbackMasterKey
                            activeSalt = localSalt
                            masterKey.fill(0)
                        } else {
                            fallbackMasterKey.fill(0)
                        }
                    }
                }

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!

                    // Initialize Keystore-wrapped vault with the verified master key
                    keySessionManager.initializeWithMasterKey(activeMasterKey, activeSalt)

                    tokenStorageManager.saveSession(
                        accessToken = body.accessToken,
                        refreshToken = body.refreshToken,
                        userId = body.user.id,
                        email = body.user.email
                    )

                    _authState.value = AuthState.Authenticated(
                        userId = body.user.id,
                        email = body.user.email
                    )

                    // Trigger immediate initial synchronization
                    syncScheduler.scheduleImmediate()

                    Result.success(Unit)
                } else {
                    activeMasterKey.fill(0)
                    _authState.value = AuthState.Unauthenticated
                    Result.failure(extractErrorMessage(response))
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Unauthenticated
                Result.failure(mapException(e))
            }
        }

    override suspend fun resetAccount(email: String, passphrase: CharArray): Result<Unit> =
        kotlinx.coroutines.withContext(Dispatchers.Default) {
            val normalizedEmail = email.trim().lowercase()
            _authState.value = AuthState.Loading

            try {
                val userSalt = deriveDeterministicUserSalt(normalizedEmail)
                val masterKey = cryptoManager.deriveMasterKey(passphrase, userSalt)
                val derived = cryptoManager.splitMasterKey(masterKey)
                val authKeyHex = derived.authKey.toHex()

                val response = authApi.resetAccount(
                    LoginRequestDto(email = normalizedEmail, password = authKeyHex)
                )

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!

                    keySessionManager.initializeWithMasterKey(masterKey, userSalt)

                    tokenStorageManager.saveSession(
                        accessToken = body.accessToken,
                        refreshToken = body.refreshToken,
                        userId = body.user.id,
                        email = body.user.email
                    )

                    _authState.value = AuthState.Authenticated(
                        userId = body.user.id,
                        email = body.user.email
                    )

                    syncScheduler.scheduleImmediate()
                    Result.success(Unit)
                } else {
                    masterKey.fill(0)
                    _authState.value = AuthState.Unauthenticated
                    Result.failure(extractErrorMessage(response))
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Unauthenticated
                Result.failure(mapException(e))
            }
        }

    override suspend fun logout() {
        tokenStorageManager.clearSession()
        keyStorageManager.clear()
        keySessionManager.lock()
        syncScheduler.cancelAll()
        _authState.value = AuthState.Unauthenticated
    }

    override suspend fun checkSession(): Result<Unit> {
        if (!tokenStorageManager.hasSession()) {
            _authState.value = AuthState.Unauthenticated
            return Result.failure(IllegalStateException("No active session"))
        }

        return try {
            val response = authApi.getMe()
            if (response.isSuccessful && response.body() != null) {
                val user = response.body()!!.user
                tokenStorageManager.saveSession(
                    accessToken = tokenStorageManager.getAccessToken().orEmpty(),
                    refreshToken = tokenStorageManager.getRefreshToken().orEmpty(),
                    userId = user.id,
                    email = user.email
                )
                _authState.value = AuthState.Authenticated(user.id, user.email)
                Result.success(Unit)
            } else {
                logout()
                Result.failure(extractErrorMessage(response))
            }
        } catch (e: Exception) {
            // If offline, maintain existing cached authenticated state
            if (e is IOException) {
                val userId = tokenStorageManager.getUserId().orEmpty()
                val email = tokenStorageManager.getUserEmail().orEmpty()
                _authState.value = AuthState.Authenticated(userId, email)
                Result.success(Unit)
            } else {
                logout()
                Result.failure(mapException(e))
            }
        }
    }

    private fun extractErrorMessage(response: Response<*>): Exception {
        val errorBody = response.errorBody()?.string()
        val parsedMessage = if (!errorBody.isNullOrBlank()) {
            try {
                json.decodeFromString<AuthErrorResponseDto>(errorBody).message
            } catch (_: Exception) {
                null
            }
        } else null

        val message = parsedMessage ?: when (response.code()) {
            400 -> "Validation failed. Please verify your input."
            401 -> "Invalid email or master passphrase."
            404 -> "Account not found."
            409 -> "An account with this email address already exists."
            429 -> "Too many requests. Please wait a moment and try again."
            else -> "Server error (${response.code()}). Please try again later."
        }
        return IllegalArgumentException(message)
    }

    private fun mapException(e: Exception): Exception {
        return if (e is IOException) {
            IOException("Network connection unavailable. Please check your network and try again.", e)
        } else {
            e
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
