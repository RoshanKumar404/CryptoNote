package com.example.cryptnote.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing an encrypted note stored locally.
 *
 * At the database layer, notes are always stored as ciphertext.
 * The repository layer handles encryption/decryption transparently.
 *
 * @property id Client-generated UUID for the note.
 * @property ciphertext Encrypted blob containing the serialized title + body JSON.
 * @property nonce Unique 12-byte nonce used for AES-256-GCM encryption.
 * @property version Optimistic concurrency version, incremented on every update.
 * @property updatedAt Epoch milliseconds of the last local modification.
 * @property deleted Tombstone flag for soft-delete sync support.
 * @property syncStatus Current synchronization state: "pending", "synced", or "failed".
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val ciphertext: ByteArray = ByteArray(0),

    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val nonce: ByteArray = ByteArray(0),

    val version: Int = 1,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    val deleted: Boolean = false,

    @ColumnInfo(name = "sync_status")
    val syncStatus: String = "pending",

    @ColumnInfo(name = "conflict_group_id")
    val conflictGroupId: String? = null,

    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val envelope: ByteArray? = null,

    @ColumnInfo(name = "format_version")
    val formatVersion: Int = 0,

    @ColumnInfo(name = "key_id", typeAffinity = ColumnInfo.BLOB)
    val keyId: ByteArray? = null,

    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val checksum: ByteArray? = null,

    @ColumnInfo(name = "is_vault")
    val isVault: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NoteEntity

        if (id != other.id) return false
        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (!nonce.contentEquals(other.nonce)) return false
        if (version != other.version) return false
        if (updatedAt != other.updatedAt) return false
        if (deleted != other.deleted) return false
        if (syncStatus != other.syncStatus) return false
        if (conflictGroupId != other.conflictGroupId) return false
        if (envelope != null) {
            if (other.envelope == null) return false
            if (!envelope.contentEquals(other.envelope)) return false
        } else if (other.envelope != null) return false
        if (formatVersion != other.formatVersion) return false
        if (keyId != null) {
            if (other.keyId == null) return false
            if (!keyId.contentEquals(other.keyId)) return false
        } else if (other.keyId != null) return false
        if (checksum != null) {
            if (other.checksum == null) return false
            if (!checksum.contentEquals(other.checksum)) return false
        } else if (other.checksum != null) return false
        if (isVault != other.isVault) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + version
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + deleted.hashCode()
        result = 31 * result + syncStatus.hashCode()
        result = 31 * result + (conflictGroupId?.hashCode() ?: 0)
        result = 31 * result + (envelope?.contentHashCode() ?: 0)
        result = 31 * result + formatVersion
        result = 31 * result + (keyId?.contentHashCode() ?: 0)
        result = 31 * result + (checksum?.contentHashCode() ?: 0)
        result = 31 * result + isVault.hashCode()
        return result
    }
}
