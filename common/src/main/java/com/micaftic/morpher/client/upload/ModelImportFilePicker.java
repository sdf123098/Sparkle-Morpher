package com.micaftic.morpher.client.upload;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.util.PerformanceProfiler;
import dev.architectury.platform.Platform;
import net.minecraft.network.chat.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ModelImportFilePicker {
    private static final String[] IMPORT_SUFFIXES = {"ysm", "zip", "bbmodel"};
    private static final String[] IMPORT_EXTENSIONS = {".ysm", ".zip", ".bbmodel"};
    private static final String FILE_FILTER_PATTERN = "*.ysm;*.zip;*.bbmodel";
    private static final String FILE_FILTER_DESCRIPTION = "Model (*.ysm, *.zip, *.bbmodel)";
    private static final int MAX_FOLDER_DEPTH = 16;
    private static final int MAX_FOLDER_FILE_COUNT = 4096;
    private static final int CLIPBOARD_OPEN = 2002;
    private static final long PICKER_REOPEN_GRACE_MS = 1_000L;
    private static final long LAUNCHER_BRIDGE_TIMEOUT_MS = 5 * 60_000L;
    private static final long LAUNCHER_BRIDGE_STABLE_MS = 750L;
    private static final long LAUNCHER_BRIDGE_AFTER_IMPORT_IDLE_MS = 2_000L;
    private static final Queue<PickedFile> completed = new ArrayDeque<>();
    private static final Map<Path, FileStamp> launcherBridgeBaseline = new HashMap<>();
    private static final Map<Path, FileCandidate> launcherBridgeCandidates = new HashMap<>();
    private static final Map<Path, DirectoryStamp> launcherBridgeDirectoryBaseline = new HashMap<>();
    private static final Map<Path, DirectoryCandidate> launcherBridgeDirectoryCandidates = new HashMap<>();
    private static volatile boolean picking = false;
    private static volatile Component lastError = Component.empty();
    private static volatile Path launcherBridgeImportDir = null;
    private static volatile long launcherBridgePollUntilMs = 0L;
    private static volatile long launcherBridgeLastActivityMs = 0L;
    private static volatile boolean launcherBridgeImportedAny = false;
    private static volatile long pickerStartedMs = 0L;
    private static final AtomicInteger requestIds = new AtomicInteger(0x7A51);

    private ModelImportFilePicker() {
    }

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

    public static synchronized PickedFile pollCompleted() {
        pollLauncherBridgeImports();
        return completed.poll();
    }

    public static synchronized Component pickYsmFile() {
        if (picking) {
            if (canReplaceActivePicker()) {
                stopLauncherBridgeImport();
                picking = false;
                lastError = Component.empty();
            } else {
                return Component.translatable("gui.sparkle_morpher.import.error.picker_open");
            }
        }
        picking = true;
        pickerStartedMs = nowMillis();
        lastError = Component.empty();

        if (tryStartFclSystemPicker()) {
            return null;
        }

        if (tryStartSystemPicker()) {
            return null;
        }

        if (tryStartAndroidXPicker()) {
            return null;
        }

        if (tryStartFclPicker()) {
            return null;
        }

        if (tryStartLauncherBridgeImport()) {
            return null;
        }

        if (tryStartTinyFileDialog()) {
            return null;
        }

        if (tryStartJvmFileDialog()) {
            return null;
        }

        picking = false;
        pickerStartedMs = 0L;
        return isEmpty(lastError) ? Component.translatable("gui.sparkle_morpher.import.error.no_android_picker") : lastError;
    }

    public static synchronized void cancelPicking() {
        stopLauncherBridgeImport();
        completed.clear();
        picking = false;
        pickerStartedMs = 0L;
        lastError = Component.empty();
    }

    private static boolean canReplaceActivePicker() {
        if (launcherBridgeImportDir != null) {
            return true;
        }
        return nowMillis() - pickerStartedMs > PICKER_REOPEN_GRACE_MS;
    }

    private static boolean tryStartFclSystemPicker() {
        try {
            Object activity = getFclCurrentActivity();
            if (activity == null) {
                rememberPickerProbe("FCL current activity unavailable");
                return false;
            }

            Class<?> listenerClass = Class.forName("com.tungsten.fcllibrary.component.ResultListener$Listener");
            Object listener = Proxy.newProxyInstance(
                    listenerClass.getClassLoader(),
                    new Class<?>[]{listenerClass},
                    (proxy, method, args) -> {
                        if ("onActivityResult".equals(method.getName())) {
                            int resultCode = args != null && args.length > 1 ? (Integer) args[1] : 0;
                            Object intent = args != null && args.length > 2 ? args[2] : null;
                            handleSystemIntentResult(activity, resultCode, intent);
                        }
                        return null;
                    }
            );
            int requestCode = requestIds.incrementAndGet();
            Method start = Class.forName("com.tungsten.fcllibrary.component.ResultListener")
                    .getDeclaredMethod("startActivityForResult", Class.forName("android.app.Activity"), Class.forName("android.content.Intent"), int.class, listenerClass);
            Object intent = createOpenDocumentIntent();
            runOnAndroidUiThread(activity, () -> invokeUnchecked(start, null, activity, intent, requestCode, listener));
            clearLastError();
            return true;
        } catch (ClassNotFoundException e) {
            rememberPickerProbe("FCL ResultListener bridge unavailable: " + safeMessage(e));
            return false;
        } catch (Throwable t) {
            lastError = error("gui.sparkle_morpher.import.error.open_picker", safeMessage(t));
            YesSteveModel.LOGGER.warn("[SM] Failed to open FCL system file picker", t);
            return false;
        }
    }

    private static boolean tryStartFclPicker() {
        try {
            Object activity = getFclCurrentActivity();
            if (activity == null) {
                rememberPickerProbe("FCL current activity unavailable for FileBrowser");
                return false;
            }

            Class<?> builderClass = Class.forName("com.tungsten.fcllibrary.browser.FileBrowser$Builder");
            Object builder = builderClass.getConstructor(Class.forName("android.content.Context")).newInstance(activity);
            builderClass.getDeclaredMethod("setTitle", String.class).invoke(builder, Component.translatable("gui.sparkle_morpher.import.title").getString());
            Class<?> libModeClass = Class.forName("com.tungsten.fcllibrary.browser.options.LibMode");
            Object fileChooser = Enum.valueOf((Class<Enum>) libModeClass.asSubclass(Enum.class), "FILE_CHOOSER");
            builderClass.getDeclaredMethod("setLibMode", libModeClass).invoke(builder, fileChooser);
            Class<?> selectionModeClass = Class.forName("com.tungsten.fcllibrary.browser.options.SelectionMode");
            Object multipleSelection = Enum.valueOf((Class<Enum>) selectionModeClass.asSubclass(Enum.class), "MULTIPLE_SELECTION");
            builderClass.getDeclaredMethod("setSelectionMode", selectionModeClass).invoke(builder, multipleSelection);

            ArrayList<String> suffixes = new ArrayList<>();
            suffixes.addAll(List.of(IMPORT_SUFFIXES));
            builderClass.getDeclaredMethod("setSuffix", ArrayList.class).invoke(builder, suffixes);
            Object browser = builderClass.getDeclaredMethod("create").invoke(builder);
            Class<?> fileBrowserClass = Class.forName("com.tungsten.fcllibrary.browser.FileBrowser");

            Class<?> listenerClass = Class.forName("com.tungsten.fcllibrary.component.ResultListener$Listener");
            Object listener = Proxy.newProxyInstance(
                    listenerClass.getClassLoader(),
                    new Class<?>[]{listenerClass},
                    (proxy, method, args) -> {
                        if ("onActivityResult".equals(method.getName())) {
                            handleFclResult(args);
                        }
                        return null;
                    }
            );
            int requestCode = requestIds.incrementAndGet();
            Method browse = fileBrowserClass.getDeclaredMethod("browse", Class.forName("android.app.Activity"), int.class, listenerClass);
            runOnAndroidUiThread(activity, () -> invokeUnchecked(browse, browser, activity, requestCode, listener));
            clearLastError();
            return true;
        } catch (ClassNotFoundException e) {
            rememberPickerProbe("FCL FileBrowser bridge unavailable: " + safeMessage(e));
            return false;
        } catch (Throwable t) {
            lastError = error("gui.sparkle_morpher.import.error.open_picker", safeMessage(t));
            YesSteveModel.LOGGER.warn("[SM] Failed to open FCL file picker", t);
            return false;
        }
    }

    private static boolean tryStartAndroidXPicker() {
        Object activity = getGenericAndroidActivity();
        if (activity == null) {
            rememberPickerProbe("AndroidX picker skipped: no Android activity");
            return false;
        }
        try {
            activity.getClass().getMethod("getActivityResultRegistry");
            Class.forName("androidx.activity.result.ActivityResultCallback");
            Class.forName("androidx.activity.result.contract.ActivityResultContract");
            Class.forName("androidx.activity.result.contract.ActivityResultContracts$GetMultipleContents");
            runOnAndroidUiThread(activity, () -> startAndroidXPickerOnUiThread(activity));
            clearLastError();
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            rememberPickerProbe("AndroidX GetMultipleContents unavailable: " + safeMessage(e));
            return false;
        } catch (Throwable t) {
            lastError = error("gui.sparkle_morpher.import.error.open_picker", safeMessage(t));
            YesSteveModel.LOGGER.warn("[SM] Failed to open AndroidX file picker", t);
            return false;
        }
    }

    private static boolean tryStartSystemPicker() {
        Object activity = getGenericAndroidActivity();
        if (activity == null) {
            rememberPickerProbe("system document picker skipped: no Android activity");
            return false;
        }
        try {
            activity.getClass().getMethod("getActivityResultRegistry");
            Class.forName("androidx.activity.result.ActivityResultCallback");
            Class.forName("androidx.activity.result.contract.ActivityResultContract");
            Class.forName("androidx.activity.result.contract.ActivityResultContracts$StartActivityForResult");
            runOnAndroidUiThread(activity, () -> startSystemPickerWithAndroidXOnUiThread(activity));
            clearLastError();
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            rememberPickerProbe("AndroidX StartActivityForResult unavailable: " + safeMessage(e));
            return false;
        } catch (Throwable t) {
            lastError = error("gui.sparkle_morpher.import.error.open_picker", safeMessage(t));
            YesSteveModel.LOGGER.warn("[SM] Failed to open system Android file picker", t);
            return false;
        }
    }

    private static boolean tryStartJvmFileDialog() {
        if (isAndroidRuntime()) {
            rememberPickerProbe("JVM file picker skipped on Android runtime");
            return false;
        }
        if (isHeadlessGraphicsEnvironment()) {
            rememberPickerProbe("JVM file picker skipped: headless graphics environment");
            return false;
        }
        try {
            Class.forName("java.awt.FileDialog");
            Class.forName("java.awt.Frame");
            CompletableFuture.runAsync(ModelImportFilePicker::showJvmFileDialog);
            clearLastError();
            return true;
        } catch (Throwable t) {
            rememberPickerProbe("AWT FileDialog unavailable: " + safeMessage(t));
        }
        try {
            Class.forName("javax.swing.JFileChooser");
            CompletableFuture.runAsync(ModelImportFilePicker::showJvmFileDialog);
            clearLastError();
            return true;
        } catch (Throwable t) {
            rememberPickerProbe("Swing JFileChooser unavailable: " + safeMessage(t));
        }
        return false;
    }

    private static boolean tryStartTinyFileDialog() {
        if (isAndroidRuntime()) {
            rememberPickerProbe("tinyfd file picker skipped on Android runtime");
            return false;
        }
        try {
            Class<?> pointerBufferClass = Class.forName("org.lwjgl.PointerBuffer");
            Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs")
                    .getMethod("tinyfd_openFileDialog", CharSequence.class, CharSequence.class,
                            pointerBufferClass, CharSequence.class, boolean.class);
            CompletableFuture.runAsync(ModelImportFilePicker::showTinyFileDialog);
            clearLastError();
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            rememberPickerProbe("LWJGL tinyfd picker unavailable: " + safeMessage(e));
            return false;
        } catch (Throwable t) {
            rememberPickerProbe("LWJGL tinyfd picker failed: " + safeMessage(t));
            return false;
        }
    }

    private static boolean tryStartLauncherBridgeImport() {
        if (!isAndroidRuntime()) {
            rememberPickerProbe("launcher folder bridge skipped outside Android runtime");
            return false;
        }
        try {
            Path dir = getLauncherBridgeImportDir();
            Files.createDirectories(dir);
            Path normalizedDir = dir.toAbsolutePath().normalize();
            cleanupLauncherBridgeImportFiles(normalizedDir);
            Map<Path, FileStamp> baseline = snapshotImportFiles(normalizedDir);
            Map<Path, DirectoryStamp> directoryBaseline = snapshotModelFolders(normalizedDir);
            String uri = toDirectoryFileUri(normalizedDir);
            Method nativeClipboard = Class.forName("org.lwjgl.glfw.CallbackBridge")
                    .getDeclaredMethod("nativeClipboard", int.class, byte[].class);
            nativeClipboard.invoke(null, CLIPBOARD_OPEN, uri.getBytes(StandardCharsets.UTF_8));
            launcherBridgeImportDir = normalizedDir;
            launcherBridgePollUntilMs = nowMillis() + LAUNCHER_BRIDGE_TIMEOUT_MS;
            launcherBridgeLastActivityMs = nowMillis();
            launcherBridgeImportedAny = false;
            launcherBridgeBaseline.clear();
            launcherBridgeBaseline.putAll(baseline);
            launcherBridgeCandidates.clear();
            launcherBridgeDirectoryBaseline.clear();
            launcherBridgeDirectoryBaseline.putAll(directoryBaseline);
            launcherBridgeDirectoryCandidates.clear();
            clearLastError();
            rememberPickerProbe("launcher folder bridge opened: " + uri);
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            rememberPickerProbe("launcher folder bridge unavailable: " + safeMessage(e));
            return false;
        } catch (InvocationTargetException e) {
            rememberPickerProbe("launcher folder bridge failed: " + safeMessage(e));
            return false;
        } catch (IOException e) {
            lastError = error("gui.sparkle_morpher.import.error.open_picker", safeMessage(e));
            YesSteveModel.LOGGER.warn("[SM] Failed to prepare launcher import folder", e);
            return false;
        } catch (Throwable t) {
            rememberPickerProbe("launcher folder bridge failed: " + safeMessage(t));
            return false;
        }
    }

    private static boolean isAndroidRuntime() {
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

    private static boolean isHeadlessGraphicsEnvironment() {
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

    private static boolean containsIgnoreCase(String text, String needle) {
        return text != null && needle != null && text.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static Object getFclCurrentActivity() {
        try {
            Class<?> appClass = Class.forName("com.tungsten.fcl.FCLApplication");
            return appClass.getDeclaredMethod("getCurrentActivity").invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object getGenericAndroidActivity() {
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

    private static Object getZalithGlobalActivity() {
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

    private static Object getZalithLauncherActivity() {
        try {
            Object launcher = Class.forName("com.movtery.zalithlauncher.bridge.ZLNativeInvoker")
                    .getDeclaredMethod("getStaticLauncher")
                    .invoke(null);
            return findAndroidActivityField(launcher);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object getActivityThreadActivity() {
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

    private static Object getActivityFromThreadRecord(Object record) {
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

    private static Object findAndroidActivityField(Object holder) {
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

    private static Field findField(Class<?> cls, String... names) {
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

    private static boolean isAndroidActivity(Object object) {
        try {
            return object != null && Class.forName("android.app.Activity").isInstance(object);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void handleFclResult(Object[] args) {
        CompletableFuture.runAsync(() -> {
            try {
                int resultCode = (Integer) args[1];
                Object intent = args[2];
                if (resultCode != getAndroidActivityResultOk() || intent == null) {
                    setError(Component.translatable("gui.sparkle_morpher.import.error.cancelled"));
                    return;
                }
                Class<?> fileBrowserClass = Class.forName("com.tungsten.fcllibrary.browser.FileBrowser");
                Object selected = fileBrowserClass.getDeclaredMethod("getSelectedFiles", Class.forName("android.content.Intent")).invoke(null, intent);
                if (!(selected instanceof List<?> list) || list.isEmpty()) {
                    setError(Component.translatable("gui.sparkle_morpher.import.error.no_file"));
                    return;
                }
                Object activity = getFclCurrentActivity();
                if (activity == null) {
                    setError(Component.translatable("gui.sparkle_morpher.import.error.no_activity"));
                    return;
                }
                for (Object selectedFile : list) {
                    readAndroidUri(activity, String.valueOf(selectedFile));
                }
            } catch (Throwable t) {
                setError(error("gui.sparkle_morpher.import.error.read_selected", safeMessage(t)));
                YesSteveModel.LOGGER.warn("[SM] Failed to read selected Android file", t);
            }
        });
    }

    private static void handleAndroidXResult(Object activity, Object uris, Object launcher) {
        unregisterAndroidXPicker(launcher);
        CompletableFuture.runAsync(() -> {
            try {
                if (!(uris instanceof List<?> list) || list.isEmpty()) {
                    setError(Component.translatable("gui.sparkle_morpher.import.error.cancelled"));
                    return;
                }
                for (Object uri : list) {
                    readAndroidUri(activity, String.valueOf(uri));
                }
            } catch (Throwable t) {
                setError(error("gui.sparkle_morpher.import.error.read_selected", safeMessage(t)));
                YesSteveModel.LOGGER.warn("[SM] Failed to read selected Android file", t);
            }
        });
    }

    private static void handleSystemPickerResult(Object activity, Object activityResult, Object launcher) {
        unregisterAndroidXPicker(launcher);
        CompletableFuture.runAsync(() -> {
            try {
                if (activityResult == null) {
                    setError(Component.translatable("gui.sparkle_morpher.import.error.cancelled"));
                    return;
                }
                int resultCode = (Integer) activityResult.getClass().getMethod("getResultCode").invoke(activityResult);
                Object intent = activityResult.getClass().getMethod("getData").invoke(activityResult);
                if (resultCode != getAndroidActivityResultOk() || intent == null) {
                    setError(Component.translatable("gui.sparkle_morpher.import.error.cancelled"));
                    return;
                }
                readAndroidIntentData(activity, intent);
            } catch (Throwable t) {
                setError(error("gui.sparkle_morpher.import.error.read_selected", safeMessage(t)));
                YesSteveModel.LOGGER.warn("[SM] Failed to read selected Android file", t);
            }
        });
    }

    private static void handleSystemIntentResult(Object activity, int resultCode, Object intent) {
        CompletableFuture.runAsync(() -> {
            try {
                if (resultCode != getAndroidActivityResultOk() || intent == null) {
                    setError(Component.translatable("gui.sparkle_morpher.import.error.cancelled"));
                    return;
                }
                readAndroidIntentData(activity, intent);
            } catch (Throwable t) {
                setError(error("gui.sparkle_morpher.import.error.read_selected", safeMessage(t)));
                YesSteveModel.LOGGER.warn("[SM] Failed to read selected Android file", t);
            }
        });
    }

    private static void readAndroidIntentData(Object activity, Object intent) throws Exception {
        List<Object> uris = getAndroidIntentUris(intent);
        if (uris.isEmpty()) {
            setError(Component.translatable("gui.sparkle_morpher.import.error.no_file"));
            return;
        }
        for (Object uri : uris) {
            takePersistableReadPermission(activity, intent, uri);
            readAndroidUri(activity, String.valueOf(uri));
        }
    }

    private static List<Object> getAndroidIntentUris(Object intent) throws Exception {
        ArrayList<Object> uris = new ArrayList<>();
        Object clipData = intent.getClass().getMethod("getClipData").invoke(intent);
        if (clipData != null) {
            int itemCount = (Integer) clipData.getClass().getMethod("getItemCount").invoke(clipData);
            for (int i = 0; i < itemCount; i++) {
                Object item = clipData.getClass().getMethod("getItemAt", int.class).invoke(clipData, i);
                Object uri = item == null ? null : item.getClass().getMethod("getUri").invoke(item);
                if (uri != null) {
                    uris.add(uri);
                }
            }
        }
        Object data = intent.getClass().getMethod("getData").invoke(intent);
        if (data != null && uris.stream().noneMatch(uri -> String.valueOf(uri).equals(String.valueOf(data)))) {
            uris.add(data);
        }
        return uris;
    }

    private static void startAndroidXPickerOnUiThread(Object activity) {
        try {
            Object registry = activity.getClass().getMethod("getActivityResultRegistry").invoke(activity);
            Class<?> callbackClass = Class.forName("androidx.activity.result.ActivityResultCallback");
            Class<?> contractClass = Class.forName("androidx.activity.result.contract.ActivityResultContract");
            Object contract = Class.forName("androidx.activity.result.contract.ActivityResultContracts$GetMultipleContents")
                    .getDeclaredConstructor()
                    .newInstance();

            Object[] launcherHolder = new Object[1];
            Object callback = Proxy.newProxyInstance(
                    callbackClass.getClassLoader(),
                    new Class<?>[]{callbackClass},
                    (proxy, method, args) -> {
                        if ("onActivityResult".equals(method.getName())) {
                            Object uris = args != null && args.length > 0 ? args[0] : null;
                            handleAndroidXResult(activity, uris, launcherHolder[0]);
                        }
                        return null;
                    }
            );
            String key = "openysm_ysm_import_" + requestIds.incrementAndGet();
            Object launcher = registry.getClass()
                    .getMethod("register", String.class, contractClass, callbackClass)
                    .invoke(registry, key, contract, callback);
            launcherHolder[0] = launcher;
            launchAndroidXPicker(launcher);
        } catch (Throwable t) {
            setError(error("gui.sparkle_morpher.import.error.open_picker", safeMessage(t)));
            YesSteveModel.LOGGER.warn("[SM] Failed to start AndroidX file picker", t);
        }
    }

    private static void startSystemPickerWithAndroidXOnUiThread(Object activity) {
        try {
            Object registry = activity.getClass().getMethod("getActivityResultRegistry").invoke(activity);
            Class<?> callbackClass = Class.forName("androidx.activity.result.ActivityResultCallback");
            Class<?> contractClass = Class.forName("androidx.activity.result.contract.ActivityResultContract");
            Object contract = Class.forName("androidx.activity.result.contract.ActivityResultContracts$StartActivityForResult")
                    .getDeclaredConstructor()
                    .newInstance();

            Object[] launcherHolder = new Object[1];
            Object callback = Proxy.newProxyInstance(
                    callbackClass.getClassLoader(),
                    new Class<?>[]{callbackClass},
                    (proxy, method, args) -> {
                        if ("onActivityResult".equals(method.getName())) {
                            Object result = args != null && args.length > 0 ? args[0] : null;
                            handleSystemPickerResult(activity, result, launcherHolder[0]);
                        }
                        return null;
                    }
            );
            String key = "openysm_ysm_import_document_" + requestIds.incrementAndGet();
            Object launcher = registry.getClass()
                    .getMethod("register", String.class, contractClass, callbackClass)
                    .invoke(registry, key, contract, callback);
            launcherHolder[0] = launcher;
            launchAndroidXPicker(launcher, createOpenDocumentIntent());
        } catch (Throwable t) {
            setError(error("gui.sparkle_morpher.import.error.open_picker", safeMessage(t)));
            YesSteveModel.LOGGER.warn("[SM] Failed to start system Android file picker", t);
        }
    }

    private static int getAndroidActivityResultOk() throws ReflectiveOperationException {
        return Class.forName("android.app.Activity").getField("RESULT_OK").getInt(null);
    }

    private static void launchAndroidXPicker(Object launcher) {
        try {
            Class.forName("androidx.activity.result.ActivityResultLauncher")
                    .getMethod("launch", Object.class)
                    .invoke(launcher, "*/*");
        } catch (Throwable t) {
            unregisterAndroidXPicker(launcher);
            setError(error("gui.sparkle_morpher.import.error.open_picker", safeMessage(t)));
            YesSteveModel.LOGGER.warn("[SM] Failed to launch AndroidX file picker", t);
        }
    }

    private static void launchAndroidXPicker(Object launcher, Object input) {
        try {
            Class.forName("androidx.activity.result.ActivityResultLauncher")
                    .getMethod("launch", Object.class)
                    .invoke(launcher, input);
        } catch (Throwable t) {
            unregisterAndroidXPicker(launcher);
            setError(error("gui.sparkle_morpher.import.error.open_picker", safeMessage(t)));
            YesSteveModel.LOGGER.warn("[SM] Failed to launch AndroidX file picker", t);
        }
    }

    private static void unregisterAndroidXPicker(Object launcher) {
        if (launcher == null) {
            return;
        }
        try {
            Class.forName("androidx.activity.result.ActivityResultLauncher")
                    .getMethod("unregister")
                    .invoke(launcher);
        } catch (Throwable ignored) {
        }
    }

    private static Object createOpenDocumentIntent() throws Exception {
        Class<?> intentClass = Class.forName("android.content.Intent");
        Object intent = intentClass.getConstructor(String.class).newInstance(getIntentAction("ACTION_OPEN_DOCUMENT", "android.intent.action.OPEN_DOCUMENT"));
        intentClass.getMethod("addCategory", String.class).invoke(intent, getIntentCategory("CATEGORY_OPENABLE", "android.intent.category.OPENABLE"));
        intentClass.getMethod("setType", String.class).invoke(intent, "*/*");
        try {
            intentClass.getMethod("putExtra", String.class, String[].class).invoke(intent, getIntentExtra("EXTRA_MIME_TYPES", "android.intent.extra.MIME_TYPES"), new String[]{"application/octet-stream", "application/zip", "application/x-zip-compressed", "application/json", "text/plain"});
        } catch (Throwable ignored) {
        }
        intentClass.getMethod("putExtra", String.class, boolean.class).invoke(intent, getIntentExtra("EXTRA_ALLOW_MULTIPLE", "android.intent.extra.ALLOW_MULTIPLE"), true);
        intentClass.getMethod("addFlags", int.class).invoke(intent, getIntentFlag("FLAG_GRANT_READ_URI_PERMISSION", 1) | getIntentFlag("FLAG_GRANT_PERSISTABLE_URI_PERMISSION", 64));
        return intent;
    }

    private static String getIntentAction(String name, String fallback) {
        return getStaticString("android.content.Intent", name, fallback);
    }

    private static String getIntentCategory(String name, String fallback) {
        return getStaticString("android.content.Intent", name, fallback);
    }

    private static String getIntentExtra(String name, String fallback) {
        return getStaticString("android.content.Intent", name, fallback);
    }

    private static int getIntentFlag(String name, int fallback) {
        try {
            Field field = Class.forName("android.content.Intent").getField(name);
            return field.getInt(null);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String getStaticString(String className, String name, String fallback) {
        try {
            Field field = Class.forName(className).getField(name);
            if (Modifier.isStatic(field.getModifiers())) {
                Object value = field.get(null);
                if (value != null) {
                    return String.valueOf(value);
                }
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static void takePersistableReadPermission(Object context, Object intent, Object uri) {
        try {
            int flags = (Integer) intent.getClass().getMethod("getFlags").invoke(intent);
            int readFlag = getIntentFlag("FLAG_GRANT_READ_URI_PERMISSION", 1);
            int persistableFlag = getIntentFlag("FLAG_GRANT_PERSISTABLE_URI_PERMISSION", 64);
            if ((flags & persistableFlag) == 0) {
                return;
            }
            Object resolver = Class.forName("android.content.Context").getMethod("getContentResolver").invoke(context);
            Class.forName("android.content.ContentResolver")
                    .getMethod("takePersistableUriPermission", Class.forName("android.net.Uri"), int.class)
                    .invoke(resolver, uri, readFlag);
        } catch (Throwable ignored) {
        }
    }

    private static void readAndroidUri(Object context, String uriText) throws Exception {
        Class<?> uriClass = Class.forName("android.net.Uri");
        Object uri = uriClass.getDeclaredMethod("parse", String.class).invoke(null, uriText);
        String scheme = getAndroidUriScheme(uri);
        if ((scheme == null || scheme.isBlank()) && new File(uriText).isAbsolute()) {
            readAndroidFilePath(uri, uriText);
            return;
        }
        Object resolver = Class.forName("android.content.Context").getMethod("getContentResolver").invoke(context);
        InputStream input = (InputStream) Class.forName("android.content.ContentResolver").getMethod("openInputStream", uriClass).invoke(resolver, uri);
        if (input == null) {
            throw new IllegalStateException("Content resolver returned no stream");
        }
        String fileName = getAndroidDisplayName(context, resolver, uri);
        try (InputStream in = input) {
            byte[] data = readAllBytes(in);
            complete(new PickedFile(fileName, data));
        }
    }

    private static void readAndroidFilePath(Object uri, String path) throws Exception {
        File file = new File(path);
        String fileName = getAndroidLastPathSegment(uri);
        if (fileName.isBlank()) {
            fileName = file.getName();
        }
        try (InputStream in = new FileInputStream(file)) {
            byte[] data = readAllBytes(in);
            complete(new PickedFile(fileName, data));
        }
    }

    private static void showTinyFileDialog() {
        try {
            String selected = openTinyFileDialog();
            if (selected == null || selected.isBlank()) {
                setError(Component.translatable("gui.sparkle_morpher.import.error.cancelled"));
                return;
            }
            for (String selectedPath : splitTinyFileDialogSelection(selected)) {
                if (!selectedPath.isBlank()) {
                    completeFile(new File(selectedPath));
                }
            }
        } catch (Throwable t) {
            setError(error("gui.sparkle_morpher.import.error.open_picker", safeMessage(t)));
            YesSteveModel.LOGGER.warn("[SM] Failed to open LWJGL tinyfd file picker", t);
        }
    }

    private static String openTinyFileDialog() throws Exception {
        Class<?> memoryUtilClass = Class.forName("org.lwjgl.system.MemoryUtil");
        Class<?> pointerBufferClass = Class.forName("org.lwjgl.PointerBuffer");
        Class<?> tinyFileDialogsClass = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
        Method memUTF8 = memoryUtilClass.getMethod("memUTF8", CharSequence.class);
        Method memFreeBuffer = memoryUtilClass.getMethod("memFree", java.nio.Buffer.class);
        Method memFreeCustomBuffer = memoryUtilClass.getMethod("memFree", Class.forName("org.lwjgl.system.CustomBuffer"));
        Method memAllocPointer = memoryUtilClass.getMethod("memAllocPointer", int.class);
        Method pointerPut = pointerBufferClass.getMethod("put", int.class, java.nio.ByteBuffer.class);
        Method open = tinyFileDialogsClass.getMethod("tinyfd_openFileDialog",
                CharSequence.class, CharSequence.class, pointerBufferClass, CharSequence.class, boolean.class);

        java.nio.ByteBuffer ysmPattern = (java.nio.ByteBuffer) memUTF8.invoke(null, "*.ysm");
        java.nio.ByteBuffer zipPattern = (java.nio.ByteBuffer) memUTF8.invoke(null, "*.zip");
        java.nio.ByteBuffer bbmodelPattern = (java.nio.ByteBuffer) memUTF8.invoke(null, "*.bbmodel");
        Object filters = memAllocPointer.invoke(null, 3);
        try {
            pointerPut.invoke(filters, 0, ysmPattern);
            pointerPut.invoke(filters, 1, zipPattern);
            pointerPut.invoke(filters, 2, bbmodelPattern);
            return (String) open.invoke(null,
                    Component.translatable("gui.sparkle_morpher.import.title").getString(),
                    "",
                    filters,
                    FILE_FILTER_DESCRIPTION,
                    true);
        } finally {
            try {
                memFreeCustomBuffer.invoke(null, filters);
            } finally {
                memFreeBuffer.invoke(null, ysmPattern);
                memFreeBuffer.invoke(null, zipPattern);
                memFreeBuffer.invoke(null, bbmodelPattern);
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

    private static void showJvmFileDialog() {
        Throwable awtError = null;
        try {
            if (showAwtFileDialog()) {
                return;
            }
        } catch (Throwable t) {
            awtError = t;
            rememberPickerProbe("AWT FileDialog failed: " + safeMessage(t));
        }
        try {
            if (showSwingFileChooser()) {
                return;
            }
        } catch (Throwable t) {
            if (awtError != null) {
                t.addSuppressed(awtError);
            }
            setError(error("gui.sparkle_morpher.import.error.open_picker", safeMessage(t)));
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
        fileDialogClass.getMethod("setFilenameFilter", FilenameFilter.class).invoke(dialog, (FilenameFilter) (dir, name) -> isImportFileName(name));
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
                    completeFile(selected);
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
            setError(Component.translatable("gui.sparkle_morpher.import.error.cancelled"));
            return true;
        }
        File selected = new File(directory == null ? "" : String.valueOf(directory), String.valueOf(file));
        completeFile(selected);
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
                    .newInstance(FILE_FILTER_DESCRIPTION, new String[]{"ysm", "zip", "bbmodel"});
            chooserClass.getMethod("setFileFilter", Class.forName("javax.swing.filechooser.FileFilter")).invoke(chooser, filter);
        } catch (Throwable t) {
            rememberPickerProbe("Swing file filter unavailable: " + safeMessage(t));
        }
        int result = (Integer) chooserClass.getMethod("showOpenDialog", Class.forName("java.awt.Component")).invoke(chooser, new Object[]{null});
        int approve = chooserClass.getField("APPROVE_OPTION").getInt(null);
        if (result != approve) {
            setError(Component.translatable("gui.sparkle_morpher.import.error.cancelled"));
            return true;
        }
        Object selectedFiles = chooserClass.getMethod("getSelectedFiles").invoke(chooser);
        if (selectedFiles instanceof File[] files && files.length > 0) {
            for (File file : files) {
                completeFile(file);
            }
            return true;
        }
        Object selectedFile = chooserClass.getMethod("getSelectedFile").invoke(chooser);
        if (!(selectedFile instanceof File file)) {
            setError(Component.translatable("gui.sparkle_morpher.import.error.no_file"));
            return true;
        }
        completeFile(file);
        return true;
    }

    private static void completeFile(File selected) throws Exception {
        if (selected.isDirectory()) {
            completeDirectory(selected.toPath());
            return;
        }
        try (InputStream in = new FileInputStream(selected)) {
            complete(new PickedFile(selected.getName(), readAllBytes(in)));
        }
    }

    public static PickedFile packDirectory(Path dir) throws IOException {
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
            }
        }
        PerformanceProfiler.logElapsed("pack_directory", baseName, perfStart,
                "files=" + count.get() + " bytes=" + out.size());
        return new PickedFile(baseName + ".zip", out.toByteArray());
    }

    private static void completeDirectory(Path dir) throws IOException {
        complete(packDirectory(dir));
    }

    private static Path getLauncherBridgeImportDir() {
        return Platform.getConfigFolder().resolve(YesSteveModel.MOD_ID).resolve("import");
    }

    private static String toDirectoryFileUri(Path dir) {
        String uri = dir.toUri().toString();
        return uri.endsWith("/") ? uri : uri + "/";
    }

    private static void pollLauncherBridgeImports() {
        Path dir = launcherBridgeImportDir;
        if (dir == null || launcherBridgePollUntilMs <= 0L) {
            return;
        }
        long now = nowMillis();
        if (now > launcherBridgePollUntilMs) {
            stopLauncherBridgeImport();
            if (!launcherBridgeImportedAny) {
                setError(Component.translatable("gui.sparkle_morpher.import.error.launcher_bridge_timeout"));
            }
            return;
        }
        try (Stream<Path> paths = Files.walk(dir, 3)) {
            for (Path path : paths
                    .filter(Files::isRegularFile)
                    .filter(ModelImportFilePicker::isImportPath)
                    .toList()) {
                tryCompleteLauncherBridgeFile(path.toAbsolutePath().normalize(), now);
            }
            try (Stream<Path> folders = Files.walk(dir, 3)) {
                for (Path path : folders
                        .filter(Files::isDirectory)
                        .filter(path -> !path.equals(dir))
                        .filter(ModelImportFilePicker::isModelFolder)
                        .toList()) {
                    tryCompleteLauncherBridgeFolder(path.toAbsolutePath().normalize(), now);
                }
            }
            if (launcherBridgeImportedAny && now - launcherBridgeLastActivityMs > LAUNCHER_BRIDGE_AFTER_IMPORT_IDLE_MS) {
                stopLauncherBridgeImport();
            }
        } catch (IOException e) {
            stopLauncherBridgeImport();
            setError(error("gui.sparkle_morpher.import.error.read_file", safeMessage(e)));
            YesSteveModel.LOGGER.warn("[SM] Failed to scan launcher import folder", e);
        }
    }

    private static boolean tryCompleteLauncherBridgeFile(Path path, long now) {
        try {
            FileStamp stamp = fileStamp(path);
            FileStamp baseline = launcherBridgeBaseline.get(path);
            if (stamp.equals(baseline)) {
                return false;
            }
            FileCandidate candidate = launcherBridgeCandidates.get(path);
            if (candidate == null || !candidate.stamp().equals(stamp)) {
                launcherBridgeCandidates.put(path, new FileCandidate(stamp, now));
                launcherBridgeLastActivityMs = now;
                return false;
            }
            if (now - candidate.firstSeenMs() < LAUNCHER_BRIDGE_STABLE_MS) {
                return false;
            }
            byte[] data;
            try (InputStream in = Files.newInputStream(path)) {
                data = readAllBytes(in);
            }
            complete(new PickedFile(path.getFileName().toString(), data));
            launcherBridgeBaseline.put(path, stamp);
            launcherBridgeCandidates.remove(path);
            deleteLauncherBridgeFile(path);
            launcherBridgeImportedAny = true;
            launcherBridgeLastActivityMs = now;
            return true;
        } catch (Throwable t) {
            stopLauncherBridgeImport();
            setError(error("gui.sparkle_morpher.import.error.read_selected", safeMessage(t)));
            YesSteveModel.LOGGER.warn("[SM] Failed to read launcher-imported file {}", path, t);
            return true;
        }
    }

    private static boolean tryCompleteLauncherBridgeFolder(Path path, long now) {
        try {
            DirectoryStamp stamp = directoryStamp(path);
            DirectoryStamp baseline = launcherBridgeDirectoryBaseline.get(path);
            if (stamp.equals(baseline)) {
                return false;
            }
            DirectoryCandidate candidate = launcherBridgeDirectoryCandidates.get(path);
            if (candidate == null || !candidate.stamp().equals(stamp)) {
                launcherBridgeDirectoryCandidates.put(path, new DirectoryCandidate(stamp, now));
                launcherBridgeLastActivityMs = now;
                return false;
            }
            if (now - candidate.firstSeenMs() < LAUNCHER_BRIDGE_STABLE_MS) {
                return false;
            }
            complete(packDirectory(path));
            launcherBridgeDirectoryBaseline.put(path, stamp);
            launcherBridgeDirectoryCandidates.remove(path);
            launcherBridgeImportedAny = true;
            launcherBridgeLastActivityMs = now;
            return true;
        } catch (Throwable t) {
            stopLauncherBridgeImport();
            setError(error("gui.sparkle_morpher.import.error.read_selected", safeMessage(t)));
            YesSteveModel.LOGGER.warn("[SM] Failed to read launcher-imported folder {}", path, t);
            return true;
        }
    }

    private static void cleanupLauncherBridgeImportFiles(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir, 3)) {
            for (Path path : paths
                    .filter(Files::isRegularFile)
                    .filter(ModelImportFilePicker::isImportPath)
                    .toList()) {
                deleteLauncherBridgeFile(path.toAbsolutePath().normalize());
            }
        }
    }

    private static void deleteLauncherBridgeFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            YesSteveModel.LOGGER.warn("[SM] Failed to remove launcher import temp file {}", path, e);
        }
    }

    private static Map<Path, FileStamp> snapshotImportFiles(Path dir) throws IOException {
        Map<Path, FileStamp> result = new HashMap<>();
        try (Stream<Path> paths = Files.walk(dir, 3)) {
            for (Path path : paths
                    .filter(Files::isRegularFile)
                    .filter(ModelImportFilePicker::isImportPath)
                    .toList()) {
                Path normalized = path.toAbsolutePath().normalize();
                result.put(normalized, fileStamp(normalized));
            }
        }
        return result;
    }

    private static Map<Path, DirectoryStamp> snapshotModelFolders(Path dir) throws IOException {
        Map<Path, DirectoryStamp> result = new HashMap<>();
        try (Stream<Path> paths = Files.walk(dir, 3)) {
            for (Path path : paths
                    .filter(Files::isDirectory)
                    .filter(path -> !path.equals(dir))
                    .filter(ModelImportFilePicker::isModelFolder)
                    .toList()) {
                Path normalized = path.toAbsolutePath().normalize();
                result.put(normalized, directoryStamp(normalized));
            }
        }
        return result;
    }

    private static boolean isImportPath(Path path) {
        Path fileName = path.getFileName();
        return fileName != null && isImportFileName(fileName.toString());
    }

    private static boolean isModelFolder(Path path) {
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

    private static FileStamp fileStamp(Path path) throws IOException {
        return new FileStamp(Files.size(path), Files.getLastModifiedTime(path).toMillis());
    }

    private static DirectoryStamp directoryStamp(Path dir) throws IOException {
        long newest = Files.getLastModifiedTime(dir).toMillis();
        long fileCount = 0L;
        long totalSize = 0L;
        try (Stream<Path> paths = Files.walk(dir, MAX_FOLDER_DEPTH)) {
            for (var iterator = paths.filter(Files::isRegularFile).iterator(); iterator.hasNext(); ) {
                Path path = iterator.next();
                fileCount++;
                totalSize += Files.size(path);
                newest = Math.max(newest, Files.getLastModifiedTime(path).toMillis());
            }
        }
        return new DirectoryStamp(fileCount, totalSize, newest);
    }

    private static long nowMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    private static synchronized void stopLauncherBridgeImport() {
        launcherBridgeImportDir = null;
        launcherBridgePollUntilMs = 0L;
        launcherBridgeLastActivityMs = 0L;
        launcherBridgeImportedAny = false;
        launcherBridgeBaseline.clear();
        launcherBridgeCandidates.clear();
        launcherBridgeDirectoryBaseline.clear();
        launcherBridgeDirectoryCandidates.clear();
    }

    private static String getAndroidDisplayName(Object context, Object resolver, Object uri) {
        try {
            Object cursor = Class.forName("android.content.ContentResolver").getMethod("query", Class.forName("android.net.Uri"), String[].class, String.class, String[].class, String.class)
                    .invoke(resolver, uri, null, null, null, null);
            if (cursor != null) {
                try {
                    Class<?> cursorClass = Class.forName("android.database.Cursor");
                    Method moveToFirst = cursorClass.getMethod("moveToFirst");
                    if (Boolean.TRUE.equals(moveToFirst.invoke(cursor))) {
                        Class<?> openableColumns = Class.forName("android.provider.OpenableColumns");
                        String displayNameColumn = String.valueOf(openableColumns.getField("DISPLAY_NAME").get(null));
                        int columnIndex = (Integer) cursorClass.getMethod("getColumnIndex", String.class).invoke(cursor, displayNameColumn);
                        if (columnIndex >= 0) {
                            Object value = cursorClass.getMethod("getString", int.class).invoke(cursor, columnIndex);
                            if (value != null && !String.valueOf(value).isBlank()) {
                                return String.valueOf(value);
                            }
                        }
                    }
                } finally {
                    Class.forName("android.database.Cursor").getMethod("close").invoke(cursor);
                }
            }
        } catch (Throwable ignored) {
        }
        String lastPathSegment = getAndroidLastPathSegment(uri);
        if (!lastPathSegment.isBlank()) {
            return lastPathSegment;
        }
        return "imported.bin";
    }

    private static String getAndroidUriScheme(Object uri) {
        try {
            Object scheme = Class.forName("android.net.Uri").getMethod("getScheme").invoke(uri);
            return scheme == null ? "" : String.valueOf(scheme);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String getAndroidLastPathSegment(Object uri) {
        try {
            Object lastPathSegment = Class.forName("android.net.Uri").getMethod("getLastPathSegment").invoke(uri);
            if (lastPathSegment == null || String.valueOf(lastPathSegment).isBlank()) {
                return "";
            }
            String name = String.valueOf(lastPathSegment);
            int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf(':'));
            return slash >= 0 ? name.substring(slash + 1) : name;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static byte[] readAllBytes(InputStream input) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[32_000];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static void runOnAndroidUiThread(Object activity, Runnable runnable) throws Exception {
        Method runOnUiThread = activity.getClass().getMethod("runOnUiThread", Runnable.class);
        runOnUiThread.invoke(activity, runnable);
    }

    private static void invokeUnchecked(Method method, Object target, Object... args) {
        try {
            method.invoke(target, args);
        } catch (Throwable t) {
            setError(error("gui.sparkle_morpher.import.error.open_picker", safeMessage(t)));
            YesSteveModel.LOGGER.warn("[SM] Failed to invoke Android file picker", t);
        }
    }

    private static synchronized void complete(PickedFile file) {
        completed.add(file);
        picking = false;
    }

    private static synchronized void setError(Component error) {
        lastError = error == null ? Component.empty() : error;
        picking = false;
    }

    private static synchronized void clearLastError() {
        lastError = Component.empty();
    }

    private static Component error(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private static boolean isEmpty(Component component) {
        return component == null || component.getString().isEmpty();
    }

    private static synchronized void rememberPickerProbe(String message) {
        YesSteveModel.LOGGER.info("[SM] Android file picker probe: {}", message);
    }

    private static String safeMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    public record PickedFile(String fileName, byte[] data) {
    }

    private record FileStamp(long size, long modifiedMillis) {
    }

    private record FileCandidate(FileStamp stamp, long firstSeenMs) {
    }

    private record DirectoryStamp(long fileCount, long totalSize, long modifiedMillis) {
    }

    private record DirectoryCandidate(DirectoryStamp stamp, long firstSeenMs) {
    }
}
