package com.example.cryptnote.domain.usecase

import com.example.cryptnote.domain.model.Note
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case responsible for zero-knowledge in-memory searching over decrypted notes.
 *
 * Implements Single Responsibility Principle (SRP):
 * All tokenization, case normalization, and relevance scoring reside here.
 */
@Singleton
class SearchNotesUseCase @Inject constructor() {

    /**
     * Filters and ranks [notes] based on [query].
     *
     * If [query] is blank, all non-deleted notes are returned preserving their default order.
     * When [query] is present:
     * - Case-insensitive substring matching is evaluated across title and body.
     * - Results are ranked by relevance: title matches rank above body-only matches.
     */
    operator fun invoke(notes: List<Note>, query: String): List<Note> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return notes.filterNot { it.deleted }
        }

        return notes
            .filterNot { it.deleted }
            .mapNotNull { note ->
                val titleMatch = note.title.contains(trimmed, ignoreCase = true)
                val bodyMatch = note.body.contains(trimmed, ignoreCase = true)

                if (titleMatch || bodyMatch) {
                    val score = when {
                        note.title.equals(trimmed, ignoreCase = true) -> 3
                        note.title.startsWith(trimmed, ignoreCase = true) -> 2
                        titleMatch -> 1
                        else -> 0
                    }
                    Pair(note, score)
                } else {
                    null
                }
            }
            .sortedWith(
                compareByDescending<Pair<Note, Int>> { it.second }
                    .thenByDescending { it.first.updatedAt }
            )
            .map { it.first }
    }
}
