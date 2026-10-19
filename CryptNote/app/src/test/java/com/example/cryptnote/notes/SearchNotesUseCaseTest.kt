package com.example.cryptnote.notes

import com.example.cryptnote.domain.model.Note
import com.example.cryptnote.domain.model.SyncStatus
import com.example.cryptnote.domain.usecase.SearchNotesUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SearchNotesUseCaseTest {

    private lateinit var searchNotesUseCase: SearchNotesUseCase

    @Before
    fun setUp() {
        searchNotesUseCase = SearchNotesUseCase()
    }

    @Test
    fun `blank query returns all non-deleted notes in original order`() {
        val notes = listOf(
            createNote(id = "1", title = "Alpha", body = "First", deleted = false),
            createNote(id = "2", title = "Beta", body = "Second", deleted = true),
            createNote(id = "3", title = "Gamma", body = "Third", deleted = false)
        )

        val result = searchNotesUseCase(notes, "   ")
        assertEquals(2, result.size)
        assertEquals("1", result[0].id)
        assertEquals("3", result[1].id)
    }

    @Test
    fun `case-insensitive title and body matching`() {
        val notes = listOf(
            createNote(id = "1", title = "Bitcoin Wallet", body = "Seed phrase backup"),
            createNote(id = "2", title = "Shopping List", body = "Buy milk and eggs"),
            createNote(id = "3", title = "Secret Recipe", body = "Store BITCOIN address safely")
        )

        val result = searchNotesUseCase(notes, "bitcoin")
        assertEquals(2, result.size)
        assertTrue(result.any { it.id == "1" })
        assertTrue(result.any { it.id == "3" })
    }

    @Test
    fun `exact title match ranks higher than substring or body match`() {
        val notes = listOf(
            createNote(id = "body_match", title = "Miscellaneous", body = "My password is here", updatedAt = 100),
            createNote(id = "exact_match", title = "Password", body = "Account secrets", updatedAt = 50),
            createNote(id = "prefix_match", title = "Passwords for Apps", body = "List", updatedAt = 70)
        )

        val result = searchNotesUseCase(notes, "password")
        assertEquals(3, result.size)
        assertEquals("exact_match", result[0].id)
        assertEquals("prefix_match", result[1].id)
        assertEquals("body_match", result[2].id)
    }

    @Test
    fun `no matching notes returns empty list`() {
        val notes = listOf(
            createNote(id = "1", title = "Hello", body = "World"),
            createNote(id = "2", title = "Android", body = "Jetpack Compose")
        )

        val result = searchNotesUseCase(notes, "Quantum")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `pinned notes are prioritized at top of blank search results`() {
        val notes = listOf(
            createNote(id = "1", title = "Unpinned Recent", body = "A", updatedAt = 200, pinned = false),
            createNote(id = "2", title = "Pinned Older", body = "B", updatedAt = 100, pinned = true),
            createNote(id = "3", title = "Pinned Newest", body = "C", updatedAt = 300, pinned = true)
        )

        val result = searchNotesUseCase(notes, "")
        assertEquals(3, result.size)
        assertEquals("3", result[0].id)
        assertEquals("2", result[1].id)
        assertEquals("1", result[2].id)
    }

    @Test
    fun `tagFilter returns only notes matching the specified tag`() {
        val notes = listOf(
            createNote(id = "1", title = "Work note", body = "Todo", tags = listOf("work", "crypto")),
            createNote(id = "2", title = "Personal note", body = "Groceries", tags = listOf("personal")),
            createNote(id = "3", title = "Another work note", body = "Meeting", tags = listOf("work"))
        )

        val result = searchNotesUseCase(notes, query = "", tagFilter = "#work")
        assertEquals(2, result.size)
        assertEquals("1", result[0].id)
        assertEquals("3", result[1].id)
    }

    @Test
    fun `search query matches tags as well`() {
        val notes = listOf(
            createNote(id = "1", title = "Important Doc", body = "Content", tags = listOf("finances")),
            createNote(id = "2", title = "Recipe", body = "Cookies", tags = listOf("cooking"))
        )

        val result = searchNotesUseCase(notes, query = "finance")
        assertEquals(1, result.size)
        assertEquals("1", result[0].id)
    }

    private fun createNote(
        id: String,
        title: String,
        body: String,
        deleted: Boolean = false,
        updatedAt: Long = System.currentTimeMillis(),
        tags: List<String> = emptyList(),
        pinned: Boolean = false
    ): Note {
        return Note(
            id = id,
            title = title,
            body = body,
            version = 1,
            updatedAt = updatedAt,
            deleted = deleted,
            syncStatus = SyncStatus.SYNCED,
            tags = tags,
            pinned = pinned
        )
    }
}
