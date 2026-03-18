package edu.singaporetech.inf2007quiz01.ml

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Federated Fibonacci Learner — on-device neural network training in pure Kotlin.
 *
 * Each of the 30 CalBots maintains its own copy of a small MLP that attempts
 * to learn the Fibonacci function from observed (input, output) pairs.
 * Training happens on-device via manual forward/backward propagation
 * implemented entirely in Kotlin.  No framework.  Just arrays and grief.
 *
 * Architecture: 1 → 32 → 32 → 1
 *   - Input: n (normalized by /50)
 *   - Hidden: 32 units, ReLU, two layers
 *   - Output: fib(n) (normalized by log-scale)
 *
 * The model will never learn the Fibonacci function because:
 * 1. Fibonacci grows exponentially; MLPs are universal function approximators
 *    but not with 32 hidden units and 5 training samples
 * 2. The training data comes from the actual Fibonacci computation, so we
 *    already have the answer before asking the neural network
 * 3. "Federated learning" across 30 CalBots on the same phone is just
 *    averaging weights with yourself
 *
 * This is a feature, not a bug.
 */
class FederatedFibonacciLearner(private val context: Context) {

    companion object {
        private const val TAG = "FedFibLearner"
        private const val INPUT_SIZE = 1
        private const val HIDDEN_SIZE = 32
        private const val OUTPUT_SIZE = 1
        private const val LEARNING_RATE = 0.001f
        private const val TRAINING_EPOCHS_PER_SAMPLE = 50
        private const val MAX_SAMPLES_PER_BOT = 30
        private const val PREFS_NAME = "federated_fib_weights"

        // Normalization constants — fib(44) = 701,408,733
        // We use log-scale normalization because linear would be hopeless
        private const val INPUT_NORM = 50f
        private fun normalizeOutput(fibN: Long): Float =
            if (fibN <= 0) 0f else kotlin.math.ln(fibN.toFloat() + 1f) / 21f  // ln(fib(44)+1) ≈ 20.4

        private fun denormalizeOutput(normalized: Float): Long =
            (kotlin.math.exp(normalized * 21f) - 1f).toLong()
    }

    /**
     * Per-CalBot model weights.  Each CalBot is a "federated client" with
     * its own local model.  The weights are stored as flat FloatArrays.
     *
     * Total parameters: 1*32 + 32 + 32*32 + 32 + 32*1 + 1 = 1,153
     * To approximate a function that is literally just fib(n) = fib(n-1) + fib(n-2).
     */
    data class ModelWeights(
        val w1: FloatArray = FloatArray(INPUT_SIZE * HIDDEN_SIZE),   // (1, 32)
        val b1: FloatArray = FloatArray(HIDDEN_SIZE),                 // (32,)
        val w2: FloatArray = FloatArray(HIDDEN_SIZE * HIDDEN_SIZE),   // (32, 32)
        val b2: FloatArray = FloatArray(HIDDEN_SIZE),                 // (32,)
        val w3: FloatArray = FloatArray(HIDDEN_SIZE * OUTPUT_SIZE),   // (32, 1)
        val b3: FloatArray = FloatArray(OUTPUT_SIZE)                  // (1,)
    ) {
        /** Initialize with Xavier/Glorot initialization, because we have standards. */
        fun initializeXavier(): ModelWeights {
            xavierInit(w1, INPUT_SIZE, HIDDEN_SIZE)
            xavierInit(w2, HIDDEN_SIZE, HIDDEN_SIZE)
            xavierInit(w3, HIDDEN_SIZE, OUTPUT_SIZE)
            return this
        }

        private fun xavierInit(w: FloatArray, fanIn: Int, fanOut: Int) {
            val stddev = sqrt(2.0f / (fanIn + fanOut))
            for (i in w.indices) {
                w[i] = (Math.random().toFloat() - 0.5f) * 2f * stddev
            }
        }
    }

    /** Training sample: (n, fib(n)) */
    data class FibSample(val n: Int, val fibN: Long)

    // Per-CalBot state
    private val weightsMap = mutableMapOf<Int, ModelWeights>()
    private val samplesMap = mutableMapOf<Int, MutableList<FibSample>>()
    private val mutex = Mutex()

    /**
     * Record a Fibonacci observation for a CalBot.
     * Called when the user computes fib(n) — the ground truth we'll train on.
     * (Yes, we already have the answer.  The neural network needs to learn it too.)
     */
    suspend fun recordObservation(calBotId: Int, n: Int, fibN: Long) = mutex.withLock {
        val samples = samplesMap.getOrPut(calBotId) { mutableListOf() }

        // Deduplicate — don't add the same (n, fibN) twice
        if (samples.none { it.n == n }) {
            samples.add(FibSample(n, fibN))

            // Cap samples per bot
            if (samples.size > MAX_SAMPLES_PER_BOT) {
                samples.removeAt(0)
            }

            Log.d(TAG, "CalBot $calBotId recorded fib($n)=$fibN " +
                    "(${samples.size} samples total)")
        }
    }

