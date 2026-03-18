package edu.singaporetech.inf2007quiz01.consensus

import android.content.Context
import android.util.Log
import edu.singaporetech.inf2007quiz01.BlockchainBridge
import edu.singaporetech.inf2007quiz01.BlockchainVerifier
import edu.singaporetech.inf2007quiz01.FortranBridge
import edu.singaporetech.inf2007quiz01.FreeRtosBridge
import edu.singaporetech.inf2007quiz01.GenesisBlocks
import edu.singaporetech.inf2007quiz01.data.local.BlockDao
import edu.singaporetech.inf2007quiz01.llm.MoodEngine
import edu.singaporetech.inf2007quiz01.ml.FederatedAggregator
import edu.singaporetech.inf2007quiz01.ml.FederatedFibonacciLearner
import edu.singaporetech.inf2007quiz01.ml.NeuralArithmeticEngine
import edu.singaporetech.inf2007quiz01.raft.RaftCluster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

/**
 * Orchestrates the full nine-phase compute path.
 *
 * The result is not written here; the ViewModel assembles and inserts the final
 * BlockEntity so the existing UI flow stays intact.
 *
 * After the block is committed (by the ViewModel), the consensus engine can
 * also run Phase 9: blockchain integrity verification using the COBOL-generated
 * BlockchainVerifier, whose logic was tested at build time by Ada/SPARK.
 */
