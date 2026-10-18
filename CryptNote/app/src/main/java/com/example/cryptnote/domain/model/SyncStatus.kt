package com.example.cryptnote.domain.model

/**
 * Representation of a note's synchronization state with the backend server.
 */
enum class SyncStatus(val value: String) {
    PENDING("pending"),
    SYNCING("syncing"),
    SYNCED("synced"),
    FAILED("failed"),
    CONFLICT("conflict");

    companion object {
        fun fromValue(value: String): SyncStatus {
            return entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: PENDING
        }
    }
}
