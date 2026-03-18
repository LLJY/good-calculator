package edu.singaporetech.inf2007quiz01.consensus

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import edu.singaporetech.inf2007quiz01.BlockchainBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import java.nio.charset.StandardCharsets

data class PqcResult(
    val signatures: List<ByteArray>,
    val publicKeys: List<ByteArray>,
    val quorumMet: Boolean,
    val validCount: Int
)

/** Manages per-CalBot ML-DSA key material in SharedPreferences. */
class PqcQuorum(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun getOrCreateKeypair(calBotId: Int, nodeId: Int): Pair<String, String> {
        val privateKeyName = "calbot_${calBotId}_node_${nodeId}_privkey"
        val publicKeyName = "calbot_${calBotId}_node_${nodeId}_pubkey"

        val existingPrivate = prefs.getString(privateKeyName, null)
        val existingPublic = prefs.getString(publicKeyName, null)
        if (existingPrivate != null && existingPublic != null) {
            return decodePem(existingPrivate) to decodePem(existingPublic)
        }

        val generated = parseKeypairPayload(BlockchainBridge.generateKeypair())
        prefs.edit()
            .putString(privateKeyName, encodePem(generated.first))
            .putString(publicKeyName, encodePem(generated.second))
            .apply()
        return generated
    }

    suspend fun signAndCollect(blockData: ByteArray, calBotId: Int): PqcResult = coroutineScope {
        val nodeResults = (0 until 3).map { nodeId ->
            async(Dispatchers.Default) {
                val (privateKeyPem, publicKeyPem) = getOrCreateKeypair(calBotId, nodeId)
                val signature = BlockchainBridge.signBlock(blockData, privateKeyPem)
                val isValid = BlockchainBridge.verifySignature(blockData, signature, publicKeyPem)
                NodeSignature(
                    signature = signature,
                    publicKey = publicKeyPem.toByteArray(StandardCharsets.UTF_8),
                    isValid = isValid
                )
            }
        }.awaitAll()

        val validCount = nodeResults.count { it.isValid }
        PqcResult(
            signatures = nodeResults.map { it.signature },
            publicKeys = nodeResults.map { it.publicKey },
            quorumMet = validCount >= 2,
            validCount = validCount
        )
    }

    private fun encodePem(pem: String): String {
        return Base64.encodeToString(
            pem.toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP
        )
    }

    private fun decodePem(encodedPem: String): String {
        return String(Base64.decode(encodedPem, Base64.DEFAULT), StandardCharsets.UTF_8)
    }

    private fun parseKeypairPayload(payload: String): Pair<String, String> {
        val trimmed = payload.trim()
        if (trimmed.startsWith("{")) {
            val json = JSONObject(trimmed)
            val privateKey = listOf("privateKeyPem", "privateKey", "privkey")
                .firstNotNullOfOrNull { key -> json.optString(key).takeIf { it.isNotBlank() } }
            val publicKey = listOf("publicKeyPem", "publicKey", "pubkey")
                .firstNotNullOfOrNull { key -> json.optString(key).takeIf { it.isNotBlank() } }
            if (privateKey != null && publicKey != null) {
                return privateKey to publicKey
            }
        }

        val pemBlocks = PEM_BLOCK_REGEX.findAll(payload).map { it.value.trim() }.toList()
        if (pemBlocks.size >= 2) {
            return pemBlocks[0] to pemBlocks[1]
        }

        payload.split("||", limit = 2)
            .takeIf { it.size == 2 && it.all(String::isNotBlank) }
            ?.let { return it[0] to it[1] }

        error("Unsupported keypair payload format from BlockchainBridge.generateKeypair()")
    }

    private data class NodeSignature(
        val signature: ByteArray,
        val publicKey: ByteArray,
        val isValid: Boolean
    )

    private companion object {
        const val PREFS_NAME = "calbot_pqc_keys"
        val PEM_BLOCK_REGEX = Regex(
            "-----BEGIN [^-]+-----[\\s\\S]*?-----END [^-]+-----"
        )
    }
}
