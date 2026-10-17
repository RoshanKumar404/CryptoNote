package com.example.cryptnote.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cryptnote.security.KeySessionManager
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
    val errorMessage: String? = null
)

sealed interface VaultAuthEvent {
    data object Authenticated : VaultAuthEvent
}

@HiltViewModel
class VaultAuthViewModel @Inject constructor(
    private val keySessionManager: KeySessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        VaultAuthUiState(isSetup = keySessionManager.isSetup)
    )
    val uiState: StateFlow<VaultAuthUiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<VaultAuthEvent>()
    val eventFlow: SharedFlow<VaultAuthEvent> = _eventFlow.asSharedFlow()

    init {
        checkStatus()
    }

    fun checkStatus() {
        val setup = keySessionManager.isSetup
        _uiState.update { it.copy(isSetup = setup, errorMessage = null) }
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
