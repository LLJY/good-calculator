package edu.singaporetech.inf2007quiz01

/**
 * JNI bridge to FreeRTOS — an embedded real-time operating system
 * designed for microcontrollers with 4KB of RAM, now running inside
 * a shared library on an Android phone to schedule arithmetic tasks.
 *
 * Each call to [compute] spawns a 3-node FreeRTOS cluster with
 * triple modular redundancy. Three RTOS tasks independently compute
 * the same operation, and the results are majority-voted.
 *
 * Task priorities are assigned by operation complexity:
 *   - Addition:       Priority 2 (idle)
 *   - Subtraction:    Priority 3 (normal)
 *   - Multiplication: Priority 4 (high)
 *   - Division:       Priority 6 (safety-critical)
 */
object FreeRtosBridge {

    var available: Boolean = false
        private set

    init {
        available = try {
            System.loadLibrary("freertos_calc")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    /**
     * Compute [a] [op] [b] using FreeRTOS task scheduling with
     * triple modular redundancy.
     *
     * Returns [Int.MIN_VALUE] on failure.
     */
    fun compute(a: Int, b: Int, op: Char): Int {
        if (!available) return Int.MIN_VALUE
        return nativeCompute(a, b, op.code.toByte())
    }

    private external fun nativeCompute(a: Int, b: Int, op: Byte): Int
}
