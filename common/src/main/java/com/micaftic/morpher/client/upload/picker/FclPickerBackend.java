package com.micaftic.morpher.client.upload.picker;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.upload.ModelImportFilePicker;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * FCL（Fold Craft Launcher）文件选择后端（1.2.7 §24.6，自 {@code ModelImportFilePicker} 等价搬运）。
 *
 * <p>包含两条 FCL 路径：{@code ResultListener + Intent} 的 FCL system picker，以及
 * {@code FileBrowser.Builder} 的 FCL 文件浏览器。仅在 Android/FCL 运行时可用，方法与反射目标原样保留。
 */
public final class FclPickerBackend {
    private static final String[] IMPORT_SUFFIXES = {"ysm", "zip", "bbmodel", "gltf", "glb"};

    static boolean tryStartFclSystemPicker() {
        try {
            Object activity = FilePickerCoordinator.getFclCurrentActivity();
            if (activity == null) {
                FilePickerCoordinator.rememberPickerProbe("FCL current activity unavailable");
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
                            AndroidDocumentPickerBackend.handleSystemIntentResult(activity, resultCode, intent);
                        }
                        return null;
                    }
            );
            int requestCode = FilePickerCoordinator.requestIds.incrementAndGet();
            Method start = Class.forName("com.tungsten.fcllibrary.component.ResultListener")
                    .getDeclaredMethod("startActivityForResult", Class.forName("android.app.Activity"), Class.forName("android.content.Intent"), int.class, listenerClass);
            Object intent = AndroidDocumentPickerBackend.createOpenDocumentIntent();
            FilePickerCoordinator.runOnAndroidUiThread(activity, () -> FilePickerCoordinator.invokeUnchecked(start, null, activity, intent, requestCode, listener));
            FilePickerCoordinator.clearLastError();
            return true;
        } catch (ClassNotFoundException e) {
            FilePickerCoordinator.rememberPickerProbe("FCL ResultListener bridge unavailable: " + FilePickerCoordinator.safeMessage(e));
            return false;
        } catch (Throwable t) {
            FilePickerCoordinator.lastError = FilePickerCoordinator.error("gui.sparkle_morpher.import.error.open_picker", FilePickerCoordinator.safeMessage(t));
            YesSteveModel.LOGGER.warn("[SM] Failed to open FCL system file picker", t);
            return false;
        }
    }

    static boolean tryStartFclPicker() {
        try {
            Object activity = FilePickerCoordinator.getFclCurrentActivity();
            if (activity == null) {
                FilePickerCoordinator.rememberPickerProbe("FCL current activity unavailable for FileBrowser");
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
            int requestCode = FilePickerCoordinator.requestIds.incrementAndGet();
            Method browse = fileBrowserClass.getDeclaredMethod("browse", Class.forName("android.app.Activity"), int.class, listenerClass);
            FilePickerCoordinator.runOnAndroidUiThread(activity, () -> FilePickerCoordinator.invokeUnchecked(browse, browser, activity, requestCode, listener));
            FilePickerCoordinator.clearLastError();
            return true;
        } catch (ClassNotFoundException e) {
            FilePickerCoordinator.rememberPickerProbe("FCL FileBrowser bridge unavailable: " + FilePickerCoordinator.safeMessage(e));
            return false;
        } catch (Throwable t) {
            FilePickerCoordinator.lastError = FilePickerCoordinator.error("gui.sparkle_morpher.import.error.open_picker", FilePickerCoordinator.safeMessage(t));
            YesSteveModel.LOGGER.warn("[SM] Failed to open FCL file picker", t);
            return false;
        }
    }

    private static void handleFclResult(Object[] args) {
        CompletableFuture.runAsync(() -> {
            try {
                int resultCode = (Integer) args[1];
                Object intent = args[2];
                if (resultCode != AndroidDocumentPickerBackend.getAndroidActivityResultOk() || intent == null) {
                    FilePickerCoordinator.setError(Component.translatable("gui.sparkle_morpher.import.error.cancelled"));
                    return;
                }
                Class<?> fileBrowserClass = Class.forName("com.tungsten.fcllibrary.browser.FileBrowser");
                Object selected = fileBrowserClass.getDeclaredMethod("getSelectedFiles", Class.forName("android.content.Intent")).invoke(null, intent);
                if (!(selected instanceof List<?> list) || list.isEmpty()) {
                    FilePickerCoordinator.setError(Component.translatable("gui.sparkle_morpher.import.error.no_file"));
                    return;
                }
                Object activity = FilePickerCoordinator.getFclCurrentActivity();
                if (activity == null) {
                    FilePickerCoordinator.setError(Component.translatable("gui.sparkle_morpher.import.error.no_activity"));
                    return;
                }
                for (Object selectedFile : list) {
                    AndroidDocumentPickerBackend.readAndroidUri(activity, String.valueOf(selectedFile));
                }
            } catch (Throwable t) {
                FilePickerCoordinator.setError(FilePickerCoordinator.error("gui.sparkle_morpher.import.error.read_selected", FilePickerCoordinator.safeMessage(t)));
                YesSteveModel.LOGGER.warn("[SM] Failed to read selected Android file", t);
            }
        });
    }

    private FclPickerBackend() {
    }
}
