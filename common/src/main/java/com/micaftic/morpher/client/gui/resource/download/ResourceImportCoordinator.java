package com.micaftic.morpher.client.gui.resource.download;

import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.gui.resource.ModelRepoClient;
import com.micaftic.morpher.client.gui.resource.ResourceDownloadManager;
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
import java.util.Locale;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 下载完成后的本地落盘与导入编排（1.2.7 §24.5，自 {@code ResourceDownloadManager} 等价搬运）。
 *
 * <p>负责：下载字节 → 本地保存（路径逃逸校验 + 原子移动 + 同模型异扩展名清理）→ 客户端本地导入 →
 * 判定是否续接服务端上传。所有阶段迁移与文案、异常类型与搬运前逐字一致。
 */
public final class ResourceImportCoordinator {
    static void onDownloadFinished(DownloadQueue.DownloadTask task, byte[] data, Throwable error) {
        synchronized (DownloadQueue.LOCK) {
            if (DownloadQueue.currentTask != task) {
                return;
            }
            DownloadQueue.downloadLoading = false;
        }
        if (error != null) {
            if (DownloadQueue.isCancellation(error)) {
                DownloadQueue.finishTask(task, ResourceDownloadManager.TaskState.CANCELLED, Component.translatable("gui.sparkle_morpher.resource_station.cancelled"));
                return;
            }
            DownloadQueue.finishTask(task, ResourceDownloadManager.TaskState.FAILED, Component.translatable("gui.sparkle_morpher.resource_station.error", DownloadQueue.rootMessage(error)));
            return;
        }
        String modelId = stripKnownImportExtension(ModelRepoClient.safeModelId(task.entry));
        synchronized (DownloadQueue.LOCK) {
            if (task.cancelRequested || task.state == ResourceDownloadManager.TaskState.CANCELLED) {
                DownloadQueue.finishTask(task, ResourceDownloadManager.TaskState.CANCELLED, Component.translatable("gui.sparkle_morpher.resource_station.cancelled"));
                return;
            }
            if (DownloadQueue.currentTask != task) {
                return;
            }
            task.state = ResourceDownloadManager.TaskState.IMPORTING;
            task.progress = Math.max(task.progress, 1f);
            task.message = Component.translatable("gui.sparkle_morpher.import.state.local_importing", modelId);
            DownloadPresenter.status = task.message;
            DownloadPresenter.statusColor = ChatFormatting.YELLOW;
        }
        DownloadPresenter.notifyListeners();
        CompletableFuture.runAsync(() -> {
                    try {
                        saveDownloadedModel(task.entry.fileName(), modelId, data);
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                }, DownloadQueue.DOWNLOAD_EXECUTOR)
                .whenComplete((ignored, saveError) ->
                        ((Executor) Minecraft.getInstance()).execute(() -> onLocalSaveFinished(task, modelId, data, saveError)));
    }

    static void onLocalSaveFinished(DownloadQueue.DownloadTask task, String modelId, byte[] data, Throwable saveError) {
        synchronized (DownloadQueue.LOCK) {
            if (DownloadQueue.currentTask != task || task.cancelRequested || task.state == ResourceDownloadManager.TaskState.CANCELLED) {
                return;
            }
        }
        if (saveError != null) {
            DownloadQueue.finishTask(task, ResourceDownloadManager.TaskState.FAILED, Component.translatable("gui.sparkle_morpher.resource_station.save_failed", DownloadQueue.rootMessage(saveError)));
            return;
        }
        ClientModelManager.importLocalModel(modelId, task.entry.fileName(), data, localError -> onLocalImportFinished(task, modelId, data, localError));
    }

    static void onLocalImportFinished(DownloadQueue.DownloadTask task, String modelId, byte[] data, Component localError) {
        synchronized (DownloadQueue.LOCK) {
            if (DownloadQueue.currentTask != task || task.cancelRequested || task.state == ResourceDownloadManager.TaskState.CANCELLED) {
                return;
            }
        }
        if (localError != null) {
            DownloadQueue.finishTask(task, ResourceDownloadManager.TaskState.FAILED, localError);
            return;
        }
        if (!canUploadToServer()) {
            DownloadQueue.finishTask(task, ResourceDownloadManager.TaskState.DONE, Component.translatable("gui.sparkle_morpher.resource_station.saved_local", modelId));
            return;
        }
        if (ClientModelManager.isGltfFileName(task.entry.fileName())) {
            DownloadQueue.finishTask(task, ResourceDownloadManager.TaskState.DONE, Component.translatable("gui.sparkle_morpher.resource_station.saved_local", modelId));
            return;
        }
        synchronized (DownloadQueue.LOCK) {
            if (DownloadQueue.currentTask != task || task.cancelRequested || task.state == ResourceDownloadManager.TaskState.CANCELLED) {
                return;
            }
            task.state = ResourceDownloadManager.TaskState.UPLOADING;
            task.progress = 0f;
            task.uploadStartedAtMs = System.currentTimeMillis();
            task.lastUploadProgressAtMs = task.uploadStartedAtMs;
            task.lastUploadSentBytes = 0;
            task.uploadFinishingAtMs = 0;
            task.message = Component.translatable("gui.sparkle_morpher.import.state.server_starting");
            DownloadPresenter.status = task.message;
            DownloadPresenter.statusColor = ChatFormatting.YELLOW;
        }
        DownloadPresenter.notifyListeners();
        Component startError = ModelUploadSession.start(modelId, task.entry.fileName(), data);
        if (startError != null) {
            if (!canUploadToServer()) {
                DownloadQueue.finishTask(task, ResourceDownloadManager.TaskState.DONE, Component.translatable("gui.sparkle_morpher.resource_station.saved_local", modelId));
            } else {
                DownloadQueue.finishTask(task, ResourceDownloadManager.TaskState.FAILED, startError);
            }
        }
    }

    static boolean canUploadToServer() {
        return NetworkHandler.isClientConnected() && ClientModelManager.isOysmServer() && ClientModelManager.isAllowUpload();
    }

    static void saveDownloadedModel(String fileName, String modelId, byte[] data) throws IOException {
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

    static void removeSiblingModelFiles(Path customRoot, String modelId, Path keepTarget) throws IOException {
        for (String extension : new String[]{".ysm", ".zip", ".bbmodel", ".gltf", ".glb"}) {
            Path sibling = ServerModelManager.CUSTOM.resolve(modelId + extension).toAbsolutePath().normalize();
            if (sibling.startsWith(customRoot) && !sibling.equals(keepTarget)) {
                Files.deleteIfExists(sibling);
            }
        }
    }

    static String extensionForFileName(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) {
            return ".zip";
        }
        if (lower.endsWith(".bbmodel")) {
            return ".bbmodel";
        }
        if (lower.endsWith(".gltf")) {
            return ".gltf";
        }
        if (lower.endsWith(".glb")) {
            return ".glb";
        }
        return ".ysm";
    }

    static String stripKnownImportExtension(String modelId) {
        String lower = modelId == null ? "" : modelId.toLowerCase(Locale.ROOT);
        for (String extension : new String[]{".ysm", ".zip", ".bbmodel", ".gltf", ".glb"}) {
            if (lower.endsWith(extension)) {
                return modelId.substring(0, modelId.length() - extension.length());
            }
        }
        return modelId;
    }
}
