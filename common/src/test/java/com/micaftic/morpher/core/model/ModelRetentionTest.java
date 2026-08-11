package com.micaftic.morpher.core.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R7.3 ModelRetention 测试：跨会话装配保留筛选（断线/换服时哪些 ModelAssembly 存活）。
 *
 * <p>从 ClientModelManager.resetClientState 的 survivors/toRelease 判定抽取；
 * 判定规则：localContext 引用相等 / localOnly id / "default" → 保留，其余释放。</p>
 */
class ModelRetentionTest {

    private static List<Map.Entry<String, String>> entries(String... ids) {
        List<Map.Entry<String, String>> list = new ArrayList<>();
        for (String id : ids) {
            list.add(Map.entry(id, "asm-" + id));
        }
        return list;
    }

    @Test
    void partition_emptyInput_returnsEmptySplit() {
        ModelRetention.Split<String> split = ModelRetention.partition(List.of(), id -> false, null);
        assertTrue(split.survivors().isEmpty());
        assertTrue(split.toRelease().isEmpty());
    }

    @Test
    void partition_keepsLocalContextByReference() {
        String localContext = "local-context-asm";
        List<Map.Entry<String, String>> entries = entries("a", "b");
        // 把 localContext 作为 "b" 的值（引用相等判定，不看 id）
        entries.set(1, Map.entry("b", localContext));

        ModelRetention.Split<String> split = ModelRetention.partition(entries, id -> false, localContext);

        assertEquals(List.of("b"), split.survivors().stream().map(Map.Entry::getKey).toList());
        assertSame(localContext, split.survivors().get(0).getValue());
        assertEquals(List.of("asm-a"), split.toRelease());
    }

    @Test
    void partition_keepsLocalOnlyAndDefaultIds() {
        ModelRetention.Split<String> split = ModelRetention.partition(
                entries("cirno", "default", "server-model"), Set.of("cirno")::contains, null);

        assertEquals(List.of("cirno", "default"), split.survivors().stream().map(Map.Entry::getKey).toList());
        assertEquals(List.of("asm-server-model"), split.toRelease());
    }

    @Test
    void partition_releasesEverythingElse() {
        ModelRetention.Split<String> split = ModelRetention.partition(
                entries("a", "b", "c"), id -> false, null);

        assertTrue(split.survivors().isEmpty());
        assertEquals(List.of("asm-a", "asm-b", "asm-c"), split.toRelease());
    }

    @Test
    void partition_preservesInputOrder() {
        ModelRetention.Split<String> split = ModelRetention.partition(
                entries("x", "cirno", "y", "default", "z"), Set.of("cirno")::contains, null);

        assertEquals(List.of("cirno", "default"), split.survivors().stream().map(Map.Entry::getKey).toList());
        assertEquals(List.of("asm-x", "asm-y", "asm-z"), split.toRelease());
    }

    @Test
    void partition_genericValues_worksWithAnyType() {
        ModelRetention.Split<Integer> split = ModelRetention.partition(
                List.of(Map.entry("keep", 42), Map.entry("drop", 7)), Set.of("keep")::contains, null);

        assertEquals(List.of("keep"), split.survivors().stream().map(Map.Entry::getKey).toList());
        assertEquals(List.of(7), split.toRelease());
    }
}
