package com.micaftic.morpher.client.gui.resource.download;

import com.micaftic.morpher.client.gui.resource.ResourceDownloadManager;
import com.micaftic.morpher.client.upload.ModelUploadSession;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * 服务端上传阶段协调器（1.2.7 §24.5，自 {@code ResourceDownloadManager} 等价搬运）。
 *
 * <p>监听 {@link ModelUploadSession} 状态更新，把上传会话进度镜像到当前下载任务，并对
 * STARTING / UPLOADING / FINISHING 三个阶段的超时做失败处理（超时常量原样保留）。
 */
public final class ServerUploadCoordinator {
    static final long SERVER_UPLOAD_START_TIMEOUT_MS = 15_000L;
    static final long SERVER_UPLOAD_STALL_TIMEOUT_MS = 30_000L;
    static final long SERVER_UPLOAD_VERIFY_TIMEOUT_MS = 90_000L;

    public static void onUploadSessionUpdate(ModelUploadSession session) {
        if (session == null) {
            return;
        }
        DownloadQueue.DownloadTask task;
        synchronized (DownloadQueue.LOCK) {
            task = DownloadQueue.currentTask;
            if (task == null || task.state != ResourceDownloadManager.TaskState.UPLOADING) {
                return;
            }
            updateCurrentTaskFromSessionLocked(task, session);
        }
        DownloadPresenter.notifyListeners();
        if (session.getState() == ModelUploadSession.State.COMPLETED) {
            DownloadQueue.finishTask(task, ResourceDownloadManager.TaskState.DONE, Component.translatable("gui.sparkle_morpher.resource_station.done"));
        } else if (session.getState() == ModelUploadSession.State.FAILED) {
            DownloadQueue.finishTask(task, ResourceDownloadManager.TaskState.FAILED, session.getMessage());
        }
    }

    public static void syncCurrentUploadSession() {
        DownloadQueue.DownloadTask task;
        synchronized (DownloadQueue.LOCK) {
            task = DownloadQueue.currentTask;
            if (task == null || task.state != ResourceDownloadManager.TaskState.UPLOADING) {
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

    static void updateCurrentTaskFromSessionLocked(DownloadQueue.DownloadTask task, ModelUploadSession session) {
        task.progress = session.getProgress();
        task.message = uploadMessage(session);
        if (session.getState() == ModelUploadSession.State.UPLOADING && session.getSentBytes() > task.lastUploadSentBytes) {
            task.lastUploadSentBytes = session.getSentBytes();
            task.lastUploadProgressAtMs = System.currentTimeMillis();
        }
        if (session.getState() == ModelUploadSession.State.FINISHING && task.uploadFinishingAtMs == 0) {
            task.uploadFinishingAtMs = System.currentTimeMillis();
        }
        DownloadPresenter.status = task.message;
        DownloadPresenter.statusColor = switch (session.getState()) {
            case COMPLETED -> ChatFormatting.GREEN;
            case FAILED -> ChatFormatting.RED;
            default -> ChatFormatting.YELLOW;
        };
    }

    static Component uploadMessage(ModelUploadSession session) {
        return switch (session.getState()) {
            case STARTING -> Component.translatable("gui.sparkle_morpher.import.state.server_starting");
            case UPLOADING -> Component.translatable("gui.sparkle_morpher.import.state.server_uploading");
            default -> session.getMessage();
        };
    }

    static void failStalledUpload(DownloadQueue.DownloadTask task, ModelUploadSession session) {
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
}
