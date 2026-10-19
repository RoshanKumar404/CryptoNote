package com.example.cryptnote.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cryptnote.data.repository.AuthRepository
import com.example.cryptnote.domain.model.SyncStatus
import com.example.cryptnote.domain.repository.NoteRepository
import com.example.cryptnote.security.KeySessionManager
import com.example.cryptnote.security.KeyStorageManager
import com.example.cryptnote.security.biometric.BiometricAuthManager
import com.example.cryptnote.security.biometric.BiometricStatus
import com.example.cryptnote.security.token.TokenStorageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ProfileUiState(
    val email: String = "",
    val userId: String = "",
    val loginTimestamp: Long = 0L,
    val loginDateTimeFormatted: String = "",
    val keyFingerprint: String = "",
    val isVaultUnlocked: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val isBiometricSupported: Boolean = false,
    val totalNotesCount: Int = 0,
    val syncedNotesCount: Int = 0,
    val serverUrl: String = "https://cryptonote-backend.onrender.com",
    val encryptionAlgorithm: String = "AES-256-GCM (Authenticated AEAD)",
    val kdfAlgorithm: String = "Argon2id (m=64MB, t=3, p=1)",
    val keystoreProvider: String = "Android Keystore (Hardware TEE / StrongBox)",
    val isLoading: Boolean = false
)

sealed interface ProfileUiEvent {
    data object NavigateToAuth : ProfileUiEvent
    data object NavigateToVaultLock : ProfileUiEvent
    data class ShowMessage(val message: String) : ProfileUiEvent
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val tokenStorageManager: TokenStorageManager,
    private val keyStorageManager: KeyStorageManager,
    private val keySessionManager: KeySessionManager,
    private val biometricAuthManager: BiometricAuthManager,
    private val authRepository: AuthRepository,
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<ProfileUiEvent>()
    val eventFlow: SharedFlow<ProfileUiEvent> = _eventFlow.asSharedFlow()

    init {
        loadProfileData()
        observeNotes()
    }

    private fun loadProfileData() {
        val email = tokenStorageManager.getUserEmail().orEmpty()
        val userId = tokenStorageManager.getUserId().orEmpty()
        val loginTs = tokenStorageManager.getLoginTimestamp()

        val sdf = SimpleDateFormat("dd MMMM yyyy, hh:mm:ss a", Locale.getDefault())
        val formattedDate = try {
            sdf.format(Date(loginTs))
        } catch (_: Exception) {
            "Unknown"
        }

        val salt = keyStorageManager.getSalt()
        val fingerprint = if (salt != null && salt.isNotEmpty()) {
            try {
                val digest = MessageDigest.getInstance("SHA-256").digest(salt)
                digest.take(8).joinToString("-") { "%02X".format(it) }
            } catch (_: Exception) {
                "SECURE-VAULT-V1"
            }
        } else {
            "SECURE-VAULT-V1"
        }

        val biometricAvail = biometricAuthManager.checkBiometricSupport() == BiometricStatus.AVAILABLE
        val biometricEnabled = keyStorageManager.isBiometricEnabled()

        _uiState.update {
            it.copy(
                email = email,
                userId = userId,
                loginTimestamp = loginTs,
                loginDateTimeFormatted = formattedDate,
                keyFingerprint = fingerprint,
                isVaultUnlocked = keySessionManager.isUnlocked.value,
                isBiometricEnabled = biometricEnabled,
                isBiometricSupported = biometricAvail
            )
        }
    }

    private fun observeNotes() {
        viewModelScope.launch {
            noteRepository.getAllNotes().collect { notes ->
                _uiState.update {
                    it.copy(
                        totalNotesCount = notes.size,
                        syncedNotesCount = notes.count { note -> note.syncStatus == SyncStatus.SYNCED }
                    )
                }
            }
        }
    }

    fun lockVault() {
        keySessionManager.lock()
        viewModelScope.launch {
            _eventFlow.emit(ProfileUiEvent.NavigateToVaultLock)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            authRepository.logout()
            _uiState.update { it.copy(isLoading = false) }
            _eventFlow.emit(ProfileUiEvent.NavigateToAuth)
        }
    }
}
