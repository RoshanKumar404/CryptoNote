package com.example.cryptnote.domain.usecase

import com.example.cryptnote.domain.model.ConflictResolutionAction
import com.example.cryptnote.domain.repository.NoteRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case responsible for orchestrating the resolution of synchronization conflicts.
 *
 * Implements Dependency Inversion Principle (DIP):
 * Relies on the [NoteRepository] interface rather than concrete Room data sources.
 */
@Singleton
class ResolveConflictUseCase @Inject constructor(
    private val noteRepository: NoteRepository
) {

    /**
     * Executes the requested [action] for the conflicting note group identified by [conflictGroupId].
     */
    suspend operator fun invoke(
        conflictGroupId: String,
        action: ConflictResolutionAction
    ) {
        require(conflictGroupId.isNotBlank()) { "conflictGroupId cannot be blank" }
        noteRepository.resolveConflict(conflictGroupId, action)
    }
}
