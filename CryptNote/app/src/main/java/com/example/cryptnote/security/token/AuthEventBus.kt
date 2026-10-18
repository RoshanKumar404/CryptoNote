package com.example.cryptnote.security.token

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthSessionEvent {
    data object ForceLogout : AuthSessionEvent
}

/**
 * Event bus for notifying UI and repositories of cross-cutting session events (e.g., expired refresh tokens).
 */
@Singleton
class AuthEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<AuthSessionEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<AuthSessionEvent> = _events.asSharedFlow()

    fun emitForceLogout() {
        _events.tryEmit(AuthSessionEvent.ForceLogout)
    }
}
