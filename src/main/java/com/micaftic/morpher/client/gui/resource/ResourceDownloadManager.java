package com.micaftic.morpher.client.gui.resource;

import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.upload.ModelUploadSession;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CancellationException;

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

    public static void cancelCurrent() {
        DownloadTask cancelledTask;
        boolean cancelUpload;
        boolean waitForDownloader;
        synchronized (LOCK) {
            if (currentTask == null) {
                return;
            }
            cancelledTask = currentTask;
            cancelUpload = cancelledTask.state == TaskState.UPLOADING;
            waitForDownloader = cancelledTask.state == TaskState.DOWNLOADING;
            cancelledTask.cancelRequested = true;
            currentTask.state = TaskState.CANCELLED;
            currentTask.message = Component.translatable("gui.sparkle_morpher.resource_station.cancelled");
            status = currentTask.message;
            statusColor = ChatFormatting.GRAY;
            if (!waitForDownloader) {
                HISTORY.add(currentTask);
                trimHistoryLocked();
                currentTask = null;
                downloadLoading = false;
            }
        }
        if (cancelUpload) {
            ModelUploadSession.failCurrent(Component.translatable("gui.sparkle_morpher.resource_station.cancelled"));
        }
        notifyListeners();
        if (!waitForDownloader) {
            processNextDownload();
        }
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

    private static void trimHistoryLocked() {
        while (HISTORY.size() > HISTORY_LIMIT) {
            HISTORY.remove(0);
        }
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
        CompletableFuture.supplyAsync(() -> {
            try {
                return ModelRepoClient.download(task.entry, task.config, new ModelRepoClient.ProgressListener() {
                    private String host = "";

                    @Override
                    public void onProgress(int downloaded, int total) {
                        onProgress(downloaded, total, 0L);
                    }

                    @Override
                    public boolean isCancelled() {
                        synchronized (LOCK) {
                            return task.cancelRequested || currentTask != task || task.state == TaskState.CANCELLED;
                        }
                    }

                    @Override
                    public void onProgress(int downloaded, int total, long bytesPerSecond) {
                        ensureNotCancelled(task);
                        int progressTotal = progressTotal(total, task.entry.size());
                        synchronized (LOCK) {
                            if (currentTask != task || task.state != TaskState.DOWNLOADING) {
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
                        ensureNotCancelled(task);
                        this.host = ModelRepoClient.hostName(url);
                        synchronized (LOCK) {
                            if (currentTask != task || task.state != TaskState.DOWNLOADING) {
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
        }, DOWNLOAD_EXECUTOR).whenComplete((data, error) ->
                ((Executor) Minecraft.getInstance()).execute(() -> onDownloadFinished(task, data, error)));
    }

    private static void onDownloadFinished(DownloadTask task, byte[] data, Throwable error) {
        synchronized (LOCK) {
            if (currentTask != task) {
                return;
            }
            downloadLoading = false;
        }
        if (error != null) {
            if (isCancellation(error)) {
                finishTask(task, TaskState.CANCELLED, Component.translatable("gui.sparkle_morpher.resource_station.cancelled"));
                return;
            }
            finishTask(task, TaskState.FAILED, Component.translatable("gui.sparkle_morpher.resource_station.error", rootMessage(error)));
            return;
        }
        String modelId = ModelRepoClient.safeModelId(task.entry);
        synchronized (LOCK) {
            if (task.cancelRequested || task.state == TaskState.CANCELLED) {
                finishTask(task, TaskState.CANCELLED, Component.translatable("gui.sparkle_morpher.resource_station.cancelled"));
                return;
            }
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
        ClientModelManager.importLocalModel(modelId, task.entry.fileName(), data, localError -> onLocalImportFinished(task, modelId, data, localError));
    }

    private static void onLocalImportFinished(DownloadTask task, String modelId, byte[] data, Component localError) {
        synchronized (LOCK) {
            if (currentTask != task) {
                return;
            }
        }
        if (localError != null) {
            finishTask(task, TaskState.FAILED, localError);
            return;
        }
        synchronized (LOCK) {
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
            finishTask(task, TaskState.FAILED, startError);
        }
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
            statusColor = state == TaskState.DONE ? ChatFormatting.GREEN : state == TaskState.CANCELLED ? ChatFormatting.GRAY : ChatFormatting.RED;
            HISTORY.add(task);
            trimHistoryLocked();
            currentTask = null;
            downloadLoading = false;
        }
        notifyListeners();
        processNextDownload();
    }

    private static TaskSnapshot snapshot(DownloadTask task) {
        return new TaskSnapshot(task.entry.name(), task.entry.fileName(), task.state, task.progress, task.message);
    }

    private static void notifyListeners() {
        for (Runnable listener : LISTENERS) {
            listener.run();
        }
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

    private static void ensureNotCancelled(DownloadTask task) {
        synchronized (LOCK) {
            if (currentTask != task || task.cancelRequested || task.state == TaskState.CANCELLED) {
                throw new CancellationException("cancelled");
            }
        }
    }

    private static boolean isCancellation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof CancellationException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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
        private boolean cancelRequested;
        private long uploadStartedAtMs;
        private long lastUploadProgressAtMs;
        private long uploadFinishingAtMs;
        private int lastUploadSentBytes;

        private DownloadTask(ModelRepoEntry entry, ResourceStationConfig.State config) {
            this.entry = entry;
            this.config = config;
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

    public record TaskSnapshot(String name, String fileName, TaskState state, float progress, Component message) {
    }

    public record Snapshot(TaskSnapshot currentTask, List<TaskSnapshot> unfinishedTasks, List<TaskSnapshot> finishedTasks,
                           int queued, long done, long failed,
                           Component status, ChatFormatting statusColor) {
    }
}
