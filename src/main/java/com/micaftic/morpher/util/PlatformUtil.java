package com.micaftic.morpher.util;

import com.micaftic.morpher.YesSteveModel;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;

public final class PlatformUtil {

    private PlatformUtil() {
    }

    public static long getMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    public static void openUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return;
        }
        try {
            URI parsedUri = URI.create(uri);
            if (openWithDesktop(parsedUri)) {
                return;
            }
            openWithSystem(uri);
        } catch (Exception e) {
            YesSteveModel.LOGGER.warn("Failed to open URI {}", uri, e);
        }
    }

    public static void openFile(File file) {
        if (file == null) {
            return;
        }
        try {
            if (openWithDesktop(file)) {
                return;
            }
            openWithSystem(file.getAbsolutePath());
        } catch (Exception e) {
            YesSteveModel.LOGGER.warn("Failed to open file {}", file, e);
        }
    }

    private static boolean openWithDesktop(URI uri) throws IOException {
        if (!Desktop.isDesktopSupported()) {
            return false;
        }
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.BROWSE)) {
            return false;
        }
        desktop.browse(uri);
        return true;
    }

    private static boolean openWithDesktop(File file) throws IOException {
        if (!Desktop.isDesktopSupported()) {
            return false;
        }
        Desktop desktop = Desktop.getDesktop();
        if (desktop.isSupported(Desktop.Action.OPEN)) {
            desktop.open(file);
            return true;
        }
        if (desktop.isSupported(Desktop.Action.BROWSE)) {
            desktop.browse(file.toURI());
            return true;
        }
        return false;
    }

    private static void openWithSystem(String target) throws IOException {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", target).start();
        } else if (osName.contains("mac")) {
            new ProcessBuilder("open", target).start();
        } else {
            new ProcessBuilder("xdg-open", target).start();
        }
    }
}
