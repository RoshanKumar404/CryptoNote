package com.example.cryptnote.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Singleton Room entity that tracks the last pull cursor for incremental sync.
 *
 * The [lastPullCursor] corresponds to the server's `server_seq` value.
 * On each pull, we request changes since this cursor and update it
 * to the newest `server_seq` received.
 *
 * Only one row exists in this table (id = 1).
 */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey
    val id: Int = 1,

    @ColumnInfo(name = "last_pull_cursor")
    val lastPullCursor: Long = 0L
)
