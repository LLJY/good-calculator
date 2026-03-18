const std = @import("std");

const AndroidArch = enum {
    arm64,
    x86_64,
};

fn envOrNull(allocator: std.mem.Allocator, name: []const u8) ?[]const u8 {
    return std.process.getEnvVarOwned(allocator, name) catch null;
}

pub fn build(b: *std.Build) void {
    const arch = b.option(AndroidArch, "android-arch", "Android target architecture: arm64 or x86_64") orelse .arm64;
    const android_api = b.option(u32, "android-api", "Android API level to target") orelse 24;

    const optimize: std.builtin.OptimizeMode = .ReleaseFast;

    const api_semver: std.SemanticVersion = .{
        .major = android_api,
        .minor = 0,
        .patch = 0,
    };

    const target_query: std.Target.Query = switch (arch) {
        .arm64 => .{
            .cpu_arch = .aarch64,
            .os_tag = .linux,
            .abi = .android,
            .os_version_min = .{ .semver = api_semver },
        },
        .x86_64 => .{
            .cpu_arch = .x86_64,
            .os_tag = .linux,
            .abi = .android,
            .os_version_min = .{ .semver = api_semver },
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

    // Collect wasm3 C source files
    var wasm3_files = std.ArrayListUnmanaged([]const u8).initCapacity(b.allocator, 16) catch @panic("OOM");
    {
        var dir = std.fs.cwd().openDir("deps/wasm3/source", .{ .iterate = true }) catch {
            @panic("deps/wasm3/source is missing; populate the wasm3 submodule first");
        };
        defer dir.close();
        var it = dir.iterate();
        while (it.next() catch @panic("failed to iterate deps/wasm3/source")) |entry| {
            if (entry.kind != .file) continue;
            if (!std.mem.endsWith(u8, entry.name, ".c")) continue;
            // Skip WASI, tracer, and uvwasi files — not needed on Android
            // and they reference stdout/WASI headers we don't have
            if (std.mem.indexOf(u8, entry.name, "wasi") != null) continue;
            if (std.mem.indexOf(u8, entry.name, "tracer") != null) continue;
            if (std.mem.indexOf(u8, entry.name, "libc") != null) continue;
            const full_path = b.pathJoin(&.{ "deps/wasm3/source", entry.name });
            wasm3_files.append(b.allocator, full_path) catch @panic("OOM");
        }
    }

    // NDK sysroot paths for libc
    const ndk_lib_dir = b.pathJoin(&.{
        android_ndk, "toolchains", "llvm", "prebuilt", "linux-x86_64", "sysroot", "usr", "lib",
        switch (arch) {
            .arm64 => "aarch64-linux-android",
            .x86_64 => "x86_64-linux-android",
        },
    });
    const ndk_api_lib_dir = b.pathJoin(&.{
        ndk_lib_dir,
        b.fmt("{d}", .{android_api}),
    });

    // Point Zig at the NDK's libc via --libc flag
    const libc_conf = switch (arch) {
        .arm64 => "libc-arm64.conf",
        .x86_64 => "libc-x86_64.conf",
    };
    b.libc_file = libc_conf;

    // Create root module for the Zig library
    const root_module = b.createModule(.{
        .root_source_file = b.path("src/jni_exports.zig"),
        .target = target,
        .optimize = optimize,
        .link_libc = true,
    });

    root_module.addIncludePath(.{ .cwd_relative = ndk_sysroot });
    root_module.addIncludePath(.{ .cwd_relative = ndk_target_include });
    root_module.addIncludePath(b.path("deps/openssl/include"));
    root_module.addIncludePath(b.path("deps/wasm3"));
    root_module.addIncludePath(b.path("deps/wasm3/source"));

    const lib = b.addLibrary(.{
        .linkage = .dynamic,
        .name = "calbot_zig",
        .root_module = root_module,
    });

    lib.addCSourceFiles(.{
        .files = wasm3_files.toOwnedSlice(b.allocator) catch @panic("OOM"),
        .flags = &.{
            "-std=c11",
            "-DM3_NO_DEBUG=1",
            "-Dd_m3HasWASI=0", // No WASI — no stdout dependency
            "-Dd_m3HasTracer=0", // No tracer output
            "-Dd_m3HasMetaWASI=0", // No meta WASI
        },
    });

    lib.addObjectFile(.{ .cwd_relative = b.pathJoin(&.{ openssl_lib_dir, "libcrypto.a" }) });

    // Link against the NDK sysroot libc
    lib.addLibraryPath(.{ .cwd_relative = ndk_api_lib_dir });
    lib.addLibraryPath(.{ .cwd_relative = ndk_lib_dir });
    lib.linkSystemLibrary2("c", .{ .preferred_link_mode = .dynamic });
    lib.linkSystemLibrary2("dl", .{ .preferred_link_mode = .dynamic });
    lib.linkSystemLibrary2("log", .{ .preferred_link_mode = .dynamic });

    b.installArtifact(lib);
}
