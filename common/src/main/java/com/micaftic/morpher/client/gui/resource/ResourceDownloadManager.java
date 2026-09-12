package com.micaftic.morpher.client.gui.resource;

import com.micaftic.morpher.client.gui.resource.download.DownloadPresenter;
import com.micaftic.morpher.client.gui.resource.download.DownloadQueue;
import com.micaftic.morpher.client.gui.resource.download.ServerUploadCoordinator;
import com.micaftic.morpher.client.upload.ModelUploadSession;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 资源站下载管理器入口。
 *
 * <p><b>1.2.7 §24.5</b>：本类已拆分为 {@code gui.resource.download} 下的专用组件，本类保留为
 * 兼容 facade（Facade First），调用点迁移完成后于 1.2.8 删除：
 * <ul>
 *   <li>{@link DownloadQueue} — 队列状态、下载执行与任务状态迁移</li>
 *   <li>{@link com.micaftic.morpher.client.gui.resource.download.ResourceImportCoordinator}
 *       — 本地落盘 / 客户端导入编排</li>
 *   <li>{@link ServerUploadCoordinator} — 服务端上传阶段协调</li>
 *   <li>{@link DownloadPresenter} — 状态栏与监听器通知</li>
 * </ul>
 *
 * <p>公开类型 {@link TaskState} / {@link TaskSnapshot} / {@link Snapshot} 仍由本类持有，以免改变
 * 外部调用点的类型引用（{@code ModernPlayerModelScreen} 直接使用这些嵌套类型）。
 *
 * @deprecated 1.2.7 §24.5：请直接使用 {@code com.micaftic.morpher.client.gui.resource.download}
 * 下的对应组件。删除留到 1.2.8。
 */
@Deprecated
public final class ResourceDownloadManager {

    static {
        ModelUploadSession.addListener(ServerUploadCoordinator::onUploadSessionUpdate);
    }

    private ResourceDownloadManager() {
    }

    public static boolean enqueue(ModelRepoEntry entry, ResourceStationConfig.State config) {
        return DownloadQueue.enqueue(entry, config);
    }

    public static int enqueueAll(List<ModelRepoEntry> entries, ResourceStationConfig.State config) {
        return DownloadQueue.enqueueAll(entries, config);
    }

    public static boolean isQueued(ModelRepoEntry entry) {
        return DownloadQueue.isQueued(entry);
    }

    public static void tick() {
        ServerUploadCoordinator.syncCurrentUploadSession();
        DownloadQueue.processNextDownload();
    }

    public static void addListener(Runnable listener) {
        DownloadPresenter.addListener(listener);
    }

    public static void removeListener(Runnable listener) {
        DownloadPresenter.removeListener(listener);
    }

    public static Snapshot snapshot() {
        return DownloadQueue.snapshot();
    }

    public static void clearFinished() {
        DownloadQueue.clearFinished();
    }

    public static void cancelCurrent() {
        DownloadQueue.cancelCurrent();
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
