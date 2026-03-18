package edu.singaporetech.inf2007quiz01.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for both calculator history and the per-CalBot blockchain.
 *
 * We still use destructive migration because this project now treats schema
 * evolution with the same calm restraint it applies to distributed consensus.
 */
@Database(entities = [HistoryEntry::class, BlockEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun blockDao(): BlockDao
}
