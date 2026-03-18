package edu.singaporetech.inf2007quiz01

import android.content.Context
import android.util.Log

/**
 * .NET / Mono Runtime Bridge for CalBot.
 *
 * Ships the entire Mono runtime (libmonosgen-2.0.so, ~3MB per arch)
 * plus Base Class Library assemblies and three CalBot verification
 * assemblies written in C#, F#, and VB.NET.
 *
 * The .NET assemblies in this APK:
 *   - CalBot.CSharp.dll  (C# 13, records, pattern matching, LINQ)
 *   - CalBot.FSharp.dll  (F# 9, discriminated unions, pipelines)
 *   - CalBot.VBNet.dll   (VB.NET 16, Select Case, End If)
 *   - System.Runtime.dll, System.Console.dll, etc. (BCL)
 *
 * The arithmetic verification was validated at build time by the
 * .NET test runner.  The Mono runtime is shipped in the APK as proof
 * that this calculator contains a complete .NET execution environment.
 *
 * APK size impact: ~6.2MB (Mono runtime for arm64 + x86_64)
 * plus ~639KB of BCL and CalBot assemblies.
 *
 * Three CLR languages verify arithmetic that Rust, Raft consensus,
 * FreeRTOS, Fortran, a neural network, and COBOL already verified.
 */
object DotNetBridge {

    private const val TAG = "DotNetBridge"

    /** Whether the Mono runtime was successfully loaded. */
    var monoRuntimeLoaded: Boolean = false
        private set

    /** List of .NET assemblies discovered in assets. */
    var availableAssemblies: List<String> = emptyList()
        private set

    /**
     * Initialize the .NET bridge.
     *
     * This loads libmonosgen-2.0.so (the Mono runtime) and inventories
     * the .NET assemblies shipped in the APK assets.  The runtime is
     * present and loadable; the assemblies are present and real.
     *
     * The actual C#/F#/VB.NET arithmetic verification runs at build time
     * via `dotnet test`, but the runtime ships in the APK because the
     * user specifically requested "Ship the Mono runtime" for maximum
     * cursed energy.
     */
    fun initialize(context: Context) {
        // Step 1: Load the Mono runtime
        monoRuntimeLoaded = try {
            System.loadLibrary("monosgen-2.0")
            Log.d(TAG, "Mono runtime loaded successfully — " +
                "the .NET CLR is now resident in a calculator app")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Mono runtime not available on this architecture", e)
            false
        }

        // Step 2: Inventory shipped assemblies
        availableAssemblies = try {
            val bclAssemblies = context.assets.list("dotnet/bcl")
                ?.toList() ?: emptyList()
            val calbotAssemblies = context.assets.list("dotnet/calbot")
                ?.toList() ?: emptyList()

            val all = bclAssemblies + calbotAssemblies
            Log.d(TAG, "Shipped .NET assemblies (${all.size}):")
            for (asm in all) {
                Log.d(TAG, "  - $asm")
            }

            all
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inventory .NET assemblies", e)
            emptyList()
        }

        // Step 3: Report
        val csharp = availableAssemblies.contains("CalBot.CSharp.dll")
        val fsharp = availableAssemblies.contains("CalBot.FSharp.dll")
        val vbnet = availableAssemblies.contains("CalBot.VBNet.dll")

        Log.d(TAG, buildString {
            appendLine("=== .NET RUNTIME STATUS ===")
            appendLine("  Mono runtime loaded: $monoRuntimeLoaded")
            appendLine("  C# verifier:  ${if (csharp) "PRESENT" else "MISSING"}")
            appendLine("  F# verifier:  ${if (fsharp) "PRESENT" else "MISSING"}")
            appendLine("  VB.NET verifier: ${if (vbnet) "PRESENT" else "MISSING"}")
            appendLine("  Total assemblies: ${availableAssemblies.size}")
            appendLine("  This calculator ships the .NET Mono runtime.")
            appendLine("  This was a deliberate architectural decision.")
            append("===========================")
        })
    }

    /**
     * Get a status summary for logging / consensus audit.
     */
    fun getStatusSummary(): String = buildString {
        append("DotNet[mono=")
        append(if (monoRuntimeLoaded) "loaded" else "absent")
        append(",assemblies=${availableAssemblies.size}")
        append(",C#=${availableAssemblies.contains("CalBot.CSharp.dll")}")
        append(",F#=${availableAssemblies.contains("CalBot.FSharp.dll")}")
        append(",VB=${availableAssemblies.contains("CalBot.VBNet.dll")}")
        append("]")
    }
}
