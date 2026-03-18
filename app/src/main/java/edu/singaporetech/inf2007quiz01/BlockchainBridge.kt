package edu.singaporetech.inf2007quiz01

/** JNI bridge for the blockchain and ML-DSA helpers in Zig. */
object BlockchainBridge {

    /** True when the Zig .so is present and PQC operations are available. */
    var available: Boolean = false
        private set

    init {
        available = try {
            System.loadLibrary("calbot_zig")
            true
        } catch (_: UnsatisfiedLinkError) {
            // Zig .so not built yet — blockchain ops will be unavailable.
            false
        }
    }

    external fun generateKeypair(): String

    external fun signBlock(blockData: ByteArray, privateKeyPem: String): ByteArray

    external fun verifySignature(
        blockData: ByteArray,
        signature: ByteArray,
        publicKeyPem: String
    ): Boolean

    external fun sha3Hash(data: ByteArray): String

    external fun mineBlock(blockData: ByteArray, difficulty: Int): Long
}
