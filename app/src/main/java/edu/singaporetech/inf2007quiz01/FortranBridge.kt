package edu.singaporetech.inf2007quiz01

import android.util.Log

/**
 * JNI bridge to Fortran 90 — a language invented in 1957 for scientific
 * computing on IBM mainframes, now raytracing 3D spheres and computing
 * eigenvalues on an Android phone every time someone presses "=".
 *
 * Each call renders a 64x64 pixel sphere with Lambertian diffuse shading,
 * computes eigenvalues of a 4x4 matrix via QR iteration, and performs
 * SVD via eigendecomposition of A^T*A. None of this is needed.
 */
object FortranBridge {

    var available: Boolean = false
        private set

    init {
        available = try {
            System.loadLibrary("calbot_fortran")
            true
        } catch (_: UnsatisfiedLinkError) {
            // Fortran .so not built for this architecture
            false
        }
    }

    /**
     * Raytrace a sphere, compute eigenvalues and SVD, all keyed off
     * the calculator [result]. Returns a summary string for logging.
     */
    fun computeUselessScience(result: Int): String {
        if (!available) return "Fortran unavailable"
        val eigenvalues = DoubleArray(4)
        val singularValues = DoubleArray(4)
        val checksum = intArrayOf(0)
        nativeCompute(result, eigenvalues, singularValues, checksum)
        val summary = "Fortran: raytraced 64x64 sphere (checksum=${checksum[0]}), " +
            "eigen=[${eigenvalues.joinToString { "%.2f".format(it) }}], " +
            "svd=[${singularValues.joinToString { "%.2f".format(it) }}]"
        Log.d("FortranBridge", summary)
        return summary
    }

    /**
     * Get the raw 64x64 RGBA pixel buffer from the last raytrace.
     * Returns null if Fortran is unavailable.
     * 64*64*4 = 16384 bytes, suitable for glTexImage2D(GL_RGBA).
     */
    fun getPixels(): ByteArray? {
        if (!available) return null
        return nativeGetPixels()
    }

    // Fortran bind(C) function: fortran_compute(int*, double[4], double[4], int*)
    private external fun nativeCompute(
        result: Int,
        eigenvalues: DoubleArray,
        singularValues: DoubleArray,
        pixelChecksum: IntArray
    )

    // Fortran bind(C) function: fortran_get_pixels(int8_t[64*64*4])
    private external fun nativeGetPixels(): ByteArray
}
