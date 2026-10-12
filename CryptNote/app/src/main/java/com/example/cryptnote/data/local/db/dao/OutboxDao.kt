package com.example.cryptnote.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.cryptnote.data.local.db.entity.OutboxEntity

/**
 * Data Access Object for the [OutboxEntity] outbox queue.
 *
 * The outbox is consumed by the sync worker, which pushes
 * operations to the backend in chronological order.
 */
@Dao
interface OutboxDao {

    /**
     * Insert a new outbox entry (within the same transaction as the note mutation).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutboxEntry(entry: OutboxEntity)

    /**
     * Get all pending outbox entries, ordered by creation time (FIFO).
     * @param limit Maximum number of entries to return per batch.
     */
    @Query("SELECT * FROM outbox WHERE status = 'pending' ORDER BY created_at ASC LIMIT :limit")
    suspend fun getPendingEntries(limit: Int = 50): List<OutboxEntity>

    /**
     * Mark an outbox entry as successfully sent.
     */
    @Query("UPDATE outbox SET status = 'sent' WHERE op_id = :opId")
    suspend fun markAsSent(opId: String)

    /**
     * Mark an outbox entry as failed and increment the attempt counter.
     */
    @Query("UPDATE outbox SET status = 'failed', attempts = attempts + 1 WHERE op_id = :opId")
    suspend fun markAsFailed(opId: String)

    /**
     * Reset failed entries back to pending for retry.
     * Only resets entries that haven't exceeded the max attempt count.
     * @param maxAttempts Maximum number of retries before giving up.
     */
    @Query("UPDATE outbox SET status = 'pending' WHERE status = 'failed' AND attempts < :maxAttempts")
    suspend fun resetFailedEntries(maxAttempts: Int = 5)

    /**
     * Delete successfully sent entries that are older than the given timestamp.
     * Called periodically to keep the outbox table lean.
     */
    @Query("DELETE FROM outbox WHERE status = 'sent' AND created_at < :olderThan")
    suspend fun purgeSentEntries(olderThan: Long)

    /**
     * Get the count of pending outbox entries.
     */
    @Query("SELECT COUNT(*) FROM outbox WHERE status = 'pending'")
    suspend fun getPendingCount(): Int

    /**
     * Get an outbox entry by its operation ID.
     */
    @Query("SELECT * FROM outbox WHERE op_id = :opId")
    suspend fun getEntryByOpId(opId: String): OutboxEntity?
}
