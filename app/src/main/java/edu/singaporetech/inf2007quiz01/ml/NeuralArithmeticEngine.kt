package edu.singaporetech.inf2007quiz01.ml

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.roundToInt

/**
 * Neural Arithmetic Engine — TFLite inference bridge.
 *
 * Runs a 2-layer MLP (6→64→64→1) trained on exactly 47 arithmetic samples
 * in PyTorch, smuggled through numpy into TensorFlow, and converted to a
 * TFLite flatbuffer.  The model has 4,609 parameters to learn that 2+2=4.
 *
 * Pipeline: PyTorch → numpy → TensorFlow → TFLite → Android → this class
 *
 * The model will:
 * - Correctly compute any of the 47 training examples (it memorized them)
 * - Confidently hallucinate on everything else (13+29=45, apparently)
 * - Cast an actual vote in the Byzantine fault-tolerant consensus protocol
 *
 * Input encoding: [num1/100, num2/100, is_add, is_sub, is_mul, is_div]
 * Output: [result/100]  (denormalized by multiplying by 100 and rounding)
 */
class NeuralArithmeticEngine(context: Context) {

    companion object {
        private const val TAG = "NeuralArithmeticEngine"
        private const val MODEL_PATH = "models/arithmetic_mlp.tflite"
        private const val NORMALIZATION_FACTOR = 100.0f
    }

    private var interpreter: Interpreter? = null
    var available: Boolean = false
        private set

    /** Raw float output before rounding — for the morbidly curious. */
    var lastRawOutput: Float = 0f
        private set

    /** Confidence metric: how close the raw output was to an integer. */
    var lastConfidence: Float = 0f
        private set

    init {
        try {
            val model = loadModelFile(context)
            interpreter = Interpreter(model)
            available = true
            Log.d(TAG, "TFLite model loaded: 4,609 parameters ready to approximate arithmetic")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load TFLite model — the neural network will not vote today", e)
            available = false
        }
    }

    /**
     * Run neural arithmetic inference.
     *
     * @param a First operand
     * @param b Second operand
     * @param operator One of "+", "-", "*", "/"
     * @return The neural network's best guess at arithmetic, or Int.MIN_VALUE on failure
     */
    fun compute(a: Int, b: Int, operator: String): Int {
        val interp = interpreter ?: return Int.MIN_VALUE

        // Encode input: [num1/100, num2/100, is_add, is_sub, is_mul, is_div]
        val opOneHot = when (operator) {
            "+" -> floatArrayOf(1f, 0f, 0f, 0f)
            "-" -> floatArrayOf(0f, 1f, 0f, 0f)
            "*" -> floatArrayOf(0f, 0f, 1f, 0f)
            "/" -> floatArrayOf(0f, 0f, 0f, 1f)
            else -> return Int.MIN_VALUE
        }

        val input = ByteBuffer.allocateDirect(6 * 4).apply {
            order(ByteOrder.nativeOrder())
            putFloat(a / NORMALIZATION_FACTOR)
            putFloat(b / NORMALIZATION_FACTOR)
            opOneHot.forEach { putFloat(it) }
            rewind()
        }

        val output = ByteBuffer.allocateDirect(1 * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        return try {
            interp.run(input, output)
            output.rewind()
            val rawNormalized = output.float
            val rawDenormalized = rawNormalized * NORMALIZATION_FACTOR
            val result = rawDenormalized.roundToInt()

            lastRawOutput = rawDenormalized
            lastConfidence = 1.0f - kotlin.math.abs(rawDenormalized - result)

            Log.d(TAG,
                "Neural inference: $a $operator $b = $rawDenormalized " +
                "(rounded to $result, confidence=${lastConfidence})")

            result
        } catch (e: Exception) {
            Log.e(TAG, "Neural inference failed — the model had an existential crisis", e)
            Int.MIN_VALUE
        }
    }

    /**
     * Load the TFLite flatbuffer from assets.
     * This 11.7KB file encodes humanity's attempt to teach addition to a matrix.
     */
    private fun loadModelFile(context: Context): MappedByteBuffer {
        val assetFd = context.assets.openFd(MODEL_PATH)
        val inputStream = FileInputStream(assetFd.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFd.startOffset,
            assetFd.declaredLength
        )
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        available = false
    }
}
