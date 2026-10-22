package com.example.cryptnote.security

import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

data class DecryptDiagnosticEntry(
    val timestamp: Long,
    val noteId: String,
    val keyIdPrefixHex: String,
    val formatVersion: Int,
    val appVersion: String,
    val resultClass: String
)

interface DecryptDiagnostics {
    fun record(
        noteId: String,
        keyIdPrefixHex: String,
        formatVersion: Int,
        appVersion: String,
        resultClass: String
    )
    fun getRecent(): List<DecryptDiagnosticEntry>
    fun clear()
}

@Singleton
class DecryptDiagnosticsImpl @Inject constructor() : DecryptDiagnostics {

    private val entries = ConcurrentLinkedQueue<DecryptDiagnosticEntry>()

    override fun record(
        noteId: String,
        keyIdPrefixHex: String,
        formatVersion: Int,
        appVersion: String,
        resultClass: String
    ) {
        val entry = DecryptDiagnosticEntry(
            timestamp = System.currentTimeMillis(),
            noteId = noteId,
            keyIdPrefixHex = keyIdPrefixHex,
            formatVersion = formatVersion,
            appVersion = appVersion,
            resultClass = resultClass
        )
        entries.add(entry)
        while (entries.size > MAX_DIAGNOSTICS_ENTRIES) {
            entries.poll()
        }
    }

    override fun getRecent(): List<DecryptDiagnosticEntry> {
        return entries.toList().reversed()
    }

    override fun clear() {
        entries.clear()
    }

    companion object {
        private const val MAX_DIAGNOSTICS_ENTRIES = 20
    }
}
