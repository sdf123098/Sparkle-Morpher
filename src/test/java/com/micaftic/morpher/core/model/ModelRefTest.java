package com.micaftic.morpher.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R5.1 ModelRef 测试：parse/toString 往返、五种来源、namespace 语义、非法输入。
 */
class ModelRefTest {

    @Test
    void toString_twoPartForm_noNamespace() {
        assertEquals("local:cirno", ModelRef.of(ModelSourceType.LOCAL, "cirno").toString());
        assertEquals("server:cirno", ModelRef.of(ModelSourceType.LEGACY_SERVER, "cirno").toString());
        assertEquals("builtin:default", ModelRef.of(ModelSourceType.BUILTIN, "default").toString());
    }

    @Test
    void toString_threePartForm_withNamespace() {
        assertEquals("cloud:123e4567-e89b:asset-1",
                ModelRef.of(ModelSourceType.CLOUD, "123e4567-e89b", "asset-1").toString());
        assertEquals("server-forced:lobby:maid",
                ModelRef.of(ModelSourceType.SERVER_FORCED, "lobby", "maid").toString());
    }

    @Test
    void parse_roundTripsAllSources() {
        assertEquals(ModelRef.of(ModelSourceType.BUILTIN, "default"), ModelRef.parse("builtin:default"));
        assertEquals(ModelRef.of(ModelSourceType.LOCAL, "cirno"), ModelRef.parse("local:cirno"));
        assertEquals(ModelRef.of(ModelSourceType.LEGACY_SERVER, "cirno"), ModelRef.parse("server:cirno"));
        assertEquals(ModelRef.of(ModelSourceType.SERVER_FORCED, "lobby", "maid"), ModelRef.parse("server-forced:lobby:maid"));
        assertEquals(ModelRef.of(ModelSourceType.CLOUD, "uuid-1", "asset-2"), ModelRef.parse("cloud:uuid-1:asset-2"));
        // token 大小写不敏感
        assertEquals(ModelRef.of(ModelSourceType.LOCAL, "cirno"), ModelRef.parse("LOCAL:cirno"));
    }

    @Test
    void parse_rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> ModelRef.parse(null));
        assertThrows(IllegalArgumentException.class, () -> ModelRef.parse(""));
        assertThrows(IllegalArgumentException.class, () -> ModelRef.parse("cirno"), "缺 source");
        assertThrows(IllegalArgumentException.class, () -> ModelRef.parse("unknown:cirno"), "未知 source token");
        assertThrows(IllegalArgumentException.class, () -> ModelRef.parse("local:"), "缺 id");
        assertThrows(IllegalArgumentException.class, () -> ModelRef.parse("a:b:c:d"), "超过三段");
        assertThrows(IllegalArgumentException.class, () -> ModelRef.parse("local::cirno"), "空 namespace");
    }

    @Test
    void constructor_normalizesEmptyNamespace() {
        assertEquals(ModelRef.of(ModelSourceType.LOCAL, "cirno"),
                new ModelRef(ModelSourceType.LOCAL, "", "cirno"), "空 namespace 归一化为 null");
        assertNull(ModelRef.of(ModelSourceType.LOCAL, "cirno").namespace());
    }

    @Test
    void constructor_rejectsNullAndEmptyId() {
        assertThrows(NullPointerException.class, () -> new ModelRef(null, null, "x"));
        assertThrows(NullPointerException.class, () -> ModelRef.of(ModelSourceType.LOCAL, null));
        assertThrows(IllegalArgumentException.class, () -> ModelRef.of(ModelSourceType.LOCAL, ""));
    }

    @Test
    void equals_hashCode_basedOnAllFields() {
        ModelRef a = ModelRef.of(ModelSourceType.LOCAL, "cirno");
        ModelRef b = ModelRef.of(ModelSourceType.LOCAL, "cirno");
        ModelRef c = ModelRef.of(ModelSourceType.LEGACY_SERVER, "cirno");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotEquals(ModelRef.of(ModelSourceType.CLOUD, "ns", "id"), ModelRef.of(ModelSourceType.CLOUD, "ns", "other"));
    }

    @Test
    void fromToken_matchesRuntimeTokens() {
        assertEquals(ModelSourceType.BUILTIN, ModelSourceType.fromToken("builtin"));
        assertEquals(ModelSourceType.LOCAL, ModelSourceType.fromToken("local"));
        assertEquals(ModelSourceType.LEGACY_SERVER, ModelSourceType.fromToken("server"));
        assertEquals(ModelSourceType.SERVER_FORCED, ModelSourceType.fromToken("server-forced"));
        assertEquals(ModelSourceType.CLOUD, ModelSourceType.fromToken("cloud"));
        assertNull(ModelSourceType.fromToken("nope"));
        assertNull(ModelSourceType.fromToken(null));
        assertTrue(ModelSourceType.values().length >= 5, "至少五种来源");
    }
}
