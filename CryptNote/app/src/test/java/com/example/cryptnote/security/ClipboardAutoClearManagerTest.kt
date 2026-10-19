package com.example.cryptnote.security

import com.example.cryptnote.security.clipboard.ClipboardAutoClearManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClipboardAutoClearManagerTest {

    private class TestableClipboardAutoClearManager(
        dispatcher: CoroutineDispatcher
    ) : ClipboardAutoClearManager {
        var currentClip: String? = null
        var clearCount = 0
        private var scheduledJob: Job? = null
        private val scope = CoroutineScope(dispatcher)

        override fun copyWithAutoClear(
            label: String,
            text: String,
            ttlMillis: Long,
            onCleared: (() -> Unit)?
        ) {
            currentClip = text
            scheduledJob?.cancel()
            scheduledJob = scope.launch {
                delay(ttlMillis)
                if (currentClip == text) {
                    clearClipboard()
                    onCleared?.invoke()
                }
            }
        }

        override fun clearClipboard() {
            currentClip = null
            clearCount++
            scheduledJob?.cancel()
        }
    }

    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun copyWithAutoClear_setsContentAndClearsAfterTtl() = runTest(testDispatcher) {
        val manager = TestableClipboardAutoClearManager(testDispatcher)
        var callbackFired = false

        manager.copyWithAutoClear("Label", "Secret Note 123", ttlMillis = 60_000L) {
            callbackFired = true
        }

        assertEquals("Secret Note 123", manager.currentClip)

        // Advance 30s: should NOT be cleared yet
        advanceTimeBy(30_000L)
        assertEquals("Secret Note 123", manager.currentClip)

        // Advance another 31s: exceeds 60s, should be cleared
        advanceTimeBy(31_000L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(manager.currentClip)
        assertTrue(callbackFired)
        assertEquals(1, manager.clearCount)
    }

    @Test
    fun copyWithAutoClear_doesNotClearIfUserCopiedNewContent() = runTest(testDispatcher) {
        val manager = TestableClipboardAutoClearManager(testDispatcher)

        manager.copyWithAutoClear("Label", "First Secret", ttlMillis = 60_000L)
        assertEquals("First Secret", manager.currentClip)

        advanceTimeBy(40_000L)

        // User copies something else
        manager.currentClip = "New Unrelated Content"

        advanceTimeBy(25_000L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Clip should NOT be wiped because content changed
        assertEquals("New Unrelated Content", manager.currentClip)
        assertEquals(0, manager.clearCount)
    }
}
