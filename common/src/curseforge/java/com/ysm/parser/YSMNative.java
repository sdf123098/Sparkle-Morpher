package com.ysm.parser;

public class YSMNative {
    private static UnsatisfiedLinkError unavailable() {
        return new UnsatisfiedLinkError("YSM native algorithms are not available in the CurseForge build");
    }

    public static long cityHash64(byte[] data) {
        throw unavailable();
    }

    public static long cityHash64WithSeed(byte[] data, long seed) {
        throw unavailable();
    }

    public static long[] cityHash128(byte[] data) {
        throw unavailable();
    }

    public static long[] cityHash128WithSeed(byte[] data, long seedLow, long seedHigh) {
        throw unavailable();
    }

    public static byte[] zstdDecompress(byte[] data) {
        throw unavailable();
    }

    public static byte[] zstdCompress(byte[] data, int level) {
        throw unavailable();
    }

    public static byte[] xchacha20Encrypt(byte[] data, byte[] key, byte[] iv, int rounds) {
        throw unavailable();
    }

    public static byte[] xchacha20Decrypt(byte[] data, byte[] key, byte[] iv, int rounds) {
        throw unavailable();
    }

    public static byte[] modifiedChaChaDecrypt(byte[] data, byte[] key, byte[] iv, long seed) {
        throw unavailable();
    }

    public static long mt19937Create(long seed) {
        throw unavailable();
    }

    public static long mt19937Next(long handle) {
        throw unavailable();
    }

    public static byte[] mt19937GenerateBytes(long handle, int count) {
        throw unavailable();
    }

    public static void mt19937Destroy(long handle) {
        throw unavailable();
    }

    public static byte[] ysmZstdDecompress(byte[] data) {
        throw unavailable();
    }

    public static byte[] ysmZstdCompress(byte[] data, int level) {
        throw unavailable();
    }
}
