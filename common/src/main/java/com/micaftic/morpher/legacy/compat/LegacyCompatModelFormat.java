package com.micaftic.morpher.legacy.compat;

import dev.spm.core.api.CancellationToken;
import dev.spm.core.api.DecodePolicy;
import dev.spm.core.api.ResourceBudget;
import dev.spm.core.api.ResourceKey;
import dev.spm.core.format.ysm.ModelArchive;
import dev.spm.core.format.ysm.YsmArchiveCodec;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compatibility boundary for the 1.2.x encrypted YSM container format, backed by {@code spm-core}'s
 * {@link YsmArchiveCodec}.
 */
public final class LegacyCompatModelFormat {
    private LegacyCompatModelFormat() {
    }

    public static int detectCryptoVersion(byte[] data) {
        if (data == null || data.length < 8) {
            return -1;
        }
        if (data[0] == (byte) 0xEF && data[1] == (byte) 0xBB && data[2] == (byte) 0xBF
                && data[3] == 0x59 && data[4] == 0x53 && data[5] == 0x47 && data[6] == 0x50) {
            return 3;
        }
        if (data[0] == 0x59 && data[1] == 0x53 && data[2] == 0x47 && data[3] == 0x50) {
            int version = ByteBuffer.wrap(data, 4, 4).order(ByteOrder.BIG_ENDIAN).getInt();
            if (version == 1 || version == 2) {
                return version;
            }
        }
        return -1;
    }

    /** Reads only the eight-byte YSM header; upload validation must not materialize an entire model. */
    public static int detectCryptoVersion(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            byte[] header = input.readNBytes(8);
            return detectCryptoVersion(header);
        }
    }

    public static Map<String, byte[]> read(byte[] data) throws IOException {
        int version = detectCryptoVersion(data);
        if (data == null || data.length < 24 || (version != 1 && version != 2)) {
            return Map.of();
        }
        try (ModelArchive archive = YsmArchiveCodec.decode(
                data, DecodePolicy.localModel(), new ResourceBudget(512L << 20), new CancellationToken())) {
            Map<String, byte[]> result = new LinkedHashMap<>();
            for (ResourceKey key : archive.entries()) {
                result.put(key.path(), archive.read(key));
            }
            return result;
        }
    }

    public static Map<String, byte[]> read(File file) throws IOException {
        return read(Files.readAllBytes(file.toPath()));
    }
}
