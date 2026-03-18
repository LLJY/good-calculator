package edu.singaporetech.inf2007quiz01.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single expression stored in the history, tied to a specific CalBot.
 * We track the timestamp so we can sort by most recent and trim old entries.
 *
 * The composite index on (cal_bot_id, timestamp) speeds up the DAO queries
 * that filter by CalBot and order by time — which is every single query we run.
 */
@Entity(
    tableName = "history_entries",
    indices = [Index(value = ["cal_bot_id", "timestamp"])]
)
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "cal_bot_id")
    val calBotId: Int,

    @ColumnInfo(name = "expression")
    val expression: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
)
