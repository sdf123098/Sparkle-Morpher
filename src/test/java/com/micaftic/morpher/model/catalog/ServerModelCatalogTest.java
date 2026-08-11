package com.micaftic.morpher.model.catalog;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R8-3 ServerModelCatalog 测试：服务端模型目录状态收敛（byName / authModels / modelHashes）。
 *
 * <p>从 ServerModelManager 的 CACHE_NAME_INFO / AUTH_MODELS / modelHashSet 三个静态字段抽取；
 * 泛型 T 不依赖 ServerModelData，JVM 单测可跑真实语义（整表替换 + 归一回退查询）。</p>
 */
class ServerModelCatalogTest {

    private static ServerModelCatalog<String> catalog() {
        return new ServerModelCatalog<>();
    }

    @Test
    void replaceAll_replacesByNameAndAuthAndHashes() {
        ServerModelCatalog<String> catalog = catalog();
        IntOpenHashSet hashes = new IntOpenHashSet();
        hashes.add(42);
        catalog.replaceAll(Map.of("cirno", "data-cirno", "dai", "data-dai"), Set.of("cirno"), hashes);

        assertEquals("data-cirno", catalog.lookup("cirno"));
        assertEquals("data-dai", catalog.lookup("dai"));
        assertNull(catalog.lookup("missing"));
        assertTrue(catalog.contains("cirno"));
        assertFalse(catalog.contains("missing"));
        assertEquals(2, catalog.all().size());
        assertTrue(catalog.authModels().contains("cirno"));
        assertTrue(catalog.modelHashes().contains(42));
        assertFalse(catalog.isEmpty());
    }

    @Test
    void replaceAll_again_replacesPreviousContent() {
        ServerModelCatalog<String> catalog = catalog();
        catalog.replaceAll(Map.of("old", "v1"), Set.of("old"), new IntOpenHashSet());
        catalog.replaceAll(Map.of("new", "v2"), Set.of(), new IntOpenHashSet());

        assertNull(catalog.lookup("old"), "第二次整表替换应清除旧条目");
        assertEquals("v2", catalog.lookup("new"));
        assertFalse(catalog.authModels().contains("old"));
    }

    @Test
    void lookupNormalized_fallsBackToNormalizedKey() {
        ServerModelCatalog<String> catalog = catalog();
        catalog.replaceAll(Map.of("my_pack/cirno", "data"), Set.of(), new IntOpenHashSet());

        assertEquals("data", catalog.lookupNormalized("My Pack/Cirno"), "大小写/空格差异应归一回退命中");
        assertEquals("data", catalog.lookupNormalized("my_pack/cirno"));
        assertNull(catalog.lookupNormalized("unknown"));
    }

    @Test
    void replaceAuth_updatesAuthSetOnly() {
        ServerModelCatalog<String> catalog = catalog();
        catalog.replaceAll(Map.of("a", "1", "b", "2"), Set.of("a"), new IntOpenHashSet());
        catalog.replaceAuth(Set.of("b"));

        assertFalse(catalog.authModels().contains("a"));
        assertTrue(catalog.authModels().contains("b"));
        assertEquals("1", catalog.lookup("a"), "replaceAuth 不应动 byName");
        assertTrue(catalog.modelHashes().isEmpty());
    }

    @Test
    void clear_resetsEverything() {
        ServerModelCatalog<String> catalog = catalog();
        IntOpenHashSet hashes = new IntOpenHashSet();
        hashes.add(7);
        catalog.replaceAll(Map.of("a", "1"), Set.of("a"), hashes);
        catalog.clear();

        assertTrue(catalog.isEmpty());
        assertNull(catalog.lookup("a"));
        assertTrue(catalog.authModels().isEmpty());
        assertTrue(catalog.modelHashes().isEmpty());
    }

    @Test
    void all_returnsLiveViewOfCurrentContent() {
        ServerModelCatalog<String> catalog = catalog();
        catalog.replaceAll(Map.of("a", "1"), Set.of(), new IntOpenHashSet());
        Map<String, String> all = catalog.all();
        assertSame(catalog.lookup("a"), all.get("a"));
        assertEquals(1, all.size());
    }
}
