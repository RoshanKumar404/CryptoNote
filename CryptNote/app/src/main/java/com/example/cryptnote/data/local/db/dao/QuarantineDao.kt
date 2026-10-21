package com.example.cryptnote.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.cryptnote.data.local.db.entity.QuarantineEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the [QuarantineEntity] table.
 */
@Dao
interface QuarantineDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: QuarantineEntity)

    @Query("SELECT * FROM quarantine ORDER BY saved_at DESC")
    fun observeAll(): Flow<List<QuarantineEntity>>

    @Query("SELECT * FROM quarantine WHERE note_id = :noteId ORDER BY saved_at DESC LIMIT 1")
    suspend fun getByNoteId(noteId: String): QuarantineEntity?

    @Query("DELETE FROM quarantine WHERE note_id = :noteId")
    suspend fun deleteByNoteId(noteId: String)

    @Query("DELETE FROM quarantine WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM quarantine WHERE saved_at < :cutoffTime")
    suspend fun purgeOlderThan(cutoffTime: Long)
}
