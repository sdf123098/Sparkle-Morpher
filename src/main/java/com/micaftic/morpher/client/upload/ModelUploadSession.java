package com.micaftic.morpher.client.upload;

import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.C2SModelUploadChunkPacket;
import com.micaftic.morpher.network.message.C2SModelUploadFinishPacket;
import com.micaftic.morpher.network.message.C2SModelUploadStartPacket;
import com.micaftic.morpher.util.DigestUtil;
import com.micaftic.morpher.util.PerformanceProfiler;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import com.micaftic.morpher.core.legacy.YesModelUtils;

import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ModelUploadSession {
    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private static volatile ModelUploadSession instance;
    private static volatile boolean serverLimitsKnown = false;
    private static volatile int lastMaxTotalBytes = 16_777_216;
    private static volatile int lastChunksPerTick = 4;

    private final String modelId;
    private final String fileName;
    private final byte[] data;
    private final String sha256;
    private volatile State state = State.STARTING;
    private volatile long uploadId = 0L;
    private volatile int chunkSize = 32_000;
    private volatile int chunksPerTick = 4;
    private volatile int nextOffset = 0;
    private volatile Component message = Component.empty();

    private ModelUploadSession(String modelId, String fileName, byte[] data) {
        this.modelId = modelId;
        this.fileName = fileName;
        this.data = data;
        this.sha256 = DigestUtil.sha256Hex(data);
    }

    public static ModelUploadSession getInstance() {
        return instance;
    }

    public static synchronized Component start(String modelId, byte[] data) {
        return start(modelId, modelId + ".ysm", data);
    }

    public static synchronized Component start(String modelId, String fileName, byte[] data) {
        if (instance != null && !instance.isTerminal()) {
            return Component.translatable("gui.sparkle_morpher.import.error.in_progress");
        }
        if (data.length == 0) {
            return Component.translatable("gui.sparkle_morpher.import.error.empty_file");
        }
        if (!NetworkHandler.isClientConnected() || !ClientModelManager.isOysmServer()) {
            return Component.translatable("gui.sparkle_morpher.import.error.waiting_handshake");
        }
        if (!ClientModelManager.isAllowUpload()) {
            return Component.translatable("gui.sparkle_morpher.import.error.disabled_by_server");
        }
        if (serverLimitsKnown && data.length > lastMaxTotalBytes) {
            return Component.translatable("gui.sparkle_morpher.import.error.server_limit", formatBytes(lastMaxTotalBytes));
        }
        ImportKind kind = ImportKind.fromFileName(fileName);
        if (kind == ImportKind.UNKNOWN) {
            return Component.translatable("gui.sparkle_morpher.import.error.invalid_extension");
        }
        if (kind == ImportKind.YSM && !isYsmFile(data)) {
            return Component.translatable("gui.sparkle_morpher.import.error.invalid_ysm");
        }
        if (kind == ImportKind.ZIP && !isZipFile(data)) {
            return Component.translatable("gui.sparkle_morpher.import.error.invalid_zip");
        }
        ModelUploadSession session = new ModelUploadSession(modelId, fileName, data);
        instance = session;
        notifyListeners();
        NetworkHandler.sendToServer(new C2SModelUploadStartPacket(modelId, fileName == null ? "" : fileName, data.length, session.sha256));
        return null;
    }

    public static boolean hasServerLimits() {
        return serverLimitsKnown;
    }

    public static int getLastMaxTotalBytes() {
        return lastMaxTotalBytes;
    }

    public static int getLastChunksPerTick() {
        return lastChunksPerTick;
    }

    public static String formatBytes(int bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.2f MB", bytes / (1024.0 * 1024.0));
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

    public static synchronized void onStartAck(long uploadId, byte status, int chunkSize, int maxTotalBytes, int chunksPerTick, String message) {
        if (maxTotalBytes > 0) {
            lastMaxTotalBytes = maxTotalBytes;
        }
        if (chunksPerTick > 0) {
            lastChunksPerTick = chunksPerTick;
        }
        serverLimitsKnown = true;
        ModelUploadSession session = instance;
        if (session == null || session.state != State.STARTING) {
            return;
        }
        if (status != 0) {
            session.fail(appendServerMessage(getRequestErrorText(status), message));
            return;
        }
        session.uploadId = uploadId;
        session.chunkSize = Math.max(1, chunkSize);
        session.chunksPerTick = Math.max(1, chunksPerTick);
        session.state = State.UPLOADING;
        session.message = Component.translatable("gui.sparkle_morpher.import.state.server_uploading");
        notifyListeners();
    }

    public static synchronized void onResult(long uploadId, byte status, String modelId, long h1, long h2, String message) {
        ModelUploadSession session = instance;
        if (session == null || session.uploadId != uploadId) {
            return;
        }
        if (status == 0) {
            session.state = State.COMPLETED;
            session.message = Component.translatable("gui.sparkle_morpher.import.state.imported_as", modelId);
            ClientModelManager.onUploadedModelAvailable(modelId);
        } else {
            session.fail(appendServerMessage(getResponseErrorText(status), message));
        }
        notifyListeners();
    }

    public static void tickCurrent() {
        ModelUploadSession session = instance;
        if (session != null) {
            session.tick();
        }
    }

    public static synchronized void failCurrent(Component reason) {
        ModelUploadSession session = instance;
        if (session == null || session.isTerminal()) {
            return;
        }
        session.fail(reason);
        notifyListeners();
    }

    private static void notifyListeners() {
        ModelUploadSession session = instance;
        for (Listener listener : listeners) {
            listener.onSessionUpdate(session);
        }
    }

    private static boolean isYsmFile(byte[] data) {
        return YesModelUtils.getYsmCryptoVersion(data) != -1;
    }

    private static boolean isZipFile(byte[] data) {
        return data.length >= 4
                && data[0] == 0x50
                && data[1] == 0x4b
                && (data[2] == 0x03 || data[2] == 0x05 || data[2] == 0x07)
                && (data[3] == 0x04 || data[3] == 0x06 || data[3] == 0x08);
    }

    private static Component getRequestErrorText(byte status) {
        return switch (status) {
            case 1 -> Component.translatable("gui.sparkle_morpher.import.error.model_exists");
            case 2 -> Component.translatable("gui.sparkle_morpher.import.error.file_exceeds_server_limit");
            case 3 -> Component.translatable("gui.sparkle_morpher.import.error.no_permission");
            case 4 -> Component.translatable("gui.sparkle_morpher.import.error.server_busy");
            case 5 -> Component.translatable("gui.sparkle_morpher.import.error.invalid_model_id_or_hash");
            case 6 -> Component.translatable("gui.sparkle_morpher.import.error.disabled_by_server");
            default -> Component.translatable("gui.sparkle_morpher.import.error.status", status);
        };
    }

    private static Component getResponseErrorText(byte status) {
        return switch (status) {
            case 1 -> Component.translatable("gui.sparkle_morpher.import.error.hash_mismatch");
            case 2 -> Component.translatable("gui.sparkle_morpher.import.error.server_parse_failed");
            case 3 -> Component.translatable("gui.sparkle_morpher.import.error.server_storage");
            case 4 -> Component.translatable("gui.sparkle_morpher.import.error.session_expired");
            case 5 -> Component.translatable("gui.sparkle_morpher.import.error.incomplete_upload");
            case 6 -> Component.translatable("gui.sparkle_morpher.import.error.server_rejected_write");
            case 8 -> Component.translatable("gui.sparkle_morpher.import.error.scan_not_visible");
            default -> Component.translatable("gui.sparkle_morpher.import.error.status", status);
        };
    }

    private static Component appendServerMessage(Component base, String serverMessage) {
        if (serverMessage == null || serverMessage.isEmpty() || isKnownServerMessage(serverMessage)) {
            return base;
        }
        MutableComponent result = base.copy();
        result.append(Component.literal(": "));
        result.append(Component.literal(serverMessage));
        return result;
    }

    private static boolean isKnownServerMessage(String serverMessage) {
        return switch (serverMessage.trim()) {
            case "Model import disabled",
                 "No import permission",
                 "Invalid model id or hash",
                 "File exceeds server limit",
                 "Model ID already exists",
                 "Session expired",
                 "Incomplete upload",
                 "Hash mismatch",
                 "Server failed to cache model",
                 "Server rejected write" -> true;
            default -> false;
        };
    }

    private synchronized void tick() {
        if (state != State.UPLOADING) {
            return;
        }
        long perfStart = PerformanceProfiler.start();
        int budget = Math.max(1, chunksPerTick);
        int chunks = 0;
        int bytes = 0;
        for (int i = 0; i < budget && nextOffset < data.length; i++) {
            int end = Math.min(nextOffset + chunkSize, data.length);
            int length = end - nextOffset;
            NetworkHandler.sendToServer(new C2SModelUploadChunkPacket(uploadId, nextOffset, data, nextOffset, length));
            nextOffset = end;
            chunks++;
            bytes += length;
        }
        PerformanceProfiler.logElapsed("client_upload_tick", modelId, perfStart,
                "chunks=" + chunks + " bytes=" + bytes + " sent=" + nextOffset + "/" + data.length);
        if (nextOffset >= data.length) {
            state = State.FINISHING;
            message = Component.translatable("gui.sparkle_morpher.import.state.verifying");
            NetworkHandler.sendToServer(new C2SModelUploadFinishPacket(uploadId));
        }
        notifyListeners();
    }

    private void fail(Component reason) {
        state = State.FAILED;
        message = reason;
    }

    public boolean isTerminal() {
        return state == State.COMPLETED || state == State.FAILED;
    }

    public State getState() {
        return state;
    }

    public String getModelId() {
        return modelId;
    }

    public String getFileName() {
        return fileName;
    }

    public int getTotalBytes() {
        return data.length;
    }

    public int getSentBytes() {
        return Math.min(nextOffset, data.length);
    }

    public Component getMessage() {
        return message;
    }

    public float getProgress() {
        if (data.length == 0 || state == State.COMPLETED) {
            return 1f;
        }
        return (float) getSentBytes() / data.length;
    }

    public enum State {STARTING, UPLOADING, FINISHING, COMPLETED, FAILED}

    private enum ImportKind {
        YSM,
        ZIP,
        BBMODEL,
        UNKNOWN;

        private static ImportKind fromFileName(String fileName) {
            if (fileName == null) {
                return UNKNOWN;
            }
            String lower = fileName.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".ysm")) {
                return YSM;
            }
            if (lower.endsWith(".zip")) {
                return ZIP;
            }
            if (lower.endsWith(".bbmodel")) {
                return BBMODEL;
            }
            return UNKNOWN;
        }
    }

    public interface Listener {
        void onSessionUpdate(ModelUploadSession session);
    }
}
