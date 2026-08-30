package com.micaftic.morpher.legacy.compat;

import com.micaftic.morpher.core.legacy.YesModelUtils;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Compatibility boundary for the 1.2.x encrypted YSM container format.
 *
 * <p>The local/cloud model pipeline must not depend on the legacy crypto
 * implementation directly. This adapter is the only production entry point
 * for reading the old container while the format remains supported.</p>
 */
public final class LegacyCompatModelFormat {
    private LegacyCompatModelFormat() {
    }

    public static int detectCryptoVersion(byte[] data) {
        return YesModelUtils.getYsmCryptoVersion(data);
    }

    public static Map<String, byte[]> read(byte[] data) throws IOException {
        return YesModelUtils.input(data);
    }

    public static Map<String, byte[]> read(File file) throws IOException {
        return YesModelUtils.input(file);
    }
}
