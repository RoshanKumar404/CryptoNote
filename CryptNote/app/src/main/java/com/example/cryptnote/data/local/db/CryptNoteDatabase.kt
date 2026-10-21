package com.example.cryptnote.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.cryptnote.data.local.db.dao.NoteDao
import com.example.cryptnote.data.local.db.dao.OutboxDao
import com.example.cryptnote.data.local.db.dao.QuarantineDao
import com.example.cryptnote.data.local.db.dao.SyncStateDao
import com.example.cryptnote.data.local.db.entity.NoteEntity
import com.example.cryptnote.data.local.db.entity.OutboxEntity
import com.example.cryptnote.data.local.db.entity.QuarantineEntity
import com.example.cryptnote.data.local.db.entity.SyncStateEntity

/**
 * Room Database for CryptoNote.
 *
 * Encapsulates the local SQLite database.
 * Version 1: Notes stored as (ciphertext + nonce).
 * Version 2: Added opaque envelope storage, format_version, key_id, checksum, is_vault, and quarantine table (NV-FE-3).
 */
@Database(
    entities = [
        NoteEntity::class,
        OutboxEntity::class,
        SyncStateEntity::class,
        QuarantineEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class CryptNoteDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao

    abstract fun outboxDao(): OutboxDao

    abstract fun syncStateDao(): SyncStateDao

    abstract fun quarantineDao(): QuarantineDao

    companion object {
        const val DATABASE_NAME = "cryptnote.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN envelope BLOB DEFAULT NULL")
                db.execSQL("ALTER TABLE notes ADD COLUMN format_version INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN key_id BLOB DEFAULT NULL")
                db.execSQL("ALTER TABLE notes ADD COLUMN checksum BLOB DEFAULT NULL")
                db.execSQL("ALTER TABLE notes ADD COLUMN is_vault INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS quarantine (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        note_id TEXT NOT NULL,
                        envelope BLOB NOT NULL,
                        reason TEXT NOT NULL,
                        saved_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
