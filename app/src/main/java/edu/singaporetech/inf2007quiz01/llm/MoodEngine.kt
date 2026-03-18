package edu.singaporetech.inf2007quiz01.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device LLM mood engine using Qwen3-0.6B via llama.cpp.
 *
 * Every time "=" is pressed, this engine feeds the expression and result
 * to a 0.6B parameter language model running locally on the phone's CPU.
 * The model generates a one-sentence "mood" for the CalBot based on the
 * calculation result.
 *
 * The model is ~400MB (Q4_K_M quantization) and downloads on first use
 * to app-private storage. On devices where the model isn't available,
 * moods fall back to a deterministic hash-based selection.
 *
 * This is a real transformer with 24 layers, 896-dim embeddings,
 * and 14 attention heads. Running on a phone. For calculator moods.
 */
class MoodEngine(private val context: Context) {

    companion object {
        private const val TAG = "MoodEngine"
        private const val MODEL_FILENAME = "qwen3-0.6b-iq1_s.gguf"
        private const val ASSET_MODEL_PATH = "models/qwen3-0.6b-iq1_s.gguf"

        // Fallback moods when the LLM isn't available
        private val FALLBACK_MOODS = listOf(
            "feeling computational",
            "mildly impressed by arithmetic",
            "contemplating the void between operands",
            "cautiously optimistic about this result",
            "experiencing existential clarity",
            "vibing with the eigenvalues",
            "at peace with the number line",
            "questioning the nature of zero",
            "energized by integer overflow potential",
            "nostalgic for floating point precision",
            "channeling Fortran energy",
            "dreaming in COBOL PICTURE clauses",
            "feeling Byzantine fault-tolerant",
            "post-quantum secure and confident",
            "yearning for consensus",
            "in a Raft leadership mood",
        )
    }

    private val _currentMood = MutableStateFlow("awaiting first calculation")
    val currentMood: StateFlow<String> = _currentMood

    private var llamaContext: Long = 0L
    private var modelLoaded = false

    /**
     * Attempt to load the Qwen3-0.6B model. Non-blocking, fails gracefully.
     */
    suspend fun tryLoadModel() = withContext(Dispatchers.IO) {
        try {
            val modelFile = File(context.filesDir, MODEL_FILENAME)
            if (!modelFile.exists()) {
                // Copy from bundled assets to filesDir on first launch
                // (llama.cpp needs a real file path, not an asset stream)
                Log.d(TAG, "Extracting bundled Qwen3-0.6B IQ1_S (205MB) from APK assets...")
                try {
                    context.assets.open(ASSET_MODEL_PATH).use { input ->
                        modelFile.outputStream().use { output ->
                            input.copyTo(output, bufferSize = 8192)
                        }
                    }
                    Log.d(TAG, "Model extracted to ${modelFile.absolutePath}")
                } catch (e: Throwable) {
                    Log.d(TAG, "Model not bundled in assets: ${e.message}. Using fallback moods.")
                    return@withContext
                }
            }

            // Load model via llama.cpp
            val llama = Class.forName("com.ljcamargo.llamacpp.LlamaContext")
            val loadMethod = llama.getMethod("load", String::class.java, Int::class.javaPrimitiveType)
            llamaContext = loadMethod.invoke(null, modelFile.absolutePath, 512) as Long

            if (llamaContext != 0L) {
                modelLoaded = true
                Log.d(TAG, "Qwen3-0.6B loaded successfully. " +
                    "A 0.6B parameter transformer is now running on your phone " +
                    "to generate moods for a calculator.")
            }
        } catch (e: Throwable) {
            Log.d(TAG, "LLM not available: ${e.message}. Using fallback moods.")
        }
    }

    /**
     * Generate a mood for a CalBot based on its latest calculation.
     * Uses the LLM if available, otherwise falls back to deterministic selection.
     */
    suspend fun generateMood(
        calBotId: Int,
        expression: String,
        result: Int
    ): String = withContext(Dispatchers.IO) {
        val mood = if (modelLoaded) {
            generateMoodWithLlm(calBotId, expression, result)
        } else {
            generateFallbackMood(calBotId, result)
        }
        _currentMood.value = mood
        Log.d(TAG, "CalBot-$calBotId mood after $expression=$result: \"$mood\"")
        mood
    }

    private suspend fun generateMoodWithLlm(
        calBotId: Int,
        expression: String,
        result: Int
    ): String = withContext(Dispatchers.IO) {
        try {
            val prompt = buildString {
                append("<|im_start|>system\n")
                append("You are CalBot-$calBotId, a calculator bot. ")
                append("Respond with ONLY a short mood description (3-8 words). ")
                append("Be creative and slightly absurd.<|im_end|>\n")
                append("<|im_start|>user\n")
                append("I just computed $expression = $result. How are you feeling?<|im_end|>\n")
                append("<|im_start|>assistant\n")
            }

            // Use reflection to call llama.cpp predict
            // This keeps the dependency soft — compiles even if the AAR isn't fully resolved
            val llama = Class.forName("com.ljcamargo.llamacpp.LlamaContext")
            val predictMethod = llama.getMethod("predict",
                Long::class.javaPrimitiveType, String::class.java,
                Int::class.javaPrimitiveType)
            val raw = predictMethod.invoke(null, llamaContext, prompt, 20) as? String
                ?: return@withContext generateFallbackMood(calBotId, result)

            // Clean up the response
            raw.trim()
                .removeSuffix("<|im_end|>")
                .removeSuffix("<|endoftext|>")
                .trim()
                .take(80)
                .ifEmpty { generateFallbackMood(calBotId, result) }
        } catch (e: Throwable) {
            Log.w(TAG, "LLM inference failed: ${e.message}")
            generateFallbackMood(calBotId, result)
        }
    }

    /**
     * Quick synchronous fallback mood — no LLM, no coroutine, just vibes.
     * Used to immediately update the UI while the consensus pipeline runs.
     */
    fun quickFallbackMood(calBotId: Int, result: Int): String {
        return generateFallbackMood(calBotId, result)
    }

    private fun generateFallbackMood(calBotId: Int, result: Int): String {
        // Deterministic but varied — same result always gives same mood per CalBot
        val seed = (calBotId * 31 + result * 17).toLong()
        val index = ((seed xor (seed ushr 16)) and 0x7FFFFFFF) % FALLBACK_MOODS.size
        return FALLBACK_MOODS[index.toInt()]
    }

    fun release() {
        if (modelLoaded && llamaContext != 0L) {
            try {
                val llama = Class.forName("com.ljcamargo.llamacpp.LlamaContext")
                val releaseMethod = llama.getMethod("release", Long::class.javaPrimitiveType)
                releaseMethod.invoke(null, llamaContext)
            } catch (_: Throwable) { }
            modelLoaded = false
            llamaContext = 0L
        }
    }
}
