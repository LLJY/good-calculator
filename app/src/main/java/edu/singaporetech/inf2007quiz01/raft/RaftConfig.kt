package edu.singaporetech.inf2007quiz01.raft

object RaftConfig {
    const val ELECTION_TIMEOUT_MIN_MS = 150L
    const val ELECTION_TIMEOUT_MAX_MS = 300L
    const val HEARTBEAT_INTERVAL_MS = 50L
    const val NODE_COUNT = 3

    fun portForOperator(op: String, nodeId: Int): Int {
        require(nodeId in 0 until NODE_COUNT) { "Unsupported node id: $nodeId" }
        val basePort = when (op) {
            "+" -> 50051
            "-" -> 50054
            "*" -> 50057
            "/" -> 50060
            else -> error("Unsupported operator for Raft port mapping: $op")
        }
        return basePort + nodeId
    }
}
