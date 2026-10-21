package com.example.cryptnote.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing an unreadable or quarantined note.
 *
 * Per NV-FE-3 and NV-FE-4:
 * When a note fails decryption or is deleted while unreadable, its envelope is preserved
 * in quarantine for up to 30 days before permanent deletion.
 *
 * @property id Auto-incrementing primary key.
 * @property noteId UUID of the original note.
 * @property envelope The opaque encrypted payload preserved untouched.
 * @property reason Human-readable or typed error reason (e.g. "Corrupt", "KeyMismatch").
 * @property savedAt Epoch timestamp when the note was moved to quarantine.
 */
@Entity(tableName = "quarantine")
data class QuarantineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "note_id")
    val noteId: String,

    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val envelope: ByteArray,

    val reason: String,

    @ColumnInfo(name = "saved_at")
    val savedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as QuarantineEntity

        if (id != other.id) return false
        if (noteId != other.noteId) return false
        if (!envelope.contentEquals(other.envelope)) return false
        if (reason != other.reason) return false
        if (savedAt != other.savedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + noteId.hashCode()
        result = 31 * result + envelope.contentHashCode()
        result = 31 * result + reason.hashCode()
        result = 31 * result + savedAt.hashCode()
        return result
    }
}
