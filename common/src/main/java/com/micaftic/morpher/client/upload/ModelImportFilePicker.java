package com.micaftic.morpher.client.upload;

import com.micaftic.morpher.client.upload.picker.FilePickerCoordinator;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 模型导入文件选择入口。
 *
 * <p><b>1.2.7 §24.6</b>：本类已拆分为 {@code client.upload.picker} 下的协调器与平台后端，本类保留为
 * 兼容 facade（Facade First），调用点迁移完成后于 1.2.8 删除：
 * <ul>
 *   <li>{@link FilePickerCoordinator} — 选择状态 / 编排 / 结果落盘与目录打包</li>
 *   <li>{@code FclPickerBackend} — FCL 文件选择</li>
 *   <li>{@code AndroidDocumentPickerBackend} — AndroidX / system Intent 文档选择</li>
 *   <li>{@code LauncherBridgeBackend} — 启动器文件夹桥</li>
 *   <li>{@code TinyFdBackend} — LWJGL tinyfd 原生对话框</li>
 *   <li>{@code DesktopPickerBackend} — AWT / Swing 桌面对话框</li>
 * </ul>
 *
 * <p>嵌套类型 {@link PickedFile} / {@link PackedSizeLimitExceededException} 仍由本类持有，以免改变
 * 外部调用点的类型引用（{@code ModelUploadScreen} / {@code ModernPlayerModelScreen} /
 * {@code CustomFolderUploadScreen} 直接使用这些嵌套类型）。
 *
 * @deprecated 1.2.7 §24.6：请直接使用 {@code com.micaftic.morpher.client.upload.picker}
 * 下的对应组件。删除留到 1.2.8。
 */
@Deprecated
public final class ModelImportFilePicker {

    private ModelImportFilePicker() {
    }

    public static boolean isPicking() {
        return FilePickerCoordinator.isPicking();
    }

    public static Component getLastError() {
        return FilePickerCoordinator.getLastError();
    }

    public static synchronized Component consumeLastError() {
        return FilePickerCoordinator.consumeLastError();
    }

    public static synchronized PickedFile pollCompleted() {
        return FilePickerCoordinator.pollCompleted();
    }

    public static synchronized Component pickYsmFile() {
        return FilePickerCoordinator.pickYsmFile();
    }

    public static synchronized void cancelPicking() {
        FilePickerCoordinator.cancelPicking();
    }

    public static PickedFile packDirectory(Path dir) throws IOException {
        return FilePickerCoordinator.packDirectory(dir);
    }

    public static PickedFile packDirectory(Path dir, int maxPackedBytes) throws IOException {
        return FilePickerCoordinator.packDirectory(dir, maxPackedBytes);
    }

    public static boolean isImportFileName(String fileName) {
        return FilePickerCoordinator.isImportFileName(fileName);
    }

    public record PickedFile(String fileName, byte[] data) {
    }

    public static final class PackedSizeLimitExceededException extends IOException {
        private final int maxBytes;

        public PackedSizeLimitExceededException(int maxBytes) {
            super("Packed folder exceeds upload limit");
            this.maxBytes = maxBytes;
        }

        public int maxBytes() {
            return this.maxBytes;
        }
    }
}
