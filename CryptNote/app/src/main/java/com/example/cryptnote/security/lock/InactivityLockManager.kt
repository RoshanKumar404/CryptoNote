package com.example.cryptnote.security.lock

import com.example.cryptnote.security.KeySessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

interface InactivityLockManager {
    val isLocked: StateFlow<Boolean>
    var timeoutDurationMs: Long

    fun onUserInteraction()
    fun onAppBackgrounded()
    fun onAppForegrounded()
    fun lockNow()
    fun unlock()
}

@Singleton
class InactivityLockManagerImpl @Inject constructor(
    private val keySessionManager: KeySessionManager,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) : InactivityLockManager {

    private val scope = CoroutineScope(dispatcher)
    private var lockJob: Job? = null
    private var backgroundTimestamp: Long? = null

    override var timeoutDurationMs: Long = DEFAULT_TIMEOUT_MS

    private val _isLocked = MutableStateFlow(!keySessionManager.isUnlocked.value)
    override val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    init {
        scope.launch {
            keySessionManager.isUnlocked.collect { unlocked ->
                _isLocked.value = !unlocked
                if (unlocked) {
                    resetTimer()
                } else {
                    lockJob?.cancel()
                }
            }
        }
    }

    override fun onUserInteraction() {
        if (keySessionManager.isUnlocked.value) {
            resetTimer()
        }
    }

    override fun onAppBackgrounded() {
        backgroundTimestamp = System.currentTimeMillis()
    }

    override fun onAppForegrounded() {
        val bgTime = backgroundTimestamp
        if (bgTime != null) {
            val elapsed = System.currentTimeMillis() - bgTime
            backgroundTimestamp = null
            if (elapsed >= timeoutDurationMs) {
                lockNow()
                return
            }
        }
        if (keySessionManager.isUnlocked.value) {
            resetTimer()
        }
    }

    override fun lockNow() {
        lockJob?.cancel()
        keySessionManager.lock()
        _isLocked.value = true
    }

    override fun unlock() {
        _isLocked.value = false
        resetTimer()
    }

    private fun resetTimer() {
        lockJob?.cancel()
        lockJob = scope.launch {
            delay(timeoutDurationMs)
            lockNow()
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 3 * 60 * 1000L // 3 minutes default
    }
}
