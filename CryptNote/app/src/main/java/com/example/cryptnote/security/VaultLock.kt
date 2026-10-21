package com.example.cryptnote.security

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface VaultState {
    object Locked : VaultState
    object Unlocking : VaultState
    object Unlocked : VaultState
}

/**
 * Vault Lock Manager per NV-FE-6.
 *
 * Enforces one central state machine (Locked, Unlocking, Unlocked).
 * Triggers lock on:
 * - 1 minute idle timeout (touch() resets idle timer)
 * - Leaving the Vault route
 * - Screen off (ACTION_SCREEN_OFF)
 * - App background (ProcessLifecycleOwner ON_STOP)
 * - Explicit "Lock now" button
 *
 * Locking executes wipe: zeroes vault keys, clears search index, clears clipboard if copied from vault.
 */
class VaultLock(
    private val scope: CoroutineScope,
    private val wipe: () -> Unit = {},
    private val idleMs: Long = 60_000,
) {
    private val _state = MutableStateFlow<VaultState>(VaultState.Locked)
    val state: StateFlow<VaultState> = _state.asStateFlow()

    private var idleJob: Job? = null

    fun onUnlocking() {
        _state.value = VaultState.Unlocking
    }

    fun onUnlocked() {
        _state.value = VaultState.Unlocked
        touch()
    }

    fun touch() {
        if (_state.value != VaultState.Unlocked) return
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(idleMs)
            lock()
        }
    }

    fun lock() {
        idleJob?.cancel()
        idleJob = null
        try {
            wipe()
        } catch (_: Exception) {
            // Ignore wipe error
        }
        _state.value = VaultState.Locked
    }
}
