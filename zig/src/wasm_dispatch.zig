const std = @import("std");

const c = @cImport({
    @cInclude("wasm3.h");
});

pub const WasmModule = struct {
    env: c.IM3Environment,
    runtime: c.IM3Runtime,
    module: c.IM3Module,
    wasm_bytes: []u8,
    export_name: [:0]const u8,
};

var modules: [4]?WasmModule = .{ null, null, null, null };
var ledger_module: ?WasmModule = null;

const stack_size_bytes: u32 = 64 * 1024;
const operator_exports = [_][:0]const u8{ "add", "sub", "mul", "div" };

fn destroyModule(slot: *?WasmModule) void {
    if (slot.*) |module| {
        c.m3_FreeRuntime(module.runtime);
        c.m3_FreeEnvironment(module.env);
        std.heap.page_allocator.free(module.wasm_bytes);
        slot.* = null;
    }
}

fn loadModule(export_name: [:0]const u8, wasm_bytes: []const u8) ?WasmModule {
    const owned = std.heap.page_allocator.alloc(u8, wasm_bytes.len) catch return null;
    errdefer std.heap.page_allocator.free(owned);
    std.mem.copyForwards(u8, owned, wasm_bytes);

    const env = c.m3_NewEnvironment() orelse return null;
    errdefer c.m3_FreeEnvironment(env);

    const runtime = c.m3_NewRuntime(env, stack_size_bytes, null) orelse return null;
    errdefer c.m3_FreeRuntime(runtime);

    var module: c.IM3Module = null;
    if (c.m3_ParseModule(env, &module, owned.ptr, @as(u32, @intCast(owned.len))) != null) {
        return null;
    }

    var module_loaded = false;
    errdefer if (!module_loaded and module != null) c.m3_FreeModule(module);

    if (c.m3_LoadModule(runtime, module) != null) return null;
    module_loaded = true;

    if (c.m3_RunStart(module) != null) return null;

    return WasmModule{
        .env = env,
        .runtime = runtime,
        .module = module,
        .wasm_bytes = owned,
        .export_name = export_name,
    };
}

pub fn operatorIndex(operator: []const u8) ?u8 {
    if (std.mem.eql(u8, operator, "+")) return 0;
    if (std.mem.eql(u8, operator, "-")) return 1;
    if (std.mem.eql(u8, operator, "*")) return 2;
    if (std.mem.eql(u8, operator, "/")) return 3;
    return null;
}

pub fn registerModule(operator_index: u8, wasm_bytes: []const u8) bool {
    if (operator_index >= modules.len) return false;

    const loaded = loadModule(operator_exports[operator_index], wasm_bytes) orelse return false;
    destroyModule(&modules[operator_index]);
    modules[operator_index] = loaded;
    return true;
}

pub fn registerLedgerModule(wasm_bytes: []const u8) bool {
    const loaded = loadModule("format_ledger", wasm_bytes) orelse return false;
    destroyModule(&ledger_module);
    ledger_module = loaded;
    return true;
}

pub fn computeViaWasm(operator_index: u8, a: i32, b: i32) ?i32 {
    if (operator_index >= modules.len) return null;
    const module = modules[operator_index] orelse return null;

    var function: c.IM3Function = null;
    if (c.m3_FindFunction(&function, module.runtime, module.export_name.ptr) != null) return null;
    if (c.m3_CallV(function, @as(c_int, a), @as(c_int, b)) != null) return null;

    var result: i32 = 0;
    if (c.m3_GetResultsV(function, &result) != null) return null;
    return result;
}

pub fn formatLedgerAlloc(allocator: std.mem.Allocator, json_data: []const u8) ?[]u8 {
    const module = ledger_module orelse return null;

    var memory_size: u32 = 0;
    const memory = c.m3_GetMemory(module.runtime, &memory_size, 0) orelse return null;

    // The COBOL-shaped WASM formatter is assumed to read input from offset 0 and
    // return a pointer to a NUL-terminated string in linear memory. It is absurd,
    // but at least it is a documented absurdity.
    if (json_data.len + 1 > memory_size) return null;
    const memory_slice = memory[0..memory_size];
    std.mem.copyForwards(u8, memory_slice[0..json_data.len], json_data);
    memory_slice[json_data.len] = 0;

    var function: c.IM3Function = null;
    if (c.m3_FindFunction(&function, module.runtime, module.export_name.ptr) != null) return null;
    if (c.m3_CallV(function, @as(c_int, 0), @as(c_int, @intCast(json_data.len))) != null) return null;

    var output_offset: i32 = 0;
    if (c.m3_GetResultsV(function, &output_offset) != null) return null;
    if (output_offset < 0) return null;

    const start: usize = @intCast(output_offset);
    if (start >= memory_slice.len) return null;

    const output = std.mem.sliceTo(memory_slice[start..], 0);
    return allocator.dupe(u8, output) catch null;
}

pub fn formatLedger(json_data: []const u8) ?[]const u8 {
    return formatLedgerAlloc(std.heap.page_allocator, json_data);
}
