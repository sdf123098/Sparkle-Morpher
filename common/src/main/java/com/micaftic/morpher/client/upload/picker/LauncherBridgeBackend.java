package com.micaftic.morpher.client.upload.picker;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.upload.ModelImportFilePicker;
import dev.architectury.platform.Platform;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 启动器文件夹桥后端（1.2.7 §24.6，自 {@code ModelImportFilePicker} 等价搬运）。
 *
 * <p>仅 Android 运行时启用：把启动器可访问的导入目录 URI 通过 {@code CallbackBridge.nativeClipboard}
 * 交给宿主，然后轮询该目录，对稳定下来的文件 / 模型文件夹完成导入。桥接状态字段集中在本类，
 * 文件稳定性判定与清理逻辑原样保留。
 */
public final class LauncherBridgeBackend {
    private static final int CLIPBOARD_OPEN = 2002;
    private static final long LAUNCHER_BRIDGE_TIMEOUT_MS = 5 * 60_000L;
    private static final long LAUNCHER_BRIDGE_STABLE_MS = 750L;
    private static final long LAUNCHER_BRIDGE_AFTER_IMPORT_IDLE_MS = 2_000L;
    private static final Map<Path, FileStamp> launcherBridgeBaseline = new HashMap<>();
    private static final Map<Path, FileCandidate> launcherBridgeCandidates = new HashMap<>();
    private static final Map<Path, DirectoryStamp> launcherBridgeDirectoryBaseline = new HashMap<>();
    private static final Map<Path, DirectoryCandidate> launcherBridgeDirectoryCandidates = new HashMap<>();
    static volatile Path launcherBridgeImportDir = null;
    private static volatile long launcherBridgePollUntilMs = 0L;
    private static volatile long launcherBridgeLastActivityMs = 0L;
    private static volatile boolean launcherBridgeImportedAny = false;

    static boolean tryStartLauncherBridgeImport() {
        if (!FilePickerCoordinator.isAndroidRuntime()) {
            FilePickerCoordinator.rememberPickerProbe("launcher folder bridge skipped outside Android runtime");
            return false;
        }
        try {
            Path dir = getLauncherBridgeImportDir();
            Files.createDirectories(dir);
            Path normalizedDir = dir.toAbsolutePath().normalize();
            cleanupLauncherBridgeImportFiles(normalizedDir);
            Map<Path, FileStamp> baseline = snapshotImportFiles(normalizedDir);
            Map<Path, DirectoryStamp> directoryBaseline = snapshotModelFolders(normalizedDir);
            String uri = toDirectoryFileUri(normalizedDir);
            Method nativeClipboard = Class.forName("org.lwjgl.glfw.CallbackBridge")
                    .getDeclaredMethod("nativeClipboard", int.class, byte[].class);
            nativeClipboard.invoke(null, CLIPBOARD_OPEN, uri.getBytes(StandardCharsets.UTF_8));
            launcherBridgeImportDir = normalizedDir;
            launcherBridgePollUntilMs = FilePickerCoordinator.nowMillis() + LAUNCHER_BRIDGE_TIMEOUT_MS;
            launcherBridgeLastActivityMs = FilePickerCoordinator.nowMillis();
            launcherBridgeImportedAny = false;
            launcherBridgeBaseline.clear();
            launcherBridgeBaseline.putAll(baseline);
            launcherBridgeCandidates.clear();
            launcherBridgeDirectoryBaseline.clear();
            launcherBridgeDirectoryBaseline.putAll(directoryBaseline);
            launcherBridgeDirectoryCandidates.clear();
            FilePickerCoordinator.clearLastError();
            FilePickerCoordinator.rememberPickerProbe("launcher folder bridge opened: " + uri);
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            FilePickerCoordinator.rememberPickerProbe("launcher folder bridge unavailable: " + FilePickerCoordinator.safeMessage(e));
            return false;
        } catch (InvocationTargetException e) {
            FilePickerCoordinator.rememberPickerProbe("launcher folder bridge failed: " + FilePickerCoordinator.safeMessage(e));
            return false;
        } catch (IOException e) {
            FilePickerCoordinator.lastError = FilePickerCoordinator.error("gui.sparkle_morpher.import.error.open_picker", FilePickerCoordinator.safeMessage(e));
            YesSteveModel.LOGGER.warn("[SM] Failed to prepare launcher import folder", e);
            return false;
        } catch (Throwable t) {
            FilePickerCoordinator.rememberPickerProbe("launcher folder bridge failed: " + FilePickerCoordinator.safeMessage(t));
            return false;
        }
    }

