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
     * Filters and ranks [notes] based on [query] and optional [tagFilter].
     *
     * - Pinned notes are prioritized at the top.
     * - Supports tag matching in search query as well as direct tag chip filtering.
     * - Results are ranked by relevance: exact title > prefix title > title/tag > body.
     */
    operator fun invoke(
        notes: List<Note>,
        query: String,
        tagFilter: String? = null
    ): List<Note> {
        val activeNotes = notes.filterNot { it.deleted }.let { list ->
            if (tagFilter.isNullOrBlank()) {
                list
            } else {
                list.filter { note ->
                    note.tags.any { it.equals(tagFilter.trim().removePrefix("#"), ignoreCase = true) }
                }
            }
        }

        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return activeNotes.sortedWith(
                compareByDescending<Note> { it.pinned }
                    .thenByDescending { it.updatedAt }
            )
        }

        val normalizedQuery = trimmed.removePrefix("#")

        return activeNotes
            .mapNotNull { note ->
                val titleMatch = note.title.contains(trimmed, ignoreCase = true)
                val bodyMatch = note.body.contains(trimmed, ignoreCase = true)
                val tagMatch = note.tags.any { it.contains(normalizedQuery, ignoreCase = true) }

                if (titleMatch || bodyMatch || tagMatch) {
                    val score = when {
                        note.title.equals(trimmed, ignoreCase = true) -> 4
                        note.title.startsWith(trimmed, ignoreCase = true) -> 3
                        titleMatch -> 2
                        tagMatch -> 2
                        bodyMatch -> 1
                        else -> 0
                    }
                    Pair(note, score)
                } else {
                    null
                }
            }
            .sortedWith(
                compareByDescending<Pair<Note, Int>> { it.first.pinned }
                    .thenByDescending { it.second }
                    .thenByDescending { it.first.updatedAt }
            )
            .map { it.first }
    }
}
