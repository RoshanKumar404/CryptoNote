package com.example.cryptnote.data.repository

import com.example.cryptnote.data.local.db.CryptNoteDatabase
import com.example.cryptnote.data.local.db.entity.OutboxEntity
import com.example.cryptnote.data.remote.api.AuthApi
import com.example.cryptnote.data.remote.api.KeysApi
import com.example.cryptnote.data.remote.model.AuthErrorResponseDto
import com.example.cryptnote.data.remote.model.LoginRequestDto
import com.example.cryptnote.data.remote.model.OutboxPayloadDto
import com.example.cryptnote.data.remote.model.PutKeysRequestDto
import com.example.cryptnote.data.remote.model.RegisterRequestDto
import com.example.cryptnote.security.KeyLifecycleManager
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
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import retrofit2.Response
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
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
    suspend fun changePassword(currentPassphrase: CharArray, newPassphrase: CharArray): Result<Unit>
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
    private val json: Json,
    private val database: CryptNoteDatabase? = null,
    private val keysApi: KeysApi? = null,
    private val keyLifecycleManager: KeyLifecycleManager? = null
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
        return MessageDigest.getInstance("SHA-256")
            .digest("CryptoNote:v1:user-salt:$normalized".toByteArray(Charsets.UTF_8))
            .copyOf(16)
    }

    private fun computeKcv(masterKey: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("cryptnote/kcv/v1".toByteArray(Charsets.UTF_8))
        digest.update(masterKey)
        return digest.digest().copyOf(8)
    }

    private suspend fun backupKeysToServer(salt: ByteArray, currentKeyVersion: Int? = null) {
        val api = keysApi ?: return
        val storedWrappedKey = keyStorageManager.getWrappedKey() ?: return
        val storedIv = keyStorageManager.getWrapperIv() ?: return
        val storedKcv = keyStorageManager.getKcv()

        val combinedWrapped = storedIv + storedWrappedKey
        val passWrappedMkBase64 = Base64.getEncoder().encodeToString(combinedWrapped)
        val passSaltBase64 = Base64.getEncoder().encodeToString(salt)
        val kcvBase64 = storedKcv?.let { Base64.getEncoder().encodeToString(it) }

        val kdfParams = buildJsonObject {
            put("algorithm", JsonPrimitive("Argon2id"))
            put("iterations", JsonPrimitive(3))
            put("memory", JsonPrimitive(65536))
            put("parallelism", JsonPrimitive(1))
        }

        try {
            api.putKeys(
                PutKeysRequestDto(
                    passWrappedMk = passWrappedMkBase64,
                    passSalt = passSaltBase64,
                    kdfParams = kdfParams,
                    recoveryWrappedMk = passWrappedMkBase64,
                    kcv = kcvBase64,
                    currentKeyVersion = currentKeyVersion
                )
            )
        } catch (_: Exception) {
            // Best effort; key backup network errors do not block local usage
        }
    }

    override suspend fun register(email: String, passphrase: CharArray): Result<Unit> =
        withContext(Dispatchers.Default) {
            val normalizedEmail = email.trim().lowercase()
            _authState.value = AuthState.Loading

            try {
                // Ensure no previous user's data remains in local database
                withContext(Dispatchers.IO) {
                    database?.clearAllTables()
                }

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

                    // 4. Back up wrapped master key and KCV to server (NV-BE-1, NV-FE-2)
                    backupKeysToServer(userSalt)

                    // 5. Trigger immediate initial synchronization
                    syncScheduler.scheduleImmediate()

                    Result.success(Unit)
                } else {
                    keyStorageManager.clear()
                    keySessionManager.lock()
                    withContext(Dispatchers.IO) {
                        database?.clearAllTables()
                    }
                    _authState.value = AuthState.Unauthenticated
                    Result.failure(extractErrorMessage(response))
                }
            } catch (e: Exception) {
                keyStorageManager.clear()
                keySessionManager.lock()
                withContext(Dispatchers.IO) {
                    database?.clearAllTables()
                }
                _authState.value = AuthState.Unauthenticated
                Result.failure(mapException(e))
            }
        }

    override suspend fun login(email: String, passphrase: CharArray): Result<Unit> =
        withContext(Dispatchers.Default) {
            val normalizedEmail = email.trim().lowercase()
            _authState.value = AuthState.Loading

            try {
                // Ensure local DB starts 100% clean on fresh login so no stale notes from prior sessions remain
                withContext(Dispatchers.IO) {
                    database?.clearAllTables()
                    try {
                        database?.syncStateDao()?.upsertSyncState(
                            com.example.cryptnote.data.local.db.entity.SyncStateEntity(id = 1, lastPullCursor = 0L)
                        )
                    } catch (_: Exception) {}
                }

                val userSalt = deriveDeterministicUserSalt(normalizedEmail)
                val masterKey = cryptoManager.deriveMasterKey(passphrase, userSalt)
                val derived = cryptoManager.splitMasterKey(masterKey)
                val authKeyHex = derived.authKey.toHex()

                val response = authApi.login(
                    LoginRequestDto(email = normalizedEmail, password = authKeyHex)
                )

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!

                    // Save session tokens immediately so subsequent authenticated calls succeed
                    tokenStorageManager.saveSession(
                        accessToken = body.accessToken,
                        refreshToken = body.refreshToken,
                        userId = body.user.id,
                        email = body.user.email
                    )

                    // Key Verification via KeysApi if present on server (NV-BE-1, NV-FE-2)
                    var keyCheckFailed = false
                    try {
                        val keysResp = keysApi?.getKeys()
                        if (keysResp != null && keysResp.isSuccessful && keysResp.body() != null) {
                            val keysBody = keysResp.body()!!
                            if (keysBody.exists && keysBody.latest != null) {
                                val remoteKcvBase64 = keysBody.latest.kcv
                                if (!remoteKcvBase64.isNullOrBlank()) {
                                    val remoteKcv = Base64.getDecoder().decode(remoteKcvBase64)
                                    val computedKcv = keyLifecycleManager?.computeKcv(masterKey)
                                        ?: computeKcv(masterKey)

                                    if (!MessageDigest.isEqual(computedKcv, remoteKcv)) {
                                        keyCheckFailed = true
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Network error during key verification - proceed with offline vault initialization
                    }

                    if (keyCheckFailed) {
                        masterKey.fill(0)
                        tokenStorageManager.clearSession()
                        keyStorageManager.clear()
                        keySessionManager.lock()
                        withContext(Dispatchers.IO) {
                            database?.clearAllTables()
                        }
                        _authState.value = AuthState.Unauthenticated
                        return@withContext Result.failure(
                            IllegalArgumentException("Wrong master passphrase (key check verification failed).")
                        )
                    }

                    // Initialize Keystore-wrapped vault with the verified master key & deterministic salt
                    keySessionManager.initializeWithMasterKey(masterKey, userSalt)

                    // If server doesn't have keys yet, back them up now
                    try {
                        val keysResp = keysApi?.getKeys()
                        if (keysResp != null && keysResp.isSuccessful && keysResp.body()?.exists == false) {
                            backupKeysToServer(userSalt)
                        }
                    } catch (_: Exception) {}

                    _authState.value = AuthState.Authenticated(
                        userId = body.user.id,
                        email = body.user.email
                    )

                    // Trigger immediate initial synchronization
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

    override suspend fun resetAccount(email: String, passphrase: CharArray): Result<Unit> =
        withContext(Dispatchers.Default) {
            val normalizedEmail = email.trim().lowercase()
            _authState.value = AuthState.Loading

            try {
                // Wipe local DB completely on account reset
                withContext(Dispatchers.IO) {
                    database?.clearAllTables()
                }

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

                    backupKeysToServer(userSalt)

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

    override suspend fun changePassword(
        currentPassphrase: CharArray,
        newPassphrase: CharArray
    ): Result<Unit> = withContext(Dispatchers.Default) {
        val userEmail = tokenStorageManager.getUserEmail().orEmpty()
        if (userEmail.isBlank()) {
            return@withContext Result.failure(IllegalStateException("No authenticated user session"))
        }

        val userSalt = deriveDeterministicUserSalt(userEmail)
        val currentMasterKey = cryptoManager.deriveMasterKey(currentPassphrase, userSalt)
        val currentDerived = cryptoManager.splitMasterKey(currentMasterKey)

        val activeKey = try { keySessionManager.getEncryptionKey() } catch (_: Exception) { null }
        if (activeKey != null && !activeKey.contentEquals(currentDerived.encryptionKey)) {
            currentMasterKey.fill(0)
            return@withContext Result.failure(IllegalArgumentException("Current password is incorrect"))
        }

        val newMasterKey = cryptoManager.deriveMasterKey(newPassphrase, userSalt)
        val newDerived = cryptoManager.splitMasterKey(newMasterKey)
        val newAuthKeyHex = newDerived.authKey.toHex()
        val newKeyId = cryptoManager.deriveKeyId(newMasterKey)

        try {
            withContext(Dispatchers.IO) {
                database?.let { db ->
                    val noteDao = db.noteDao()
                    val outboxDao = db.outboxDao()
                    val allEntities = noteDao.getAllActiveNotesSync()

                    for (entity in allEntities) {
                        val noteUuid = try { UUID.fromString(entity.id) } catch (_: Exception) { UUID.nameUUIDFromBytes(entity.id.toByteArray(Charsets.UTF_8)) }
                        val currentKeyId = try { keySessionManager.getKeyId() } catch (_: Exception) { ByteArray(8) }

                        val plaintext: String? = try {
                            if (entity.envelope != null && entity.envelope.isNotEmpty()) {
                                when (val openRes = com.example.cryptnote.security.EnvelopeV1.open(activeKey ?: currentDerived.encryptionKey, currentKeyId, noteUuid, entity.version.toLong(), entity.envelope)) {
                                    is com.example.cryptnote.security.OpenResult.Opened -> String(openRes.plaintext, Charsets.UTF_8)
                                    else -> null
                                }
                            } else null
                        } catch (_: Exception) { null }

                        if (plaintext != null) {
                            val newEnvelope = com.example.cryptnote.security.EnvelopeV1.seal(
                                key = newDerived.encryptionKey,
                                keyId = newKeyId,
                                noteId = noteUuid,
                                plaintext = plaintext.toByteArray(Charsets.UTF_8)
                            )
                            val newCiphertextBytes = newEnvelope.copyOfRange(22, newEnvelope.size)
                            val newNonceBytes = newEnvelope.copyOfRange(10, 22)
                            val newKeyIdBytes = newEnvelope.copyOfRange(2, 10)

                            val updatedEntity = entity.copy(
                                ciphertext = newCiphertextBytes,
                                nonce = newNonceBytes,
                                envelope = newEnvelope,
                                keyId = newKeyIdBytes,
                                formatVersion = 1,
                                syncStatus = "pending",
                                updatedAt = System.currentTimeMillis()
                            )
                            noteDao.insertNote(updatedEntity)

                            val payloadDto = OutboxPayloadDto(
                                ciphertext = Base64.getEncoder().encodeToString(newCiphertextBytes),
                                nonce = Base64.getEncoder().encodeToString(newNonceBytes),
                                envelope = Base64.getEncoder().encodeToString(newEnvelope),
                                formatVersion = 1,
                                keyId = Base64.getEncoder().encodeToString(newKeyIdBytes)
                            )
                            val payloadJson = json.encodeToString(OutboxPayloadDto.serializer(), payloadDto)
                            outboxDao.insertOutboxEntry(
                                OutboxEntity(
                                    opId = UUID.randomUUID().toString(),
                                    noteId = entity.id,
                                    type = "update",
                                    baseVersion = entity.version,
                                    version = entity.version + 1,
                                    payload = payloadJson,
                                    status = "pending",
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
            }

            val response = authApi.resetAccount(
                LoginRequestDto(email = userEmail, password = newAuthKeyHex)
            )

            currentMasterKey.fill(0)

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                keySessionManager.initializeWithMasterKey(newMasterKey, userSalt)
                tokenStorageManager.saveSession(
                    accessToken = body.accessToken,
                    refreshToken = body.refreshToken,
                    userId = body.user.id,
                    email = body.user.email
                )
                backupKeysToServer(userSalt)
                syncScheduler.scheduleImmediate()
                newMasterKey.fill(0)
                Result.success(Unit)
            } else {
                newMasterKey.fill(0)
                Result.failure(extractErrorMessage(response))
            }
        } catch (e: Exception) {
            currentMasterKey.fill(0)
            newMasterKey.fill(0)
            Result.failure(mapException(e))
        }
    }

    override suspend fun logout() {
        tokenStorageManager.clearSession()
        keyStorageManager.clear()
        keySessionManager.lock()
        syncScheduler.cancelAll()
        withContext(Dispatchers.IO) {
            try {
                database?.clearAllTables()
                database?.syncStateDao()?.upsertSyncState(
                    com.example.cryptnote.data.local.db.entity.SyncStateEntity(id = 1, lastPullCursor = 0L)
                )
            } catch (_: Exception) {}
        }
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
