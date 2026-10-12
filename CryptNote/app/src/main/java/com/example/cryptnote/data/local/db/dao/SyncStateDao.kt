package com.example.cryptnote.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.cryptnote.data.local.db.entity.SyncStateEntity

/**
 * Data Access Object for the [SyncStateEntity] singleton.
 *
 * Manages the pull cursor used for incremental server sync.
 */
@Dao
interface SyncStateDao {

    /**
     * Get the current sync state (pull cursor).
     * Returns null if no sync has ever occurred.
     */
    @Query("SELECT * FROM sync_state WHERE id = 1")
    suspend fun getSyncState(): SyncStateEntity?

    /**
     * Insert or replace the sync state.
     * Used to initialize or update the pull cursor.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncState(state: SyncStateEntity)

    /**
     * Update just the pull cursor value.
     */
    @Query("UPDATE sync_state SET last_pull_cursor = :cursor WHERE id = 1")
    suspend fun updatePullCursor(cursor: Long)
}
