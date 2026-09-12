package com.micaftic.morpher.client.upload.picker;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.upload.ModelImportFilePicker;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.network.chat.Component;

/**
 * Android 文档选择后端（1.2.7 §24.6，自 {@code ModelImportFilePicker} 等价搬运）。
 *
 * <p>包含 AndroidX {@code GetMultipleContents} 与 {@code StartActivityForResult} 两条注册式选择路径，
 * 以及 system {@code Intent} 构造、结果解析与 {@code content://} URI 读取。反射目标与文案原样保留。
 */
public final class AndroidDocumentPickerBackend {
    static boolean tryStartAndroidXPicker() {
        Object activity = FilePickerCoordinator.getGenericAndroidActivity();
        if (activity == null) {
            FilePickerCoordinator.rememberPickerProbe("AndroidX picker skipped: no Android activity");
            return false;
        }
        try {
            activity.getClass().getMethod("getActivityResultRegistry");
            Class.forName("androidx.activity.result.ActivityResultCallback");
            Class.forName("androidx.activity.result.contract.ActivityResultContract");
            Class.forName("androidx.activity.result.contract.ActivityResultContracts$GetMultipleContents");
            FilePickerCoordinator.runOnAndroidUiThread(activity, () -> startAndroidXPickerOnUiThread(activity));
            FilePickerCoordinator.clearLastError();
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            FilePickerCoordinator.rememberPickerProbe("AndroidX GetMultipleContents unavailable: " + FilePickerCoordinator.safeMessage(e));
            return false;
        } catch (Throwable t) {
            FilePickerCoordinator.lastError = FilePickerCoordinator.error("gui.sparkle_morpher.import.error.open_picker", FilePickerCoordinator.safeMessage(t));
            YesSteveModel.LOGGER.warn("[SM] Failed to open AndroidX file picker", t);
            return false;
        }
    }

    static boolean tryStartSystemPicker() {
        Object activity = FilePickerCoordinator.getGenericAndroidActivity();
        if (activity == null) {
            FilePickerCoordinator.rememberPickerProbe("system document picker skipped: no Android activity");
            return false;
        }
        try {
            activity.getClass().getMethod("getActivityResultRegistry");
            Class.forName("androidx.activity.result.ActivityResultCallback");
            Class.forName("androidx.activity.result.contract.ActivityResultContract");
            Class.forName("androidx.activity.result.contract.ActivityResultContracts$StartActivityForResult");
            FilePickerCoordinator.runOnAndroidUiThread(activity, () -> startSystemPickerWithAndroidXOnUiThread(activity));
            FilePickerCoordinator.clearLastError();
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            FilePickerCoordinator.rememberPickerProbe("AndroidX StartActivityForResult unavailable: " + FilePickerCoordinator.safeMessage(e));
            return false;
        } catch (Throwable t) {
            FilePickerCoordinator.lastError = FilePickerCoordinator.error("gui.sparkle_morpher.import.error.open_picker", FilePickerCoordinator.safeMessage(t));
            YesSteveModel.LOGGER.warn("[SM] Failed to open system Android file picker", t);
            return false;
        }
    }

    private static void handleAndroidXResult(Object activity, Object uris, Object launcher) {
        unregisterAndroidXPicker(launcher);
        CompletableFuture.runAsync(() -> {
            try {
                if (!(uris instanceof List<?> list) || list.isEmpty()) {
                    FilePickerCoordinator.setError(Component.translatable("gui.sparkle_morpher.import.error.cancelled"));
                    return;
                }
                for (Object uri : list) {
                    readAndroidUri(activity, String.valueOf(uri));
                }
            } catch (Throwable t) {
                FilePickerCoordinator.setError(FilePickerCoordinator.error("gui.sparkle_morpher.import.error.read_selected", FilePickerCoordinator.safeMessage(t)));
                YesSteveModel.LOGGER.warn("[SM] Failed to read selected Android file", t);
            }
        });
    }

    private static void handleSystemPickerResult(Object activity, Object activityResult, Object launcher) {
        unregisterAndroidXPicker(launcher);
        CompletableFuture.runAsync(() -> {
            try {
                if (activityResult == null) {
                    FilePickerCoordinator.setError(Component.translatable("gui.sparkle_morpher.import.error.cancelled"));
                    return;
                }
                int resultCode = (Integer) activityResult.getClass().getMethod("getResultCode").invoke(activityResult);
                Object intent = activityResult.getClass().getMethod("getData").invoke(activityResult);
                if (resultCode != getAndroidActivityResultOk() || intent == null) {
                    FilePickerCoordinator.setError(Component.translatable("gui.sparkle_morpher.import.error.cancelled"));
                    return;
                }
                readAndroidIntentData(activity, intent);
            } catch (Throwable t) {
                FilePickerCoordinator.setError(FilePickerCoordinator.error("gui.sparkle_morpher.import.error.read_selected", FilePickerCoordinator.safeMessage(t)));
                YesSteveModel.LOGGER.warn("[SM] Failed to read selected Android file", t);
            }
        });
    }

