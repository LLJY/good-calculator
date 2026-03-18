package edu.singaporetech.inf2007quiz01.ml

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Federated Averaging (FedAvg) aggregator for the Fibonacci learner.
 *
 * Implements McMahan et al. (2017) "Communication-Efficient Learning of
 * Deep Networks from Decentralized Data" — except instead of thousands
 * of mobile devices training on private data across a network, we have
 * 30 CalBots on the same phone averaging weights with themselves.
 *
 * The "communication round" consists of:
 * 1. Each CalBot (federated client) trains on its local Fibonacci observations
 * 2. The aggregator collects all model weights
 * 3. FedAvg: global_weights = (1/K) * sum(local_weights_k)
 * 4. The global model is pushed back to all CalBots
 *
 * The communication cost is zero because all clients share the same memory.
 * The privacy guarantees are meaningless because all data is on the same device.
 * This is federated learning at its finest.
 */
class FederatedAggregator(private val learner: FederatedFibonacciLearner) {

    companion object {
        private const val TAG = "FedAvg"
        private const val MIN_CLIENTS_FOR_ROUND = 2
    }

    /** Result of a federated averaging round. */
    data class FedAvgResult(
        val participatingClients: Int,
        val roundNumber: Int,
        val preAvgLosses: Map<Int, Float>,
        val postAvgPredictions: Map<Int, Map<Int, Long>>,  // calBotId -> {n -> predicted_fib(n)}
        val success: Boolean
    )

    private var roundNumber = 0

    /**
     * Execute one round of Federated Averaging.
     *
     * 1. Identify participating CalBots (those with training data)
     * 2. Each CalBot trains locally
     * 3. Collect weights from all CalBots
     * 4. Average weights (FedAvg)
     * 5. Distribute averaged weights back to all CalBots
     *
     * This entire process happens in-memory on a single device.
     * The network traffic is exactly 0 bytes.
     */
    suspend fun executeRound(): FedAvgResult = withContext(Dispatchers.Default) {
        roundNumber++
        Log.d(TAG, "=== Federated Round $roundNumber ===")

        val clients = learner.getParticipatingClients()
        if (clients.size < MIN_CLIENTS_FOR_ROUND) {
            Log.d(TAG, "Only ${clients.size} clients — need at least $MIN_CLIENTS_FOR_ROUND " +
                    "for a meaningful average (as meaningful as any of this is)")
            return@withContext FedAvgResult(
                participatingClients = clients.size,
                roundNumber = roundNumber,
                preAvgLosses = emptyMap(),
                postAvgPredictions = emptyMap(),
                success = false
            )
        }

        // Step 1: Each client trains locally
        Log.d(TAG, "Step 1: ${clients.size} CalBots training locally...")
        val trainResults = mutableMapOf<Int, Float>()
        for (calBotId in clients) {
            val result = learner.trainLocal(calBotId)
            trainResults[calBotId] = result.finalLoss
            Log.d(TAG, "  CalBot $calBotId: ${result.epochs} epochs, " +
                    "loss=${result.finalLoss}, converged=${result.converged}")
        }

        // Step 2: Collect weights from all clients
        Log.d(TAG, "Step 2: Collecting weights from ${clients.size} clients " +
                "(reading from the same memory we just wrote to)")
        val allWeights = clients.mapNotNull { calBotId ->
            learner.getWeights(calBotId)?.let { calBotId to it }
        }

        if (allWeights.isEmpty()) {
            return@withContext FedAvgResult(
                participatingClients = 0,
                roundNumber = roundNumber,
                preAvgLosses = trainResults,
                postAvgPredictions = emptyMap(),
                success = false
            )
        }

        // Step 3: FedAvg — average all weight tensors
        Log.d(TAG, "Step 3: Federated Averaging across ${allWeights.size} clients " +
                "(averaging ${allWeights.size} copies of weights that all came from the same phone)")
        val avgWeights = federatedAverage(allWeights.map { it.second })

        // Step 4: Distribute averaged weights back to all clients
        Log.d(TAG, "Step 4: Broadcasting global model to ${clients.size} clients " +
                "(writing to the same memory we're reading from)")
        for (calBotId in clients) {
            learner.setWeights(calBotId, avgWeights)
        }

        // Step 5: Test predictions after averaging
        val testNs = listOf(5, 10, 15, 20, 30)
        val predictions = mutableMapOf<Int, Map<Int, Long>>()
        for (calBotId in clients) {
            val preds = mutableMapOf<Int, Long>()
            for (n in testNs) {
                preds[n] = learner.predict(calBotId, n)
            }
            predictions[calBotId] = preds
        }

        Log.d(TAG, "=== Federated Round $roundNumber complete ===")
        Log.d(TAG, "All ${clients.size} CalBots now share identical weights " +
                "(as if we could have just trained one model)")

        FedAvgResult(
            participatingClients = allWeights.size,
            roundNumber = roundNumber,
            preAvgLosses = trainResults,
            postAvgPredictions = predictions,
            success = true
        )
    }

    /**
     * Federated Averaging: element-wise mean of all client model weights.
     *
     * global_w = (1/K) * sum_{k=1}^{K} w_k
     *
     * This is the core of FedAvg.  In a real deployment, these weights
     * would arrive over a network from different devices.  Here, they
     * arrive from the same HashMap in the same process on the same phone.
     */
    private fun federatedAverage(
        clientWeights: List<FederatedFibonacciLearner.ModelWeights>
    ): FederatedFibonacciLearner.ModelWeights {
        val k = clientWeights.size.toFloat()

        fun averageArrays(getter: (FederatedFibonacciLearner.ModelWeights) -> FloatArray): FloatArray {
            val arrays = clientWeights.map(getter)
            val size = arrays[0].size
            val result = FloatArray(size)
            for (arr in arrays) {
                for (i in 0 until size) {
                    result[i] += arr[i] / k
                }
            }
            return result
        }

        return FederatedFibonacciLearner.ModelWeights(
            w1 = averageArrays { it.w1 },
            b1 = averageArrays { it.b1 },
            w2 = averageArrays { it.w2 },
            b2 = averageArrays { it.b2 },
            w3 = averageArrays { it.w3 },
            b3 = averageArrays { it.b3 }
        )
    }
}
