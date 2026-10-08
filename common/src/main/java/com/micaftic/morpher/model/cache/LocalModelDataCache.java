package com.micaftic.morpher.model.cache;

import com.micaftic.morpher.core.security.YsmCrypt;
import com.micaftic.morpher.core.storage.AtomicFileMover;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Local model data cache — hashes, validates and atomically writes cached model files.
 * 
 * 模型序列化与数据类映射留在调用方。
 *
 * <p>纯 Java：依赖 YsmCrypt（core/security）与 AtomicFileMover（core/storage），可单测。
 * 写入是尽力而为：调用方决定失败时是否容忍（原 SMM 写失败仅 warn，发送路径按需重建）。
 */
public final class LocalModelDataCache {

    private LocalModelDataCache() {
    }

    /** 由模型 sha256 + serverKey 计算缓存哈希对（确定性）。 */
    public static long[] hashes(String sha256, byte[] serverKey) {
        return YsmCrypt.calculateModelHashes(sha256, serverKey);
    }

    /** 缓存文件名（哈希对 hex 拼接，32 字符）。 */
    public static String fileName(long[] hashes) {
        return String.format("%016x%016x", hashes[0], hashes[1]);
    }

    /** Reads current or retained legacy identities. Never removes a failed candidate. */
    public static byte[] readCompatible(Path directory, String sha256, byte[] serverKey) throws IOException {
        java.util.LinkedHashSet<String> identities = new java.util.LinkedHashSet<>();
        identities.add(YsmCrypt.getModelCacheIdentity());
        identities.addAll(com.micaftic.morpher.core.storage.CacheIdentityHistory.read(directory.getParent()));
        for (String identity : identities) {
            long[] hashes = com.micaftic.morpher.core.security.ModelCacheKeyDerivation.hashes(sha256, serverKey, identity);
            Path candidate = directory.resolve(fileName(hashes));
            if (!Files.isRegularFile(candidate)) continue;
            byte[] bytes = Files.readAllBytes(candidate);
            if (!isValid(bytes, hashes, serverKey)) continue;
            try { return YsmCrypt.read(bytes, serverKey); }
            catch (Exception ignored) { /* Try another retained identity. */ }
        }
        throw new IOException("No readable model cache for content hash " + sha256);
    }

    /**
     * 缓存数据是否有效：兼容缓存签名校验通过，且可用缓存密钥解密读取。
     * 读失败/签名不符 → false（调用方据此重建缓存）。
     */
    public static boolean isValid(byte[] cacheData, long[] hashes, byte[] serverKey) {
        if (cacheData == null || cacheData.length == 0) {
            return false;
        }
        try {
            if (!YsmCrypt.verifyServerCache(cacheData, hashes[0], hashes[1])) {
                return false;
            }
            YsmCrypt.read(cacheData, serverKey);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 加密并原子写入缓存文件（父目录不存在时创建；临时文件 + 重试替换，避免半截缓存）。
     *
     * @throws IOException 写入失败（含并发替换瞬时失败重试仍失败）
     */
    public static void write(Path cacheFile, byte[] serialized, long[] hashes, byte[] serverKey) throws IOException {
        Path parent = cacheFile.getParent();
        Files.createDirectories(parent);
        byte[] encrypted;
        try {
            encrypted = YsmCrypt.encryptServerCache(serialized, serverKey, hashes[0], hashes[1]);
        } catch (Exception e) {
            throw new IOException("Failed to encrypt server cache", e);
        }
        Path cacheTmp = Files.createTempFile(parent, cacheFile.getFileName().toString(), ".tmp");
        try {
            Files.write(cacheTmp, encrypted);
            AtomicFileMover.moveWithRetry(cacheTmp, cacheFile);
        } finally {
            Files.deleteIfExists(cacheTmp);
        }
    }
}
