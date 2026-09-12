package com.micaftic.morpher.client.upload.picker;

import com.micaftic.morpher.YesSteveModel;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.FilenameFilter;
import java.util.concurrent.CompletableFuture;

/**
 * 桌面 JVM 文件对话框后端（1.2.7 §24.6，自 {@code ModelImportFilePicker} 等价搬运）。
 *
 * <p>非 Android 运行时可用：优先 {@code java.awt.FileDialog}，回退 {@code javax.swing.JFileChooser}。
 * 过滤器模式、回退顺序与错误抑制（suppressed）行为原样保留。
 */
public final class DesktopPickerBackend {
    private static final String FILE_FILTER_PATTERN = "*.ysm;*.zip;*.bbmodel;*.gltf;*.glb";

    static boolean tryStartJvmFileDialog() {
        if (FilePickerCoordinator.isAndroidRuntime()) {
            FilePickerCoordinator.rememberPickerProbe("JVM file picker skipped on Android runtime");
            return false;
        }
        if (FilePickerCoordinator.isHeadlessGraphicsEnvironment()) {
            FilePickerCoordinator.rememberPickerProbe("JVM file picker skipped: headless graphics environment");
            return false;
        }
        try {
            Class.forName("java.awt.FileDialog");
            Class.forName("java.awt.Frame");
            CompletableFuture.runAsync(DesktopPickerBackend::showJvmFileDialog);
            FilePickerCoordinator.clearLastError();
            return true;
        } catch (Throwable t) {
            FilePickerCoordinator.rememberPickerProbe("AWT FileDialog unavailable: " + FilePickerCoordinator.safeMessage(t));
        }
        try {
            Class.forName("javax.swing.JFileChooser");
            CompletableFuture.runAsync(DesktopPickerBackend::showJvmFileDialog);
            FilePickerCoordinator.clearLastError();
            return true;
        } catch (Throwable t) {
            FilePickerCoordinator.rememberPickerProbe("Swing JFileChooser unavailable: " + FilePickerCoordinator.safeMessage(t));
        }
        return false;
    }

    private static void showJvmFileDialog() {
        Throwable awtError = null;
        try {
            if (showAwtFileDialog()) {
                return;
            }
        } catch (Throwable t) {
            awtError = t;
            FilePickerCoordinator.rememberPickerProbe("AWT FileDialog failed: " + FilePickerCoordinator.safeMessage(t));
        }
        try {
            if (showSwingFileChooser()) {
                return;
            }
        } catch (Throwable t) {
            if (awtError != null) {
                t.addSuppressed(awtError);
            }
            FilePickerCoordinator.setError(FilePickerCoordinator.error("gui.sparkle_morpher.import.error.open_picker", FilePickerCoordinator.safeMessage(t)));
            YesSteveModel.LOGGER.warn("[SM] Failed to open JVM file picker", t);
        }
    }

    private static boolean showAwtFileDialog() throws Exception {
        Class<?> fileDialogClass = Class.forName("java.awt.FileDialog");
        Class<?> frameClass = Class.forName("java.awt.Frame");
        int loadMode = fileDialogClass.getField("LOAD").getInt(null);
        Object dialog = fileDialogClass.getConstructor(frameClass, String.class, int.class)
                .newInstance(null, Component.translatable("gui.sparkle_morpher.import.title").getString(), loadMode);
        fileDialogClass.getMethod("setFile", String.class).invoke(dialog, FILE_FILTER_PATTERN);
        fileDialogClass.getMethod("setFilenameFilter", FilenameFilter.class).invoke(dialog, (FilenameFilter) (dir, name) -> FilePickerCoordinator.isImportFileName(name));
        try {
            fileDialogClass.getMethod("setMultipleMode", boolean.class).invoke(dialog, true);
        } catch (NoSuchMethodException ignored) {
        }
        fileDialogClass.getMethod("setVisible", boolean.class).invoke(dialog, true);
        try {
            Object files = fileDialogClass.getMethod("getFiles").invoke(dialog);
            if (files instanceof File[] selectedFiles && selectedFiles.length > 0) {
                fileDialogClass.getMethod("dispose").invoke(dialog);
                for (File selected : selectedFiles) {
                    FilePickerCoordinator.completeFile(selected);
                }
                return true;
            }
        } catch (NoSuchMethodException ignored) {
        }
        Object directory = fileDialogClass.getMethod("getDirectory").invoke(dialog);
        Object file = fileDialogClass.getMethod("getFile").invoke(dialog);
        try {
            fileDialogClass.getMethod("dispose").invoke(dialog);
        } catch (Throwable ignored) {
        }
        if (file == null) {
            FilePickerCoordinator.setError(Component.translatable("gui.sparkle_morpher.import.error.cancelled"));
            return true;
        }
        File selected = new File(directory == null ? "" : String.valueOf(directory), String.valueOf(file));
        FilePickerCoordinator.completeFile(selected);
        return true;
    }

    private static boolean showSwingFileChooser() throws Exception {
        Class<?> chooserClass = Class.forName("javax.swing.JFileChooser");
        Object chooser = chooserClass.getDeclaredConstructor().newInstance();
        chooserClass.getMethod("setDialogTitle", String.class).invoke(chooser, Component.translatable("gui.sparkle_morpher.import.title").getString());
        chooserClass.getMethod("setMultiSelectionEnabled", boolean.class).invoke(chooser, true);
        try {
            Class<?> filterClass = Class.forName("javax.swing.filechooser.FileNameExtensionFilter");
            Object filter = filterClass.getConstructor(String.class, String[].class)
                    .newInstance(FilePickerCoordinator.FILE_FILTER_DESCRIPTION, new String[]{"ysm", "zip", "bbmodel", "gltf", "glb"});
            chooserClass.getMethod("setFileFilter", Class.forName("javax.swing.filechooser.FileFilter")).invoke(chooser, filter);
        } catch (Throwable t) {
            FilePickerCoordinator.rememberPickerProbe("Swing file filter unavailable: " + FilePickerCoordinator.safeMessage(t));
        }
        int result = (Integer) chooserClass.getMethod("showOpenDialog", Class.forName("java.awt.Component")).invoke(chooser, new Object[]{null});
        int approve = chooserClass.getField("APPROVE_OPTION").getInt(null);
        if (result != approve) {
            FilePickerCoordinator.setError(Component.translatable("gui.sparkle_morpher.import.error.cancelled"));
            return true;
        }
        Object selectedFiles = chooserClass.getMethod("getSelectedFiles").invoke(chooser);
        if (selectedFiles instanceof File[] files && files.length > 0) {
            for (File file : files) {
                FilePickerCoordinator.completeFile(file);
            }
            return true;
        }
        Object selectedFile = chooserClass.getMethod("getSelectedFile").invoke(chooser);
        if (!(selectedFile instanceof File file)) {
            FilePickerCoordinator.setError(Component.translatable("gui.sparkle_morpher.import.error.no_file"));
            return true;
        }
        FilePickerCoordinator.completeFile(file);
        return true;
    }

    private DesktopPickerBackend() {
    }
}
