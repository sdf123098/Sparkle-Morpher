package com.micaftic.morpher.client.gui.resource;

import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.upload.ModelUploadSession;
import com.micaftic.morpher.model.ServerModelManager;
import com.micaftic.morpher.network.NetworkHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

public final class ResourceDownloadManager {
    private static final int HISTORY_LIMIT = 128;
    private static final long SERVER_UPLOAD_START_TIMEOUT_MS = 15_000L;
    private static final long SERVER_UPLOAD_STALL_TIMEOUT_MS = 30_000L;
    private static final long SERVER_UPLOAD_VERIFY_TIMEOUT_MS = 90_000L;
    private static final ExecutorService DOWNLOAD_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "SM Resource Download");
        thread.setDaemon(true);
        return thread;
    });
    private static final Object LOCK = new Object();
    private static final ArrayDeque<DownloadTask> QUEUE = new ArrayDeque<>();
    private static final List<DownloadTask> HISTORY = new ArrayList<>();
    private static final CopyOnWriteArrayList<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private static DownloadTask currentTask;
    private static boolean downloadLoading;
    private static Component status = Component.empty();
    private static ChatFormatting statusColor = ChatFormatting.GRAY;

    static {
        ModelUploadSession.addListener(ResourceDownloadManager::onUploadSessionUpdate);
    }

    private ResourceDownloadManager() {
    }

    public static boolean enqueue(ModelRepoEntry entry, ResourceStationConfig.State config) {
        boolean added;
        synchronized (LOCK) {
            added = enqueueLocked(entry, config);
        }
        if (added) {
            notifyListeners();
            processNextDownload();
        }
        return added;
    }

    public static int enqueueAll(List<ModelRepoEntry> entries, ResourceStationConfig.State config) {
        int added = 0;
        synchronized (LOCK) {
            for (ModelRepoEntry entry : entries) {
                if (enqueueLocked(entry, config)) {
                    added++;
                }
            }
        }
        if (added > 0) {
            notifyListeners();
            processNextDownload();
        }
        return added;
    }

    public static boolean isQueued(ModelRepoEntry entry) {
        synchronized (LOCK) {
            return isQueuedLocked(entry);
        }
    }

    public static void tick() {
        syncCurrentUploadSession();
        processNextDownload();
    }

    public static void addListener(Runnable listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(Runnable listener) {
        LISTENERS.remove(listener);
    }

    public static Snapshot snapshot() {
        synchronized (LOCK) {
            List<TaskSnapshot> unfinishedTasks = new ArrayList<>();
            if (currentTask != null) {
                unfinishedTasks.add(snapshot(currentTask));
            }
            unfinishedTasks.addAll(QUEUE.stream().map(ResourceDownloadManager::snapshot).toList());
            List<TaskSnapshot> finishedTasks = HISTORY.stream().map(ResourceDownloadManager::snapshot).toList();
            long done = HISTORY.stream().filter(task -> task.state == TaskState.DONE).count();
            long failed = HISTORY.stream().filter(task -> task.state == TaskState.FAILED).count();
            return new Snapshot(currentTask == null ? null : snapshot(currentTask), unfinishedTasks, finishedTasks,
                    QUEUE.size(), done, failed, status, statusColor);
        }
    }

    public static void clearFinished() {
        synchronized (LOCK) {
            HISTORY.clear();
            status = Component.translatable("gui.sparkle_morpher.resource_station.finished_cleared");
            statusColor = ChatFormatting.GRAY;
        }
        notifyListeners();
    }

    public static boolean cancelCurrent() {
        TaskSnapshot current = snapshot().currentTask();
        return current != null && cancel(current.id());
    }

    public static boolean cancel(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return false;
        }
        DownloadTask cancelledTask = null;
        CompletableFuture<byte[]> downloadFuture = null;
        boolean cancelUpload = false;
        synchronized (LOCK) {
            if (currentTask != null && Objects.equals(currentTask.id(), taskId)) {
                cancelledTask = currentTask;
                cancelUpload = cancelledTask.state == TaskState.UPLOADING;
                downloadFuture = cancelledTask.downloadFuture;
                currentTask = null;
                downloadLoading = false;
            } else {
                Iterator<DownloadTask> iterator = QUEUE.iterator();
                while (iterator.hasNext()) {
                    DownloadTask task = iterator.next();
                    if (Objects.equals(task.id(), taskId)) {
                        cancelledTask = task;
                        iterator.remove();
                        break;
                    }
                }
            }
            if (cancelledTask == null) {
                return false;
            }
            cancelledTask.cancelled = true;
            cancelledTask.state = TaskState.CANCELLED;
            cancelledTask.message = cancelMessage();
            status = cancelledTask.message;
            statusColor = ChatFormatting.GRAY;
            HISTORY.add(cancelledTask);
            trimHistoryLocked();
        }
        if (downloadFuture != null) {
            downloadFuture.cancel(true);
        }
        if (cancelUpload) {
            ModelUploadSession.failCurrent(cancelMessage());
        }
        notifyListeners();
        processNextDownload();
        return true;
    }

    private static boolean enqueueLocked(ModelRepoEntry entry, ResourceStationConfig.State config) {
        if (isQueuedLocked(entry)) {
            return false;
        }
        DownloadTask task = new DownloadTask(entry, config);
        QUEUE.add(task);
        status = Component.translatable("gui.sparkle_morpher.resource_station.queued", entry.name());
        statusColor = ChatFormatting.YELLOW;
        return true;
    }

    private static boolean isQueuedLocked(ModelRepoEntry entry) {
        if (currentTask != null && Objects.equals(currentTask.entry.url(), entry.url())) {
            return true;
        }
        return QUEUE.stream().anyMatch(task -> Objects.equals(task.entry.url(), entry.url()));
    }

    private static void processNextDownload() {
        ModelUploadSession.clearIfTerminal();
        ModelUploadSession existing = ModelUploadSession.getInstance();
        if (existing != null && !existing.isTerminal()) {
            return;
        }
        DownloadTask task;
        synchronized (LOCK) {
            if (currentTask != null || downloadLoading) {
                return;
            }
            task = QUEUE.poll();
            if (task == null) {
                return;
            }
            currentTask = task;
            downloadLoading = true;
            task.state = TaskState.DOWNLOADING;
            task.progress = 0f;
            task.message = Component.translatable("gui.sparkle_morpher.resource_station.downloading", task.entry.name());
            status = task.message;
            statusColor = ChatFormatting.YELLOW;
        }
        notifyListeners();
        CompletableFuture<byte[]> downloadFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return ModelRepoClient.download(task.entry, task.config, new ModelRepoClient.ProgressListener() {
                    private String host = "";

                    @Override
                    public boolean isCancelled() {
                        synchronized (LOCK) {
                            return task.cancelled || currentTask != task;
                        }
                    }

                    @Override
                    public void onProgress(int downloaded, int total) {
                        onProgress(downloaded, total, 0L);
                    }

                    @Override
                    public void onProgress(int downloaded, int total, long bytesPerSecond) {
                        int progressTotal = progressTotal(total, task.entry.size());
                        synchronized (LOCK) {
                            if (task.cancelled || currentTask != task) {
                                return;
                            }
                            task.progress = progressTotal > 0 ? Math.min(1f, (float) downloaded / progressTotal) : task.progress;
                            String bytes = ModelUploadSession.formatBytes(downloaded) + (total > 0 ? "/" + ModelUploadSession.formatBytes(total) : "");
                            String speed = bytesPerSecond > 0 ? " " + ModelRepoClient.formatSpeed(bytesPerSecond) : "";
                            String source = this.host.isBlank() ? "" : " @" + this.host;
                            task.message = Component.literal(bytes + speed + source);
                            status = task.message;
                            statusColor = ChatFormatting.YELLOW;
                        }
                        notifyListeners();
                    }

                    @Override
                    public void onCandidate(String url, int index, int total) {
                        this.host = ModelRepoClient.hostName(url);
                        synchronized (LOCK) {
                            if (task.cancelled || currentTask != task) {
                                return;
                            }
                            task.message = Component.translatable("gui.sparkle_morpher.resource_station.try_source", index, total, this.host);
                            status = task.message;
                            statusColor = ChatFormatting.YELLOW;
                        }
                        notifyListeners();
                    }
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, DOWNLOAD_EXECUTOR).orTimeout(taskTimeoutMs(task.config), TimeUnit.MILLISECONDS);
        synchronized (LOCK) {
            if (currentTask == task && !task.cancelled) {
                task.downloadFuture = downloadFuture;
            } else {
                downloadFuture.cancel(true);
            }
        }
        downloadFuture.whenComplete((data, error) ->
                ((Executor) Minecraft.getInstance()).execute(() -> onDownloadFinished(task, data, error)));
    }

    private static void onDownloadFinished(DownloadTask task, byte[] data, Throwable error) {
        synchronized (LOCK) {
            if (currentTask != task || task.cancelled) {
                return;
            }
            downloadLoading = false;
        }
        if (error instanceof CancellationException) {
            finishTask(task, TaskState.CANCELLED, cancelMessage());
            return;
        }
        if (error != null) {
            finishTask(task, TaskState.FAILED, Component.translatable("gui.sparkle_morpher.resource_station.error", rootMessage(error)));
            return;
        }
        String modelId = stripKnownImportExtension(ModelRepoClient.safeModelId(task.entry));
        synchronized (LOCK) {
            if (currentTask != task) {
                return;
            }
            task.state = TaskState.IMPORTING;
            task.progress = Math.max(task.progress, 1f);
            task.message = Component.translatable("gui.sparkle_morpher.import.state.local_importing", modelId);
            status = task.message;
            statusColor = ChatFormatting.YELLOW;
        }
        notifyListeners();
        CompletableFuture.runAsync(() -> {
                    try {
                        saveDownloadedModel(task.entry.fileName(), modelId, data);
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                }, DOWNLOAD_EXECUTOR)
                .whenComplete((ignored, saveError) ->
                        ((Executor) Minecraft.getInstance()).execute(() -> onLocalSaveFinished(task, modelId, data, saveError)));
    }

    private static void onLocalSaveFinished(DownloadTask task, String modelId, byte[] data, Throwable saveError) {
        synchronized (LOCK) {
            if (currentTask != task || task.cancelled) {
                return;
            }
        }
        if (saveError != null) {
            finishTask(task, TaskState.FAILED, Component.translatable("gui.sparkle_morpher.resource_station.save_failed", rootMessage(saveError)));
            return;
        }
        ClientModelManager.importLocalModel(modelId, task.entry.fileName(), data, localError -> onLocalImportFinished(task, modelId, data, localError));
    }

    private static void onLocalImportFinished(DownloadTask task, String modelId, byte[] data, Component localError) {
        synchronized (LOCK) {
            if (currentTask != task || task.cancelled) {
                return;
            }
        }
        if (localError != null) {
            finishTask(task, TaskState.FAILED, localError);
            return;
        }
        if (!canUploadToServer()) {
            finishTask(task, TaskState.DONE, Component.translatable("gui.sparkle_morpher.resource_station.saved_local", modelId));
            return;
        }
        synchronized (LOCK) {
            if (currentTask != task || task.cancelled) {
                return;
            }
            task.state = TaskState.UPLOADING;
            task.progress = 0f;
            task.uploadStartedAtMs = System.currentTimeMillis();
            task.lastUploadProgressAtMs = task.uploadStartedAtMs;
            task.lastUploadSentBytes = 0;
            task.uploadFinishingAtMs = 0;
            task.message = Component.translatable("gui.sparkle_morpher.import.state.server_starting");
            status = task.message;
            statusColor = ChatFormatting.YELLOW;
        }
        notifyListeners();
        Component startError = ModelUploadSession.start(modelId, task.entry.fileName(), data);
        if (startError != null) {
            if (!canUploadToServer()) {
                finishTask(task, TaskState.DONE, Component.translatable("gui.sparkle_morpher.resource_station.saved_local", modelId));
            } else {
                finishTask(task, TaskState.FAILED, startError);
            }
        }
    }

    private static boolean canUploadToServer() {
        return NetworkHandler.isClientConnected() && ClientModelManager.isOysmServer() && ClientModelManager.isAllowUpload();
    }

    private static void saveDownloadedModel(String fileName, String modelId, byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            throw new IOException("Empty model file");
        }
        Path customRoot = ServerModelManager.CUSTOM.toAbsolutePath().normalize();
        String extension = extensionForFileName(fileName);
        Path target = ServerModelManager.CUSTOM.resolve(modelId + extension).normalize();
        Path absoluteTarget = target.toAbsolutePath().normalize();
        if (!absoluteTarget.startsWith(customRoot)) {
            throw new IOException("Rejected model path");
        }
        Files.createDirectories(absoluteTarget.getParent());
        Path temp = Files.createTempFile(absoluteTarget.getParent(), absoluteTarget.getFileName().toString(), ".tmp");
        boolean moved = false;
        try {
            Files.write(temp, data);
            try {
                Files.move(temp, absoluteTarget, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, absoluteTarget, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
            removeSiblingModelFiles(customRoot, modelId, absoluteTarget);
        } finally {
            if (!moved) {
                Files.deleteIfExists(temp);
            }
        }
    }

    private static void removeSiblingModelFiles(Path customRoot, String modelId, Path keepTarget) throws IOException {
        for (String extension : new String[]{".ysm", ".zip", ".bbmodel"}) {
            Path sibling = ServerModelManager.CUSTOM.resolve(modelId + extension).toAbsolutePath().normalize();
            if (sibling.startsWith(customRoot) && !sibling.equals(keepTarget)) {
                Files.deleteIfExists(sibling);
            }
        }
    }

    private static String extensionForFileName(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) {
            return ".zip";
        }
        if (lower.endsWith(".bbmodel")) {
            return ".bbmodel";
        }
        return ".ysm";
    }

    private static String stripKnownImportExtension(String modelId) {
        String lower = modelId == null ? "" : modelId.toLowerCase(Locale.ROOT);
        for (String extension : new String[]{".ysm", ".zip", ".bbmodel"}) {
            if (lower.endsWith(extension)) {
                return modelId.substring(0, modelId.length() - extension.length());
            }
        }
        return modelId;
    }

    private static void onUploadSessionUpdate(ModelUploadSession session) {
        if (session == null) {
            return;
        }
        DownloadTask task;
        synchronized (LOCK) {
            task = currentTask;
            if (task == null || task.state != TaskState.UPLOADING) {
                return;
            }
            updateCurrentTaskFromSessionLocked(task, session);
        }
        notifyListeners();
        if (session.getState() == ModelUploadSession.State.COMPLETED) {
            finishTask(task, TaskState.DONE, Component.translatable("gui.sparkle_morpher.resource_station.done"));
        } else if (session.getState() == ModelUploadSession.State.FAILED) {
            finishTask(task, TaskState.FAILED, session.getMessage());
        }
    }

    private static void syncCurrentUploadSession() {
        DownloadTask task;
        synchronized (LOCK) {
            task = currentTask;
            if (task == null || task.state != TaskState.UPLOADING) {
                return;
            }
        }
        ModelUploadSession session = ModelUploadSession.getInstance();
        if (session == null) {
            return;
        }
        onUploadSessionUpdate(session);
        failStalledUpload(task, session);
    }

    private static void updateCurrentTaskFromSessionLocked(DownloadTask task, ModelUploadSession session) {
        task.progress = session.getProgress();
        task.message = uploadMessage(session);
        if (session.getState() == ModelUploadSession.State.UPLOADING && session.getSentBytes() > task.lastUploadSentBytes) {
            task.lastUploadSentBytes = session.getSentBytes();
            task.lastUploadProgressAtMs = System.currentTimeMillis();
        }
        if (session.getState() == ModelUploadSession.State.FINISHING && task.uploadFinishingAtMs == 0) {
            task.uploadFinishingAtMs = System.currentTimeMillis();
        }
        status = task.message;
        statusColor = switch (session.getState()) {
            case COMPLETED -> ChatFormatting.GREEN;
            case FAILED -> ChatFormatting.RED;
            default -> ChatFormatting.YELLOW;
        };
    }

    private static Component uploadMessage(ModelUploadSession session) {
        return switch (session.getState()) {
            case STARTING -> Component.translatable("gui.sparkle_morpher.import.state.server_starting");
            case UPLOADING -> Component.translatable("gui.sparkle_morpher.import.state.server_uploading");
            default -> session.getMessage();
        };
    }

    private static void failStalledUpload(DownloadTask task, ModelUploadSession session) {
        long now = System.currentTimeMillis();
        if (session.getState() == ModelUploadSession.State.STARTING
                && task.uploadStartedAtMs > 0
                && now - task.uploadStartedAtMs > SERVER_UPLOAD_START_TIMEOUT_MS) {
            ModelUploadSession.failCurrent(Component.translatable("gui.sparkle_morpher.resource_station.upload_start_timeout"));
            return;
        }
        if (session.getState() == ModelUploadSession.State.UPLOADING
                && task.lastUploadProgressAtMs > 0
                && now - task.lastUploadProgressAtMs > SERVER_UPLOAD_STALL_TIMEOUT_MS) {
            ModelUploadSession.failCurrent(Component.translatable("gui.sparkle_morpher.resource_station.upload_stalled"));
            return;
        }
        if (session.getState() == ModelUploadSession.State.FINISHING
                && task.uploadFinishingAtMs > 0
                && now - task.uploadFinishingAtMs > SERVER_UPLOAD_VERIFY_TIMEOUT_MS) {
            ModelUploadSession.failCurrent(Component.translatable("gui.sparkle_morpher.resource_station.upload_stalled"));
        }
    }

    private static void finishTask(DownloadTask task, TaskState state, Component message) {
        synchronized (LOCK) {
            if (currentTask != task) {
                return;
            }
            task.state = state;
            task.message = message;
            task.progress = state == TaskState.DONE ? 1f : task.progress;
            status = message;
            statusColor = switch (state) {
                case DONE -> ChatFormatting.GREEN;
                case CANCELLED -> ChatFormatting.GRAY;
                default -> ChatFormatting.RED;
            };
            HISTORY.add(task);
            trimHistoryLocked();
            currentTask = null;
            downloadLoading = false;
        }
        notifyListeners();
        processNextDownload();
    }

    private static void trimHistoryLocked() {
        while (HISTORY.size() > HISTORY_LIMIT) {
            HISTORY.remove(0);
        }
    }

    private static TaskSnapshot snapshot(DownloadTask task) {
        return new TaskSnapshot(task.id(), task.entry.name(), task.entry.fileName(), task.state, task.progress, task.message);
    }

    private static Component cancelMessage() {
        return Component.translatable("gui.sparkle_morpher.resource_station.cancelled");
    }

    private static void notifyListeners() {
        for (Runnable listener : LISTENERS) {
            listener.run();
        }
    }

    private static long taskTimeoutMs(ResourceStationConfig.State config) {
        return Math.max(15_000L, config.timeoutMs() * 4L);
    }

    private static int progressTotal(int contentLength, long entrySize) {
        if (contentLength > 0) {
            return contentLength;
        }
        if (entrySize > 0 && entrySize <= Integer.MAX_VALUE) {
            return (int) entrySize;
        }
        return 0;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static class DownloadTask {
        private final ModelRepoEntry entry;
        private final ResourceStationConfig.State config;
        private TaskState state = TaskState.QUEUED;
        private float progress;
        private Component message = Component.empty();
        private long uploadStartedAtMs;
        private long lastUploadProgressAtMs;
        private long uploadFinishingAtMs;
        private int lastUploadSentBytes;
        private boolean cancelled;
        private CompletableFuture<byte[]> downloadFuture;

        private DownloadTask(ModelRepoEntry entry, ResourceStationConfig.State config) {
            this.entry = entry;
            this.config = config;
        }

        private String id() {
            return this.entry.url();
        }
    }

    public enum TaskState {
        QUEUED,
        DOWNLOADING,
        IMPORTING,
        UPLOADING,
        DONE,
        FAILED,
        CANCELLED
    }

    public record TaskSnapshot(String id, String name, String fileName, TaskState state, float progress, Component message) {
    }

    public record Snapshot(TaskSnapshot currentTask, List<TaskSnapshot> unfinishedTasks, List<TaskSnapshot> finishedTasks,
                           int queued, long done, long failed,
                           Component status, ChatFormatting statusColor) {
    }
}
