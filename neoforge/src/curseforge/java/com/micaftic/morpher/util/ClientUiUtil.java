package com.micaftic.morpher.util;

import com.micaftic.morpher.YesSteveModel;

import java.io.File;

public final class ClientUiUtil {
    private ClientUiUtil() {
    }

    public static long getMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    public static void openFile(File file) {
        if (file != null) {
            YesSteveModel.LOGGER.info("Model folder: {}", file.getAbsolutePath());
        }
    }

    public static void openUri(String uri) {
        if (uri != null && !uri.isBlank()) {
            YesSteveModel.LOGGER.info("URI: {}", uri);
        }
    }
}
