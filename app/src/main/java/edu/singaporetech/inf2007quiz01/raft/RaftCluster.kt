package edu.singaporetech.inf2007quiz01.raft

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

data class RaftResult(
    val result: Int,
    val term: Int,
    val leaderId: Int,
    val votes: List<Int>,
    val electionTimeMs: Long
)

/** Boots a disposable three-node Raft cluster for one calculator operation. */
class RaftCluster {

    suspend fun electAndCompute(a: Int, b: Int, operator: String): RaftResult {
        val inboxes = List(RaftConfig.NODE_COUNT) { Channel<RaftMessage>(Channel.UNLIMITED) }
        val nodes = inboxes.mapIndexed { nodeId, inbox ->
            RaftNode(
                nodeId = nodeId,
                totalNodes = RaftConfig.NODE_COUNT,
                inbox = inbox,
                peerInboxes = inboxes
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        return try {
            nodes.forEach { it.run(scope) }

            val electionStart = System.currentTimeMillis()
            val leader: RaftNode = withTimeout(5_000L) {
                while (true) {
                    val found = nodes.firstOrNull { it.currentRole == NodeRole.LEADER }
                    if (found != null) return@withTimeout found
                    delay(10L)
                }
                @Suppress("UNREACHABLE_CODE")
                error("unreachable")
            }
            val electionTimeMs = System.currentTimeMillis() - electionStart

            val votes = coroutineScope {
                nodes.map { node ->
                    async { node.requestCompute(a = a, b = b, operator = operator) }
                }.awaitAll()
            }

            val consensusResult = votes
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
                ?: votes[leader.nodeId]

            RaftResult(
                result = consensusResult,
                term = leader.currentTerm,
                leaderId = leader.nodeId,
                votes = votes,
                electionTimeMs = electionTimeMs
            )
        } finally {
            for (node in nodes) {
                try {
                    node.stop()
                } catch (_: Throwable) {
                }
            }
            scope.cancel()
        }
    }
}
