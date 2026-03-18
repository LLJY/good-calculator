# CalBot: A Byzantine Fault-Tolerant Post-Quantum Calculator with Heterogeneous Compute Verification

## Abstract

CalBot is an Android calculator application that employs a multi-layered verification architecture to ensure computational correctness across heterogeneous execution environments. Each arithmetic operation is independently verified through Raft-based distributed consensus, FreeRTOS real-time task scheduling with triple modular redundancy (TMR), a pure-Kotlin oracle, and polyglot WebAssembly microservices. Results are committed to a per-user blockchain secured with FIPS 204 ML-DSA-65 post-quantum digital signatures. A Fortran 90 scientific computing module provides supplementary linear algebra verification (eigenvalue decomposition, singular value decomposition) and computational proof-of-work via CPU ray tracing.

## Architecture

```
+---------------------------------------------------------------+
|  CalBot Android Application                                    |
|                                                                |
|  Jetpack Compose UI                                [Kotlin]    |
|    |                                                           |
|    v                                                           |
|  CalculatorViewModel (Hilt-injected)               [Kotlin]    |
|    |                                                           |
|    +---> NativeEngine (JNI) ----------------------> [Rust]     |
|    |       Shunting-yard evaluator, O(n) parsing               |
|    |       Fast-doubling Fibonacci, O(log n)                   |
|    |                                                           |
|    +---> ConsensusEngine (fire-and-forget)          [Kotlin]   |
|            |                                                   |
|            +---> Raft Consensus (3 nodes)           [Kotlin]   |
|            |       Leader election, log replication             |
|            |       Channel<RaftMessage> transport               |
|            |                                                   |
|            +---> WASM Dispatch (wasm3 runtime)      [Zig/C]   |
|            |       add.wasm [Go], sub.wasm [C],                |
|            |       mul.wasm [Zig], div.wasm [Rust]             |
|            |                                                   |
|            +---> FreeRTOS TMR                       [C]        |
|            |       3 preemptive tasks, majority vote            |
|            |       POSIX port on pthreads                      |
|            |                                                   |
|            +---> Kotlin Oracle                      [Kotlin]   |
|            |       Pure arithmetic cross-check                 |
|            |                                                   |
|            +---> Fortran Scientific Module           [Fortran] |
|            |       64x64 ray tracing, QR eigenvalues, SVD     |
|            |                                                   |
|            +---> PQC Signing (ML-DSA-65)            [Zig/C]   |
|            |       3 signatures per block (~3.3KB each)        |
|            |                                                   |
|            +---> Proof-of-Work                      [Zig/C]   |
|            |       SHA3-256, difficulty=2                       |
|            |                                                   |
|            +---> Block Commit                       [SQL]      |
|                    Room SQLite, COBOL genesis hashes            |
+---------------------------------------------------------------+
```

## Motivation

Modern mobile applications face an increasingly adversarial threat landscape. A single computational error in a calculator — undetected — could propagate through financial, scientific, or engineering workflows with catastrophic consequences. CalBot addresses this by treating every arithmetic operation as a safety-critical computation requiring multi-path verification, cryptographic attestation, and real-time scheduling guarantees.

The transition to post-quantum cryptography (PQC) is an urgent concern. NIST's standardization of ML-DSA (FIPS 204) in 2024 provides lattice-based digital signatures resistant to Shor's algorithm. CalBot integrates ML-DSA-65 to ensure that its computational audit trail remains tamper-proof even in a post-quantum threat environment.

The inclusion of FreeRTOS — an RTOS designed for microcontrollers with as little as 4KB of RAM — provides real-time scheduling guarantees for arithmetic operations. While Android is not a hard real-time platform, the FreeRTOS layer ensures deterministic task priority assignment: division (priority 6, safety-critical) always preempts addition (priority 2, idle). Triple modular redundancy, a technique borrowed from aerospace systems (cf. Space Shuttle GPC architecture), provides hardware-agnostic fault tolerance through majority voting.

Fortran 90 ray tracing serves as a computational canary: if the rendered sphere checksum deviates between builds, the computational environment has been compromised. The eigenvalue and SVD computations provide a dense floating-point workload that exercises the FPU pipeline, serving as an implicit hardware verification step.

## Verification Pipeline

When a user presses `=` on `3+5`:

| Phase | System | Language | Time | Description |
|-------|--------|----------|------|-------------|
| 1 | Native Evaluator | Rust | <1ms | Shunting-yard parse, immediate display |
| 2 | Raft Consensus | Kotlin | ~200ms | 3-node leader election, WASM compute |
| 3 | FreeRTOS TMR | C | ~100ms | 3 preemptive tasks, majority vote |
| 4 | Kotlin Oracle | Kotlin | <1ms | Pure arithmetic cross-check |
| 5 | Fortran Science | Fortran | ~5ms | Ray trace + eigenvalues + SVD |
| 6 | PQC Attestation | Zig/OpenSSL | ~500ms | 3x ML-DSA-65 signatures |
| 7 | Proof-of-Work | Zig/OpenSSL | ~100ms | SHA3-256 nonce mining |
| 8 | Block Commit | SQL | ~50ms | Room insert with COBOL genesis chain |
| | **Audio feedback** | **Android** | | **faaah.mp3** |

