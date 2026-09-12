package com.micaftic.morpher.client.upload.picker;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.upload.ModelImportFilePicker;
import com.micaftic.morpher.util.PerformanceProfiler;
import net.minecraft.network.chat.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.Map;

/**
 * 模型导入文件选择协调器（1.2.7 §24.6）。
 *
 * <p>自 {@code ModelImportFilePicker} 等价搬运：持有选择状态（picking / lastError / completed /
 * pickerStartedMs）、平台检测、Android Activity 发现、选择结果落盘与目录打包，并按既定顺序把
 * 平台入口委托给五个 Backend：
 * <ul>
 *   <li>{@link FclPickerBackend} — FCL FileBrowser / FCL system picker</li>
 *   <li>{@link AndroidDocumentPickerBackend} — AndroidX / system Intent document picker</li>
 *   <li>{@link LauncherBridgeBackend} — 启动器文件夹桥（Android 桥接目录轮询）</li>
 *   <li>{@link TinyFdBackend} — LWJGL tinyfd 原生对话框</li>
 *   <li>{@link DesktopPickerBackend} — AWT / Swing JVM 文件对话框</li>
 * </ul>
 *
 * <p>行为与搬运前逐字等价：同一调用顺序、同一文案、同一异常。并发语义上，原本由
 * {@code ModelImportFilePicker.class} 这一把锁保护的状态现集中于本类；Backend 只在本包内协作。
 */
public final class FilePickerCoordinator {
    static final String[] IMPORT_EXTENSIONS = {".ysm", ".zip", ".bbmodel", ".gltf", ".glb"};
    static final String FILE_FILTER_DESCRIPTION = "Model (*.ysm, *.zip, *.bbmodel, *.gltf, *.glb)";
    static final int MAX_FOLDER_DEPTH = 16;
    static final int MAX_FOLDER_FILE_COUNT = 4096;
    private static final long PICKER_REOPEN_GRACE_MS = 1_000L;
    private static final Queue<ModelImportFilePicker.PickedFile> completed = new ArrayDeque<>();
    private static volatile boolean picking = false;
    static volatile Component lastError = Component.empty();
    private static volatile long pickerStartedMs = 0L;
    static final AtomicInteger requestIds = new AtomicInteger(0x7A51);

    public static boolean isPicking() {
        return picking;
    }

    public static Component getLastError() {
        return lastError;
    }

    public static synchronized Component consumeLastError() {
        Component error = lastError;
        lastError = Component.empty();
        return error;
    }

    public static synchronized ModelImportFilePicker.PickedFile pollCompleted() {
        LauncherBridgeBackend.pollLauncherBridgeImports();
        return completed.poll();
    }

    public static synchronized Component pickYsmFile() {
        if (picking) {
            if (canReplaceActivePicker()) {
                LauncherBridgeBackend.stopLauncherBridgeImport();
                picking = false;
                lastError = Component.empty();
            } else {
                return Component.translatable("gui.sparkle_morpher.import.error.picker_open");
            }
        }
        picking = true;
        pickerStartedMs = nowMillis();
        lastError = Component.empty();

        if (FclPickerBackend.tryStartFclSystemPicker()) {
            return null;
        }

        if (AndroidDocumentPickerBackend.tryStartSystemPicker()) {
            return null;
        }

        if (AndroidDocumentPickerBackend.tryStartAndroidXPicker()) {
            return null;
        }

        if (FclPickerBackend.tryStartFclPicker()) {
            return null;
        }

        if (LauncherBridgeBackend.tryStartLauncherBridgeImport()) {
            return null;
        }

        if (TinyFdBackend.tryStartTinyFileDialog()) {
            return null;
        }

        if (DesktopPickerBackend.tryStartJvmFileDialog()) {
            return null;
        }

        picking = false;
        pickerStartedMs = 0L;
        return isEmpty(lastError) ? Component.translatable("gui.sparkle_morpher.import.error.no_android_picker") : lastError;
    }

    public static synchronized void cancelPicking() {
        LauncherBridgeBackend.stopLauncherBridgeImport();
        completed.clear();
        picking = false;
        pickerStartedMs = 0L;
        lastError = Component.empty();
    }

    private static boolean canReplaceActivePicker() {
        if (LauncherBridgeBackend.launcherBridgeImportDir != null) {
            return true;
        }
        return nowMillis() - pickerStartedMs > PICKER_REOPEN_GRACE_MS;
    }
    static boolean isAndroidRuntime() {
        if (YesSteveModel.isOnAndroid()) {
            return true;
        }
        return System.getenv("MOD_ANDROID_RUNTIME") != null
                || System.getenv("FCL_VERSION_CODE") != null
                || System.getenv("ZALITH_VERSION_CODE") != null
                || containsIgnoreCase(System.getProperty("java.vm.name"), "dalvik")
                || containsIgnoreCase(System.getProperty("java.runtime.name"), "android")
                || containsIgnoreCase(System.getProperty("os.name"), "android");
    }

