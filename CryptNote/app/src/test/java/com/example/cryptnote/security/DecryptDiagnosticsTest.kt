package com.example.cryptnote.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DecryptDiagnosticsTest {

    @Test
    fun testRecordAndRetrieveDiagnostics() {
        val diagnostics = DecryptDiagnosticsImpl()
        diagnostics.record(
            noteId = "test-note-id",
            keyIdPrefixHex = "a1b2c3d4",
            formatVersion = 1,
            appVersion = "1.0.0",
            resultClass = "Opened"
        )

        val recent = diagnostics.getRecent()
        assertEquals(1, recent.size)
        assertEquals("test-note-id", recent[0].noteId)
        assertEquals("Opened", recent[0].resultClass)
    }

    @Test
    fun testClearDiagnostics() {
        val diagnostics = DecryptDiagnosticsImpl()
        diagnostics.record(
            noteId = "test-note-id",
            keyIdPrefixHex = "a1b2c3d4",
            formatVersion = 1,
            appVersion = "1.0.0",
            resultClass = "Opened"
        )
        assertEquals(1, diagnostics.getRecent().size)
        diagnostics.clear()
        assertTrue(diagnostics.getRecent().isEmpty())
    }
}