    static void pollLauncherBridgeImports() {
        Path dir = launcherBridgeImportDir;
        if (dir == null || launcherBridgePollUntilMs <= 0L) {
            return;
        }
        long now = FilePickerCoordinator.nowMillis();
        if (now > launcherBridgePollUntilMs) {
            stopLauncherBridgeImport();
            if (!launcherBridgeImportedAny) {
                FilePickerCoordinator.setError(Component.translatable("gui.sparkle_morpher.import.error.launcher_bridge_timeout"));
            }
            return;
        }
        try (Stream<Path> paths = Files.walk(dir, 3)) {
            for (Path path : paths
                    .filter(Files::isRegularFile)
                    .filter(FilePickerCoordinator::isImportPath)
                    .toList()) {
                tryCompleteLauncherBridgeFile(path.toAbsolutePath().normalize(), now);
            }
            try (Stream<Path> folders = Files.walk(dir, 3)) {
                for (Path path : folders
                        .filter(Files::isDirectory)
                        .filter(path -> !path.equals(dir))
                        .filter(FilePickerCoordinator::isModelFolder)
                        .toList()) {
                    tryCompleteLauncherBridgeFolder(path.toAbsolutePath().normalize(), now);
                }
            }
            if (launcherBridgeImportedAny && now - launcherBridgeLastActivityMs > LAUNCHER_BRIDGE_AFTER_IMPORT_IDLE_MS) {
                stopLauncherBridgeImport();
            }
        } catch (IOException e) {
            stopLauncherBridgeImport();
            FilePickerCoordinator.setError(FilePickerCoordinator.error("gui.sparkle_morpher.import.error.read_file", FilePickerCoordinator.safeMessage(e)));
            YesSteveModel.LOGGER.warn("[SM] Failed to scan launcher import folder", e);
        }
    }

    private static boolean tryCompleteLauncherBridgeFile(Path path, long now) {
        try {
            FileStamp stamp = fileStamp(path);
            FileStamp baseline = launcherBridgeBaseline.get(path);
            if (stamp.equals(baseline)) {
                return false;
            }
            FileCandidate candidate = launcherBridgeCandidates.get(path);
            if (candidate == null || !candidate.stamp().equals(stamp)) {
                launcherBridgeCandidates.put(path, new FileCandidate(stamp, now));
                launcherBridgeLastActivityMs = now;
                return false;
            }
            if (now - candidate.firstSeenMs() < LAUNCHER_BRIDGE_STABLE_MS) {
                return false;
            }
            byte[] data;
            try (InputStream in = Files.newInputStream(path)) {
                data = FilePickerCoordinator.readAllBytes(in);
            }
            FilePickerCoordinator.complete(new ModelImportFilePicker.PickedFile(path.getFileName().toString(), data));
            launcherBridgeBaseline.put(path, stamp);
            launcherBridgeCandidates.remove(path);
            deleteLauncherBridgeFile(path);
            launcherBridgeImportedAny = true;
            launcherBridgeLastActivityMs = now;
            return true;
        } catch (Throwable t) {
            stopLauncherBridgeImport();
            FilePickerCoordinator.setError(FilePickerCoordinator.error("gui.sparkle_morpher.import.error.read_selected", FilePickerCoordinator.safeMessage(t)));
            YesSteveModel.LOGGER.warn("[SM] Failed to read launcher-imported file {}", path, t);
            return true;
        }
    }

    private static boolean tryCompleteLauncherBridgeFolder(Path path, long now) {
        try {
            DirectoryStamp stamp = directoryStamp(path);
            DirectoryStamp baseline = launcherBridgeDirectoryBaseline.get(path);
            if (stamp.equals(baseline)) {
                return false;
            }
            DirectoryCandidate candidate = launcherBridgeDirectoryCandidates.get(path);
            if (candidate == null || !candidate.stamp().equals(stamp)) {
                launcherBridgeDirectoryCandidates.put(path, new DirectoryCandidate(stamp, now));
                launcherBridgeLastActivityMs = now;
                return false;
            }
            if (now - candidate.firstSeenMs() < LAUNCHER_BRIDGE_STABLE_MS) {
                return false;
            }
            FilePickerCoordinator.complete(ModelImportFilePicker.packDirectory(path));
            launcherBridgeDirectoryBaseline.put(path, stamp);
            launcherBridgeDirectoryCandidates.remove(path);
            launcherBridgeImportedAny = true;
            launcherBridgeLastActivityMs = now;
            return true;
        } catch (Throwable t) {
            stopLauncherBridgeImport();
            FilePickerCoordinator.setError(FilePickerCoordinator.error("gui.sparkle_morpher.import.error.read_selected", FilePickerCoordinator.safeMessage(t)));
            YesSteveModel.LOGGER.warn("[SM] Failed to read launcher-imported folder {}", path, t);
            return true;
        }
    }