## Technology Stack

| Layer | Language | Purpose |
|-------|----------|---------|
| UI | Kotlin (Jetpack Compose) | Material 3, spring-physics animations, vaporwave theme |
| ViewModel | Kotlin | Hilt DI, coroutines, Navigation 3 |
| Native Evaluator | Rust | Shunting-yard parser, fast-doubling Fibonacci |
| Consensus | Kotlin | Raft protocol (leader election, log replication) |
| WASM Runtime | Zig + C (wasm3) | WebAssembly module hosting |
| Addition | Go (TinyGo) | `add.wasm` compiled to WASI |
| Subtraction | C (clang) | `sub.wasm` compiled to WASI |
| Multiplication | Zig | `mul.wasm` (Zig compiling Zig-as-WASM) |
| Division | Rust | `div.wasm` compiled to wasm32-wasip1 |
| RTOS Verification | C (FreeRTOS) | Triple modular redundancy, POSIX port |
| Scientific Computing | Fortran 90 | Ray tracing, QR eigenvalues, SVD |
| Blockchain Crypto | Zig + OpenSSL 3.5 | SHA3-256, ML-DSA-65 (FIPS 204) |
| Genesis Hashes | COBOL (GCC gcobol) | Deterministic per-CalBot chain anchors |
| Ledger Formatting | COBOL | Fixed-width PICTURE clause formatting |
| Persistence | SQL (Room) | BlockEntity (~15KB/block), HistoryEntry |
| Build System | Groovy/KTS (Gradle) | Android build orchestration |
| Build Scripts | Bash | Multi-compiler build pipeline |

**Languages: 13** | **Native .so libraries: 3** | **Compilers required: 7** | **Lines of code: ~37,000**

## Build Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| `kotlinc` | via Gradle | Kotlin compiler |
| `rustc` | 1.75+ | Native evaluator + div.wasm |
| `zig` | 0.13+ | Native .so + mul.wasm |
| `gfortran` | 15+ | Ray tracer + linear algebra |
| `gcobol` | 15+ | Genesis block generation |
| `gcc` | 15+ | FreeRTOS POSIX port |
| `tinygo` | 0.31+ | add.wasm (Go to WASM) |
| `clang` | 17+ | sub.wasm (C to WASM) |
| OpenSSL | 3.5+ | ML-DSA-65 source build |
| Android NDK | r26+ | Native cross-compilation |

## Build

```bash
# Full build (requires all 7 compilers)
./scripts/build-cursed.sh

# Or step-by-step — see CURSED_PLAN.md
```

## Test

```bash
cd test
JAVA_HOME=/usr/lib/jvm/java-21-openjdk bash test.sh
```

```
Tests run: 12,  Failures: 0
OK (12 tests)
```

All 12 instrumented grading tests pass with the full verification pipeline active. The user-facing evaluation path remains synchronous (< 1ms), while the consensus pipeline runs asynchronously in the background.

## Block Schema

Each computation produces a ~15KB block:

```
BlockEntity {
  calBotId:     Int           // which CalBot (1-30)
  expression:   String        // "3+5"
  result:       Int           // 8
  prevHash:     String        // SHA3-256 of previous block
  blockHash:    String        // SHA3-256 of this block
  nonce:        Long          // proof-of-work nonce
  timestamp:    Long          // epoch millis
  raftTerm:     Int           // Raft consensus term
  leaderNode:   Int           // elected leader (0, 1, or 2)
  votes:        String        // "[8, 8, 8]" from all nodes
  oracleAgreed: Boolean       // Kotlin oracle cross-check
  sigNode0:     ByteArray     // ML-DSA-65 signature (~3.3KB)
  sigNode1:     ByteArray     // ML-DSA-65 signature (~3.3KB)
  sigNode2:     ByteArray     // ML-DSA-65 signature (~3.3KB)
  pubkeyNode0:  ByteArray     // ML-DSA-65 public key (~2KB)
  pubkeyNode1:  ByteArray     // ML-DSA-65 public key (~2KB)
  pubkeyNode2:  ByteArray     // ML-DSA-65 public key (~2KB)
}
```

30 CalBots x 20 blocks = ~9MB of quantum-resistant signed arithmetic stored in SQLite.

## References

- D. Ongaro and J. Ousterhout, "In Search of an Understandable Consensus Algorithm," USENIX ATC 2014
- NIST FIPS 204, "Module-Lattice-Based Digital Signature Standard (ML-DSA)," 2024
- FreeRTOS, "POSIX/Linux Simulator," freertos.org
- A. Haas et al., "Bringing the Web up to Speed with WebAssembly," PLDI 2017
- ISO/IEC 1989:2023, "COBOL — Programming Language"
- ISO/IEC 1539-1:2023, "Fortran — Programming Language"
