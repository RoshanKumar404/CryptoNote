package com.example.cryptnote.security

import android.util.Log
import java.util.concurrent.ConcurrentLinkedDeque
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Diagnostic audit entry for note decryption attempts per NV-FE-9.
 *
 * Strictly non-sensitive metadata only:
 * - Never contains key material, plaintext, or envelope payloads.
 */
data class DecryptDiagnosticEntry(
    val timestamp: Long = System.currentTimeMillis(),
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
    private val history = ConcurrentLinkedDeque<DecryptDiagnosticEntry>()

    override fun record(
        noteId: String,
        keyIdPrefixHex: String,
        formatVersion: Int,
        appVersion: String,
        resultClass: String
    ) {
        val entry = DecryptDiagnosticEntry(
            noteId = noteId,
            keyIdPrefixHex = keyIdPrefixHex,
            formatVersion = formatVersion,
            appVersion = appVersion,
            resultClass = resultClass
        )

        // NV-FE-9: Never log keys, plaintext or envelopes.
        // Surface only non-sensitive audit metadata.
        try {
            Log.i(
                TAG,
                "Decrypt audit: noteId=$noteId, keyIdPrefix=$keyIdPrefixHex, formatVersion=$formatVersion, appVersion=$appVersion, result=$resultClass"
            )
        } catch (_: Exception) {
            // In unit tests where android.util.Log is not mocked
        }

        history.addFirst(entry)
        while (history.size > MAX_ENTRIES) {
            history.removeLast()
        }
    }

    override fun getRecent(): List<DecryptDiagnosticEntry> = history.toList()

    override fun clear() {
        history.clear()
    }

    companion object {
        private const val TAG = "DecryptDiagnostics"
        const val MAX_ENTRIES = 20
    }
}
