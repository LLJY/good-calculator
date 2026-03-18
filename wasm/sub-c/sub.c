/**
 * CalBot Subtraction Microservice
 * Compiled to WebAssembly via clang --target=wasm32-wasi.
 *
 * We are using an entire WebAssembly virtual machine, hosted inside
 * a Zig shared library, loaded via JNI from Kotlin, running on an
 * Android phone, to subtract two integers.
 */
#include <stdint.h>

__attribute__((export_name("sub")))
int32_t sub(int32_t a, int32_t b) {
    return a - b;
}