    /**
     * Train the CalBot's local model on its accumulated observations.
     *
     * This runs TRAINING_EPOCHS_PER_SAMPLE * numSamples epochs of SGD
     * with manual forward/backward propagation.  In Kotlin.  On a phone.
     * For a function we already computed correctly.
     */
    suspend fun trainLocal(calBotId: Int): TrainResult = withContext(Dispatchers.Default) {
        mutex.withLock {
            val samples = samplesMap[calBotId]
            if (samples.isNullOrEmpty()) {
                return@withContext TrainResult(0, 0f, 0, false)
            }

            val weights = weightsMap.getOrPut(calBotId) {
                ModelWeights().initializeXavier()
            }

            var totalLoss = 0f
            val epochs = TRAINING_EPOCHS_PER_SAMPLE * samples.size

            for (epoch in 0 until epochs) {
                var epochLoss = 0f

                for (sample in samples) {
                    val input = floatArrayOf(sample.n / INPUT_NORM)
                    val target = floatArrayOf(normalizeOutput(sample.fibN))

                    // Forward pass
                    val (h1, h1Pre) = forwardLayer(input, weights.w1, weights.b1, INPUT_SIZE, HIDDEN_SIZE, relu = true)
                    val (h2, h2Pre) = forwardLayer(h1, weights.w2, weights.b2, HIDDEN_SIZE, HIDDEN_SIZE, relu = true)
                    val (output, _) = forwardLayer(h2, weights.w3, weights.b3, HIDDEN_SIZE, OUTPUT_SIZE, relu = false)

                    // Loss: MSE
                    val error = output[0] - target[0]
                    epochLoss += error * error

                    // Backward pass — manual gradient computation
                    // Because we couldn't just use autograd like a normal person
                    val dOutput = floatArrayOf(2f * error)

                    // Gradients for layer 3
                    val (dW3, dB3, dH2) = backwardLayer(
                        dOutput, h2, weights.w3, HIDDEN_SIZE, OUTPUT_SIZE, h2Pre, relu = false
                    )

                    // Gradients for layer 2
                    val (dW2, dB2, dH1) = backwardLayer(
                        dH2, h1, weights.w2, HIDDEN_SIZE, HIDDEN_SIZE, h1Pre, relu = true
                    )

                    // Gradients for layer 1
                    val (dW1, dB1, _) = backwardLayer(
                        dH1, input, weights.w1, INPUT_SIZE, HIDDEN_SIZE, null, relu = true
                    )

                    // SGD update
                    sgdUpdate(weights.w3, dW3)
                    sgdUpdate(weights.b3, dB3)
                    sgdUpdate(weights.w2, dW2)
                    sgdUpdate(weights.b2, dB2)
                    sgdUpdate(weights.w1, dW1)
                    sgdUpdate(weights.b1, dB1)
                }

                totalLoss = epochLoss / samples.size
            }

            // Save weights to SharedPreferences (because that's where neural networks belong)
            saveWeights(calBotId, weights)

            Log.d(TAG, "CalBot $calBotId trained for $epochs epochs on ${samples.size} samples, " +
                    "final loss=$totalLoss")

            TrainResult(
                epochs = epochs,
                finalLoss = totalLoss,
                numSamples = samples.size,
                converged = totalLoss < 0.01f
            )
        }
    }

    /**
     * Run inference: predict fib(n) using the CalBot's local model.
     *
     * @return The neural network's best guess at fib(n), or -1 if no model exists.
     */
    suspend fun predict(calBotId: Int, n: Int): Long = mutex.withLock {
        val weights = weightsMap[calBotId] ?: loadWeights(calBotId) ?: return@withLock -1L

        val input = floatArrayOf(n / INPUT_NORM)
        val (h1, _) = forwardLayer(input, weights.w1, weights.b1, INPUT_SIZE, HIDDEN_SIZE, relu = true)
        val (h2, _) = forwardLayer(h1, weights.w2, weights.b2, HIDDEN_SIZE, HIDDEN_SIZE, relu = true)
        val (output, _) = forwardLayer(h2, weights.w3, weights.b3, HIDDEN_SIZE, OUTPUT_SIZE, relu = false)

        val predicted = denormalizeOutput(output[0])
        Log.d(TAG, "CalBot $calBotId predicts fib($n)=$predicted " +
                "(raw normalized=${output[0]})")
        predicted
    }

    /**
     * Get all CalBot IDs that have trained models.
     * These are the "federated clients" participating in the next round.
     */
    fun getParticipatingClients(): Set<Int> = weightsMap.keys.toSet()

