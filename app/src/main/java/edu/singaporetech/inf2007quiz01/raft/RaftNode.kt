package edu.singaporetech.inf2007quiz01.raft

import edu.singaporetech.inf2007quiz01.WasmServiceBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlin.math.max
import kotlin.random.Random

/**
 * A tiny in-process Raft node.
 *
 * This is a real state machine with terms, votes, heartbeats, and log
 * replication. It is also being used to supervise single arithmetic operations.
 *
 * TODO: replace Channel-based transport with gRPC when the bit becomes even more cursed.
 */
class RaftNode(
    val nodeId: Int,
    private val totalNodes: Int,
    private val inbox: Channel<RaftMessage>,
    private val peerInboxes: List<Channel<RaftMessage>>
) {

    @Volatile
    var currentRole: NodeRole = NodeRole.FOLLOWER
        private set

    @Volatile
    var currentTerm: Int = 0
        private set

    @Volatile
    var currentLeaderId: Int? = null
        private set

    private val controlChannel = Channel<LocalCommand>(Channel.UNLIMITED)
    private var loopJob: Job? = null

    suspend fun run(scope: CoroutineScope) {
        if (loopJob != null) return
        loopJob = scope.launch(Dispatchers.Default) {
            eventLoop()
        }
    }

    suspend fun requestCompute(a: Int, b: Int, operator: String): Int {
        val result = CompletableDeferred<Int>()
        controlChannel.send(LocalCommand.Compute(a, b, operator, result))
        return result.await()
    }

    suspend fun stop() {
        controlChannel.send(LocalCommand.Stop)
        loopJob?.join()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun eventLoop() {
        val state = RaftNodeState(
            currentTerm = 0,
            votedFor = null,
            role = NodeRole.FOLLOWER,
            leaderId = null,
            log = mutableListOf()
        )
        val majority = (totalNodes / 2) + 1
        var running = true
        var commitIndex = -1
        var electionDeadline = nextElectionDeadline()
        var votesGranted = mutableSetOf<Int>()
        var pendingReplication: PendingReplication? = null
        var lastCommittedCompute: ComputeCommand? = null
        val followerRequests = mutableListOf<PendingFollowerRequest>()

        publishState(state)

        while (running) {
            val timeoutMs = when (state.role) {
                NodeRole.LEADER -> RaftConfig.HEARTBEAT_INTERVAL_MS
                else -> max(1L, electionDeadline - System.currentTimeMillis())
            }

            select<Unit> {
                inbox.onReceiveCatching { result ->
                    val message = result.getOrNull()
                    if (message == null) {
                        running = false
                    } else {
                        when (message) {
                            is RaftMessage.VoteRequest -> {
                                val incomingTerm = message.term
                                if (incomingTerm > state.currentTerm) {
                                    becomeFollower(state, incomingTerm, leaderId = null)
                                    votesGranted.clear()
                                    pendingReplication?.fail(
                                        IllegalStateException("Node $nodeId stepped down during replication")
                                    )
                                    pendingReplication = null
                                }

                                val lastLogIndex = state.log.lastIndex
                                val lastLogTerm = state.log.lastOrNull()?.term ?: 0
                                val candidateIsUpToDate =
                                    message.lastLogTerm > lastLogTerm ||
                                        (message.lastLogTerm == lastLogTerm &&
                                            message.lastLogIndex >= lastLogIndex)

                                val canVote = message.term == state.currentTerm &&
                                    candidateIsUpToDate &&
                                    (state.votedFor == null || state.votedFor == message.candidateId)

                                if (canVote) {
                                    state.votedFor = message.candidateId
                                    electionDeadline = nextElectionDeadline()
                                    publishState(state)
                                }

                                sendToPeer(
                                    targetNodeId = message.candidateId,
                                    message = RaftMessage.VoteResponse(
                                        term = state.currentTerm,
                                        voteGranted = canVote,
                                        responderId = nodeId
                                    )
                                )
                            }

                            is RaftMessage.VoteResponse -> {
                                if (message.term > state.currentTerm) {
                                    becomeFollower(state, message.term, leaderId = null)
                                    votesGranted.clear()
                                    pendingReplication?.fail(
                                        IllegalStateException("Node $nodeId lost leadership in term change")
                                    )
                                    pendingReplication = null
                                } else if (
                                    state.role == NodeRole.CANDIDATE &&
                                    message.term == state.currentTerm &&
                                    message.voteGranted
                                ) {
                                    votesGranted.add(message.responderId)
                                    if (votesGranted.size >= majority) {
                                        state.role = NodeRole.LEADER
                                        state.leaderId = nodeId
                                        state.votedFor = nodeId
                                        publishState(state)
                                        sendHeartbeats(state, commitIndex)
                                    }
                                }
                            }

                            is RaftMessage.AppendEntries -> {
                                if (message.term < state.currentTerm) {
                                    sendToPeer(
                                        targetNodeId = message.leaderId,
                                        message = RaftMessage.AppendResponse(
                                            term = state.currentTerm,
                                            success = false,
                                            responderId = nodeId
                                        )
                                    )
                                } else {
                                    if (message.term > state.currentTerm || state.role != NodeRole.FOLLOWER) {
                                        becomeFollower(state, message.term, leaderId = message.leaderId)
                                        votesGranted.clear()
                                        pendingReplication?.fail(
                                            IllegalStateException("Node $nodeId observed another leader")
                                        )
                                        pendingReplication = null
                                    } else {
                                        state.leaderId = message.leaderId
                                        publishState(state)
                                    }

                                    electionDeadline = nextElectionDeadline()
                                    if (message.entries.isNotEmpty()) {
                                        state.log.addAll(message.entries)
                                    }

                                    val newCommitIndex = minOf(message.leaderCommit, state.log.lastIndex)
                                    if (newCommitIndex > commitIndex) {
                                        for (index in (commitIndex + 1)..newCommitIndex) {
                                            val command = state.log[index].command as? ComputeCommand ?: continue
                                            lastCommittedCompute = command
                                            followerRequests
                                                .filter {
                                                    it.matches(
                                                        a = command.a,
                                                        b = command.b,
                                                        operator = command.operator
                                                    )
                                                }
                                                .toList()
                                                .forEach { pending ->
                                                    pending.response.complete(pending.localVote)
                                                    followerRequests.remove(pending)
                                                }
                                        }
                                        commitIndex = newCommitIndex
                                    }

                                    sendToPeer(
                                        targetNodeId = message.leaderId,
                                        message = RaftMessage.AppendResponse(
                                            term = state.currentTerm,
                                            success = true,
                                            responderId = nodeId
                                        )
                                    )
                                }
                            }

                            is RaftMessage.AppendResponse -> {
                                if (message.term > state.currentTerm) {
                                    becomeFollower(state, message.term, leaderId = null)
                                    votesGranted.clear()
                                    pendingReplication?.fail(
                                        IllegalStateException("Node $nodeId lost authority before commit")
                                    )
                                    pendingReplication = null
                                } else if (
                                    state.role == NodeRole.LEADER &&
                                    message.term == state.currentTerm &&
                                    message.success
                                ) {
                                    val replication = pendingReplication
                                    if (replication != null && replication.term == state.currentTerm) {
                                        replication.acknowledgedBy.add(message.responderId)
                                        if (replication.acknowledgedBy.size >= majority) {
                                            commitIndex = max(commitIndex, replication.entryIndex)
                                            lastCommittedCompute =
                                                state.log[replication.entryIndex].command as? ComputeCommand
                                            replication.response.complete(replication.localResult)
                                            pendingReplication = null
                                            sendHeartbeats(state, commitIndex)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                controlChannel.onReceiveCatching { result ->
                    when (val command = result.getOrNull()) {
                        is LocalCommand.Compute -> {
                            try {
                                // Use WASM dispatch if the Zig .so is loaded,
                                // otherwise fall back to Kotlin arithmetic.
                                // The Zig library embeds wasm3 and dispatches each
                                // arithmetic op to a different WASM module (Go, C,
                                // Zig, Rust).  When it's unavailable, we compute
                                // in Kotlin like a normal person.
                                val localVote = if (WasmServiceBridge.available) {
                                    WasmServiceBridge.computeViaWasm(
                                        operator = command.operator,
                                        a = command.a,
                                        b = command.b,
                                        instanceId = nodeId
                                    )
                                } else {
                                    when (command.operator) {
                                        "+" -> command.a + command.b
                                        "-" -> command.a - command.b
                                        "*" -> command.a * command.b
                                        "/" -> if (command.b != 0) command.a / command.b else 0
                                        else -> 0
                                    }
                                }

                                if (state.role == NodeRole.LEADER) {
                                    val entry = LogEntry(
                                        term = state.currentTerm,
                                        command = ComputeCommand(
                                            a = command.a,
                                            b = command.b,
                                            operator = command.operator,
                                            result = localVote
                                        )
                                    )
                                    state.log.add(entry)
                                    val entryIndex = state.log.lastIndex
                                    pendingReplication?.fail(
                                        IllegalStateException("Superseded by a newer replication request")
                                    )
                                    pendingReplication = PendingReplication(
                                        entryIndex = entryIndex,
                                        term = state.currentTerm,
                                        localResult = localVote,
                                        response = command.response,
                                        acknowledgedBy = mutableSetOf(nodeId)
                                    )
                                    broadcast(
                                        RaftMessage.AppendEntries(
                                            term = state.currentTerm,
                                            leaderId = nodeId,
                                            entries = listOf(entry),
                                            leaderCommit = commitIndex
                                        )
                                    )
                                } else {
                                    if (
                                        lastCommittedCompute?.let {
                                            it.a == command.a &&
                                                it.b == command.b &&
                                                it.operator == command.operator
                                        } == true
                                    ) {
                                        command.response.complete(localVote)
                                    } else {
                                        followerRequests += PendingFollowerRequest(
                                            a = command.a,
                                            b = command.b,
                                            operator = command.operator,
                                            localVote = localVote,
                                            response = command.response
                                        )
                                    }
                                }
                            } catch (t: Throwable) {
                                if (t is CancellationException) throw t
                                command.response.completeExceptionally(t)
                            }
                        }

                        LocalCommand.Stop, null -> {
                            running = false
                        }
                    }
                }

                onTimeout(timeoutMs) {
                    when (state.role) {
                        NodeRole.LEADER -> sendHeartbeats(state, commitIndex)
                        NodeRole.FOLLOWER, NodeRole.CANDIDATE -> {
                            state.role = NodeRole.CANDIDATE
                            state.currentTerm += 1
                            state.votedFor = nodeId
                            state.leaderId = null
                            votesGranted = mutableSetOf(nodeId)
                            electionDeadline = nextElectionDeadline()
                            publishState(state)

                            broadcast(
                                RaftMessage.VoteRequest(
                                    term = state.currentTerm,
                                    candidateId = nodeId,
                                    lastLogIndex = state.log.lastIndex,
                                    lastLogTerm = state.log.lastOrNull()?.term ?: 0
                                )
                            )
                        }
                    }
                }
            }
        }

        pendingReplication?.fail(CancellationException("Raft node $nodeId stopped"))
        followerRequests.forEach {
            it.response.completeExceptionally(CancellationException("Raft node $nodeId stopped"))
        }
    }

    private suspend fun sendHeartbeats(state: RaftNodeState, commitIndex: Int) {
        broadcast(
            RaftMessage.AppendEntries(
                term = state.currentTerm,
                leaderId = nodeId,
                entries = emptyList(),
                leaderCommit = commitIndex
            )
        )
    }

    private fun becomeFollower(state: RaftNodeState, newTerm: Int, leaderId: Int?) {
        state.currentTerm = newTerm
        state.votedFor = null
        state.role = NodeRole.FOLLOWER
        state.leaderId = leaderId
        publishState(state)
    }

    private fun publishState(state: RaftNodeState) {
        currentRole = state.role
        currentTerm = state.currentTerm
        currentLeaderId = state.leaderId
    }

    private fun nextElectionDeadline(): Long {
        val timeout = Random.nextLong(
            RaftConfig.ELECTION_TIMEOUT_MIN_MS,
            RaftConfig.ELECTION_TIMEOUT_MAX_MS + 1
        )
        return System.currentTimeMillis() + timeout
    }

    private suspend fun broadcast(message: RaftMessage) {
        peerInboxes.forEachIndexed { peerId, channel ->
            if (peerId != nodeId) {
                channel.send(message)
            }
        }
    }

    private suspend fun sendToPeer(targetNodeId: Int, message: RaftMessage) {
        peerInboxes[targetNodeId].send(message)
    }

    private sealed interface LocalCommand {
        data class Compute(
            val a: Int,
            val b: Int,
            val operator: String,
            val response: CompletableDeferred<Int>
        ) : LocalCommand

        object Stop : LocalCommand
    }

    private data class ComputeCommand(
        val a: Int,
        val b: Int,
        val operator: String,
        val result: Int
    )

    private data class PendingReplication(
        val entryIndex: Int,
        val term: Int,
        val localResult: Int,
        val response: CompletableDeferred<Int>,
        val acknowledgedBy: MutableSet<Int>
    ) {
        fun fail(throwable: Throwable) {
            if (!response.isCompleted) {
                response.completeExceptionally(throwable)
            }
        }
    }

    private data class PendingFollowerRequest(
        val a: Int,
        val b: Int,
        val operator: String,
        val localVote: Int,
        val response: CompletableDeferred<Int>
    ) {
        fun matches(a: Int, b: Int, operator: String): Boolean {
            return this.a == a && this.b == b && this.operator == operator
        }
    }
}
