package edu.singaporetech.inf2007quiz01.consensus

data class ConsensusResult(
    val result: Int,
    val unanimous: Boolean,
    val oracleAgreed: Boolean,
    val pqcQuorumMet: Boolean,
    val raftTerm: Int,
    val leaderNode: Int,
    val votes: List<Int>,
    val signatures: List<ByteArray>,
    val publicKeys: List<ByteArray>,
    val blockHash: String,
    val nonce: Long,
    val wallTimeMs: Long,
    val mood: String = "awaiting consciousness"
)