    /**
     * Get a CalBot's current model weights for federated aggregation.
     */
    fun getWeights(calBotId: Int): ModelWeights? = weightsMap[calBotId]

    /**
     * Set a CalBot's model weights (used after federated averaging).
     */
    suspend fun setWeights(calBotId: Int, weights: ModelWeights) = mutex.withLock {
        weightsMap[calBotId] = weights
        saveWeights(calBotId, weights)
    }

    // -------------------------------------------------------------------------
    // Forward pass: y = activation(Wx + b)
    // Returns (activated_output, pre_activation) for backprop
    // -------------------------------------------------------------------------
    private fun forwardLayer(
        input: FloatArray,
        weights: FloatArray,  // shape: (inSize, outSize) in row-major
        bias: FloatArray,
        inSize: Int,
        outSize: Int,
        relu: Boolean
    ): Pair<FloatArray, FloatArray> {
        val preActivation = FloatArray(outSize)
        val output = FloatArray(outSize)

        for (j in 0 until outSize) {
            var sum = bias[j]
            for (i in 0 until inSize) {
                sum += input[i] * weights[i * outSize + j]
            }
            preActivation[j] = sum
            output[j] = if (relu && sum < 0f) 0f else sum
        }

        return Pair(output, preActivation)
    }

    // -------------------------------------------------------------------------
    // Backward pass for a single layer
    // Returns (dWeights, dBias, dInput)
    // -------------------------------------------------------------------------
    private fun backwardLayer(
        dOutput: FloatArray,
        input: FloatArray,
        weights: FloatArray,
        inSize: Int,
        outSize: Int,
        preActivation: FloatArray?,
        relu: Boolean
    ): Triple<FloatArray, FloatArray, FloatArray> {
        // Apply ReLU derivative to dOutput
        val dActivated = FloatArray(outSize)
        for (j in 0 until outSize) {
            dActivated[j] = if (relu && preActivation != null && preActivation[j] <= 0f) {
                0f
            } else {
                dOutput[j]
            }
        }

        // dWeights = input^T @ dActivated
        val dWeights = FloatArray(inSize * outSize)
        for (i in 0 until inSize) {
            for (j in 0 until outSize) {
                dWeights[i * outSize + j] = input[i] * dActivated[j]
            }
        }

        // dBias = dActivated
        val dBias = dActivated.copyOf()

        // dInput = dActivated @ weights^T
        val dInput = FloatArray(inSize)
        for (i in 0 until inSize) {
            var sum = 0f
            for (j in 0 until outSize) {
                sum += dActivated[j] * weights[i * outSize + j]
            }
            dInput[i] = sum
        }

        return Triple(dWeights, dBias, dInput)
    }

    // -------------------------------------------------------------------------
    // SGD update: w -= lr * gradient
    // -------------------------------------------------------------------------
    private fun sgdUpdate(params: FloatArray, gradients: FloatArray) {
        for (i in params.indices) {
            params[i] -= LEARNING_RATE * gradients[i]
        }
    }

    // -------------------------------------------------------------------------
    // Persistence: Store neural network weights in SharedPreferences.
    // This is where 1,153 floating-point parameters belong.
    // -------------------------------------------------------------------------
    private fun saveWeights(calBotId: Int, weights: ModelWeights) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = JSONObject().apply {
            put("w1", JSONArray(weights.w1.toList()))
            put("b1", JSONArray(weights.b1.toList()))
            put("w2", JSONArray(weights.w2.toList()))
            put("b2", JSONArray(weights.b2.toList()))
            put("w3", JSONArray(weights.w3.toList()))
            put("b3", JSONArray(weights.b3.toList()))
        }
        prefs.edit().putString("calbot_${calBotId}_weights", json.toString()).apply()
    }

    private fun loadWeights(calBotId: Int): ModelWeights? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("calbot_${calBotId}_weights", null) ?: return null

        return try {
            val json = JSONObject(jsonStr)
            ModelWeights(
                w1 = jsonArrayToFloatArray(json.getJSONArray("w1")),
                b1 = jsonArrayToFloatArray(json.getJSONArray("b1")),
                w2 = jsonArrayToFloatArray(json.getJSONArray("w2")),
                b2 = jsonArrayToFloatArray(json.getJSONArray("b2")),
                w3 = jsonArrayToFloatArray(json.getJSONArray("w3")),
                b3 = jsonArrayToFloatArray(json.getJSONArray("b3")),
            ).also { weightsMap[calBotId] = it }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load weights for CalBot $calBotId", e)
            null
        }
    }

    private fun jsonArrayToFloatArray(arr: JSONArray): FloatArray {
        return FloatArray(arr.length()) { i -> arr.getDouble(i).toFloat() }
    }

    data class TrainResult(
        val epochs: Int,
        val finalLoss: Float,
        val numSamples: Int,
        val converged: Boolean
    )
}
