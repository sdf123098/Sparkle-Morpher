const std = @import("std");

const PlatformSpec = struct {
    name: []const u8,
    arch_os_abi: []const u8,
    extra_flags: []const []const u8 = &.{},
    needs_ndk: bool = false,
};

const platforms = [_]PlatformSpec{
    .{
        .name = "windows-x64",
        .arch_os_abi = "x86_64-windows-gnu",
        .extra_flags = &.{"-march=x86-64-v3"},
    },
    .{
        .name = "windows-x86",
        .arch_os_abi = "x86-windows-gnu",
        .extra_flags = &.{ "-mavx2", "-mfma", "-mbmi2" },
    },
    .{
        .name = "linux-x64",
        .arch_os_abi = "x86_64-linux-gnu",
        .extra_flags = &.{"-march=x86-64-v3"},
    },
    .{
        .name = "macos-x64",
        .arch_os_abi = "x86_64-macos-none",
        .extra_flags = &.{"-march=x86-64-v3"},
    },
    .{
        .name = "macos-arm64",
        .arch_os_abi = "aarch64-macos-none",
        .extra_flags = &.{},
    },
    .{
        .name = "android-arm64",
        .arch_os_abi = "aarch64-linux-android",
        .extra_flags = &.{},
        .needs_ndk = true,
    },
};

pub fn build(b: *std.Build) void {
    if (b.release_mode == .off and b.user_input_options.get("optimize") == null) {
        b.release_mode = .fast;
    }
    const optimize = b.standardOptimizeOption(.{});

    const platform_filter = b.option(
        []const u8,
        "platform",
        "Target platform: windows-x64, windows-x86, linux-x64, macos-x64, macos-arm64, android-arm64, or all",
    ) orelse "all";

    const ndk_root: ?[]const u8 = b.option(
        []const u8,
        "android-ndk",
        "Path to Android NDK root (overrides $ANDROID_NDK_ROOT). Required for android-* targets.",
    ) orelse b.graph.environ_map.get("ANDROID_NDK_ROOT") orelse
        b.graph.environ_map.get("ANDROID_NDK_HOME");

    const android_api = b.option(
        u32,
        "android-api",
        "Android API level (default 21).",
    ) orelse 21;

    const base_flags = [_][]const u8{
        "-std=c++17",
        "-fvisibility=hidden",
        "-fno-exceptions",
        "-fno-rtti",
        "-ffp-contract=fast",
        "-fno-math-errno",
        "-fno-trapping-math",
        "-Wno-unused-command-line-argument",
        "-Wno-unknown-pragmas",
    };

    var matched = false;
    for (platforms) |p| {
        if (!std.mem.eql(u8, platform_filter, "all") and
            !std.mem.eql(u8, platform_filter, p.name)) continue;
        matched = true;

        if (p.needs_ndk and ndk_root == null) {
            if (!std.mem.eql(u8, platform_filter, "all")) {
                std.debug.print(
                    "skipping {s}: ANDROID_NDK_ROOT not set and -Dandroid-ndk=... not provided\n",
                    .{p.name},
                );
            }
            continue;
        }

        var arch_os_abi_buf: [128]u8 = undefined;
        const arch_os_abi = if (p.needs_ndk)
            std.fmt.bufPrint(&arch_os_abi_buf, "{s}.{d}", .{ p.arch_os_abi, android_api }) catch unreachable
        else
            p.arch_os_abi;

        const query = std.Target.Query.parse(.{
            .arch_os_abi = arch_os_abi,
        }) catch |err| {
            std.debug.print("invalid target {s}: {s}\n", .{ arch_os_abi, @errorName(err) });
            continue;
        };
        const target = b.resolveTargetQuery(query);

        if (p.needs_ndk) {
            buildAndroid(b, p, ndk_root.?, android_api, optimize, &base_flags) catch |err| {
                std.debug.print("android build setup failed: {s}\n", .{@errorName(err)});
            };
            continue;
        }

        const mod = b.createModule(.{
            .target = target,
            .optimize = optimize,
            .link_libcpp = true,
            .strip = optimize != .Debug,
            .pic = true,
        });

        var flags = std.ArrayList([]const u8).initCapacity(
            b.allocator,
            base_flags.len + p.extra_flags.len,
        ) catch unreachable;
        flags.appendSliceAssumeCapacity(&base_flags);
        flags.appendSliceAssumeCapacity(p.extra_flags);

        mod.addIncludePath(b.path("third_party/jni"));
        mod.addIncludePath(b.path("third_party/sse2neon"));

        mod.addCSourceFile(.{
            .file = b.path("dllmain.cpp"),
            .flags = flags.items,
            .language = .cpp,
        });

        const lib = b.addLibrary(.{
            .name = "ysm-core",
            .linkage = .dynamic,
            .root_module = mod,
        });

        const install = b.addInstallArtifact(lib, .{
            .dest_dir = .{ .override = .{ .custom = p.name } },
        });

        const platform_step = b.step(p.name, b.fmt("Build {s} library", .{p.name}));
        platform_step.dependOn(&install.step);
        b.getInstallStep().dependOn(&install.step);
    }

    if (!matched) {
        std.debug.print(
            "unknown platform: '{s}'. Use one of: windows-x64, windows-x86, linux-x64, macos-x64, macos-arm64, android-arm64, all\n",
            .{platform_filter},
        );
    }
}

fn ndkHostTag() []const u8 {
    return switch (@import("builtin").os.tag) {
        .windows => "windows-x86_64",
        .linux => "linux-x86_64",
        .macos => "darwin-x86_64",
        else => "linux-x86_64",
    };
}

fn buildAndroid(
    b: *std.Build,
    p: PlatformSpec,
    ndk_root: []const u8,
    android_api: u32,
    optimize: std.builtin.OptimizeMode,
    base_flags: []const []const u8,
) !void {
    const host = ndkHostTag();
    const clang_exe = b.fmt(
        "{s}/toolchains/llvm/prebuilt/{s}/bin/aarch64-linux-android{d}-clang++.cmd",
        .{ ndk_root, host, android_api },
    );

    const out_name = "libysm-core.so";

    const cmd = b.addSystemCommand(&.{clang_exe});
    cmd.addArgs(base_flags);
    cmd.addArg("-shared");
    cmd.addArg("-fPIC");
    cmd.addArg(switch (optimize) {
        .Debug => "-O0",
        .ReleaseSafe => "-O2",
        .ReleaseFast => "-O3",
        .ReleaseSmall => "-Os",
    });
    if (optimize != .Debug) cmd.addArg("-s");
    cmd.addArg("-static-libstdc++");
    cmd.addArg("-Wl,--build-id=none");
    cmd.addArg("-Wl,--no-undefined");
    cmd.addArg("-Wl,-z,noexecstack");
    cmd.addArg("-llog");
    cmd.addArg("-I");
    cmd.addDirectoryArg(b.path("third_party/jni"));
    cmd.addArg("-I");
    cmd.addDirectoryArg(b.path("third_party/sse2neon"));
    cmd.addArg("-x");
    cmd.addArg("c++");
    cmd.addFileArg(b.path("dllmain.cpp"));
    cmd.addArg("-o");
    const out_lib = cmd.addOutputFileArg(out_name);

    const install = b.addInstallFileWithDir(out_lib, .{ .custom = p.name }, out_name);
    const platform_step = b.step(p.name, b.fmt("Build {s} library", .{p.name}));
    platform_step.dependOn(&install.step);
    b.getInstallStep().dependOn(&install.step);
}
