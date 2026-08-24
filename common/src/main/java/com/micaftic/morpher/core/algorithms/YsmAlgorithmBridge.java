package com.micaftic.morpher.core.algorithms;

import com.ysm.parser.YSMNative;

public final class YsmAlgorithmBridge {
    private YsmAlgorithmBridge() {
    }

    public static byte[] ysmZstdDecompress(byte[] data) {
        return YSMNative.ysmZstdDecompress(data);
    }

    public static byte[] ysmZstdCompress(byte[] data, int level) {
        return YSMNative.ysmZstdCompress(data, level);
    }

    public static byte[] zstdDecompress(byte[] data) {
        return YSMNative.zstdDecompress(data);
    }

    public static byte[] zstdCompress(byte[] data, int level) {
        return YSMNative.zstdCompress(data, level);
    }
}
