package com.micaftic.morpher.resource;

import com.micaftic.morpher.core.security.YSMByteBuf;
import com.micaftic.morpher.core.security.YsmCrypt;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R0.2 corpus：YSM binary 序列化链路 round-trip（合成最小模型，无第三方版权资产）。
 *
 * 真实配对（ServerModelManager:878 → ClientModelManager:737）：
 *   RawYsmModel → YSMBinarySerializer.serialize(model, 32, true)
 *     → YsmCrypt.encryptServerCache（服务端缓存）
 *     → YsmCrypt.transcodeServerDataToClientCache + read（客户端还原明文）
 *     → YSMBinaryDeserializer(clear, 32).deserialize() → RawYsmModel
 *
 * 注意：serialize 输出不含 format DWORD 头（服务器下发格式），不能直接喂 1 参
 * YSMBinaryDeserializer；且 writeModern 仅支持 format>=26。legacy 格式（<16）
 * 的行为锁定依赖真实模型样本（本机一次性验证，不进入仓库）。
 */
class YsmBinaryCorpusTest {

    private static final byte[] SERVER_KEY = fixedBytes(56, (byte) 0x3C);
    private static final byte[] CLIENT_KEY = fixedBytes(56, (byte) 0xA7);

    private static byte[] fixedBytes(int len, byte fill) {
        byte[] arr = new byte[len];
        Arrays.fill(arr, fill);
        return arr;
    }

    private static RawYsmModel syntheticModel() {
        RawYsmModel model = new RawYsmModel();
        model.modelId = "corpus-test";
        model.formatVersion = 32;

        RawYsmModel.RawGeometry geo = new RawYsmModel.RawGeometry();
        geo.modelType = 1;
        geo.identifier = "geometry.corpus";
        geo.textureWidth = 64;
        geo.textureHeight = 64;
        model.mainEntity.mainModel = geo;

        RawYsmModel.RawTexture tex = new RawYsmModel.RawTexture();
        tex.name = "default";
        tex.width = 64;
        tex.height = 64;
        tex.imageFormat = 1;
        tex.data = new byte[]{1, 2, 3, 4};
        tex.unknownFlag = 1;
        model.mainEntity.textures.put("default", tex);

        return model;
    }

    private static byte[] serializeBytes(RawYsmModel model, int format) throws Exception {
        try (YSMByteBuf buf = YSMBinarySerializer.serialize(model, format, true)) {
            byte[] data = new byte[buf.getRawBuf().readableBytes()];
            buf.getRawBuf().readBytes(data);
            return data;
        }
    }

    /** 真实全链路：serialize → 服务端缓存加密 → transcode → 客户端 read → deserialize。 */
    @Test
    void fullServerPipeline_roundTrip() throws Exception {
        RawYsmModel src = syntheticModel();
        byte[] serialized = serializeBytes(src, 32);

        long[] hashes = YsmCrypt.calculateModelHashes("model-abc", SERVER_KEY);
        byte[] serverData = YsmCrypt.encryptServerCache(serialized, SERVER_KEY, hashes[0], hashes[1]);
        byte[] clientData = YsmCrypt.transcodeServerDataToClientCache(serverData, SERVER_KEY, CLIENT_KEY, hashes[0], hashes[1]);
        byte[] clear = YsmCrypt.read(clientData, CLIENT_KEY);

        RawYsmModel out = new YSMBinaryDeserializer(clear, 32).deserialize();
        assertNotNull(out.mainEntity.mainModel, "几何体全链路 round-trip");
        assertEquals("geometry.corpus", out.mainEntity.mainModel.identifier, "geometry identifier round-trip");
        assertTrue(out.mainEntity.textures.containsKey("default"), "texture 全链路 round-trip");
    }

    /** serialize 输出确定性（无随机路径）——之后任何协议重构输出必须稳定。 */
    @Test
    void serialize_outputIsDeterministic() throws Exception {
        byte[] a = serializeBytes(syntheticModel(), 32);
        byte[] b = serializeBytes(syntheticModel(), 32);
        assertArrayEquals(a, b, "同模型 serialize 输出必须确定");
    }

    /** writeModern 仅支持 format>=26。 */
    @Test
    void serializeRejectsLegacyAndOldFormats() {
        RawYsmModel src = syntheticModel();
        assertThrows(UnsupportedOperationException.class, () -> serializeBytes(src, 15),
                "serializer 不支持 legacy 格式（<16）——legacy 行为由真实样本验证");
        assertThrows(UnsupportedOperationException.class, () -> serializeBytes(src, 25),
                "writeModern 不支持 format<26");
    }
}
