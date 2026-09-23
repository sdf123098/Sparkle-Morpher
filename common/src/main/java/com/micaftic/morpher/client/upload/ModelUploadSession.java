package com.micaftic.morpher.client.upload;

import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.core.api.network.state.CloudState;
import com.micaftic.morpher.core.api.network.upload.ModelUploadTransport;
import com.micaftic.morpher.legacy.compat.LegacyCompatModelFormat;
import com.micaftic.morpher.util.DigestUtil;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** One asynchronous upload to the configured SPM Cloud instance. */
public final class ModelUploadSession {
    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private static volatile ModelUploadSession instance;
    private static volatile int lastMaxTotalBytes = 128 * 1024 * 1024;
    private final String modelId;
    private final String fileName;
    private final Path source;
    private final boolean deleteSourceOnCompletion;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile State state = State.STARTING;
    private volatile long sentBytes;
    private volatile Component message = Component.translatable("gui.sparkle_morpher.import.state.importing");

    private ModelUploadSession(String modelId, String fileName, Path source,
                               boolean deleteSourceOnCompletion) {
        this.modelId = modelId;
        this.fileName = fileName;
        this.source = source;
        this.deleteSourceOnCompletion = deleteSourceOnCompletion;
    }

    public static ModelUploadSession getInstance() {
        return instance;
    }

    /** Existing screens may pass bytes; the HTTP body is still streamed from a temporary file. */
    public static synchronized Component start(String modelId, String fileName, byte[] data) {
        return start(modelId, fileName, data, true, "PRIVATE");
    }

    public static synchronized Component start(String modelId, String fileName, byte[] data,
                                               boolean syncSelectionOnComplete) {
        return start(modelId, fileName, data, syncSelectionOnComplete, "PRIVATE");
    }

    public static synchronized Component start(String modelId, String fileName, byte[] data,
                                               boolean syncSelectionOnComplete, String visibility) {
        if (data == null || data.length == 0) {
            return Component.translatable("gui.sparkle_morpher.import.error.empty_file");
        }
        try {
            Path temporary = Files.createTempFile("spm-cloud-upload-", extensionFor(fileName));
            Files.write(temporary, data);
            Component error = start(modelId, fileName, temporary, syncSelectionOnComplete, visibility, true);
            if (error != null) Files.deleteIfExists(temporary);
            return error;
        } catch (IOException error) {
            return Component.translatable("gui.sparkle_morpher.import.error.local_storage");
        }
    }

    /** Starts a Cloud upload without materializing the source in memory. */
    public static synchronized Component start(String modelId, String fileName, Path source,
                                               boolean syncSelectionOnComplete) {
        return start(modelId, fileName, source, syncSelectionOnComplete, "PRIVATE", false);
    }

    private static Component start(String modelId, String fileName, Path source,
                                   boolean ignoredSyncSelectionOnComplete,
                                   String visibility,
                                   boolean deleteSourceOnCompletion) {
        if (instance != null && !instance.isTerminal()) {
            return Component.translatable("gui.sparkle_morpher.import.error.in_progress");
        }
        ModelUploadTransport uploadTransport = CloudUploadRuntime.transport();
        if (uploadTransport == null || !CloudState.isAvailable()) {
            return Component.translatable("gui.sparkle_morpher.import.error.cloud_unavailable");
        }
        if (modelId == null || modelId.isBlank() || fileName == null || fileName.isBlank()) {
            return Component.translatable("gui.sparkle_morpher.import.error.invalid_model_id_or_hash");
        }
        final long totalBytes;
        final String sha256;
        try {
            totalBytes = Files.size(source);
            if (totalBytes <= 0 || totalBytes > Integer.MAX_VALUE) {
                return Component.translatable("gui.sparkle_morpher.import.error.empty_file");
            }
            sha256 = DigestUtil.sha256Hex(source);
        } catch (IOException error) {
            return Component.translatable("gui.sparkle_morpher.import.error.local_storage");
        }
        if (totalBytes > lastMaxTotalBytes) {
            return Component.translatable("gui.sparkle_morpher.import.error.server_limit", formatBytes(lastMaxTotalBytes));
        }
        ImportKind kind = ImportKind.fromFileName(fileName);
        if (kind == ImportKind.UNKNOWN) {
            return Component.translatable("gui.sparkle_morpher.import.error.invalid_extension");
        }
        try {
            if (kind == ImportKind.YSM && LegacyCompatModelFormat.detectCryptoVersion(Files.readAllBytes(source)) == -1) {
                return Component.translatable("gui.sparkle_morpher.import.error.invalid_ysm");
            }
        } catch (IOException error) {
            return Component.translatable("gui.sparkle_morpher.import.error.local_storage");
        }

        ModelUploadSession session = new ModelUploadSession(modelId, fileName, source, deleteSourceOnCompletion);
        instance = session;
        notifyListeners();
        ModelUploadTransport.UploadMetadata metadata = new ModelUploadTransport.UploadMetadata(
                modelId, fileName, kind.wireName, sha256, totalBytes, visibility);
        uploadTransport.upload(metadata, source, session::onProgress, session.cancelled::get)
                .whenComplete((result, error) -> session.complete(result, error));
        return null;
    }