    static void handleSystemIntentResult(Object activity, int resultCode, Object intent) {
        CompletableFuture.runAsync(() -> {
            try {
                if (resultCode != getAndroidActivityResultOk() || intent == null) {
                    FilePickerCoordinator.setError(Component.translatable("gui.sparkle_morpher.import.error.cancelled"));
                    return;
                }
                readAndroidIntentData(activity, intent);
            } catch (Throwable t) {
                FilePickerCoordinator.setError(FilePickerCoordinator.error("gui.sparkle_morpher.import.error.read_selected", FilePickerCoordinator.safeMessage(t)));
                YesSteveModel.LOGGER.warn("[SM] Failed to read selected Android file", t);
            }
        });
    }

    private static void readAndroidIntentData(Object activity, Object intent) throws Exception {
        List<Object> uris = getAndroidIntentUris(intent);
        if (uris.isEmpty()) {
            FilePickerCoordinator.setError(Component.translatable("gui.sparkle_morpher.import.error.no_file"));
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
            String key = "openysm_ysm_import_" + FilePickerCoordinator.requestIds.incrementAndGet();
            Object launcher = registry.getClass()
                    .getMethod("register", String.class, contractClass, callbackClass)
                    .invoke(registry, key, contract, callback);
            launcherHolder[0] = launcher;
            launchAndroidXPicker(launcher);
        } catch (Throwable t) {
            FilePickerCoordinator.setError(FilePickerCoordinator.error("gui.sparkle_morpher.import.error.open_picker", FilePickerCoordinator.safeMessage(t)));
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
            String key = "openysm_ysm_import_document_" + FilePickerCoordinator.requestIds.incrementAndGet();
            Object launcher = registry.getClass()
                    .getMethod("register", String.class, contractClass, callbackClass)
                    .invoke(registry, key, contract, callback);
            launcherHolder[0] = launcher;
            launchAndroidXPicker(launcher, createOpenDocumentIntent());
        } catch (Throwable t) {
            FilePickerCoordinator.setError(FilePickerCoordinator.error("gui.sparkle_morpher.import.error.open_picker", FilePickerCoordinator.safeMessage(t)));
            YesSteveModel.LOGGER.warn("[SM] Failed to start system Android file picker", t);
        }
    }

    static int getAndroidActivityResultOk() throws ReflectiveOperationException {
        return Class.forName("android.app.Activity").getField("RESULT_OK").getInt(null);
    }

    private static void launchAndroidXPicker(Object launcher) {
        try {
            Class.forName("androidx.activity.result.ActivityResultLauncher")
                    .getMethod("launch", Object.class)
                    .invoke(launcher, "*/*");
        } catch (Throwable t) {
            unregisterAndroidXPicker(launcher);
            FilePickerCoordinator.setError(FilePickerCoordinator.error("gui.sparkle_morpher.import.error.open_picker", FilePickerCoordinator.safeMessage(t)));
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
            FilePickerCoordinator.setError(FilePickerCoordinator.error("gui.sparkle_morpher.import.error.open_picker", FilePickerCoordinator.safeMessage(t)));
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

    static Object createOpenDocumentIntent() throws Exception {
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

    static void readAndroidUri(Object context, String uriText) throws Exception {
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
            byte[] data = FilePickerCoordinator.readAllBytes(in);
            FilePickerCoordinator.complete(new ModelImportFilePicker.PickedFile(fileName, data));
        }
    }

    private static void readAndroidFilePath(Object uri, String path) throws Exception {
        File file = new File(path);
        String fileName = getAndroidLastPathSegment(uri);
        if (fileName.isBlank()) {
            fileName = file.getName();
        }
        try (InputStream in = new FileInputStream(file)) {
            byte[] data = FilePickerCoordinator.readAllBytes(in);
            FilePickerCoordinator.complete(new ModelImportFilePicker.PickedFile(fileName, data));
        }
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

    private AndroidDocumentPickerBackend() {
    }
}
