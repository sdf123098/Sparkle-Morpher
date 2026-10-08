package com.micaftic.morpher.model.cache;

import com.micaftic.morpher.core.security.YsmCrypt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R8 遗留① LocalModelDataCache 测试：服务端缓存引擎
 * （哈希命名 / 签名校验 / 加密原子写），从 LocalModelService 模型缓存路径
 * / canReadServerCache 抽取。
 *
 * <p>纯 Java：serverKey 用 56 字节固定值（同 YsmCryptGoldenTest 约定），无 MC 依赖。</p>
 */
class LocalModelDataCacheTest {

    private static final byte[] SERVER_KEY = fixedBytes(56, (byte) 0x3C);

    @TempDir
    Path tempDir;

    @Test void retainedIdentityCanReadAnOldEncryptedCacheWithoutReplacingIt() throws Exception {
        String old = "sparkle_morpher:model_cache\nmodVersion=1.9.7";
        com.micaftic.morpher.core.storage.CacheIdentityHistory.retain(tempDir, old);
        Path server = tempDir.resolve("server");
        long[] hashes = com.micaftic.morpher.core.security.ModelCacheKeyDerivation.hashes("old-sha", SERVER_KEY, old);
        Path file = server.resolve(LocalModelDataCache.fileName(hashes));
        byte[] payload = "old offline cache".getBytes(StandardCharsets.UTF_8);
        LocalModelDataCache.write(file, payload, hashes, SERVER_KEY);
        byte[] before = Files.readAllBytes(file);
        com.micaftic.morpher.core.storage.CacheIdentityHistory.retain(tempDir, YsmCrypt.getModelCacheIdentity());
        org.junit.jupiter.api.Assertions.assertArrayEquals(payload, LocalModelDataCache.readCompatible(server, "old-sha", SERVER_KEY));
        org.junit.jupiter.api.Assertions.assertArrayEquals(before, Files.readAllBytes(file));
        org.junit.jupiter.api.Assertions.assertArrayEquals(YsmCrypt.calculateModelHashes("sha", SERVER_KEY),
                com.micaftic.morpher.core.security.ModelCacheKeyDerivation.hashes("sha", SERVER_KEY, YsmCrypt.getModelCacheIdentity()));
    }

    private static byte[] fixedBytes(int len, byte fill) {
        byte[] arr = new byte[len];
        Arrays.fill(arr, fill);
        return arr;
    }

    @Test
    void fileName_isDeterministicHexPair() {
        long[] hashes = LocalModelDataCache.hashes("model-abc", SERVER_KEY);
        String name = LocalModelDataCache.fileName(hashes);
        assertEquals(32, name.length());
        assertTrue(name.matches("[0-9a-f]{32}"), "expected 32 hex chars, got: " + name);
        // 同输入必须稳定（GoldenTest 同思路：无 MC 环境下 calculateModelHashes 确定性）
        assertEquals(name, LocalModelDataCache.fileName(LocalModelDataCache.hashes("model-abc", SERVER_KEY)));
    }

    @Test
    void writeThenValidate_roundTrips() throws Exception {
        long[] hashes = LocalModelDataCache.hashes("model-abc", SERVER_KEY);
        Path cacheFile = tempDir.resolve(LocalModelDataCache.fileName(hashes));
        byte[] serialized = "ysm serialized model payload".getBytes(StandardCharsets.UTF_8);

        LocalModelDataCache.write(cacheFile, serialized, hashes, SERVER_KEY);

        assertTrue(Files.exists(cacheFile));
        byte[] written = Files.readAllBytes(cacheFile);
        // 加密后不是明文
        assertFalse(Arrays.equals(written, serialized));
        assertTrue(LocalModelDataCache.isValid(written, hashes, SERVER_KEY));
        // 解密内容与明文一致（加密往返保真）
        byte[] decrypted = YsmCrypt.read(written, SERVER_KEY);
        assertTrue(Arrays.equals(serialized, decrypted));
    }

    @Test
    void isValid_rejectsTamperedData() throws Exception {
        long[] hashes = LocalModelDataCache.hashes("model-abc", SERVER_KEY);
        Path cacheFile = tempDir.resolve(LocalModelDataCache.fileName(hashes));
        LocalModelDataCache.write(cacheFile, "payload".getBytes(StandardCharsets.UTF_8), hashes, SERVER_KEY);

        byte[] data = Files.readAllBytes(cacheFile);
        data[data.length - 1] ^= 0x01; // 篡改尾部字节
        assertFalse(LocalModelDataCache.isValid(data, hashes, SERVER_KEY));
    }

    @Test
    void isValid_rejectsGarbageOrEmpty() {
        long[] hashes = LocalModelDataCache.hashes("model-abc", SERVER_KEY);
        assertFalse(LocalModelDataCache.isValid(new byte[0], hashes, SERVER_KEY));
        assertFalse(LocalModelDataCache.isValid("not a cache".getBytes(StandardCharsets.UTF_8), hashes, SERVER_KEY));
        assertFalse(LocalModelDataCache.isValid(null, hashes, SERVER_KEY));
    }

    @Test
    void write_overwritesExistingCache() throws Exception {
        long[] hashes = LocalModelDataCache.hashes("model-abc", SERVER_KEY);
        Path cacheFile = tempDir.resolve(LocalModelDataCache.fileName(hashes));
        LocalModelDataCache.write(cacheFile, "v1".getBytes(StandardCharsets.UTF_8), hashes, SERVER_KEY);
        LocalModelDataCache.write(cacheFile, "v2-longer-payload".getBytes(StandardCharsets.UTF_8), hashes, SERVER_KEY);

        byte[] written = Files.readAllBytes(cacheFile);
        assertTrue(LocalModelDataCache.isValid(written, hashes, SERVER_KEY));
        byte[] decrypted = YsmCrypt.read(written, SERVER_KEY);
        assertEquals("v2-longer-payload", new String(decrypted, StandardCharsets.UTF_8));
    }
}
