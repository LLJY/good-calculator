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
    val mood: String = "awaiting consciousness",
    // --- Neural Arithmetic Verification ---
    // A 2-layer MLP trained on 47 samples gets an actual vote.
    val mlpVote: Int = Int.MIN_VALUE,
    val mlpAgreed: Boolean = false,
    val mlpRawOutput: Float = 0f,
    val mlpConfidence: Float = 0f,
    // --- Blockchain Integrity Proof (Phase 9) ---
    // Verified by COBOL-generated Kotlin, tested by Ada/SPARK.
    val chainVerdict: String = "PENDING",
    val chainIntegrityProof: String = ""
)
