package com.micaftic.morpher.core.algorithms;

public final class YsmAlgorithmBridge {
    private YsmAlgorithmBridge() {
    }

    private static UnsatisfiedLinkError unavailable() {
        return new UnsatisfiedLinkError("Accelerated algorithms are not available in the CurseForge build");
    }

    public static byte[] ysmZstdDecompress(byte[] data) {
        throw unavailable();
    }

    public static byte[] ysmZstdCompress(byte[] data, int level) {
        throw unavailable();
    }

    public static byte[] zstdDecompress(byte[] data) {
        throw unavailable();
    }

    public static byte[] zstdCompress(byte[] data, int level) {
        throw unavailable();
    }
}
