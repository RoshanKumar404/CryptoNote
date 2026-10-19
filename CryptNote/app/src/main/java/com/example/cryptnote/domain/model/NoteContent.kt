package com.example.cryptnote.domain.model

import kotlinx.serialization.Serializable

/**
 * Encrypted payload model containing the plaintext fields (title, body, tags, pinned)
 * serialized to JSON before AES-256-GCM encryption.
 */
@Serializable
data class NoteContent(
    val title: String = "",
    val body: String = "",
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false
)

