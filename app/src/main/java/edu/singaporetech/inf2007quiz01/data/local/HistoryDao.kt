package edu.singaporetech.inf2007quiz01.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for reading/writing expression history per CalBot.
 * Each CalBot keeps at most 20 entries — oldest get trimmed after every insert.
 */
@Dao
interface HistoryDao {
    /** Grab the last 20 expressions for a given CalBot, newest first. */
    @Query("SELECT * FROM history_entries WHERE cal_bot_id = :calBotId ORDER BY timestamp DESC LIMIT 20")
    fun getHistoryForCalBot(calBotId: Int): Flow<List<HistoryEntry>>

    @Insert
    suspend fun insert(entry: HistoryEntry)

    /** Delete anything older than the 20 most recent entries for this CalBot. */
    @Query(
        "DELETE FROM history_entries WHERE cal_bot_id = :calBotId AND id NOT IN " +
        "(SELECT id FROM history_entries WHERE cal_bot_id = :calBotId ORDER BY timestamp DESC LIMIT 20)"
    )
    suspend fun trimHistory(calBotId: Int)
}
