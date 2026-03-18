const std = @import("std");

const AndroidArch = enum {
    arm64,
    x86_64,
};

fn envOrNull(allocator: std.mem.Allocator, name: []const u8) ?[]const u8 {
    return std.process.getEnvVarOwned(allocator, name) catch null;
}

fn collectWasm3Sources(b: *std.Build) []const []const u8 {
    var files = std.ArrayList([]const u8).init(b.allocator);

    var dir = std.fs.cwd().openDir("deps/wasm3/source", .{ .iterate = true }) catch {
        @panic("deps/wasm3/source is missing; populate the wasm3 submodule first");
    };
    defer dir.close();

    var it = dir.iterate();
    while (it.next() catch @panic("failed to iterate deps/wasm3/source")) |entry| {
        if (entry.kind != .file) continue;
        if (!std.mem.endsWith(u8, entry.name, ".c")) continue;

        const full_path = b.pathJoin(&.{ "deps/wasm3/source", entry.name });
        files.append(full_path) catch @panic("out of memory while collecting wasm3 sources");
    }

    if (files.items.len == 0) {
        @panic("deps/wasm3/source contains no .c files; populate the wasm3 submodule first");
    }

    std.mem.sort([]const u8, files.items, {}, struct {
        fn lessThan(_: void, lhs: []const u8, rhs: []const u8) bool {
            return std.mem.lessThan(u8, lhs, rhs);
        }
    }.lessThan);

    return files.toOwnedSlice() catch @panic("out of memory while finalising wasm3 source list");
}

pub fn build(b: *std.Build) void {
    const arch = b.option(AndroidArch, "android-arch", "Android target architecture: arm64 or x86_64") orelse .arm64;
    const android_api = b.option(u32, "android-api", "Android API level to target") orelse 24;

    const optimize: std.builtin.OptimizeMode = .ReleaseFast;

    const target_query: std.Target.Query = switch (arch) {
        .arm64 => .{
            .cpu_arch = .aarch64,
            .os_tag = .linux,
            .abi = .android,
            .os_version_min = .{ .android = android_api },
        },
        .x86_64 => .{
            .cpu_arch = .x86_64,
            .os_tag = .linux,
            .abi = .android,
            .os_version_min = .{ .android = android_api },
        },
    };
    const target = b.resolveTargetQuery(target_query);

    const android_ndk = b.option([]const u8, "android-ndk", "Path to the Android NDK root") orelse
        envOrNull(b.allocator, "ANDROID_NDK_HOME") orelse
        envOrNull(b.allocator, "ANDROID_NDK_ROOT") orelse
        @panic("pass -Dandroid-ndk=/path/to/android-ndk or set ANDROID_NDK_HOME");

    const ndk_sysroot = b.pathJoin(&.{ android_ndk, "toolchains", "llvm", "prebuilt", "linux-x86_64", "sysroot", "usr", "include" });
    const ndk_target_include = b.pathJoin(&.{
        ndk_sysroot,
        switch (arch) {
            .arm64 => "aarch64-linux-android",
            .x86_64 => "x86_64-linux-android",
        },
    });

    const openssl_lib_dir = switch (arch) {
        .arm64 => "deps/openssl/lib-arm64",
        .x86_64 => "deps/openssl/lib-x86_64",
    };

    const lib = b.addSharedLibrary(.{
        .name = "calbot_zig",
        .root_source_file = b.path("src/jni_exports.zig"),
        .target = target,
        .optimize = optimize,
    });

    // Android wants PIC shared objects; Zig already does the right thing for
    // shared libraries, but being explicit keeps the cursed intent readable.
    lib.pic = true;
    lib.strip = true;
    lib.linkLibC();

    lib.root_module.addIncludePath(.{ .cwd_relative = ndk_sysroot });
    lib.root_module.addIncludePath(.{ .cwd_relative = ndk_target_include });
    lib.root_module.addIncludePath(b.path("deps/openssl/include"));
    lib.root_module.addIncludePath(b.path("deps/wasm3"));
    lib.root_module.addIncludePath(b.path("deps/wasm3/source"));

    lib.addCSourceFiles(.{
        .files = collectWasm3Sources(b),
        .flags = &.{
            "-std=c11",
            "-DM3_NO_DEBUG=1",
        },
    });

    // OpenSSL is supplied as a prebuilt static archive so the shared object can
    // drag its post-quantum baggage around without depending on a system copy.
    lib.addObjectFile(.{ .cwd_relative = b.pathJoin(&.{ openssl_lib_dir, "libcrypto.a" }) });

    b.installArtifact(lib);
}
