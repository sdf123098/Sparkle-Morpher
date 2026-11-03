package com.micaftic.morpher.core.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R0.3 golden protocol 基线（YsmCrypt）。
 *
 * 目的：在拆分 YsmCrypt（packet/cache/file/compression/integrity）之前锁定协议行为。
 * 1) 确定性路径 byte-for-byte 锁定（packet encrypt appendNextKey=false、calculateModelHashes）；
 * 2) 非确定性路径（SecureRandom padding/key）round-trip characterization；
 * 3) server cache 头部 varint 结构 golden。
 *
 * 单测环境说明：无 Minecraft/mod 加载，getModelCacheIdentity() 的 modVersion 解析全部
 * 反射失败 → 固定为 "unknown"，因此 calculateModelHashes 是确定性的。
 */
class YsmCryptGoldenTest {

    /** 固定测试 key+iv（56 字节：32 key + 24 iv）。 */
    private static final byte[] KEY_IV = fixedBytes(56, (byte) 0x5A);
    private static final byte[] SERVER_KEY = fixedBytes(56, (byte) 0x3C);
    private static final byte[] CLIENT_KEY = fixedBytes(56, (byte) 0xA7);
    private static final byte[] PAYLOAD = "Hello YSM golden protocol".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CLEAR_TEXT = "{\"model\":\"cirno\",\"texture\":\"default\"}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] RAW_FILE = "ysm binary payload \u0000\u0001\u0002 magic".getBytes(StandardCharsets.UTF_8);

    private static byte[] fixedBytes(int len, byte fill) {
        byte[] arr = new byte[len];
        Arrays.fill(arr, fill);
        return arr;
    }

    // ---------- 确定性路径 ----------

    @Test
    void packetEncryptWithoutNextKey_isDeterministic() throws Exception {
        byte[] a = YsmCrypt.encrypt(PAYLOAD, KEY_IV, false).data();
        byte[] b = YsmCrypt.encrypt(PAYLOAD, KEY_IV, false).data();
        assertArrayEquals(a, b, "appendNextKey=false 的 packet 加密必须确定（无随机路径）");
    }

    @Test
    void calculateModelHashes_isDeterministic() throws Exception {
        long[] a = YsmCrypt.calculateModelHashes("model-abc", SERVER_KEY);
        long[] b = YsmCrypt.calculateModelHashes("model-abc", SERVER_KEY);
        assertArrayEquals(a, b, "calculateModelHashes 必须确定（单测环境 modVersion=unknown）");
    }

    // ---------- round-trip characterization ----------

    @Test
    void roundTrip_packetWithoutNextKey() throws Exception {
        YsmCrypt.EncryptedPacket encrypted = YsmCrypt.encrypt(PAYLOAD, KEY_IV, false);
        byte[] decrypted = YsmCrypt.decrypt(encrypted.data(), KEY_IV);
        assertArrayEquals(PAYLOAD, decrypted, "packet 加解密 round-trip 必须还原原文");
    }

    @Test
    void roundTrip_packetWithNextKey() throws Exception {
        YsmCrypt.EncryptedPacket encrypted = YsmCrypt.encrypt(PAYLOAD, KEY_IV, true);
        byte[] decrypted = YsmCrypt.decrypt(encrypted.data(), KEY_IV);
        assertTrue(decrypted.length >= PAYLOAD.length + 56, "appendNextKey=true 时明文携带 56 字节 nextKey");
        assertArrayEquals(PAYLOAD, Arrays.copyOf(decrypted, PAYLOAD.length), "解密结果前缀必须是原文");
    }

    @Test
    void roundTrip_serverCache_fullChain() throws Exception {
        long[] hashes = YsmCrypt.calculateModelHashes("model-abc", SERVER_KEY);
        byte[] serverData = YsmCrypt.encryptServerCache(CLEAR_TEXT, SERVER_KEY, hashes[0], hashes[1]);
        assertTrue(YsmCrypt.verifyServerCache(serverData, hashes[0], hashes[1]),
                "服务端缓存签名校验必须通过（含随机 padding）");

        byte[] clientData = YsmCrypt.transcodeServerDataToClientCache(serverData, SERVER_KEY, CLIENT_KEY, hashes[0], hashes[1]);
        byte[] restored = YsmCrypt.read(clientData, CLIENT_KEY);
        assertArrayEquals(CLEAR_TEXT, restored, "server cache → transcode → client cache → read 必须还原原文");
    }

    @Test
    void roundTrip_ysmFile() throws Exception {
        byte[] encrypted = YsmCrypt.encryptYsmFile(RAW_FILE);
        byte[] decrypted = YsmCrypt.decryptYsmFile(encrypted);
        assertArrayEquals(RAW_FILE, decrypted, "ysm 文件加解密 round-trip 必须还原原文");
    }

    // ---------- 结构 golden ----------

    /** byte-for-byte 锁定：2026-08-10 记录的 packet 加密输出（appendNextKey=false，KEY_IV=0x5A×56，payload="Hello YSM golden protocol"）。 */
    private static final String GOLDEN_PACKET_B64 = "IGQSY+7MxH+8hYIYksrxJitS4H7573PCvSJGVr2FAuxO";
    /** byte-for-byte 锁定：2026-08-10 记录的 calculateModelHashes("model-abc", SERVER_KEY)（单测环境 modVersion=unknown）。 */
    private static final long[] GOLDEN_HASHES = {-8304608359687288056L, 2575551593107348603L};

    @Test
    void packetEncrypt_goldenBytes() throws Exception {
        byte[] encrypted = YsmCrypt.encrypt(PAYLOAD, KEY_IV, false).data();
        assertArrayEquals(java.util.Base64.getDecoder().decode(GOLDEN_PACKET_B64), encrypted,
                "packet 加密输出必须与 golden 向量一致（协议行为锁定）");
    }

    @Test
    void calculateModelHashes_goldenValues() {
        long[] hashes = YsmCrypt.calculateModelHashes("model-abc", SERVER_KEY);
        assertArrayEquals(GOLDEN_HASHES, hashes, "model hashes 必须与 golden 向量一致");
    }

    @Test
    void serverCacheHeader_varintPrefixGolden() throws Exception {
        long[] hashes = YsmCrypt.calculateModelHashes("model-abc", SERVER_KEY);
        byte[] serverData = YsmCrypt.encryptServerCache(CLEAR_TEXT, SERVER_KEY, hashes[0], hashes[1]);
        // header：writeVarInt(1,0,0,0,32,0,0,0,0) → 01 00 00 00 20 00 00 00 00（不受随机 padding 影响）
        byte[] expectedPrefix = {0x01, 0x00, 0x00, 0x00, 0x20, 0x00, 0x00, 0x00, 0x00};
        assertArrayEquals(expectedPrefix, Arrays.copyOf(serverData, expectedPrefix.length),
                "server cache 头部 varint 结构必须保持不变");
    }
}