    public static int getLastMaxTotalBytes() {
        return lastMaxTotalBytes;
    }

    public static boolean hasServerLimits() {
        return false;
    }

    public static int getLastChunksPerTick() {
        return 0;
    }

    public static String formatBytes(int bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }

    public static synchronized void clearIfTerminal() {
        if (instance != null && instance.isTerminal()) {
            instance = null;
            notifyListeners();
        }
    }

    public static void addListener(Listener listener) {
        listeners.add(listener);
    }

    public static void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public static synchronized void failCurrent(Component reason) {
        ModelUploadSession session = instance;
        if (session == null || session.isTerminal()) return;
        session.cancelled.set(true);
        session.fail(reason);
        notifyListeners();
    }

    private static void notifyListeners() {
        ModelUploadSession session = instance;
        for (Listener listener : listeners) listener.onSessionUpdate(session);
    }

    private void onProgress(long sent, long total) {
        sentBytes = Math.min(Math.max(sent, 0), total);
        state = State.UPLOADING;
        message = Component.translatable("gui.sparkle_morpher.import.state.importing");
        notifyListeners();
    }

    private synchronized void complete(ModelUploadTransport.UploadResult result, Throwable error) {
        try {
            if (error != null) {
                fail(Component.literal(rootMessage(error)));
            } else if (cancelled.get()) {
                fail(Component.translatable("gui.sparkle_morpher.resource_station.cancelled"));
            } else {
                sentBytes = result.byteLength();
                state = State.COMPLETED;
                message = Component.translatable("gui.sparkle_morpher.import.state.imported_as", result.assetId());
                // Cloud assets are not selected through the legacy Minecraft packet channel.
                ClientModelManager.onUploadedModelImported(result.assetId());
            }
        } finally {
            if (deleteSourceOnCompletion) {
                try { Files.deleteIfExists(source); } catch (IOException ignored) { }
            }
            notifyListeners();
        }
    }

    private void fail(Component reason) {
        state = State.FAILED;
        message = reason;
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static String extensionFor(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) return ".zip";
        if (lower.endsWith(".bbmodel")) return ".bbmodel";
        if (lower.endsWith(".gltf")) return ".gltf";
        if (lower.endsWith(".glb")) return ".glb";
        return ".ysm";
    }

    public boolean isTerminal() {
        return state == State.COMPLETED || state == State.FAILED;
    }

    public State getState() { return state; }
    public String getModelId() { return modelId; }
    public String getFileName() { return fileName; }

    public int getTotalBytes() {
        try { return (int) Math.min(Integer.MAX_VALUE, Files.size(source)); }
        catch (IOException ignored) { return 0; }
    }

    public int getSentBytes() { return (int) Math.min(Integer.MAX_VALUE, sentBytes); }
    public Component getMessage() { return message; }

    public float getProgress() {
        long total;
        try { total = Files.size(source); } catch (IOException ignored) { return 0f; }
        return total <= 0 ? 0f : Math.min(1f, (float) sentBytes / total);
    }

    public enum State { STARTING, UPLOADING, FINISHING, COMPLETED, FAILED }

    private enum ImportKind {
        YSM("ysm"), ZIP("zip"), BBMODEL("bbmodel"), GLTF("gltf"), GLB("glb"), UNKNOWN("");
        private final String wireName;
        ImportKind(String wireName) { this.wireName = wireName; }
        private static ImportKind fromFileName(String fileName) {
            if (fileName == null) return UNKNOWN;
            String lower = fileName.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".ysm")) return YSM;
            if (lower.endsWith(".zip")) return ZIP;
            if (lower.endsWith(".bbmodel")) return BBMODEL;
            if (lower.endsWith(".gltf")) return GLTF;
            if (lower.endsWith(".glb")) return GLB;
            return UNKNOWN;
        }
    }

    public interface Listener { void onSessionUpdate(ModelUploadSession session); }
}
