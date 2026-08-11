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
 * R8 遗留① ServerModelCache 测试：服务端缓存引擎
 * （哈希命名 / 签名校验 / 加密原子写），从 ServerModelManager.processAndCacheModel
 * / canReadServerCache 抽取。
 *
 * <p>纯 Java：serverKey 用 56 字节固定值（同 YsmCryptGoldenTest 约定），无 MC 依赖。</p>
 */
class ServerModelCacheTest {

    private static final byte[] SERVER_KEY = fixedBytes(56, (byte) 0x3C);

    @TempDir
    Path tempDir;

    private static byte[] fixedBytes(int len, byte fill) {
        byte[] arr = new byte[len];
        Arrays.fill(arr, fill);
        return arr;
    }

    @Test
    void fileName_isDeterministicHexPair() {
        long[] hashes = ServerModelCache.hashes("model-abc", SERVER_KEY);
        String name = ServerModelCache.fileName(hashes);
        assertEquals(32, name.length());
        assertTrue(name.matches("[0-9a-f]{32}"), "expected 32 hex chars, got: " + name);
        // 同输入必须稳定（GoldenTest 同思路：无 MC 环境下 calculateModelHashes 确定性）
        assertEquals(name, ServerModelCache.fileName(ServerModelCache.hashes("model-abc", SERVER_KEY)));
    }

    @Test
    void writeThenValidate_roundTrips() throws Exception {
        long[] hashes = ServerModelCache.hashes("model-abc", SERVER_KEY);
        Path cacheFile = tempDir.resolve(ServerModelCache.fileName(hashes));
        byte[] serialized = "ysm serialized model payload".getBytes(StandardCharsets.UTF_8);

        ServerModelCache.write(cacheFile, serialized, hashes, SERVER_KEY);

        assertTrue(Files.exists(cacheFile));
        byte[] written = Files.readAllBytes(cacheFile);
        // 加密后不是明文
        assertFalse(Arrays.equals(written, serialized));
        assertTrue(ServerModelCache.isValid(written, hashes, SERVER_KEY));
        // 解密内容与明文一致（加密往返保真）
        byte[] decrypted = YsmCrypt.read(written, SERVER_KEY);
        assertTrue(Arrays.equals(serialized, decrypted));
    }

    @Test
    void isValid_rejectsTamperedData() throws Exception {
        long[] hashes = ServerModelCache.hashes("model-abc", SERVER_KEY);
        Path cacheFile = tempDir.resolve(ServerModelCache.fileName(hashes));
        ServerModelCache.write(cacheFile, "payload".getBytes(StandardCharsets.UTF_8), hashes, SERVER_KEY);

        byte[] data = Files.readAllBytes(cacheFile);
        data[data.length - 1] ^= 0x01; // 篡改尾部字节
        assertFalse(ServerModelCache.isValid(data, hashes, SERVER_KEY));
    }

    @Test
    void isValid_rejectsGarbageOrEmpty() {
        long[] hashes = ServerModelCache.hashes("model-abc", SERVER_KEY);
        assertFalse(ServerModelCache.isValid(new byte[0], hashes, SERVER_KEY));
        assertFalse(ServerModelCache.isValid("not a cache".getBytes(StandardCharsets.UTF_8), hashes, SERVER_KEY));
        assertFalse(ServerModelCache.isValid(null, hashes, SERVER_KEY));
    }

    @Test
    void write_overwritesExistingCache() throws Exception {
        long[] hashes = ServerModelCache.hashes("model-abc", SERVER_KEY);
        Path cacheFile = tempDir.resolve(ServerModelCache.fileName(hashes));
        ServerModelCache.write(cacheFile, "v1".getBytes(StandardCharsets.UTF_8), hashes, SERVER_KEY);
        ServerModelCache.write(cacheFile, "v2-longer-payload".getBytes(StandardCharsets.UTF_8), hashes, SERVER_KEY);

        byte[] written = Files.readAllBytes(cacheFile);
        assertTrue(ServerModelCache.isValid(written, hashes, SERVER_KEY));
        byte[] decrypted = YsmCrypt.read(written, SERVER_KEY);
        assertEquals("v2-longer-payload", new String(decrypted, StandardCharsets.UTF_8));
    }
}
