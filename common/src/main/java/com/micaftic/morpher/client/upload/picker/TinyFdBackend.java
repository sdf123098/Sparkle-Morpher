package com.micaftic.morpher.client.upload.picker;

import com.micaftic.morpher.YesSteveModel;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * LWJGL tinyfd 原生文件对话框后端（1.2.7 §24.6，自 {@code ModelImportFilePicker} 等价搬运）。
 *
 * <p>非 Android 运行时可用：通过 {@code TinyFileDialogs.tinyfd_openFileDialog} 打开原生多选对话框，
 * 并把 tinyfd 的 {@code |} 分隔结果还原为文件列表。反射目标与过滤器描述原样保留。
 */
public final class TinyFdBackend {
    static boolean tryStartTinyFileDialog() {
        if (FilePickerCoordinator.isAndroidRuntime()) {
            FilePickerCoordinator.rememberPickerProbe("tinyfd file picker skipped on Android runtime");
            return false;
        }
        try {
            Class<?> pointerBufferClass = Class.forName("org.lwjgl.PointerBuffer");
            Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs")
                    .getMethod("tinyfd_openFileDialog", CharSequence.class, CharSequence.class,
                            pointerBufferClass, CharSequence.class, boolean.class);
            CompletableFuture.runAsync(TinyFdBackend::showTinyFileDialog);
            FilePickerCoordinator.clearLastError();
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            FilePickerCoordinator.rememberPickerProbe("LWJGL tinyfd picker unavailable: " + FilePickerCoordinator.safeMessage(e));
            return false;
        } catch (Throwable t) {
            FilePickerCoordinator.rememberPickerProbe("LWJGL tinyfd picker failed: " + FilePickerCoordinator.safeMessage(t));
            return false;
        }
    }

    private static void showTinyFileDialog() {
        try {
            String selected = openTinyFileDialog();
            if (selected == null || selected.isBlank()) {
                FilePickerCoordinator.setError(Component.translatable("gui.sparkle_morpher.import.error.cancelled"));
                return;
            }
            for (String selectedPath : splitTinyFileDialogSelection(selected)) {
                if (!selectedPath.isBlank()) {
                    FilePickerCoordinator.completeFile(new File(selectedPath));
                }
            }
        } catch (Throwable t) {
            FilePickerCoordinator.setError(FilePickerCoordinator.error("gui.sparkle_morpher.import.error.open_picker", FilePickerCoordinator.safeMessage(t)));
            YesSteveModel.LOGGER.warn("[SM] Failed to open LWJGL tinyfd file picker", t);
        }
    }
    private static String openTinyFileDialog() throws Exception {
        Class<?> memoryUtilClass = Class.forName("org.lwjgl.system.MemoryUtil");
        Class<?> pointerBufferClass = Class.forName("org.lwjgl.PointerBuffer");
        Class<?> tinyFileDialogsClass = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
        Method memUTF8 = memoryUtilClass.getMethod("memUTF8", CharSequence.class);
        Method memFreeByteBuffer = memoryUtilClass.getMethod("memFree", java.nio.ByteBuffer.class);
        Method memFreeCustomBuffer = memoryUtilClass.getMethod("memFree", Class.forName("org.lwjgl.system.CustomBuffer"));
        Method memAllocPointer = memoryUtilClass.getMethod("memAllocPointer", int.class);
        Method pointerPut = pointerBufferClass.getMethod("put", int.class, java.nio.ByteBuffer.class);
        Method open = tinyFileDialogsClass.getMethod("tinyfd_openFileDialog",
                CharSequence.class, CharSequence.class, pointerBufferClass, CharSequence.class, boolean.class);

        java.nio.ByteBuffer ysmPattern = (java.nio.ByteBuffer) memUTF8.invoke(null, "*.ysm");
        java.nio.ByteBuffer zipPattern = (java.nio.ByteBuffer) memUTF8.invoke(null, "*.zip");
        java.nio.ByteBuffer bbmodelPattern = (java.nio.ByteBuffer) memUTF8.invoke(null, "*.bbmodel");
        java.nio.ByteBuffer gltfPattern = (java.nio.ByteBuffer) memUTF8.invoke(null, "*.gltf");
        java.nio.ByteBuffer glbPattern = (java.nio.ByteBuffer) memUTF8.invoke(null, "*.glb");
        Object filters = memAllocPointer.invoke(null, 5);
        try {
            pointerPut.invoke(filters, 0, ysmPattern);
            pointerPut.invoke(filters, 1, zipPattern);
            pointerPut.invoke(filters, 2, bbmodelPattern);
            pointerPut.invoke(filters, 3, gltfPattern);
            pointerPut.invoke(filters, 4, glbPattern);
            return (String) open.invoke(null,
                    Component.translatable("gui.sparkle_morpher.import.title").getString(),
                    "",
                    filters,
                    FilePickerCoordinator.FILE_FILTER_DESCRIPTION,
                    true);
        } finally {
            try {
                memFreeCustomBuffer.invoke(null, filters);
            } finally {
                memFreeByteBuffer.invoke(null, ysmPattern);
                memFreeByteBuffer.invoke(null, zipPattern);
                memFreeByteBuffer.invoke(null, bbmodelPattern);
                memFreeByteBuffer.invoke(null, gltfPattern);
                memFreeByteBuffer.invoke(null, glbPattern);
            }
        }
    }
    private static List<String> splitTinyFileDialogSelection(String selected) {
        if (!selected.contains("|")) {
            return List.of(selected);
        }
        ArrayList<String> parts = new ArrayList<>();
        for (String part : selected.split("\\|")) {
            if (!part.isBlank()) {
                parts.add(part);
            }
        }
        if (parts.size() > 1) {
            File directory = new File(parts.get(0));
            if (directory.isDirectory() && parts.stream().skip(1).noneMatch(part -> new File(part).isAbsolute())) {
                ArrayList<String> result = new ArrayList<>();
                for (int i = 1; i < parts.size(); i++) {
                    result.add(new File(directory, parts.get(i)).getPath());
                }
                return result;
            }
        }
        ArrayList<String> result = new ArrayList<>();
        result.addAll(parts);
        return result;
    }

    private TinyFdBackend() {
    }
}
