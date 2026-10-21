package com.example.cryptnote.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO representing an individual note mutation within a batch push request.
 */
@Serializable
data class PushOpDto(
    @SerialName("op_id")
    val opId: String,

    @SerialName("note_id")
    val noteId: String,

    val type: String,

    @SerialName("base_version")
    val baseVersion: Int,

    val version: Int,

    val ciphertext: String? = null,

    val nonce: String? = null,

    val envelope: String? = null,

    @SerialName("format_version")
    val formatVersion: Int? = null,

    @SerialName("key_id")
    val keyId: String? = null,

    val checksum: String? = null
)

/**
 * DTO representing the batch push request body.
 */
@Serializable
data class PushRequestDto(
    val ops: List<PushOpDto>
)

/**
 * DTO representing an individual operation result within the batch push response.
 */
@Serializable
data class PushResultDto(
    @SerialName("op_id")
    val opId: String,

    @SerialName("note_id")
    val noteId: String? = null,

    val status: String,

    val result: String? = null,

    @SerialName("server_version")
    val serverVersion: Int,

    @SerialName("server_seq")
    val serverSeq: Long? = null
)

/**
 * DTO representing the batch push response body.
 */
@Serializable
data class PushResponseDto(
    val results: List<PushResultDto>
)

/**
 * DTO representing an incremental note change returned by the pull endpoint.
 */
@Serializable
data class PullChangeDto(
    val id: String,

    val ciphertext: String? = null,

    val nonce: String? = null,

    val envelope: String? = null,

    @SerialName("format_version")
    val formatVersion: Int = 0,

    @SerialName("key_id")
    val keyId: String? = null,

    val checksum: String? = null,

    val version: Int,

    val deleted: Boolean,

    @SerialName("updated_at")
    val updatedAt: Long,

    @SerialName("server_seq")
    val serverSeq: Long
)

/**
 * DTO representing the incremental pull response body.
 */
@Serializable
data class PullResponseDto(
    val changes: List<PullChangeDto>,

    @SerialName("next_cursor")
    val nextCursor: Long,

    @SerialName("has_more")
    val hasMore: Boolean
)

/**
 * DTO representing the JSON-encoded payload stored in OutboxEntity for create/update.
 */
@Serializable
data class OutboxPayloadDto(
    val ciphertext: String? = null,
    val nonce: String? = null,
    val envelope: String? = null,
    val formatVersion: Int = 1,
    val keyId: String? = null,
    val checksum: String? = null
)
