package com.example.cryptnote.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cryptnote.data.repository.AuthRepository
import com.example.cryptnote.data.repository.AuthState
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

enum class AuthTab {
    LOGIN,
    REGISTER
}

data class AuthUiState(
    val selectedTab: AuthTab = AuthTab.LOGIN,
    val email: String = "",
    val passphrase: String = "",
    val confirmPassphrase: String = "",
    val isPassphraseVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isEmailValid: Boolean = true,
    val isPassphraseValid: Boolean = true
)

sealed interface AuthUiEvent {
    data object NavigateToNotes : AuthUiEvent
    data class ShowSnackbar(val message: String) : AuthUiEvent
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<AuthUiEvent>()
    val eventFlow: SharedFlow<AuthUiEvent> = _eventFlow.asSharedFlow()

    init {
        viewModelScope.launch {
            authRepository.authState.collect { authState ->
                if (authState is AuthState.Authenticated) {
                    _eventFlow.emit(AuthUiEvent.NavigateToNotes)
                }
            }
        }
    }

    fun onTabSelected(tab: AuthTab) {
        _uiState.update {
            it.copy(
                selectedTab = tab,
                errorMessage = null,
                confirmPassphrase = ""
            )
        }
    }

    fun onEmailChange(email: String) {
        _uiState.update {
            it.copy(
                email = email,
                errorMessage = null,
                isEmailValid = email.isBlank() || isValidEmail(email)
            )
        }
    }

    fun onPassphraseChange(passphrase: String) {
        _uiState.update {
            it.copy(
                passphrase = passphrase,
                errorMessage = null,
                isPassphraseValid = passphrase.length >= 8
            )
        }
    }

    fun onConfirmPassphraseChange(confirm: String) {
        _uiState.update {
            it.copy(
                confirmPassphrase = confirm,
                errorMessage = null
            )
        }
    }

    fun togglePassphraseVisibility() {
        _uiState.update {
            it.copy(isPassphraseVisible = !it.isPassphraseVisible)
        }
    }

    fun submit() {
        val state = _uiState.value
        val email = state.email.trim()
        val passphrase = state.passphrase

        if (email.isBlank() || !isValidEmail(email)) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid email address", isEmailValid = false) }
            return
        }

        if (passphrase.length < 8) {
            _uiState.update { it.copy(errorMessage = "Passphrase must be at least 8 characters", isPassphraseValid = false) }
            return
        }

        if (state.selectedTab == AuthTab.REGISTER) {
            if (passphrase != state.confirmPassphrase) {
                _uiState.update { it.copy(errorMessage = "Passphrases do not match") }
                return
            }

            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            viewModelScope.launch {
                val result = authRepository.register(email, passphrase.toCharArray())
                _uiState.update { it.copy(isLoading = false) }
                result.fold(
                    onSuccess = {
                        _eventFlow.emit(AuthUiEvent.NavigateToNotes)
                    },
                    onFailure = { error ->
                        _uiState.update { it.copy(errorMessage = error.message ?: "Registration failed") }
                    }
                )
            }
        } else {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            viewModelScope.launch {
                val result = authRepository.login(email, passphrase.toCharArray())
                _uiState.update { it.copy(isLoading = false) }
                result.fold(
                    onSuccess = {
                        _eventFlow.emit(AuthUiEvent.NavigateToNotes)
                    },
                    onFailure = { error ->
                        _uiState.update { it.copy(errorMessage = error.message ?: "Authentication failed") }
                    }
                )
            }
        }
    }

    fun resetAccount(email: String, newPassphrase: String) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || !isValidEmail(trimmedEmail)) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid email address", isEmailValid = false) }
            return
        }

        if (newPassphrase.length < 8) {
            _uiState.update { it.copy(errorMessage = "New passphrase must be at least 8 characters", isPassphraseValid = false) }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = authRepository.resetAccount(trimmedEmail, newPassphrase.toCharArray())
            _uiState.update { it.copy(isLoading = false) }
            result.fold(
                onSuccess = {
                    _eventFlow.emit(AuthUiEvent.NavigateToNotes)
                },
                onFailure = { error ->
                    _uiState.update { it.copy(errorMessage = error.message ?: "Account reset failed") }
                }
            )
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return EMAIL_REGEX.matches(email)
    }

    companion object {
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}
