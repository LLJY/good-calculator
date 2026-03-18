const std = @import("std");

const evaluator = @import("evaluator.zig");
const fibonacci = @import("fibonacci.zig");
const wasm_dispatch = @import("wasm_dispatch.zig");
const blockchain = @import("blockchain.zig");
const pqc_signer = @import("pqc_signer.zig");

const c = @cImport({
    @cInclude("jni.h");
});

const jint_min: c.jint = std.math.minInt(i32);
const jni_abort: c.jint = 2;

fn wrapI64ToI32(value: i64) i32 {
    const bits: u64 = @bitCast(value);
    const lower: u32 = @truncate(bits);
    return @bitCast(lower);
}

fn newJavaString(env: *c.JNIEnv, allocator: std.mem.Allocator, bytes: []const u8) ?c.jstring {
    const fns = env.*.*;
    const owned = allocator.allocSentinel(u8, bytes.len, 0) catch return null;
    defer allocator.free(owned);

    std.mem.copyForwards(u8, owned[0..bytes.len], bytes);
    return fns.NewStringUTF.?(env, owned.ptr);
}

fn newJavaByteArray(env: *c.JNIEnv, bytes: []const u8) ?c.jbyteArray {
    const fns = env.*.*;
    const len = std.math.cast(c.jsize, bytes.len) orelse return null;

    const array = fns.NewByteArray.?(env, len) orelse return null;
    if (bytes.len > 0) {
        fns.SetByteArrayRegion.?(env, array, 0, len, @ptrCast(bytes.ptr));
    }
    return array;
}

pub export fn Java_edu_singaporetech_inf2007quiz01_NativeEngine_fib(
    env: *c.JNIEnv,
    _: c.jclass,
    n: c.jint,
) callconv(.C) c.jint {
    _ = env;
    const safe_n: u32 = if (n < 0) 0 else @intCast(n);
    return wrapI64ToI32(fibonacci.fastDoublingFib(safe_n));
}

pub export fn Java_edu_singaporetech_inf2007quiz01_NativeEngine_half(
    env: *c.JNIEnv,
    _: c.jclass,
    x: c.jint,
) callconv(.C) c.jint {
    _ = env;
    return fibonacci.half(x);
}

pub export fn Java_edu_singaporetech_inf2007quiz01_NativeEngine_selfFn(
    env: *c.JNIEnv,
    _: c.jclass,
    x: c.jint,
) callconv(.C) c.jint {
    _ = env;
    return fibonacci.selfFn(x);
}

pub export fn Java_edu_singaporetech_inf2007quiz01_NativeEngine_evaluate(
    env: *c.JNIEnv,
    _: c.jclass,
    expr: c.jstring,
) callconv(.C) c.jint {
    const fns = env.*.*;
    const chars = fns.GetStringUTFChars.?(env, expr, null) orelse return jint_min;
    defer fns.ReleaseStringUTFChars.?(env, expr, chars);

    return evaluator.evaluate(std.mem.span(chars)) orelse jint_min;
}

pub export fn Java_edu_singaporetech_inf2007quiz01_WasmServiceBridge_computeViaWasm(
    env: *c.JNIEnv,
    _: c.jclass,
    operator: c.jstring,
    a: c.jint,
    b: c.jint,
    instance_id: c.jint,
) callconv(.C) c.jint {
    _ = instance_id;

    const fns = env.*.*;
    const op_chars = fns.GetStringUTFChars.?(env, operator, null) orelse return jint_min;
    defer fns.ReleaseStringUTFChars.?(env, operator, op_chars);

    const index = wasm_dispatch.operatorIndex(std.mem.span(op_chars)) orelse return jint_min;
    return wasm_dispatch.computeViaWasm(index, a, b) orelse jint_min;
}

pub export fn Java_edu_singaporetech_inf2007quiz01_WasmServiceBridge_formatLedger(
    env: *c.JNIEnv,
    _: c.jclass,
    json_data: c.jstring,
) callconv(.C) c.jstring {
    var arena = std.heap.ArenaAllocator.init(std.heap.page_allocator);
    defer arena.deinit();

    const fns = env.*.*;
    const chars = fns.GetStringUTFChars.?(env, json_data, null) orelse return null;
    defer fns.ReleaseStringUTFChars.?(env, json_data, chars);

    const formatted = wasm_dispatch.formatLedgerAlloc(arena.allocator(), std.mem.span(chars)) orelse return null;
    return newJavaString(env, arena.allocator(), formatted);
}