    static boolean isHeadlessGraphicsEnvironment() {
        if ("true".equalsIgnoreCase(System.getProperty("java.awt.headless"))) {
            return true;
        }
        try {
            Class<?> graphicsEnvironment = Class.forName("java.awt.GraphicsEnvironment");
            Object headless = graphicsEnvironment.getMethod("isHeadless").invoke(null);
            return Boolean.TRUE.equals(headless);
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Throwable t) {
            rememberPickerProbe("headless graphics check unavailable: " + safeMessage(t));
            return false;
        }
    }

    static boolean containsIgnoreCase(String text, String needle) {
        return text != null && needle != null && text.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    static Object getFclCurrentActivity() {
        try {
            Class<?> appClass = Class.forName("com.tungsten.fcl.FCLApplication");
            return appClass.getDeclaredMethod("getCurrentActivity").invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static Object getGenericAndroidActivity() {
        Object zalithActivity = getZalithGlobalActivity();
        if (zalithActivity != null) {
            return zalithActivity;
        }
        Object zalithLauncherActivity = getZalithLauncherActivity();
        if (zalithLauncherActivity != null) {
            return zalithLauncherActivity;
        }
        Object fclActivity = getFclCurrentActivity();
        if (fclActivity != null) {
            return fclActivity;
        }
        return getActivityThreadActivity();
    }

    static Object getZalithGlobalActivity() {
        try {
            Object context = Class.forName("com.movtery.zalithlauncher.context.ContextsKt")
                    .getDeclaredMethod("getGlobalContext")
                    .invoke(null);
            if (isAndroidActivity(context)) {
                return context;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    static Object getZalithLauncherActivity() {
        try {
            Object launcher = Class.forName("com.movtery.zalithlauncher.bridge.ZLNativeInvoker")
                    .getDeclaredMethod("getStaticLauncher")
                    .invoke(null);
            return findAndroidActivityField(launcher);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static Object getActivityThreadActivity() {
        try {
            Class<?> threadClass = Class.forName("android.app.ActivityThread");
            Object thread = threadClass.getDeclaredMethod("currentActivityThread").invoke(null);
            if (thread == null) {
                return null;
            }
            Field activitiesField = threadClass.getDeclaredField("mActivities");
            activitiesField.setAccessible(true);
            Object activities = activitiesField.get(thread);
            if (!(activities instanceof Map<?, ?> map)) {
                rememberPickerProbe("ActivityThread.mActivities is not a Map: " + (activities == null ? "null" : activities.getClass().getName()));
                return null;
            }
            for (Object record : map.values()) {
                Object activity = getActivityFromThreadRecord(record);
                if (activity != null) {
                    return activity;
                }
            }
            rememberPickerProbe("ActivityThread had " + map.size() + " records but no resumed activity");
        } catch (Throwable ignored) {
            rememberPickerProbe("ActivityThread activity lookup failed: " + safeMessage(ignored));
        }
        return null;
    }

    static Object getActivityFromThreadRecord(Object record) {
        if (record == null) {
            return null;
        }
        try {
            Field pausedField = findField(record.getClass(), "paused", "mPaused", "isPaused");
            if (pausedField != null && pausedField.getType() == boolean.class) {
                pausedField.setAccessible(true);
                if (pausedField.getBoolean(record)) {
                    return null;
                }
            }
            Object activity = findAndroidActivityField(record);
            return isAndroidActivity(activity) ? activity : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    static Object findAndroidActivityField(Object holder) {
        if (holder == null) {
            return null;
        }
        try {
            Class<?> activityClass = Class.forName("android.app.Activity");
            Class<?> cls = holder.getClass();
            while (cls != null) {
                for (Field field : cls.getDeclaredFields()) {
                    if (activityClass.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        Object activity = field.get(holder);
                        if (activity != null) {
                            return activity;
                        }
                    }
                }
                cls = cls.getSuperclass();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    static Field findField(Class<?> cls, String... names) {
        Class<?> current = cls;
        while (current != null) {
            for (String name : names) {
                try {
                    return current.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    static boolean isAndroidActivity(Object object) {
        try {
            return object != null && Class.forName("android.app.Activity").isInstance(object);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static void runOnAndroidUiThread(Object activity, Runnable runnable) throws Exception {
        Method runOnUiThread = activity.getClass().getMethod("runOnUiThread", Runnable.class);
        runOnUiThread.invoke(activity, runnable);
    }

    static void invokeUnchecked(Method method, Object target, Object... args) {
        try {
            method.invoke(target, args);
        } catch (Throwable t) {
            setError(error("gui.sparkle_morpher.import.error.open_picker", safeMessage(t)));
            YesSteveModel.LOGGER.warn("[SM] Failed to invoke Android file picker", t);
        }
    }

    static void completeFile(File selected) throws Exception {
        if (selected.isDirectory()) {
            completeDirectory(selected.toPath());
            return;
        }
        try (InputStream in = new FileInputStream(selected)) {
            complete(new ModelImportFilePicker.PickedFile(selected.getName(), readAllBytes(in)));
        }
    }

    public static ModelImportFilePicker.PickedFile packDirectory(Path dir) throws IOException {
        return packDirectory(dir, -1);
    }

    public static ModelImportFilePicker.PickedFile packDirectory(Path dir, int maxPackedBytes) throws IOException {
        if (dir == null || !Files.isDirectory(dir)) {
            throw new IOException("Not a directory: " + dir);
        }
        Path root = dir.toAbsolutePath().normalize();
        String baseName = root.getFileName() == null ? "model" : root.getFileName().toString();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AtomicInteger count = new AtomicInteger();
        long perfStart = PerformanceProfiler.start();
        try (ZipOutputStream zip = new ZipOutputStream(out);
             Stream<Path> stream = Files.walk(root, MAX_FOLDER_DEPTH)) {
            for (var iterator = stream.filter(Files::isRegularFile).iterator(); iterator.hasNext(); ) {
                Path path = iterator.next();
                Path normalized = path.toAbsolutePath().normalize();
                if (!normalized.startsWith(root)) {
                    throw new IOException("Invalid path outside import folder: " + path);
                }
                if (count.incrementAndGet() > MAX_FOLDER_FILE_COUNT) {
                    throw new IOException("Too many files in model folder");
                }
                String entryName = root.relativize(normalized).toString().replace('\\', '/');
                if (entryName.isBlank() || entryName.startsWith("/") || entryName.contains("../")) {
                    throw new IOException("Invalid zip entry path: " + entryName);
                }
                ZipEntry entry = new ZipEntry(entryName);
                zip.putNextEntry(entry);
                Files.copy(normalized, zip);
                zip.closeEntry();
                checkPackedSize(out, maxPackedBytes);
            }
        }
        checkPackedSize(out, maxPackedBytes);
        PerformanceProfiler.logElapsed("pack_directory", baseName, perfStart,
                "files=" + count.get() + " bytes=" + out.size());
        return new ModelImportFilePicker.PickedFile(baseName + ".zip", out.toByteArray());
    }

    private static void checkPackedSize(ByteArrayOutputStream out, int maxPackedBytes) throws ModelImportFilePicker.PackedSizeLimitExceededException {
        if (maxPackedBytes > 0 && out.size() > maxPackedBytes) {
            throw new ModelImportFilePicker.PackedSizeLimitExceededException(maxPackedBytes);
        }
    }

    private static void completeDirectory(Path dir) throws IOException {
        complete(packDirectory(dir));
    }

    static boolean isImportPath(Path path) {
        Path fileName = path.getFileName();
        return fileName != null && isImportFileName(fileName.toString());
    }

    static boolean isModelFolder(Path path) {
        if (path == null || !Files.isDirectory(path)) {
            return false;
        }
        return Files.isRegularFile(path.resolve("ysm.json"))
                || (Files.isRegularFile(path.resolve("main.json")) && Files.isRegularFile(path.resolve("arm.json")));
    }

    public static boolean isImportFileName(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String extension : IMPORT_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    static long nowMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    static synchronized void complete(ModelImportFilePicker.PickedFile file) {
        completed.add(file);
        picking = false;
    }

    static synchronized void setError(Component error) {
        lastError = error == null ? Component.empty() : error;
        picking = false;
    }

    static synchronized void clearLastError() {
        lastError = Component.empty();
    }

    static Component error(String key, Object... args) {
        return Component.translatable(key, args);
    }

    static synchronized void rememberPickerProbe(String message) {
        YesSteveModel.LOGGER.info("[SM] Android file picker probe: {}", message);
    }

    static String safeMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private static boolean isEmpty(Component component) {
        return component == null || component.getString().isEmpty();
    }

    static byte[] readAllBytes(InputStream input) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[32_000];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private FilePickerCoordinator() {
    }
}
