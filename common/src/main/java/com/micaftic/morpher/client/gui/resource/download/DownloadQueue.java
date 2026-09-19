package com.micaftic.morpher.client.gui.resource.download;

import com.micaftic.morpher.client.gui.resource.ModelRepoClient;
import com.micaftic.morpher.client.gui.resource.ModelRepoEntry;
import com.micaftic.morpher.client.gui.resource.ResourceStationConfig;
import com.micaftic.morpher.client.upload.ModelUploadSession;
import com.micaftic.morpher.util.SmExecutors;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * 下载任务队列与下载执行内核（1.2.8，历史下载门面已移除）。
 *
 * <p>持有队列状态（当前任务、待处理队列、历史、重入锁）与下载执行流程；状态栏文案与监听器
 * 通知交给 {@link DownloadPresenter}，本地导入阶段交给
 * {@link ResourceImportCoordinator}，服务端上传阶段交给 {@link ServerUploadCoordinator}。
 * 行为与搬运前逐字等价：同一把锁、同一状态迁移、同一文案与异常。
 *
 * <p>任务状态与快照类型由本类直接持有，调用方统一通过本队列服务访问。
 */
public final class DownloadQueue {
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

    static {
        ModelUploadSession.addListener(ServerUploadCoordinator::onUploadSessionUpdate);
    }

    static final int HISTORY_LIMIT = 128;
    static final ExecutorService DOWNLOAD_EXECUTOR = SmExecutors.pool(SmExecutors.Pool.DOWNLOAD_IO);
    static final Object LOCK = new Object();
    static final ArrayDeque<DownloadTask> QUEUE = new ArrayDeque<>();
    static final List<DownloadTask> HISTORY = new ArrayList<>();
    static DownloadTask currentTask;
    static boolean downloadLoading;

    public static boolean enqueue(ModelRepoEntry entry, ResourceStationConfig.State config) {
        boolean added;
        synchronized (LOCK) {
            added = enqueueLocked(entry, config);
        }
        if (added) {
            DownloadPresenter.notifyListeners();
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
            DownloadPresenter.notifyListeners();
            processNextDownload();
        }
        return added;
    }

    public static boolean isQueued(ModelRepoEntry entry) {
        synchronized (LOCK) {
            return isQueuedLocked(entry);
        }
    }

    public static Snapshot snapshot() {
        synchronized (LOCK) {
            List<TaskSnapshot> unfinishedTasks = new ArrayList<>();
            if (currentTask != null) {
                unfinishedTasks.add(snapshot(currentTask));
            }
            unfinishedTasks.addAll(QUEUE.stream().map(DownloadQueue::snapshot).toList());
            List<TaskSnapshot> finishedTasks = HISTORY.stream().map(DownloadQueue::snapshot).toList();
            long done = HISTORY.stream().filter(task -> task.state == TaskState.DONE).count();
            long failed = HISTORY.stream().filter(task -> task.state == TaskState.FAILED).count();
            return new Snapshot(currentTask == null ? null : snapshot(currentTask), unfinishedTasks, finishedTasks,
                    QUEUE.size(), done, failed, DownloadPresenter.status, DownloadPresenter.statusColor);
        }
    }

    public static void clearFinished() {
        synchronized (LOCK) {
            HISTORY.clear();
            DownloadPresenter.status = Component.translatable("gui.sparkle_morpher.resource_station.finished_cleared");
            DownloadPresenter.statusColor = ChatFormatting.GRAY;
        }
        DownloadPresenter.notifyListeners();
    }

    public static void tick() {
        ServerUploadCoordinator.syncCurrentUploadSession();
        processNextDownload();
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
            DownloadPresenter.status = currentTask.message;
            DownloadPresenter.statusColor = ChatFormatting.GRAY;
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
        DownloadPresenter.notifyListeners();
        if (!waitForDownloader) {
            processNextDownload();
        }
    }

    static boolean enqueueLocked(ModelRepoEntry entry, ResourceStationConfig.State config) {
        if (isQueuedLocked(entry)) {
            return false;
        }
        DownloadTask task = new DownloadTask(entry, config);
        QUEUE.add(task);
        DownloadPresenter.status = Component.translatable("gui.sparkle_morpher.resource_station.queued", entry.name());
        DownloadPresenter.statusColor = ChatFormatting.YELLOW;
        return true;
    }

