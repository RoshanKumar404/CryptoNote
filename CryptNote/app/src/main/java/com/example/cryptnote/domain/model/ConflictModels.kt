package com.example.cryptnote.domain.model

/**
 * Domain model representing two conflicting versions of a note sharing the same [conflictGroupId].
 *
 * @property conflictGroupId Unique identifier grouping the conflicting versions together.
 * @property localNote The local fork containing user edits made on this device.
 * @property serverNote The canonical note updated from the remote cloud server.
 */
data class ConflictPair(
    val conflictGroupId: String,
    val localNote: Note,
    val serverNote: Note
)

/**
 * User-selected strategy for resolving a synchronization conflict.
 */
sealed interface ConflictResolutionAction {
    /**
     * Keep the local version, overwriting the server version on the next sync push.
     */
    data object KeepLocal : ConflictResolutionAction

    /**
     * Keep the server version, discarding local edits.
     */
    data object KeepServer : ConflictResolutionAction

    /**
     * Merge contents into a single consolidated note with updated title and body.
     */
    data class CustomMerge(
        val mergedTitle: String,
        val mergedBody: String
    ) : ConflictResolutionAction
}
