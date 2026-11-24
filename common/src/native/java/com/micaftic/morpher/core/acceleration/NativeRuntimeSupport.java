package com.micaftic.morpher.core.acceleration;

import com.micaftic.morpher.core.api.PlatformAPI;
import com.micaftic.morpher.core.nativeutil.NativeArtifactVerifier;
import com.micaftic.morpher.core.nativeutil.NativeArtifactVerifier.NativeArtifact;
import com.micaftic.morpher.core.storage.AtomicFileMover;
import com.sun.jna.NativeLibrary;
import dev.spm.core.acceleration.CpuComputeProvider;
import dev.spm.core.acceleration.Isa;
import dev.spm.core.nativejni.NativeComputeProvider;
import dev.spm.core.render.cpu.JavaComputeProvider;
import net.minecraft.network.chat.Component;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Verifies, extracts, and loads the bundled {@code spm-core 1.0.0} Rust SIMD native library
 * via {@link NativeComputeProvider}. Falls back to {@link JavaComputeProvider} on error or on
 * CurseForge builds.
 */
public final class NativeRuntimeSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger("sparkle_morpher");
    private static final CpuComputeProvider JAVA_PROVIDER = new JavaComputeProvider();

    private static volatile CpuComputeProvider computeProvider = JAVA_PROVIDER;
    private static volatile boolean available = false;
    private static volatile boolean loaded = false;
    private static volatile boolean isAndroid = false;
    private static volatile ErrorState lastError = null;

    private enum TargetPlatform {
        WINDOWS_X64("windows-x64", "spm_core_native.dll", Path.of(System.getProperty("java.io.tmpdir"), "spm")),
        WINDOWS_X86("windows-x86", "spm_core_native.dll", Path.of(System.getProperty("java.io.tmpdir"), "spm")),
        LINUX_X64("linux-x64", "libspm_core_native.so", Path.of(System.getProperty("user.home"), ".spm")),
        LINUX_ARM64("linux-arm64", "libspm_core_native.so", Path.of(System.getProperty("user.home"), ".spm")),
        LINUX_RISCV64("linux-riscv64", "libspm_core_native.so", Path.of(System.getProperty("user.home"), ".spm")),
        LINUX_LOONGARCH64("linux-loongarch64", "libspm_core_native.so", Path.of(System.getProperty("user.home"), ".spm")),
        MACOS_X64("macos-x64", "libspm_core_native.dylib", Path.of(System.getProperty("user.home"), ".spm")),
        MACOS_ARM64("macos-arm64", "libspm_core_native.dylib", Path.of(System.getProperty("user.home"), ".spm")),
         WINDOWS_ARM64("windows-arm64", "spm_core_native.dll", Path.of(System.getProperty("java.io.tmpdir"), "spm")),
        ANDROID_ARM64("android-arm64", "libspm_core_native.so", null),
        ANDROID_ARM32("android-arm32", "libspm_core_native.so", null);

        final String resDir;
        final String fileName;
        final Path defaultStorage;

        TargetPlatform(String resDir, String fileName, Path defaultStorage) {
            this.resDir = resDir;
            this.fileName = fileName;
            this.defaultStorage = defaultStorage;
        }

        String getResourcePath() {
            return "/natives/" + resDir + "/" + fileName;
        }
    }

    private enum LibcType { UNSUPPORTED, GNU, BIONIC }

    private record ErrorState(Component component, String key, Object[] args, String logMsg) {
    }

    private NativeRuntimeSupport() {
    }

    public static void init(Supplier<Path> configDirSupplier) {
        loaded = false;
        computeProvider = JAVA_PROVIDER;
        try {
            initializeNative(configDirSupplier);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Native acceleration initialization failed; continuing with Java rendering", e);
            setUnsatisfiedRuntimeError(e.toString());
            computeProvider = JAVA_PROVIDER;
        } finally {
            available = true;
        }
    }

    private static void initializeNative(Supplier<Path> configDirSupplier) throws IOException {
        if (PlatformAPI.isServer()) {
            available = true;
            loaded = false;
            LOGGER.info("Skipping native library loading on dedicated server");
            return;
        }
        if (isNativeDisabled()) {
            LOGGER.info("Native acceleration disabled via system property; using JavaComputeProvider");
            return;
        }

        String override = System.getenv("SPM_CORE_LIB");
        if (override != null && !override.isEmpty()) {
            String expectedSha = verifyOverrideNative(override);
            if (expectedSha != null && loadNativeLib(Path.of(override), expectedSha)) {
                loaded = true;
            }
            return;
        }

        TargetPlatform platform = resolvePlatform();
        if (platform == null) {
            return;
        }
        NativeArtifact artifact = loadManifestArtifact(platform.resDir);
        if (artifact == null) {
            return;
        }
        Path libPath = extractLibPath(platform, artifact, configDirSupplier);
        if (libPath != null && loadNativeLib(libPath, artifact.sha256())) {
            loaded = true;
        }
    }

    private static boolean isNativeDisabled() {
        return System.getProperty("spm.disable.native") != null
                || System.getProperty("OYSM_DISABLE_SMID") != null;
    }

    private static @Nullable String verifyOverrideNative(@NotNull String overridePath) {
        Path file = Path.of(overridePath);
        if ("1".equals(System.getenv("SPM_CORE_LIB_FORCE"))) {
            LOGGER.warn("SPM_CORE_LIB_FORCE=1: skipping manifest verification for native override {}", overridePath);
            try {
                return NativeArtifactVerifier.sha256(file);
            } catch (IOException e) {
                setUnsatisfiedRuntimeError("Unreadable native override: " + e.getMessage());
                return null;
            }
        }
        TargetPlatform platform = resolvePlatform();
        NativeArtifact artifact = platform == null ? null : loadManifestArtifact(platform.resDir);
        if (artifact != null && NativeArtifactVerifier.verify(file, artifact)) {
            LOGGER.info("Native override verified against native-manifest version={} platform={}",
                    artifact.version(), platform.resDir);
            return artifact.sha256();
        }
        String detail;
        if (artifact == null) {
            detail = "native-manifest unavailable for the current platform";
        } else {
            String actual;
            try {
                actual = Files.isRegularFile(file) ? NativeArtifactVerifier.sha256(file) : "<file not found>";
            } catch (IOException e) {
                actual = "<unreadable: " + e.getMessage() + ">";
            }
            detail = "expected sha256 " + artifact.sha256() + ", got " + actual;
        }
        String msg = "SPM_CORE_LIB override is not a trusted spm-core native build (" + detail + "): "
                + overridePath + ". Refusing to load an untrusted library. Set SPM_CORE_LIB_FORCE=1 only if you built it yourself.";
        LOGGER.error("Failed native trust chain: {}", msg);
        setUnsatisfiedRuntimeError(msg);
        return null;
    }

    private static @Nullable Path extractLibPath(TargetPlatform platform, NativeArtifact artifact,
                                                 Supplier<Path> configDirSupplier) throws IOException {
        Path storageDir = platform.defaultStorage;
        if (platform == TargetPlatform.ANDROID_ARM64 || platform == TargetPlatform.ANDROID_ARM32) {
            String androidRuntime = System.getenv("MOD_ANDROID_RUNTIME");
            if (androidRuntime == null) {
                setUnsupportedLauncherError();
                return null;
            }
            isAndroid = true;
            storageDir = Path.of(androidRuntime);
        }

        storageDir = ensureDirectory(storageDir, configDirSupplier)
                .resolve(platform.resDir)
                .resolve(artifact.sha256().toLowerCase(Locale.ROOT));
        Files.createDirectories(storageDir);
        Path targetFile = storageDir.resolve(platform.fileName).toAbsolutePath().normalize();

        if (Files.isRegularFile(targetFile) && NativeArtifactVerifier.verify(targetFile, artifact)) {
            LOGGER.info("Reusing cached native {} (manifest version={})", targetFile, artifact.version());
            return targetFile;
        }
        if (Files.isRegularFile(targetFile)) {
            LOGGER.warn("Cached native at {} does not match manifest (version={}); re-installing",
                    targetFile, artifact.version());
        }

        byte[] data = readResource(platform.getResourcePath());
        if (data == null) {
            setUnsatisfiedBuildError();
            return null;
        }
        if (!NativeArtifactVerifier.verify(data, artifact)) {
            String msg = "native digest mismatch for " + platform.resDir + "/" + platform.fileName
                    + ": expected " + artifact.sha256() + ", got " + NativeArtifactVerifier.sha256(data);
            LOGGER.error("Failed native trust chain: {}", msg);
            setUnsatisfiedRuntimeError(msg);
            return null;
        }
        return installAtomically(storageDir, targetFile, data, artifact);
    }

    private static @Nullable NativeArtifact loadManifestArtifact(String platformKey) {
        try (InputStream in = NativeRuntimeSupport.class.getResourceAsStream("/native-manifest.json")) {
            if (in == null) {
                setUnsatisfiedBuildError("native-manifest.json not found in jar");
                return null;
            }
            List<NativeArtifact> artifacts = NativeArtifactVerifier.parseManifest(in);
            NativeArtifact artifact = NativeArtifactVerifier.findArtifact(artifacts, platformKey).orElse(null);
            if (artifact == null) {
                setUnsatisfiedBuildError("native-manifest.json has no entry for platform: " + platformKey);
            }
            return artifact;
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Failed to parse native-manifest.json", e);
            setUnsatisfiedBuildError("native-manifest.json unreadable: " + e.getMessage());
            return null;
        }
    }

    private static Path installAtomically(Path storageDir, Path targetFile, byte[] data, NativeArtifact artifact) throws IOException {
        Path tmp = Files.createTempFile(storageDir, targetFile.getFileName().toString(), ".tmp");
        try {
            Files.write(tmp, data);
            try {
                AtomicFileMover.moveWithRetry(tmp, targetFile);
                return targetFile;
            } catch (IOException e) {
                if (NativeArtifactVerifier.verify(targetFile, artifact)) {
                    return targetFile;
                }
                Path privateDir = Files.createTempDirectory(storageDir, "instance-");
                Path privateFile = privateDir.resolve(targetFile.getFileName());
                privateDir.toFile().deleteOnExit();
                privateFile.toFile().deleteOnExit();
                AtomicFileMover.moveWithRetry(tmp, privateFile);
                LOGGER.warn("Native cache entry could not be replaced; using isolated native {}", privateFile);
                return privateFile;
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static @Nullable TargetPlatform resolvePlatform() {
        boolean isX86_64 = "amd64".equals(SystemUtils.OS_ARCH) || "x86_64".equals(SystemUtils.OS_ARCH);
        boolean isAarch64 = "aarch64".equals(SystemUtils.OS_ARCH) || "arm64".equals(SystemUtils.OS_ARCH);
        boolean isRiscv64 = "riscv64".equals(SystemUtils.OS_ARCH) || "riscv64gc".equals(SystemUtils.OS_ARCH);
        boolean isLoongArch64 = "loongarch64".equals(SystemUtils.OS_ARCH) || "loong64".equals(SystemUtils.OS_ARCH);
        boolean isArm32 = "arm".equals(SystemUtils.OS_ARCH) || "armv7".equals(SystemUtils.OS_ARCH)
                || "armv7l".equals(SystemUtils.OS_ARCH) || "armv8l".equals(SystemUtils.OS_ARCH);

        if (SystemUtils.IS_OS_WINDOWS) {
            // Windows on ARM is a distinct platform, not a 32-bit x86 host: falling through to
            // WINDOWS_X86 there would hand the loader a 32-bit DLL it can never map, and the only
            // symptom would be a silent drop to the Java writer.
            if (isAarch64) return TargetPlatform.WINDOWS_ARM64;
            if (isX86_64) return TargetPlatform.WINDOWS_X64;
            return TargetPlatform.WINDOWS_X86;
        }
        if (SystemUtils.IS_OS_LINUX) {
            LibcType libc = detectLibcType();
            if (libc == LibcType.GNU) {
                if (isX86_64) return TargetPlatform.LINUX_X64;
                if (isAarch64) return TargetPlatform.LINUX_ARM64;
                // 信创 hosts: RISC-V and LoongArch. Both run a scalar kernel, which is still a
                // real speedup over the Java writer; there is no vector kernel for either.
                if (isRiscv64) return TargetPlatform.LINUX_RISCV64;
                if (isLoongArch64) return TargetPlatform.LINUX_LOONGARCH64;
                setUnsupportedPlatformError("Linux (Unsupported Architecture: " + SystemUtils.OS_ARCH + ")");
                return null;
            }
            if (libc == LibcType.BIONIC) {
                if (isAarch64) return TargetPlatform.ANDROID_ARM64;
                // 32-bit Android (armeabi-v7a) is a real target with its own cross-compiled
                // library. Any other BIONIC architecture has no build, so it keeps falling back to
                // the Java writer rather than being handed a library it cannot map.
                if (isArm32) return TargetPlatform.ANDROID_ARM32;
                return null;
            }
            setUnsupportedPlatformError("Linux (Unknown Libc)");
            return null;
        }
        if (SystemUtils.IS_OS_MAC) {
            if (isAarch64) return TargetPlatform.MACOS_ARM64;
            if (isX86_64) return TargetPlatform.MACOS_X64;
            setUnsupportedPlatformError("macOS (Unsupported Architecture: " + SystemUtils.OS_ARCH + ")");
            return null;
        }
        setUnsupportedPlatformError(SystemUtils.OS_NAME + " " + SystemUtils.OS_ARCH);
        return null;
    }

    private static boolean loadNativeLib(Path path, String sha256) {
        try {
            long start = System.currentTimeMillis();
            LOGGER.info("Loading spm-core native library: {}", path);
            NativeComputeProvider provider = NativeComputeProvider.load(path, sha256.toLowerCase(Locale.ROOT), Isa.AUTO);
            computeProvider = provider;
            LOGGER.info("Loaded spm-core native library ({}) in {}ms", provider.backend(), System.currentTimeMillis() - start);
            return true;
        } catch (Throwable th) {
            LOGGER.error("Failed to load spm-core native library: " + path, th);
            setUnsatisfiedRuntimeError(th.getMessage() != null ? th.getMessage() : th.toString());
            computeProvider = JAVA_PROVIDER;
            return false;
        }
    }

    private static @Nullable byte[] readResource(String path) throws IOException {
        URL url = NativeRuntimeSupport.class.getResource(path);
        if (url == null) return null;
        try (InputStream is = url.openStream()) {
            return IOUtils.toByteArray(is);
        }
    }

    private static Path ensureDirectory(Path path, Supplier<Path> configDirSupplier) {
        try {
            if (!Files.isDirectory(path)) Files.createDirectories(path);
            return path;
        } catch (Throwable th) {
            return configDirSupplier.get().resolve("sparkle_morpher").resolve("cache");
        }
    }

    private static LibcType detectLibcType() {
        try {
            NativeLibrary libc = NativeLibrary.getInstance(com.sun.jna.Platform.C_LIBRARY_NAME);
            if (libc != null) {
                if (hasFunction(libc, "android_set_abort_message")) return LibcType.BIONIC;
                if (hasFunction(libc, "gnu_get_libc_version")) return LibcType.GNU;
            }
        } catch (Throwable ignored) {
        }
        return LibcType.UNSUPPORTED;
    }

    private static boolean hasFunction(NativeLibrary lib, String name) {
        try {
            return lib.getFunction(name) != null;
        } catch (Throwable e) {
            return false;
        }
    }

    private static void setUnsupportedPlatformError(@Nullable String detail) {
        String info = detail != null ? detail : SystemUtils.OS_NAME + " " + SystemUtils.OS_ARCH;
        lastError = new ErrorState(Component.translatable("error.sparkle_morpher.unsupported_platform", info), "error.sparkle_morpher.unsupported_platform_ext", new Object[]{info}, "[SM] Unsupported platform: " + info);
    }

    private static void setUnsatisfiedRuntimeError(@NotNull String msg) {
        lastError = new ErrorState(Component.translatable("error.sparkle_morpher.unsatisfied_runtime_env", msg), "error.sparkle_morpher.unsatisfied_runtime_env_ext", new Object[]{msg}, "[SM] Runtime error: " + msg);
    }

    private static void setUnsatisfiedBuildError() {
        setUnsatisfiedBuildError(SystemUtils.OS_NAME + " " + SystemUtils.OS_ARCH);
    }

    private static void setUnsatisfiedBuildError(@NotNull String detail) {
        lastError = new ErrorState(Component.translatable("error.sparkle_morpher.unsatisfied_build", detail), "error.sparkle_morpher.unsatisfied_build_ext", new Object[]{detail}, "[SM] Build error: " + detail);
    }

    private static void setUnsupportedLauncherError() {
        String fcl = System.getenv("FCL_VERSION_CODE");
        if (StringUtils.isNotBlank(fcl)) {
            lastError = createLauncherError("FCL", "1.2.6.7");
            return;
        }
        String zalith = System.getenv("ZALITH_VERSION_CODE");
        if (StringUtils.isNotBlank(zalith)) {
            int ver = Integer.parseInt(zalith);
            lastError = (ver < 190000) ? createLauncherError("Zalith 1", "1.4.1.1") : createLauncherError("Zalith 2", "2.0.0_beta-20251118a");
            return;
        }
        lastError = new ErrorState(Component.translatable("error.sparkle_morpher.unsupported_launcher"), null, null, "[SM] Unsupported Launcher");
    }

    private static ErrorState createLauncherError(String name, String minVer) {
        return new ErrorState(Component.translatable("error.sparkle_morpher.old_launcher", name, minVer), "error.sparkle_morpher.old_launcher_ext", new Object[]{name, minVer}, "[SM] Old launcher version: " + name);
    }

    public static CpuComputeProvider computeProvider() {
        return computeProvider;
    }

    /**
     * Compiles a packed-skeleton evaluator for a model, or returns {@code null} when the native
     * path is not available.
     *
     * <p>This is an optimisation, never a requirement: a {@code null} return means the caller keeps
     * using {@code SpmCoreBoneMatrixWriter}'s Java implementation, which produces the same records.
     * The handle it returns is generation-encoded on the native side, so a model that is unloaded
     * and later reloaded can never pick up a stale handle by accident.
     *
     * @param parents one entry per bone: the parent index, or {@code -1} for a root
     * @param pivots three floats per bone, already scaled by {@code 0.0625}
     * @param glow one entry per bone: {@code 0} normal, {@code 1} full-bright
     */
    public static dev.spm.core.nativejni.NativePackedBoneEvaluator compilePacked(
            int[] parents, float[] pivots, int[] glow) {
        try {
            return dev.spm.core.nativejni.NativePackedBoneEvaluator.compile(parents, pivots, glow);
        } catch (Throwable th) {
            // Two cases land here and both mean "keep using the Java writer": the provider is not
            // ready (the library is absent or refused to load), or the native side rejected this
            // particular skeleton. The caller latches the attempt, so this is logged once per
            // model rather than once per frame.
            LOGGER.debug("Native packed skeleton unavailable; keeping the Java bone writer", th);
            return null;
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static boolean isOnAndroid() {
        return isAndroid;
    }

    public static @Nullable Component getErrorComponent() {
        return lastError != null ? lastError.component : null;
    }

    public static @Nullable String getErrorMessage() {
        return lastError != null ? lastError.logMsg : null;
    }
}
