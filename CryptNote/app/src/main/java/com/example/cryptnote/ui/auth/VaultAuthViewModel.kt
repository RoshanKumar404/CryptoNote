package com.example.cryptnote.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cryptnote.security.KeySessionManager
import com.example.cryptnote.security.biometric.BiometricAuthManager
import com.example.cryptnote.security.biometric.BiometricKeyManager
import com.example.cryptnote.security.biometric.BiometricStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VaultAuthUiState(
    val isSetup: Boolean = false,
    val passphraseInput: String = "",
    val confirmPassphraseInput: String = "",
    val isPassphraseVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isBiometricAvailable: Boolean = false,
    val isBiometricEnabled: Boolean = false
)

sealed interface VaultAuthEvent {
    data object Authenticated : VaultAuthEvent
    data object SignOut : VaultAuthEvent
    data class RequestBiometricAuth(val cipher: javax.crypto.Cipher) : VaultAuthEvent
    data class ShowMessage(val message: String) : VaultAuthEvent
}

@HiltViewModel
class VaultAuthViewModel @Inject constructor(
    private val keySessionManager: KeySessionManager,
    private val biometricAuthManager: BiometricAuthManager,
    private val biometricKeyManager: BiometricKeyManager,
    private val tokenStorageManager: com.example.cryptnote.security.token.TokenStorageManager,
    private val keyStorageManager: com.example.cryptnote.security.KeyStorageManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        VaultAuthUiState(
            isSetup = keySessionManager.isSetup,
            isBiometricAvailable = biometricAuthManager.checkBiometricSupport() == BiometricStatus.AVAILABLE,
            isBiometricEnabled = keySessionManager.canUnlockWithBiometric()
        )
    )
    val uiState: StateFlow<VaultAuthUiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<VaultAuthEvent>()
    val eventFlow: SharedFlow<VaultAuthEvent> = _eventFlow.asSharedFlow()

    init {
        checkStatus()
    }

    fun checkStatus() {
        val setup = keySessionManager.isSetup
        val biometricAvail = biometricAuthManager.checkBiometricSupport() == BiometricStatus.AVAILABLE
        val biometricEnabled = keySessionManager.canUnlockWithBiometric()
        _uiState.update {
            it.copy(
                isSetup = setup,
                isBiometricAvailable = biometricAvail,
                isBiometricEnabled = biometricEnabled,
                errorMessage = null
            )
        }
        if (keySessionManager.isUnlocked.value) {
            viewModelScope.launch {
                _eventFlow.emit(VaultAuthEvent.Authenticated)
            }
        }
    }

    fun onPassphraseChange(value: String) {
        _uiState.update { it.copy(passphraseInput = value, errorMessage = null) }
    }

    fun onConfirmPassphraseChange(value: String) {
        _uiState.update { it.copy(confirmPassphraseInput = value, errorMessage = null) }
    }

    fun togglePassphraseVisibility() {
        _uiState.update { it.copy(isPassphraseVisible = !it.isPassphraseVisible) }
    }

    fun lockVault() {
        keySessionManager.lock()
        _uiState.update {
            it.copy(
                isSetup = keySessionManager.isSetup,
                passphraseInput = "",
                confirmPassphraseInput = "",
                errorMessage = null
            )
        }
    }

    fun signOutAndReset() {
        tokenStorageManager.clearSession()
        keyStorageManager.clear()
        keySessionManager.lock()
        viewModelScope.launch {
            _eventFlow.emit(VaultAuthEvent.SignOut)
        }
    }

    fun triggerBiometricUnlock() {
        if (!keySessionManager.canUnlockWithBiometric()) {
            _uiState.update {
                it.copy(errorMessage = "Biometric unlock not enabled yet. Enter your master passphrase to unlock and activate biometrics.")
            }
            return
        }
        val iv = keySessionManager.getBiometricIv() ?: ByteArray(0)
        try {
            val decryptCipher = biometricKeyManager.createDecryptCipher(iv)
            viewModelScope.launch {
                _eventFlow.emit(VaultAuthEvent.RequestBiometricAuth(decryptCipher))
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Biometric cipher error: ${e.message}") }
        }
    }

    fun onBiometricAuthSuccess(cipher: javax.crypto.Cipher) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = keySessionManager.unlockWithBiometric(cipher)
            _uiState.update { it.copy(isLoading = false) }
            result.fold(
                onSuccess = {
                    _eventFlow.emit(VaultAuthEvent.Authenticated)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Failed to unlock vault with biometric")
                    }
                }
            )
        }
    }

    fun enableBiometricUnlock() {
        try {
            val encryptCipher = biometricKeyManager.createEncryptCipher()
            val result = keySessionManager.setupBiometric(encryptCipher)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isBiometricEnabled = true) }
                    viewModelScope.launch {
                        _eventFlow.emit(VaultAuthEvent.ShowMessage("Biometric unlock enabled"))
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(errorMessage = "Failed to enable biometric: ${error.message}") }
                }
            )
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Biometric setup error: ${e.message}") }
        }
    }

    fun submit() {
        val state = _uiState.value
        val passphrase = state.passphraseInput

        if (passphrase.length < 8) {
            _uiState.update { it.copy(errorMessage = "Passphrase must be at least 8 characters") }
            return
        }

        if (!state.isSetup) {
            if (passphrase != state.confirmPassphraseInput) {
                _uiState.update { it.copy(errorMessage = "Passphrases do not match") }
                return
            }

            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            viewModelScope.launch {
                val result = keySessionManager.setupPassphrase(passphrase.toCharArray())
                _uiState.update { it.copy(isLoading = false) }
                result.fold(
                    onSuccess = {
                        if (biometricAuthManager.checkBiometricSupport() == BiometricStatus.AVAILABLE) {
                            try {
                                val encryptCipher = biometricKeyManager.createEncryptCipher()
                                val setupResult = keySessionManager.setupBiometric(encryptCipher)
                                if (setupResult.isSuccess) {
                                    _uiState.update { it.copy(isBiometricEnabled = true) }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("VaultAuthViewModel", "Biometric initial setup failed", e)
                            }
                        }
                        _eventFlow.emit(VaultAuthEvent.Authenticated)
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(errorMessage = error.message ?: "Failed to initialize secure vault")
                        }
                    }
                )
            }
        } else {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            viewModelScope.launch {
                val result = keySessionManager.unlock(passphrase.toCharArray())
                _uiState.update { it.copy(isLoading = false) }
                result.fold(
                    onSuccess = {
                        if (biometricAuthManager.checkBiometricSupport() == BiometricStatus.AVAILABLE) {
                            try {
                                val encryptCipher = biometricKeyManager.createEncryptCipher()
                                val setupResult = keySessionManager.setupBiometric(encryptCipher)
                                if (setupResult.isSuccess) {
                                    _uiState.update { it.copy(isBiometricEnabled = true) }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("VaultAuthViewModel", "Biometric auto-enroll failed", e)
                            }
                        }
                        _eventFlow.emit(VaultAuthEvent.Authenticated)
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(errorMessage = error.message ?: "Invalid master passphrase")
                        }
                    }
                )
            }
        }
    }
}
