//! CalBot Multiplication Microservice
//!
//! Compiled to WebAssembly by the same Zig compiler that also builds
//! the native .so which hosts the wasm3 runtime that interprets this
//! very module. Zig compiling Zig for Zig to interpret. Ouroboros.

export fn mul(a: i32, b: i32) i32 {
    return a * b;
}