    static boolean isQueuedLocked(ModelRepoEntry entry) {
        if (currentTask != null && Objects.equals(currentTask.entry.url(), entry.url())) {
            return true;
        }
        return QUEUE.stream().anyMatch(task -> Objects.equals(task.entry.url(), entry.url()));
    }

    static void trimHistoryLocked() {
        while (HISTORY.size() > HISTORY_LIMIT) {
            HISTORY.remove(0);
        }
    }

    public static void processNextDownload() {
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
            DownloadPresenter.status = task.message;
            DownloadPresenter.statusColor = ChatFormatting.YELLOW;
        }
        DownloadPresenter.notifyListeners();
        CompletableFuture.supplyAsync(() -> {
            try {
                return ModelRepoClient.download(task.entry, task.config, new ModelRepoClient.ProgressListener() {
                    String host = "";

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
                            task.message = DownloadPresenter.progressMessage(downloaded, total, bytesPerSecond, this.host);
                            DownloadPresenter.status = task.message;
                            DownloadPresenter.statusColor = ChatFormatting.YELLOW;
                        }
                        DownloadPresenter.notifyListeners();
                    }

                    @Override
                    public void onCandidate(String url, int index, int total) {
                        ensureNotCancelled(task);
                        this.host = ModelRepoClient.hostName(url);
                        synchronized (LOCK) {
                            if (currentTask != task || task.state != TaskState.DOWNLOADING) {
                                return;
                            }
                            task.message = DownloadPresenter.trySourceMessage(index, total, this.host);
                            DownloadPresenter.status = task.message;
                            DownloadPresenter.statusColor = ChatFormatting.YELLOW;
                        }
                        DownloadPresenter.notifyListeners();
                    }
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, DOWNLOAD_EXECUTOR).whenComplete((data, error) ->
                ((Executor) Minecraft.getInstance()).execute(() -> ResourceImportCoordinator.onDownloadFinished(task, data, error)));
    }

    static void finishTask(DownloadTask task, TaskState state, Component message) {
        synchronized (LOCK) {
            if (currentTask != task) {
                return;
            }
            task.state = state;
            task.message = message;
            task.progress = state == TaskState.DONE ? 1f : task.progress;
            DownloadPresenter.status = message;
            DownloadPresenter.statusColor = state == TaskState.DONE ? ChatFormatting.GREEN : state == TaskState.CANCELLED ? ChatFormatting.GRAY : ChatFormatting.RED;
            HISTORY.add(task);
            trimHistoryLocked();
            currentTask = null;
            downloadLoading = false;
        }
        DownloadPresenter.notifyListeners();
        processNextDownload();
    }

    static TaskSnapshot snapshot(DownloadTask task) {
        return new TaskSnapshot(task.entry.name(), task.entry.fileName(), task.state, task.progress, task.message);
    }

    static int progressTotal(int contentLength, long entrySize) {
        if (contentLength > 0) {
            return contentLength;
        }
        if (entrySize > 0 && entrySize <= Integer.MAX_VALUE) {
            return (int) entrySize;
        }
        return 0;
    }

    static void ensureNotCancelled(DownloadTask task) {
        synchronized (LOCK) {
            if (currentTask != task || task.cancelRequested || task.state == TaskState.CANCELLED) {
                throw new CancellationException("cancelled");
            }
        }
    }

    static boolean isCancellation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof CancellationException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    static class DownloadTask {
        final ModelRepoEntry entry;
        final ResourceStationConfig.State config;
        TaskState state = TaskState.QUEUED;
        float progress;
        Component message = Component.empty();
        boolean cancelRequested;
        long uploadStartedAtMs;
        long lastUploadProgressAtMs;
        long uploadFinishingAtMs;
        int lastUploadSentBytes;

        DownloadTask(ModelRepoEntry entry, ResourceStationConfig.State config) {
            this.entry = entry;
            this.config = config;
        }
    }
}
