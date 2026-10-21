package com.example.cryptnote.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DecryptDiagnosticsTest {

    @Test
    fun recordsEntriesAndCapsAt20() {
        val diagnostics = DecryptDiagnosticsImpl()

        for (i in 1..25) {
            diagnostics.record(
                noteId = "note-$i",
                keyIdPrefixHex = "3d158e58",
                formatVersion = 1,
                appVersion = "1.0.0",
                resultClass = if (i % 2 == 0) "Opened" else "Corrupt"
            )
        }

        val recent = diagnostics.getRecent()
        assertEquals(20, recent.size)
        // Most recent should be note-25
        assertEquals("note-25", recent.first().noteId)
        // Oldest preserved should be note-6
        assertEquals("note-6", recent.last().noteId)
    }

    @Test
    fun clear_removesAllEntries() {
        val diagnostics = DecryptDiagnosticsImpl()
        diagnostics.record(
            noteId = "test-note",
            keyIdPrefixHex = "3d158e58",
            formatVersion = 1,
            appVersion = "1.0.0",
            resultClass = "Opened"
        )
        assertEquals(1, diagnostics.getRecent().size)

        diagnostics.clear()
        assertTrue(diagnostics.getRecent().isEmpty())
    }
}
