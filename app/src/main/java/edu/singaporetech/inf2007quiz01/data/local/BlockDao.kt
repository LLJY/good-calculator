package edu.singaporetech.inf2007quiz01.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockDao {

    @Query("SELECT * FROM blocks WHERE cal_bot_id = :calBotId ORDER BY timestamp DESC")
    fun getBlocksForCalBot(calBotId: Int): Flow<List<BlockEntity>>

    @Query("SELECT * FROM blocks WHERE cal_bot_id = :calBotId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestBlock(calBotId: Int): BlockEntity?

    @Insert
    suspend fun insert(block: BlockEntity)

    @Query("SELECT COUNT(*) FROM blocks WHERE cal_bot_id = :calBotId")
    suspend fun getBlockCount(calBotId: Int): Int

    /** Non-Flow query for the COBOL-generated BlockchainVerifier (Phase 9). */
    @Query("SELECT * FROM blocks WHERE cal_bot_id = :calBotId ORDER BY timestamp ASC")
    suspend fun getBlocksForCalBotList(calBotId: Int): List<BlockEntity>
}
