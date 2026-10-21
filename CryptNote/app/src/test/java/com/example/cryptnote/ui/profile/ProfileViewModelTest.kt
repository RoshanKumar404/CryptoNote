package com.example.cryptnote.ui.profile

import com.example.cryptnote.data.repository.AuthRepository
import com.example.cryptnote.data.repository.AuthState
import com.example.cryptnote.domain.model.ConflictPair
import com.example.cryptnote.domain.model.ConflictResolutionAction
import com.example.cryptnote.domain.model.Note
import com.example.cryptnote.domain.model.SyncStatus
import com.example.cryptnote.domain.repository.NoteRepository
import com.example.cryptnote.security.CryptoManagerImpl
import com.example.cryptnote.security.InMemoryKeyStorageManager
import com.example.cryptnote.security.JvmKeyWrapper
import com.example.cryptnote.security.KeySessionManager
import com.example.cryptnote.security.KeySessionManagerImpl
import com.example.cryptnote.security.biometric.BiometricAuthManager
import com.example.cryptnote.security.biometric.BiometricStatus
import com.example.cryptnote.security.token.InMemoryTokenStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tokenStorageManager: InMemoryTokenStorageManager
    private lateinit var keyStorageManager: InMemoryKeyStorageManager
    private lateinit var keySessionManager: KeySessionManager
    private lateinit var fakeBiometricAuthManager: FakeBiometricAuthManager
    private lateinit var fakeAuthRepository: FakeAuthRepository
    private lateinit var fakeNoteRepository: FakeNoteRepository

    private lateinit var viewModel: ProfileViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        tokenStorageManager = InMemoryTokenStorageManager().apply {
            saveSession(
                accessToken = "test.jwt.access",
                refreshToken = "test.jwt.refresh",
                userId = "user-1234-uuid",
                email = "roshan@example.com"
            )
        }

        keyStorageManager = InMemoryKeyStorageManager().apply {
            val sampleSalt = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)
            storeVault(sampleSalt, ByteArray(32), ByteArray(12))
            storeBiometricVault(ByteArray(32), ByteArray(12))
            setBiometricEnabled(true)
        }

        val cryptoManager = CryptoManagerImpl()
        val keyWrapper = JvmKeyWrapper()
        keySessionManager = KeySessionManagerImpl(
            cryptoManager = cryptoManager,
            keyWrapper = keyWrapper,
            keyStorageManager = keyStorageManager
        )

        fakeBiometricAuthManager = FakeBiometricAuthManager(status = BiometricStatus.AVAILABLE)
        fakeAuthRepository = FakeAuthRepository()
        fakeNoteRepository = FakeNoteRepository()

        viewModel = ProfileViewModel(
            tokenStorageManager = tokenStorageManager,
            keyStorageManager = keyStorageManager,
            keySessionManager = keySessionManager,
            biometricAuthManager = fakeBiometricAuthManager,
            authRepository = fakeAuthRepository,
            noteRepository = fakeNoteRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadProfileData_populatesUserCredentialsAndKeyFingerprint() = runTest {
        val state = viewModel.uiState.value
        assertEquals("roshan@example.com", state.email)
        assertEquals("user-1234-uuid", state.userId)
        assertTrue(state.loginTimestamp > 0L)
        assertTrue(state.loginDateTimeFormatted.isNotBlank())
        assertTrue(state.isBiometricEnabled)
        assertTrue(state.isBiometricSupported)

        val sampleSalt = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)
        val expectedFingerprint = MessageDigest.getInstance("SHA-256")
            .digest(sampleSalt)
            .take(8)
            .joinToString("-") { "%02X".format(it) }

        assertEquals(expectedFingerprint, state.keyFingerprint)
    }

    @Test
    fun observeNotes_updatesTotalAndSyncedCount() = runTest {
        advanceUntilIdle()

        val note1 = createNote(id = "1", syncStatus = SyncStatus.SYNCED)
        val note2 = createNote(id = "2", syncStatus = SyncStatus.PENDING)
        val note3 = createNote(id = "3", syncStatus = SyncStatus.SYNCED)

        fakeNoteRepository.notesFlow.value = listOf(note1, note2, note3)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(3, state.totalNotesCount)
        assertEquals(2, state.syncedNotesCount)
    }

    @Test
    fun lockVault_locksKeySessionAndEmitsEvent() = runTest {
        val events = mutableListOf<ProfileUiEvent>()
        val job = launch {
            viewModel.eventFlow.collect { events.add(it) }
        }

        viewModel.lockVault()
        advanceUntilIdle()

        assertFalse(keySessionManager.isUnlocked.value)
        assertEquals(1, events.size)
        assertTrue(events.first() is ProfileUiEvent.NavigateToVaultLock)

        job.cancel()
    }

    @Test
    fun signOut_callsAuthRepositoryLogoutAndEmitsEvent() = runTest {
        val events = mutableListOf<ProfileUiEvent>()
        val job = launch {
            viewModel.eventFlow.collect { events.add(it) }
        }

        viewModel.signOut()
        advanceUntilIdle()

        assertTrue(fakeAuthRepository.loggedOut)
        assertEquals(1, events.size)
        assertTrue(events.first() is ProfileUiEvent.NavigateToAuth)

        job.cancel()
    }

    private fun createNote(id: String, syncStatus: SyncStatus): Note {
        return Note(
            id = id,
            title = "Test Note $id",
            body = "Decrypted content",
            version = 1,
            updatedAt = System.currentTimeMillis(),
            deleted = false,
            syncStatus = syncStatus
        )
    }

    private class FakeBiometricAuthManager(
        private val status: BiometricStatus
    ) : BiometricAuthManager {
        override fun checkBiometricSupport(): BiometricStatus = status
        override fun authenticate(
            activity: androidx.fragment.app.FragmentActivity,
            title: String,
            subtitle: String,
            negativeButtonText: String,
            cryptoObject: androidx.biometric.BiometricPrompt.CryptoObject?,
            onSuccess: (androidx.biometric.BiometricPrompt.AuthenticationResult) -> Unit,
            onError: (errorCode: Int, errString: CharSequence) -> Unit,
            onFailed: () -> Unit
        ) = Unit
    }

    private class FakeAuthRepository : AuthRepository {
        private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
        override val authState: StateFlow<AuthState> = _authState
        var loggedOut = false

        override fun hasActiveSession(): Boolean = false
        override suspend fun register(email: String, passphrase: CharArray): Result<Unit> = Result.success(Unit)
        override suspend fun login(email: String, passphrase: CharArray): Result<Unit> = Result.success(Unit)
        override suspend fun resetAccount(email: String, passphrase: CharArray): Result<Unit> = Result.success(Unit)
        override suspend fun logout() {
            loggedOut = true
            _authState.value = AuthState.Unauthenticated
        }
        override suspend fun checkSession(): Result<Unit> = Result.success(Unit)
    }

    private class FakeNoteRepository : NoteRepository {
        val notesFlow = MutableStateFlow<List<Note>>(emptyList())

        override fun getAllNotes(): Flow<List<Note>> = notesFlow
        override fun getNoteById(id: String): Flow<Note?> = flowOf(notesFlow.value.find { it.id == id })
        override suspend fun saveNote(note: Note) = Unit
        override suspend fun deleteNote(id: String) = Unit
        override fun getConflictNotes(): Flow<List<Note>> = flowOf(emptyList())
        override fun getConflictPairs(): Flow<List<ConflictPair>> = flowOf(emptyList())
        override fun getVaultNotes(): Flow<List<Note>> = flowOf(emptyList())
        override suspend fun moveToVault(id: String, toVault: Boolean) = Unit
        override suspend fun quarantineNote(id: String, reason: String) = Unit
        override suspend fun resolveConflict(conflictGroupId: String, action: ConflictResolutionAction) = Unit
    }
}
