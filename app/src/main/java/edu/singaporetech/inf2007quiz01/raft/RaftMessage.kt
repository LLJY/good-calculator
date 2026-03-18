package edu.singaporetech.inf2007quiz01.raft

sealed interface RaftMessage {
    data class VoteRequest(
        val term: Int,
        val candidateId: Int,
        val lastLogIndex: Int,
        val lastLogTerm: Int
    ) : RaftMessage

    data class VoteResponse(
        val term: Int,
        val voteGranted: Boolean,
        val responderId: Int
    ) : RaftMessage

    data class AppendEntries(
        val term: Int,
        val leaderId: Int,
        val entries: List<LogEntry>,
        val leaderCommit: Int
    ) : RaftMessage

    data class AppendResponse(
        val term: Int,
        val success: Boolean,
        val responderId: Int
    ) : RaftMessage
}

data class LogEntry(
    val term: Int,
    val command: Any
)
