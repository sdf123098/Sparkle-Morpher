package com.micaftic.morpher;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.sun.jna.NativeLibrary;
import com.micaftic.morpher.core.api.PlatformAPI;
import com.micaftic.morpher.core.native.NativeArtifactVerifier;
import com.micaftic.morpher.core.native.NativeArtifactVerifier.NativeArtifact;
import com.micaftic.morpher.core.storage.AtomicFileMover;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class RuntimeAccelerationLoader {
    private static final int EXPECTED_NATIVE_ABI_VERSION = 3;

    private static boolean available = false;
    private static boolean loaded = false;
    private static boolean isAndroid = false;
    private static ErrorState lastError = null;

    private enum TargetPlatform {
        WINDOWS_X64("windows-x64", "ysm-core.dll", Path.of(System.getProperty("java.io.tmpdir"), "spm")),
        WINDOWS_X86("windows-x86", "ysm-core.dll", Path.of(System.getProperty("java.io.tmpdir"), "spm")),
        LINUX_X64("linux-x64", "libysm-core.so", Path.of(System.getProperty("user.home"), ".spm")),
        MACOS_X64("macos-x64", "libysm-core.dylib", Path.of(System.getProperty("user.home"), ".spm")),
        MACOS_ARM64("macos-arm64", "libysm-core.dylib", Path.of(System.getProperty("user.home"), ".spm")),
        ANDROID_ARM64("android-arm64", "libysm-core.so", null);

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

    private enum LibcType {UNSUPPORTED, GNU, BIONIC}

    private record ErrorState(Component component, String key, Object[] args, String logMsg) {
    }

    public static void init() throws IOException {
        if (PlatformAPI.isServer()) {
            available = true;
            loaded = false;
            YesSteveModel.LOGGER.info("Skipping native library loading on dedicated server");
            return;
        }

        // 开发 override（§11.2）：跳过 manifest 信任链，但保留 ABI 校验。
        String override = System.getenv("YSM_CORE_LIB");
        if (!StringUtil.isNullOrEmpty(override)) {
            if (loadNativeLib(override)) {
                loaded = true;
            }
            available = true;
            return;
        }

        String path = extractAndGetLibPath();
        if (path != null && loadNativeLib(path)) {
            loaded = true;
        }
        available = true;
    }

    private static @Nullable String extractAndGetLibPath() throws IOException {
        TargetPlatform platform = resolvePlatform();
        if (platform == null) return null;

        Path storageDir = platform.defaultStorage;
        if (platform == TargetPlatform.ANDROID_ARM64) {
            String androidRuntime = System.getenv("MOD_ANDROID_RUNTIME");
            if (androidRuntime == null) {
                setUnsupportedLauncherError();
                return null;
            }
            isAndroid = true;
            storageDir = Path.of(androidRuntime);
        }

        // §11 信任链：manifest 缺失 / 无当前平台条目 → 明确错误 + 回退 Java 路径。
        NativeArtifact artifact = loadManifestArtifact(platform.resDir);
        if (artifact == null) {
            return null;
        }

        Path targetFile = ensureDirectory(storageDir).resolve(platform.fileName).toAbsolutePath().normalize();

        // 已安装且 digest 匹配 → 直接复用（不重写、不联网）。
        if (Files.isRegularFile(targetFile) && NativeArtifactVerifier.verify(targetFile, artifact)) {
            return targetFile.toString();
        }

        byte[] data = readResource(platform.getResourcePath());
        if (data == null) {
            data = downloadFromReleases(platform);
            if (data == null) {
                setUnsatisfiedBuildError();
                return null;
            }
        }

        // §11.2：digest mismatch 禁止加载。
        if (!NativeArtifactVerifier.verify(data, artifact)) {
            String msg = "native digest mismatch for " + platform.resDir + "/" + platform.fileName
                    + ": expected " + artifact.sha256() + ", got " + NativeArtifactVerifier.sha256(data);
            YesSteveModel.LOGGER.error("Failed native trust chain: {}", msg);
            setUnsatisfiedRuntimeError(msg);
            return null;
        }

        // 原子安装（§11.2）：临时文件 + 原子替换，杜绝半截文件。
        installAtomically(storageDir, targetFile, data);
        return targetFile.toString();
    }

    /**
     * 解析打包的 native-manifest.json 并取当前平台信任记录。
     * 失败（缺失/解析错误/无条目）→ 记录明确错误，返回 null（回退 Java 路径）。
     */
    private static @Nullable NativeArtifact loadManifestArtifact(String platformKey) {
        try (InputStream in = YesSteveModel.class.getResourceAsStream("/native-manifest.json")) {
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
            YesSteveModel.LOGGER.error("Failed to parse native-manifest.json", e);
            setUnsatisfiedBuildError("native-manifest.json unreadable: " + e.getMessage());
            return null;
        }
    }

    private static void installAtomically(Path storageDir, Path targetFile, byte[] data) throws IOException {
        Path tmp = Files.createTempFile(storageDir, targetFile.getFileName().toString(), ".tmp");
        try {
            Files.write(tmp, data);
            AtomicFileMover.moveWithRetry(tmp, targetFile);
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }

    /** 资源缺失时从 GitHub Releases 下载（URL 与 mod 版本绑定，保持历史行为）。 */
    private static @Nullable byte[] downloadFromReleases(TargetPlatform platform) throws IOException {
        String version = "1.0.0";
        try {
            Class<?> platformClass = Class.forName("dev.architectury.platform.Platform");
            Object mod = platformClass.getMethod("getMod", String.class).invoke(null, "sparkle_morpher");
            if (mod != null) {
                version = (String) mod.getClass().getMethod("getVersion").invoke(mod);
            }
        } catch (Throwable t) {
            try {
                Class<?> modListClass = Class.forName("net.neoforged.fml.ModList");
                Object modList = modListClass.getMethod("get").invoke(null);
                Object optContainer = modListClass.getMethod("getModContainerById", String.class).invoke(modList, "sparkle_morpher");
                if (optContainer instanceof java.util.Optional<?> opt && opt.isPresent()) {
                    Object container = opt.get();
                    Object modInfo = container.getClass().getMethod("getModInfo").invoke(container);
                    Object modVersion = modInfo.getClass().getMethod("getVersion").invoke(modInfo);
                    version = modVersion.toString();
                }
            } catch (Throwable ignored) {}
        }

        if (version.contains("-")) {
            version = version.substring(0, version.indexOf('-'));
        }

        String ext = platform.fileName.substring(platform.fileName.lastIndexOf('.') + 1);
        String remoteFileName = "ysm-core-" + platform.resDir + "." + ext;
        String githubPath = "sdf123098/Sparkle-Morpher/releases/download/v" + version + "/" + remoteFileName;
        String downloadUrl = "https://github.com/" + githubPath;
        String mirrorUrl = "https://mirror.ghproxy.com/https://github.com/" + githubPath;

        YesSteveModel.LOGGER.info("Native library resource not found in jar, attempting download from mirror: {}", mirrorUrl);
        byte[] data = downloadLibrary(mirrorUrl);
        if (data == null) {
            YesSteveModel.LOGGER.info("Mirror download failed, falling back to direct GitHub URL: {}", downloadUrl);
            data = downloadLibrary(downloadUrl);
        }
        return data;
    }

    private static @Nullable TargetPlatform resolvePlatform() {
        boolean isX86_64 = SystemUtils.OS_ARCH.equals("amd64") || SystemUtils.OS_ARCH.equals("x86_64");
        boolean isAarch64 = SystemUtils.OS_ARCH.equals("aarch64");

        if (SystemUtils.IS_OS_WINDOWS) {
            return isX86_64 ? TargetPlatform.WINDOWS_X64 : TargetPlatform.WINDOWS_X86;
        }

        if (SystemUtils.IS_OS_LINUX) {
            LibcType libc = detectLibcType();
            if (libc == LibcType.GNU) return isX86_64 ? TargetPlatform.LINUX_X64 : null;
            if (libc == LibcType.BIONIC) return isAarch64 ? TargetPlatform.ANDROID_ARM64 : null;
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

    private static boolean loadNativeLib(String path) {
        if(System.getProperty("OYSM_DISABLE_SMID") != null) {
            return false;
        }
        try {
            long start = System.currentTimeMillis();
            YesSteveModel.LOGGER.info("Begin load native library");
            System.load(path);
            int actualAbi = GeoModel.nGetAbiVersion();
            if (actualAbi != EXPECTED_NATIVE_ABI_VERSION) {
                String msg = "native ABI mismatch: expected " + EXPECTED_NATIVE_ABI_VERSION + ", got " + actualAbi;
                YesSteveModel.LOGGER.error("Failed to load native lib: {}", msg);
                setUnsatisfiedRuntimeError(msg);
                return false;
            }
            YesSteveModel.LOGGER.info("Successfully load native library in {}ms", System.currentTimeMillis() - start);
            return true;
        } catch (Throwable th) {
            YesSteveModel.LOGGER.error("Failed to load native lib: " + path, th);
            setUnsatisfiedRuntimeError(th.getMessage());
            return false;
        }
    }

    private static byte[] readResource(String path) throws IOException {
        URL url = YesSteveModel.class.getResource(path);
        if (url == null) return null;
        try (InputStream is = url.openStream()) {
            return IOUtils.toByteArray(is);
        }
    }

    private static Path ensureDirectory(Path path) {
        try {
            if (!Files.isDirectory(path)) Files.createDirectories(path);
            return path;
        } catch (Throwable th) {
            return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(YesSteveModel.MOD_ID).resolve("cache");
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
        String info = SystemUtils.OS_NAME + " " + SystemUtils.OS_ARCH;
        lastError = new ErrorState(Component.translatable("error.sparkle_morpher.unsatisfied_build", info), "error.sparkle_morpher.unsatisfied_build_ext", new Object[]{info}, "[SM] No build for platform: " + info);
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

    public static boolean isAvailable() {
        return available;
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static boolean isOnAndroid() {
        return isAndroid;
    }

    public static Component getErrorComponent() {
        return lastError != null ? lastError.component : null;
    }

    public static String getErrorMessage() {
        return lastError != null ? lastError.logMsg : null;
    }

    private static byte[] downloadLibrary(String urlString) {
        try {
            java.net.URL url = new java.net.URI(urlString).toURL();
            try (InputStream is = url.openStream()) {
                return IOUtils.toByteArray(is);
            }
        } catch (Throwable t) {
            YesSteveModel.LOGGER.error("Failed to download native library from: " + urlString, t);
            return null;
        }
    }
}