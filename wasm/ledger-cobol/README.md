# COBOL Ledger WASM Pipeline

This directory documents the most unreasonable part of the stack: a COBOL report formatter that is supposed to end up as `ledger.wasm` for an Android calculator.

## Theoretical pipeline

1. `cobc -C -x ledger.cob -o ledger.c`
   - GnuCOBOL translates the COBOL program to C.
   - The `-C` flag is the documented translation-only mode.
   - The `-x` flag tells GnuCOBOL to include a main entry point for an executable-style build.
2. `clang --target=wasm32-wasi ... ledger.c -o ledger.wasm`
   - Clang turns the generated C into a WASI WebAssembly module.
3. `wasm3` inside the Zig JNI layer loads the artifact and calls the ledger formatter entry point.

## Why this is cursed

- COBOL expects the `libcob` runtime, which is easy on a normal host and awkward inside WASM.
- GnuCOBOL happily emits C, but that C still wants the COBOL runtime support machinery.
- Android does not need any of this. The calculator definitely does not need any of this.

## Entry point strategy

The COBOL program defines an alternate `ENTRY "format_ledger"` so a C host can resolve it through the GnuCOBOL runtime bridge. That gives the native layer a stable callable symbol even though the actual execution path still flows through `libcob`.

## Fallback plan

If the full `COBOL -> C -> WASM` pipeline becomes too painful to maintain, keep `ledger.cob` as the canonical specification of the formatting rules and reimplement the same logic in a tiny C shim that is easier to compile to WASM. The COBOL stays as the ceremonial source of truth, which is exactly the kind of sentence this project deserves.
