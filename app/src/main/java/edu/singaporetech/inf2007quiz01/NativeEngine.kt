package edu.singaporetech.inf2007quiz01

/**
 * JNI bridge to the native compute library.
 *
 * Tries to load libcalbot_zig.so first (the cursed Zig replacement),
 * falls back to libcalbot_native.so (the original Rust library) if
 * the Zig .so hasn't been built yet. Either way, the JNI function
 * signatures are identical so the app layer doesn't care.
 *
 * All heavy compute goes through here:
 * - fib()     → fast doubling Fibonacci, O(log n)
 * - half()    → integer halving
 * - selfFn()  → billion-iteration sqrt identity (LLVM-optimised)
 * - evaluate() → shunting-yard expression parser with operator precedence
 */
object NativeEngine {

    init {
        try {
            System.loadLibrary("calbot_zig")
        } catch (_: UnsatisfiedLinkError) {
            // Zig .so not built yet — fall back to the original Rust library
            System.loadLibrary("calbot_native")
        }
    }

    /** Fast doubling Fibonacci — O(log n). */
    external fun fib(n: Int): Int

    /** Integer halving — x / 2. */
    external fun half(x: Int): Int

    /**
     * Billion-iteration sqrt(y)*sqrt(y) loop.
     * Named selfFn to avoid clashing with Kotlin's `self` keyword.
     */
    external fun selfFn(x: Int): Int

    /**
     * Evaluate an arithmetic expression with correct operator precedence.
     * Supports +, -, *, / and handles leading negatives (result reuse).
     *
     * Returns [Int.MIN_VALUE] on parse error or division by zero —
     * callers should treat that as null/error.
     */
    external fun evaluate(expr: String): Int
}