pub export fn Java_edu_singaporetech_inf2007quiz01_BlockchainBridge_generateKeypair(
    env: *c.JNIEnv,
    _: c.jclass,
) callconv(.C) c.jstring {
    var arena = std.heap.ArenaAllocator.init(std.heap.page_allocator);
    defer arena.deinit();

    var priv_buf: [32768]u8 = std.mem.zeroes([32768]u8);
    var pub_buf: [16384]u8 = std.mem.zeroes([16384]u8);

    if (!pqc_signer.generateKeypair(&priv_buf, &pub_buf)) return null;

    const payload = .{
        .privateKeyPem = std.mem.sliceTo(priv_buf[0..], 0),
        .publicKeyPem = std.mem.sliceTo(pub_buf[0..], 0),
    };

    var json: std.ArrayListUnmanaged(u8) = .empty;
    std.json.stringify(payload, .{}, json.writer(arena.allocator())) catch return null;
    return newJavaString(env, arena.allocator(), json.items);
}

pub export fn Java_edu_singaporetech_inf2007quiz01_BlockchainBridge_signBlock(
    env: *c.JNIEnv,
    _: c.jclass,
    block_data: c.jbyteArray,
    privkey_pem: c.jstring,
) callconv(.C) c.jbyteArray {
    const fns = env.*.*;
    const key_chars = fns.GetStringUTFChars.?(env, privkey_pem, null) orelse return null;
    defer fns.ReleaseStringUTFChars.?(env, privkey_pem, key_chars);

    const block_len: usize = @intCast(fns.GetArrayLength.?(env, block_data));
    const block_raw = fns.GetByteArrayElements.?(env, block_data, null) orelse return null;
    defer fns.ReleaseByteArrayElements.?(env, block_data, block_raw, jni_abort);

    const block_bytes: []const u8 = @as([*]const u8, @ptrCast(block_raw))[0..block_len];
    var sig_buf: [8192]u8 = undefined;
    const sig_len = pqc_signer.sign(block_bytes, std.mem.span(key_chars), &sig_buf) orelse return null;
    return newJavaByteArray(env, sig_buf[0..sig_len]);
}

pub export fn Java_edu_singaporetech_inf2007quiz01_BlockchainBridge_verifySignature(
    env: *c.JNIEnv,
    _: c.jclass,
    block_data: c.jbyteArray,
    signature: c.jbyteArray,
    pubkey_pem: c.jstring,
) callconv(.C) c.jboolean {
    const fns = env.*.*;
    const key_chars = fns.GetStringUTFChars.?(env, pubkey_pem, null) orelse return 0;
    defer fns.ReleaseStringUTFChars.?(env, pubkey_pem, key_chars);

    const block_len: usize = @intCast(fns.GetArrayLength.?(env, block_data));
    const block_raw = fns.GetByteArrayElements.?(env, block_data, null) orelse return 0;
    defer fns.ReleaseByteArrayElements.?(env, block_data, block_raw, jni_abort);

    const sig_len: usize = @intCast(fns.GetArrayLength.?(env, signature));
    const sig_raw = fns.GetByteArrayElements.?(env, signature, null) orelse return 0;
    defer fns.ReleaseByteArrayElements.?(env, signature, sig_raw, jni_abort);

    const block_bytes: []const u8 = @as([*]const u8, @ptrCast(block_raw))[0..block_len];
    const sig_bytes: []const u8 = @as([*]const u8, @ptrCast(sig_raw))[0..sig_len];

    return if (pqc_signer.verify(block_bytes, sig_bytes, std.mem.span(key_chars))) 1 else 0;
}

pub export fn Java_edu_singaporetech_inf2007quiz01_BlockchainBridge_sha3Hash(
    env: *c.JNIEnv,
    _: c.jclass,
    block_data: c.jbyteArray,
) callconv(.C) c.jstring {
    var arena = std.heap.ArenaAllocator.init(std.heap.page_allocator);
    defer arena.deinit();

    const fns = env.*.*;
    const block_len: usize = @intCast(fns.GetArrayLength.?(env, block_data));
    const block_raw = fns.GetByteArrayElements.?(env, block_data, null) orelse return null;
    defer fns.ReleaseByteArrayElements.?(env, block_data, block_raw, jni_abort);

    const block_bytes: []const u8 = @as([*]const u8, @ptrCast(block_raw))[0..block_len];
    const hex = blockchain.sha3_256_hex(block_bytes);
    return newJavaString(env, arena.allocator(), &hex);
}

pub export fn Java_edu_singaporetech_inf2007quiz01_BlockchainBridge_mineBlock(
    env: *c.JNIEnv,
    _: c.jclass,
    block_data: c.jbyteArray,
    difficulty: c.jint,
) callconv(.C) c.jlong {
    const fns = env.*.*;
    const block_len: usize = @intCast(fns.GetArrayLength.?(env, block_data));
    const block_raw = fns.GetByteArrayElements.?(env, block_data, null) orelse return 0;
    defer fns.ReleaseByteArrayElements.?(env, block_data, block_raw, jni_abort);

    const block_bytes: []const u8 = @as([*]const u8, @ptrCast(block_raw))[0..block_len];
    const safe_difficulty: u32 = if (difficulty < 0) 0 else @intCast(difficulty);
    return @intCast(blockchain.mineBlock(block_bytes, safe_difficulty));
}
