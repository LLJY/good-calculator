package edu.singaporetech.inf2007quiz01

/**
 * Maps function names to their implementations.
 * All three compute functions now delegate to Rust via JNI:
 *
 * - fib  → fast doubling O(log n), was O(2^n) naive recursive
 * - half → trivial but native for completeness
 * - self → billion sqrt iterations, dramatically faster with LLVM
 *
 * Kotlin signatures are unchanged so nothing downstream breaks.
 */
object FunctionMap {

    fun half(x: Int): Int = NativeEngine.half(x)

    fun fib(x: Int): Int = NativeEngine.fib(x)

    /** Named selfFn on the native side to dodge Kotlin's `self` keyword. */
    fun self(x: Int): Int = NativeEngine.selfFn(x)

    val functionMap = mapOf<String, (Int) -> Int>(
        "half" to ::half,
        "fib" to ::fib,
        "self" to ::`self`
    )
}