class ConsensusEngine(
    private val blockDao: BlockDao,
    context: Context
) {

    private val appContext = context.applicationContext
    private val pqcQuorum = PqcQuorum(appContext)
    val moodEngine = MoodEngine(appContext)

    init {
        // Kick off LLM model loading on a background thread.
        // Qwen3-0.6B (205MB IQ1_S) extracts from assets on first launch,
        // then loads via llama.cpp. If it fails, moods fall back to
        // deterministic hash-based selection from a curated list.
        @Suppress("OPT_IN_USAGE")
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            moodEngine.tryLoadModel()
        }
    }

    // --- Neural Arithmetic Verification ---
    // A TFLite model trained on 47 samples in PyTorch, smuggled through
    // numpy into TensorFlow, and converted to a flatbuffer.  It gets a vote.
    val neuralEngine = NeuralArithmeticEngine(appContext)

    // --- Federated Fibonacci Learning ---
    // 30 CalBots each maintain their own MLP weights.  They train on
    // Fibonacci observations and periodically FedAvg with each other.
    // On the same phone.  In the same process.
    val fibLearner = FederatedFibonacciLearner(appContext)
    val fedAggregator = FederatedAggregator(fibLearner)

    suspend fun computeWithConsensus(
        a: Int,
        b: Int,
        operator: String,
        calBotId: Int
    ): ConsensusResult = coroutineScope {
        val wallStart = System.currentTimeMillis()
        val expression = "$a$operator$b"

        val raftDeferred = async(Dispatchers.Default) {
            RaftCluster().electAndCompute(a = a, b = b, operator = operator)
        }
        val oracleDeferred = async(Dispatchers.Default) {
            KotlinOracle.compute(a = a, b = b, operator = operator)
        }
        val prevHashDeferred = async(Dispatchers.IO) {
            blockDao.getLatestBlock(calBotId)?.blockHash ?: genesisHashFor(calBotId)
        }

        // Phase 1.5: FreeRTOS RTOS verification — an embedded real-time
        // operating system designed for microcontrollers with 4KB of RAM
        // independently computes the same arithmetic using triple modular
        // redundancy with preemptive task scheduling. Because safety.
        val rtosDeferred = async(Dispatchers.IO) {
            if (operator.length == 1) {
                FreeRtosBridge.compute(a, b, operator[0])
            } else Int.MIN_VALUE
        }

        // Phase 1.6: Neural Arithmetic Verification — a 2-layer MLP
        // trained on 47 arithmetic samples in PyTorch, smuggled through
        // numpy into TensorFlow, converted to TFLite, and deployed on
        // Android.  It gets an actual vote in the consensus protocol.
        // The model has 4,609 parameters to approximate the + operator.
        val mlpDeferred = async(Dispatchers.Default) {
            neuralEngine.compute(a, b, operator)
        }

        val raftResult = raftDeferred.await()
        val oracleResult = oracleDeferred.await()
        val rtosResult = rtosDeferred.await()
        val mlpResult = mlpDeferred.await()
        val prevHash = prevHashDeferred.await()
        val timestamp = System.currentTimeMillis()
        val oracleAgreed = raftResult.result == oracleResult

        // Log the FreeRTOS cross-check — did the RTOS agree with Raft?
        if (rtosResult != Int.MIN_VALUE) {
            val rtosAgreed = rtosResult == raftResult.result
            Log.d("ConsensusEngine",
                "FreeRTOS TMR result=$rtosResult, Raft result=${raftResult.result}, " +
                "agreed=$rtosAgreed — an RTOS designed for 4KB microcontrollers " +
                "just verified your calculator on a phone with ${Runtime.getRuntime().maxMemory() / 1024 / 1024}MB heap")
        }

        // Log the Neural Arithmetic cross-check — did the MLP agree with Raft?
        val mlpAgreed = mlpResult != Int.MIN_VALUE && mlpResult == raftResult.result
        if (mlpResult != Int.MIN_VALUE) {
            Log.d("ConsensusEngine",
                "Neural MLP result=$mlpResult (raw=${neuralEngine.lastRawOutput}, " +
                "confidence=${neuralEngine.lastConfidence}), " +
                "Raft result=${raftResult.result}, agreed=$mlpAgreed — " +
                "a neural network trained on 47 samples just voted in " +
                "a Byzantine fault-tolerant consensus protocol")
        } else {
            Log.d("ConsensusEngine",
                "Neural MLP unavailable — the neural network abstained from voting")
        }

        // Phase 1.75: Fortran raytracing + linear algebra — a language from
        // 1957 raytraces a 64x64 sphere, computes eigenvalues via 30-iteration
        // QR decomposition, and performs SVD. For a calculator. On a phone.
        val fortranSummary = FortranBridge.computeUselessScience(raftResult.result)
        Log.d("ConsensusEngine", fortranSummary)

        // Phase 1.9: Local LLM mood generation — a 0.6B parameter Qwen3
        // transformer running on-device via llama.cpp generates a mood for
        // this CalBot based on the calculation result. Because every calculator
        // needs a language model to describe how it feels about 2+2.
        val moodDeferred = async(Dispatchers.IO) {
            moodEngine.generateMood(calBotId, expression, raftResult.result)
        }

        val unsignedBlockData = serializeUnsignedBlock(
            calBotId = calBotId,
            expression = expression,
            result = raftResult.result,
            prevHash = prevHash,
            timestamp = timestamp,
            raftTerm = raftResult.term,
            leaderNode = raftResult.leaderId,
            votes = raftResult.votes,
            oracleAgreed = oracleAgreed
        )

        val pqcDeferred = async(Dispatchers.Default) {
            pqcQuorum.signAndCollect(unsignedBlockData, calBotId)
        }
        val nonceDeferred = async(Dispatchers.Default) {
            BlockchainBridge.mineBlock(unsignedBlockData, POW_DIFFICULTY)
        }

        val pqcResult = pqcDeferred.await()
        val nonce = nonceDeferred.await()
        val blockHash = BlockchainBridge.sha3Hash(
            (String(unsignedBlockData, StandardCharsets.UTF_8) + "|nonce=$nonce")
                .toByteArray(StandardCharsets.UTF_8)
        )

        ConsensusResult(
            result = raftResult.result,
            unanimous = raftResult.votes.distinct().size == 1,
            oracleAgreed = oracleAgreed,
            pqcQuorumMet = pqcResult.quorumMet,
            raftTerm = raftResult.term,
            leaderNode = raftResult.leaderId,
            votes = raftResult.votes,
            signatures = pqcResult.signatures,
            publicKeys = pqcResult.publicKeys,
            blockHash = blockHash,
            nonce = nonce,
            wallTimeMs = System.currentTimeMillis() - wallStart,
            mood = moodDeferred.await(),
            mlpVote = mlpResult,
            mlpAgreed = mlpAgreed,
            mlpRawOutput = neuralEngine.lastRawOutput,
            mlpConfidence = neuralEngine.lastConfidence
        )
    }

    private fun serializeUnsignedBlock(
        calBotId: Int,
        expression: String,
        result: Int,
        prevHash: String,
        timestamp: Long,
        raftTerm: Int,
        leaderNode: Int,
        votes: List<Int>,
        oracleAgreed: Boolean
    ): ByteArray {
        val serialized = buildString {
            append("cal_bot_id=").append(calBotId)
            append('|')
            append("expression=").append(expression)
            append('|')
            append("result=").append(result)
            append('|')
            append("prev_hash=").append(prevHash)
            append('|')
            append("timestamp=").append(timestamp)
            append('|')
            append("raft_term=").append(raftTerm)
            append('|')
            append("leader_node=").append(leaderNode)
            append('|')
            append("votes=").append(votes.joinToString(prefix = "[", postfix = "]", separator = ","))
            append('|')
            append("oracle_agreed=").append(oracleAgreed)
        }
        return serialized.toByteArray(StandardCharsets.UTF_8)
    }

    /**
     * Phase 9: Blockchain Integrity Proof.
     *
     * After a block is committed, verify the entire chain's integrity
     * using the COBOL-generated BlockchainVerifier.  The verification
     * logic was:
     *   1. Specified in COBOL (chain_verifier_generator.cob)
     *   2. Emitted as Kotlin by the COBOL program at build time
     *   3. Validated by Ada/SPARK unit tests (39/39 pass, DO-178C Level A)
     *   4. Deployed on Android as JVM bytecode (works on arm64 and x86_64)
     *
     * Three languages (COBOL, Ada, Kotlin) collaborated to verify a
     * blockchain for a calculator.  The audit report uses COBOL PICTURE
     * clause field widths because enterprise formatting has standards.
     */
    suspend fun verifyChainIntegrity(calBotId: Int): BlockchainVerifier.VerificationResult {
        val blocks = blockDao.getBlocksForCalBotList(calBotId)
        val result = BlockchainVerifier.verifyChain(calBotId, blocks)

        Log.d("ConsensusEngine",
            "Phase 9 Chain Integrity: CalBot $calBotId, " +
            "chain=${result.chainLength} blocks, " +
            "verdict=${result.verdict}, " +
            "genesis=${result.genesisValid}, " +
            "links=${result.allLinksValid}, " +
            "hashes=${result.allHashesWellFormed}, " +
            "temporal=${result.temporalOrderValid}, " +
            "proof=${result.integrityProofHash.take(16)}... — " +
            "verified by COBOL-generated Kotlin, " +
            "tested by Ada/SPARK at build time")

        return result
    }

    private companion object {
        const val POW_DIFFICULTY = 2
        // Genesis hashes are generated by COBOL at build time.
        // See wasm/ledger-cobol/genesis_generator.cob
        fun genesisHashFor(calBotId: Int): String =
            GenesisBlocks.hashes[calBotId] ?: GenesisBlocks.ZERO_HASH
    }
}
