package com.example.cryptnote.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.cryptnote.data.local.db.dao.NoteDao
import com.example.cryptnote.data.local.db.dao.OutboxDao
import com.example.cryptnote.data.local.db.dao.SyncStateDao
import com.example.cryptnote.data.local.db.entity.NoteEntity
import com.example.cryptnote.data.local.db.entity.OutboxEntity
import com.example.cryptnote.data.local.db.entity.SyncStateEntity

/**
 * Room Database for CryptoNote.
 *
 * Encapsulates the local SQLite database.
 * Notes are stored in encrypted format (ciphertext + nonce).
 * Outbox operations track pending mutations for background synchronization.
 */
@Database(
    entities = [
        NoteEntity::class,
        OutboxEntity::class,
        SyncStateEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CryptNoteDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao

    abstract fun outboxDao(): OutboxDao

    abstract fun syncStateDao(): SyncStateDao

    companion object {
        const val DATABASE_NAME = "cryptnote.db"
    }
}
