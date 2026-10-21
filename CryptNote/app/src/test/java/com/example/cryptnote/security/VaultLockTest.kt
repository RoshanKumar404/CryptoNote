package com.example.cryptnote.security

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VaultLockTest {

    @Test
    fun initialState_isLocked() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        var wipeCalled = false

        val vaultLock = VaultLock(
            scope = scope,
            wipe = { wipeCalled = true },
            idleMs = 60_000
        )

        assertEquals(VaultState.Locked, vaultLock.state.value)
    }

    @Test
    fun unlock_setsStateToUnlocked() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)

        val vaultLock = VaultLock(
            scope = scope,
            wipe = {},
            idleMs = 60_000
        )

        vaultLock.onUnlocked()
        assertEquals(VaultState.Unlocked, vaultLock.state.value)
    }

    @Test
    fun idleTimeout_triggersLockAndWipe() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        var wipeCount = 0

        val vaultLock = VaultLock(
            scope = scope,
            wipe = { wipeCount++ },
            idleMs = 60_000
        )

        vaultLock.onUnlocked()
        assertEquals(VaultState.Unlocked, vaultLock.state.value)

        // Advance time by 30 seconds: should remain unlocked
        scope.advanceTimeBy(30_000)
        assertEquals(VaultState.Unlocked, vaultLock.state.value)
        assertEquals(0, wipeCount)

        // Advance remaining 31 seconds: should trigger lock and wipe
        scope.advanceTimeBy(31_000)
        assertEquals(VaultState.Locked, vaultLock.state.value)
        assertEquals(1, wipeCount)
    }

    @Test
    fun touch_resetsIdleTimer() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        var wipeCount = 0

        val vaultLock = VaultLock(
            scope = scope,
            wipe = { wipeCount++ },
            idleMs = 60_000
        )

        vaultLock.onUnlocked()

        // 40 seconds elapse
        scope.advanceTimeBy(40_000)
        assertEquals(VaultState.Unlocked, vaultLock.state.value)

        // User interacts (touch)
        vaultLock.touch()

        // 40 more seconds elapse (total 80s, but only 40s since touch)
        scope.advanceTimeBy(40_000)
        assertEquals(VaultState.Unlocked, vaultLock.state.value)
        assertEquals(0, wipeCount)

        // 21 more seconds elapse: triggers lock
        scope.advanceTimeBy(21_000)
        assertEquals(VaultState.Locked, vaultLock.state.value)
        assertEquals(1, wipeCount)
    }

    @Test
    fun explicitLock_locksImmediatelyAndCallsWipe() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        var wipeCalled = false

        val vaultLock = VaultLock(
            scope = scope,
            wipe = { wipeCalled = true },
            idleMs = 60_000
        )

        vaultLock.onUnlocked()
        vaultLock.lock()

        assertEquals(VaultState.Locked, vaultLock.state.value)
        assertTrue(wipeCalled)
    }
}
