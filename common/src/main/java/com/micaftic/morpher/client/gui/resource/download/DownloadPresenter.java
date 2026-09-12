package com.micaftic.morpher.client.gui.resource.download;

import com.micaftic.morpher.client.gui.resource.ModelRepoClient;
import com.micaftic.morpher.client.upload.ModelUploadSession;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 资源下载状态展示器（1.2.7 §24.5，自 {@code ResourceDownloadManager} 等价搬运）。
 *
 * <p>持有状态栏（{@code status}/{@code statusColor}）与监听器列表，负责状态通知与下载进度文案
 * 拼接。状态字段与任务状态一样在 {@link DownloadQueue#LOCK} 下读写（搬运前为同一把锁）。
 */
public final class DownloadPresenter {
    static final CopyOnWriteArrayList<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    static Component status = Component.empty();
    static ChatFormatting statusColor = ChatFormatting.GRAY;

    public static void addListener(Runnable listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(Runnable listener) {
        LISTENERS.remove(listener);
    }

    static void notifyListeners() {
        for (Runnable listener : LISTENERS) {
            listener.run();
        }
    }

    static Component progressMessage(int downloaded, int total, long bytesPerSecond, String host) {
        String bytes = ModelUploadSession.formatBytes(downloaded) + (total > 0 ? "/" + ModelUploadSession.formatBytes(total) : "");
        String speed = bytesPerSecond > 0 ? " " + ModelRepoClient.formatSpeed(bytesPerSecond) : "";
        String source = host.isBlank() ? "" : " @" + host;
        return Component.literal(bytes + speed + source);
    }

    static Component trySourceMessage(int index, int total, String host) {
        return Component.translatable("gui.sparkle_morpher.resource_station.try_source", index, total, host);
    }

    private DownloadPresenter() {
    }
}
