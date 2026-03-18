package edu.singaporetech.inf2007quiz01.raft

enum class NodeRole {
    FOLLOWER,
    CANDIDATE,
    LEADER
}

data class RaftNodeState(
    var currentTerm: Int,
    var votedFor: Int?,
    var role: NodeRole,
    var leaderId: Int?,
    val log: MutableList<LogEntry>
)