    private static void cleanupLauncherBridgeImportFiles(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir, 3)) {
            for (Path path : paths
                    .filter(Files::isRegularFile)
                    .filter(FilePickerCoordinator::isImportPath)
                    .toList()) {
                deleteLauncherBridgeFile(path.toAbsolutePath().normalize());
            }
        }
    }

    private static void deleteLauncherBridgeFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            YesSteveModel.LOGGER.warn("[SM] Failed to remove launcher import temp file {}", path, e);
        }
    }

    private static Map<Path, FileStamp> snapshotImportFiles(Path dir) throws IOException {
        Map<Path, FileStamp> result = new HashMap<>();
        try (Stream<Path> paths = Files.walk(dir, 3)) {
            for (Path path : paths
                    .filter(Files::isRegularFile)
                    .filter(FilePickerCoordinator::isImportPath)
                    .toList()) {
                Path normalized = path.toAbsolutePath().normalize();
                result.put(normalized, fileStamp(normalized));
            }
        }
        return result;
    }

    private static Map<Path, DirectoryStamp> snapshotModelFolders(Path dir) throws IOException {
        Map<Path, DirectoryStamp> result = new HashMap<>();
        try (Stream<Path> paths = Files.walk(dir, 3)) {
            for (Path path : paths
                    .filter(Files::isDirectory)
                    .filter(path -> !path.equals(dir))
                    .filter(FilePickerCoordinator::isModelFolder)
                    .toList()) {
                Path normalized = path.toAbsolutePath().normalize();
                result.put(normalized, directoryStamp(normalized));
            }
        }
        return result;
    }

    private static Path getLauncherBridgeImportDir() {
        return Platform.getConfigFolder().resolve(YesSteveModel.MOD_ID).resolve("import");
    }

    private static String toDirectoryFileUri(Path dir) {
        String uri = dir.toUri().toString();
        return uri.endsWith("/") ? uri : uri + "/";
    }

    private static FileStamp fileStamp(Path path) throws IOException {
        return new FileStamp(Files.size(path), Files.getLastModifiedTime(path).toMillis());
    }

    private static DirectoryStamp directoryStamp(Path dir) throws IOException {
        long newest = Files.getLastModifiedTime(dir).toMillis();
        long fileCount = 0L;
        long totalSize = 0L;
        try (Stream<Path> paths = Files.walk(dir, FilePickerCoordinator.MAX_FOLDER_DEPTH)) {
            for (var iterator = paths.filter(Files::isRegularFile).iterator(); iterator.hasNext(); ) {
                Path path = iterator.next();
                fileCount++;
                totalSize += Files.size(path);
                newest = Math.max(newest, Files.getLastModifiedTime(path).toMillis());
            }
        }
        return new DirectoryStamp(fileCount, totalSize, newest);
    }

    static synchronized void stopLauncherBridgeImport() {
        launcherBridgeImportDir = null;
        launcherBridgePollUntilMs = 0L;
        launcherBridgeLastActivityMs = 0L;
        launcherBridgeImportedAny = false;
        launcherBridgeBaseline.clear();
        launcherBridgeCandidates.clear();
        launcherBridgeDirectoryBaseline.clear();
        launcherBridgeDirectoryCandidates.clear();
    }

    private record FileStamp(long size, long modifiedMillis) {
    }

    private record FileCandidate(FileStamp stamp, long firstSeenMs) {
    }

    private record DirectoryStamp(long fileCount, long totalSize, long modifiedMillis) {
    }

    private record DirectoryCandidate(DirectoryStamp stamp, long firstSeenMs) {
    }

    private LauncherBridgeBackend() {
    }
}
