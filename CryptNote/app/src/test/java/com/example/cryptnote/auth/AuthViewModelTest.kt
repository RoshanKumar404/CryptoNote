package com.example.cryptnote.auth

import com.example.cryptnote.data.repository.AuthRepository
import com.example.cryptnote.data.repository.AuthState
import com.example.cryptnote.ui.auth.AuthTab
import com.example.cryptnote.ui.auth.AuthUiEvent
import com.example.cryptnote.ui.auth.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeAuthRepository: FakeAuthRepository
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeAuthRepository = FakeAuthRepository()
        viewModel = AuthViewModel(fakeAuthRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isLoginTabWithEmptyInputs() {
        val state = viewModel.uiState.value
        assertEquals(AuthTab.LOGIN, state.selectedTab)
        assertEquals("", state.email)
        assertEquals("", state.passphrase)
        assertEquals("", state.confirmPassphrase)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun onTabSelected_switchesTabsAndClearsError() {
        viewModel.onTabSelected(AuthTab.REGISTER)
        assertEquals(AuthTab.REGISTER, viewModel.uiState.value.selectedTab)

        viewModel.onTabSelected(AuthTab.LOGIN)
        assertEquals(AuthTab.LOGIN, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun submit_validation_flagsInvalidEmail() {
        viewModel.onEmailChange("invalid-email")
        viewModel.onPassphraseChange("securepassphrase123")
        viewModel.submit()

        val state = viewModel.uiState.value
        assertFalse(state.isEmailValid)
        assertEquals("Please enter a valid email address", state.errorMessage)
    }

    @Test
    fun submit_validation_flagsShortPassphrase() {
        viewModel.onEmailChange("valid@example.com")
        viewModel.onPassphraseChange("short")
        viewModel.submit()

        val state = viewModel.uiState.value
        assertFalse(state.isPassphraseValid)
        assertEquals("Passphrase must be at least 8 characters", state.errorMessage)
    }

    @Test
    fun submit_register_flagsMismatchingConfirmPassphrase() {
        viewModel.onTabSelected(AuthTab.REGISTER)
        viewModel.onEmailChange("user@example.com")
        viewModel.onPassphraseChange("supersecretpass1")
        viewModel.onConfirmPassphraseChange("supersecretpass2")

        viewModel.submit()

        val state = viewModel.uiState.value
        assertEquals("Passphrases do not match", state.errorMessage)
    }

    @Test
    fun submit_login_success_emitsNavigateToNotes() = runTest {
        fakeAuthRepository.loginResult = Result.success(Unit)

        viewModel.onEmailChange("test@example.com")
        viewModel.onPassphraseChange("validpass123")

        var emittedEvent: AuthUiEvent? = null
        val job = launch {
            emittedEvent = viewModel.eventFlow.first()
        }

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(AuthUiEvent.NavigateToNotes, emittedEvent)
        assertNull(viewModel.uiState.value.errorMessage)
        job.cancel()
    }

    @Test
    fun submit_register_success_emitsNavigateToNotes() = runTest {
        fakeAuthRepository.registerResult = Result.success(Unit)

        viewModel.onTabSelected(AuthTab.REGISTER)
        viewModel.onEmailChange("newuser@example.com")
        viewModel.onPassphraseChange("validpass123")
        viewModel.onConfirmPassphraseChange("validpass123")

        var emittedEvent: AuthUiEvent? = null
        val job = launch {
            emittedEvent = viewModel.eventFlow.first()
        }

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(AuthUiEvent.NavigateToNotes, emittedEvent)
        job.cancel()
    }

    @Test
    fun submit_login_failure_displaysErrorMessage() = runTest {
        fakeAuthRepository.loginResult = Result.failure(IllegalArgumentException("Invalid email or master passphrase."))

        viewModel.onEmailChange("test@example.com")
        viewModel.onPassphraseChange("wrongpass123")

        viewModel.submit()
        advanceUntilIdle()

        assertEquals("Invalid email or master passphrase.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    private class FakeAuthRepository : AuthRepository {
        private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
        override val authState: StateFlow<AuthState> = _authState.asStateFlow()

        var registerResult: Result<Unit> = Result.success(Unit)
        var loginResult: Result<Unit> = Result.success(Unit)

        override fun hasActiveSession(): Boolean = false

        override suspend fun register(email: String, passphrase: CharArray): Result<Unit> {
            return registerResult.also {
                if (it.isSuccess) {
                    _authState.value = AuthState.Authenticated("id_1", email)
                }
            }
        }

        override suspend fun login(email: String, passphrase: CharArray): Result<Unit> {
            return loginResult.also {
                if (it.isSuccess) {
                    _authState.value = AuthState.Authenticated("id_1", email)
                }
            }
        }

        override suspend fun logout() {
            _authState.value = AuthState.Unauthenticated
        }

        override suspend fun checkSession(): Result<Unit> = Result.success(Unit)
    }
}
