package edu.singaporetech.inf2007quiz01

/**
 * JNI bridge for the WASM dispatch layer.
 *
 * Yes, we are loading WASM modules through Zig so the calculator can add two
 * integers with the appropriate amount of ceremony.
 */
object WasmServiceBridge {

    /** True when the Zig .so is present and the WASM layer is operational. */
    var available: Boolean = false
        private set

    init {
        available = try {
            System.loadLibrary("calbot_zig")
            true
        } catch (_: UnsatisfiedLinkError) {
            // Zig .so not built yet — WASM dispatch will be unavailable.
            false
        }
    }

    external fun computeViaWasm(operator: String, a: Int, b: Int, instanceId: Int): Int

    external fun formatLedger(blockJson: String): String
}
