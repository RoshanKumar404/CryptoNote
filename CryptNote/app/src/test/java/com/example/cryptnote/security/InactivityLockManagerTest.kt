package com.example.cryptnote.security

import com.example.cryptnote.security.lock.InactivityLockManager
import com.example.cryptnote.security.lock.InactivityLockManagerImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InactivityLockManagerTest {

    private lateinit var keySessionManager: KeySessionManager
    private lateinit var lockManager: InactivityLockManager
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        val cryptoManager = CryptoManagerImpl()
        val keyWrapper = JvmKeyWrapper()
        val keyStorageManager = InMemoryKeyStorageManager()
        keySessionManager = KeySessionManagerImpl(
            cryptoManager = cryptoManager,
            keyWrapper = keyWrapper,
            keyStorageManager = keyStorageManager
        )
        lockManager = InactivityLockManagerImpl(
            keySessionManager = keySessionManager,
            dispatcher = testDispatcher
        )
    }

    @Test
    fun initialLockState_isLockedWhenSessionNotUnlocked() {
        assertTrue(lockManager.isLocked.value)
    }

    @Test
    fun unlock_setsLockStateFalse() = runTest(testDispatcher) {
        val passphrase = "test-passphrase-2026".toCharArray()
        keySessionManager.setupPassphrase(passphrase)
        testDispatcher.scheduler.runCurrent()

        assertFalse(lockManager.isLocked.value)
    }

    @Test
    fun lockNow_immediatelyLocksSessionAndEmitsLockedState() = runTest(testDispatcher) {
        val passphrase = "test-passphrase-2026".toCharArray()
        keySessionManager.setupPassphrase(passphrase)
        testDispatcher.scheduler.runCurrent()
        assertFalse(lockManager.isLocked.value)

        lockManager.lockNow()
        testDispatcher.scheduler.runCurrent()

        assertTrue(lockManager.isLocked.value)
        assertFalse(keySessionManager.isUnlocked.value)
    }

    @Test
    fun inactivityTimer_locksVaultAfterTimeout() = runTest(testDispatcher) {
        val passphrase = "test-passphrase-2026".toCharArray()
        keySessionManager.setupPassphrase(passphrase)
        testDispatcher.scheduler.runCurrent()
        assertFalse(lockManager.isLocked.value)

        lockManager.timeoutDurationMs = 10_000L

        // Reset timer
        lockManager.onUserInteraction()
        advanceTimeBy(5_000L)
        testDispatcher.scheduler.runCurrent()
        assertFalse(lockManager.isLocked.value)

        // Advance past timeout
        advanceTimeBy(6_000L)
        testDispatcher.scheduler.runCurrent()

        assertTrue(lockManager.isLocked.value)
        assertFalse(keySessionManager.isUnlocked.value)
    }

    @Test
    fun userInteraction_resetsInactivityTimer() = runTest(testDispatcher) {
        val passphrase = "test-passphrase-2026".toCharArray()
        keySessionManager.setupPassphrase(passphrase)
        testDispatcher.scheduler.runCurrent()

        lockManager.timeoutDurationMs = 10_000L

        lockManager.onUserInteraction()
        advanceTimeBy(7_000L)
        testDispatcher.scheduler.runCurrent()
        assertFalse(lockManager.isLocked.value)

        // User interacts, resetting timer
        lockManager.onUserInteraction()
        advanceTimeBy(7_000L)
        testDispatcher.scheduler.runCurrent()
        assertFalse(lockManager.isLocked.value)

        // Now advance past new timeout
        advanceTimeBy(4_000L)
        testDispatcher.scheduler.runCurrent()
        assertTrue(lockManager.isLocked.value)
    }

    @Test
    fun backgrounding_foregroundingAfterTimeout_triggersLock() = runTest(testDispatcher) {
        val passphrase = "test-passphrase-2026".toCharArray()
        keySessionManager.setupPassphrase(passphrase)
        testDispatcher.scheduler.runCurrent()

        lockManager.timeoutDurationMs = 100L
        lockManager.onAppBackgrounded()

        Thread.sleep(150L)
        lockManager.onAppForegrounded()
        testDispatcher.scheduler.runCurrent()

        assertTrue(lockManager.isLocked.value)
        assertFalse(keySessionManager.isUnlocked.value)
    }

    @Test
    fun defaultTimeout_is3Minutes() {
        org.junit.Assert.assertEquals(3 * 60 * 1000L, lockManager.timeoutDurationMs)
    }
}
