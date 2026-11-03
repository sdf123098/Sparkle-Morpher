package com.micaftic.morpher.util;

import java.io.File;

public final class PlatformUtil {
    private PlatformUtil() {
    }

    public static long getMillis() {
        return ClientUiUtil.getMillis();
    }

    public static void openUri(String uri) {
        ClientUiUtil.openUri(uri);
    }

    public static void openFile(File file) {
        ClientUiUtil.openFile(file);
    }
}
