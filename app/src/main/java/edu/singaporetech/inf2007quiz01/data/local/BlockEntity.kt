package edu.singaporetech.inf2007quiz01.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One block in a per-CalBot blockchain.
 *
 * Room is now storing a proof-of-work, PQC-signed audit trail for calculator
 * button presses, which is either visionary or a cry for help.
 */
@Entity(
    tableName = "blocks",
    indices = [Index(value = ["cal_bot_id", "timestamp"])]
)
data class BlockEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "cal_bot_id")
    val calBotId: Int,
    @ColumnInfo(name = "expression")
    val expression: String,
    @ColumnInfo(name = "result")
    val result: Int,
    @ColumnInfo(name = "prev_hash")
    val prevHash: String,
    @ColumnInfo(name = "block_hash")
    val blockHash: String,
    @ColumnInfo(name = "nonce")
    val nonce: Long,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    @ColumnInfo(name = "raft_term")
    val raftTerm: Int,
    @ColumnInfo(name = "leader_node")
    val leaderNode: Int,
    @ColumnInfo(name = "votes")
    val votes: String,
    @ColumnInfo(name = "oracle_agreed")
    val oracleAgreed: Boolean,
    @ColumnInfo(name = "sig_node_0")
    val sigNode0: ByteArray,
    @ColumnInfo(name = "sig_node_1")
    val sigNode1: ByteArray,
    @ColumnInfo(name = "sig_node_2")
    val sigNode2: ByteArray,
    @ColumnInfo(name = "pubkey_node_0")
    val pubkeyNode0: ByteArray,
    @ColumnInfo(name = "pubkey_node_1")
    val pubkeyNode1: ByteArray,
    @ColumnInfo(name = "pubkey_node_2")
    val pubkeyNode2: ByteArray
)
