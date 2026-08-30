package com.micaftic.morpher.core.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R7.1 LocalModelImportStore 测试：原子写 / 路径沙箱 / 扩展名归一 / sibling 清理。
 */
class LocalModelImportStoreTest {

    @TempDir
    Path tempDir;

    private LocalModelImportStore store() throws IOException {
        Path custom = tempDir.resolve("custom");
        Files.createDirectories(custom);
        return new LocalModelImportStore(custom);
    }

    @Test
    void persist_writesBytesAtomically() throws Exception {
        LocalModelImportStore store = store();
        byte[] data = "model-bytes".getBytes(StandardCharsets.UTF_8);
        Path target = store.persist("cirno", "cirno.zip", data);
        assertTrue(target.getFileName().toString().endsWith(".zip"), "扩展名按文件名识别");
        assertArrayEquals(data, Files.readAllBytes(target));
        // 无 .tmp 残留
        try (var stream = Files.list(target.getParent())) {
            assertFalse(stream.anyMatch(p -> p.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void persist_unknownExtensionFallsBackToYsm() throws Exception {
        LocalModelImportStore store = store();
        Path target = store.persist("cirno", "cirno.unknownext", new byte[]{1});
        assertTrue(target.getFileName().toString().endsWith(".ysm"), "未知扩展名回退 .ysm");
    }

    @Test
    void persist_rejectsPathEscape() throws Exception {
        LocalModelImportStore store = store();
        // modelId 含路径逃逸 → 拒绝（不写入 custom 之外）
        assertThrows(IOException.class, () -> store.persist("../evil", "x.zip", new byte[]{1}));
        Path custom = tempDir.resolve("custom");
        assertFalse(Files.exists(custom.resolve("..").resolve("evil.zip")), "不得写入 custom 之外");
        // 绝对路径 style 同样拒绝
        assertThrows(IOException.class, () -> store.persist("/abs/evil", "x.zip", new byte[]{1}));
    }

    @Test
    void persist_replacesSiblingExtensions() throws Exception {
        LocalModelImportStore store = store();
        Path custom = tempDir.resolve("custom");
        // 预先放一个 .ysm 旧文件
        Files.writeString(custom.resolve("cirno.ysm"), "old");
        Path target = store.persist("cirno", "cirno.zip", new byte[]{2});
        assertTrue(target.getFileName().toString().endsWith(".zip"));
        assertFalse(Files.exists(custom.resolve("cirno.ysm")), "同 id 的 .ysm 兄弟文件应被清理");
        assertTrue(Files.exists(custom.resolve("cirno.zip")));
    }

    @Test
    void persist_nullInputsReturnNull() throws Exception {
        LocalModelImportStore store = store();
        assertNull(store.persist(null, "x.zip", new byte[]{1}));
        assertNull(store.persist("", "x.zip", new byte[]{1}));
        assertNull(store.persist("cirno", "x.zip", null));
    }

    @Test
    void importExtension_recognizesKnownExtensions() {
        assertEquals(".ysm", LocalModelImportStore.importExtension("model.YSM"));
        assertEquals(".zip", LocalModelImportStore.importExtension("model.zip"));
        assertEquals(".bbmodel", LocalModelImportStore.importExtension("model.bbmodel"));
        assertEquals(".gltf", LocalModelImportStore.importExtension("model.GLTF"));
        assertEquals(".glb", LocalModelImportStore.importExtension("model.glb"));
        assertEquals("", LocalModelImportStore.importExtension("model.txt"));
        assertEquals("", LocalModelImportStore.importExtension(null));
        assertEquals("", LocalModelImportStore.importExtension("noext"));
    }

    @Test
    void isInside_rejectsTraversalAndAcceptsChildren() throws Exception {
        Path root = tempDir.resolve("custom").toAbsolutePath().normalize();
        Files.createDirectories(root);
        assertTrue(LocalModelImportStore.isInside(root, root.resolve("a/b.zip")));
        assertFalse(LocalModelImportStore.isInside(root, root.resolve("../outside.zip")));
        assertFalse(LocalModelImportStore.isInside(root, tempDir.resolve("outside")));
    }

    @Test
    void persist_reimportOverwritesExisting() throws Exception {
        LocalModelImportStore store = store();
        store.persist("cirno", "cirno.ysm", "v1".getBytes(StandardCharsets.UTF_8));
        store.persist("cirno", "cirno.ysm", "v2".getBytes(StandardCharsets.UTF_8));
        Path custom = tempDir.resolve("custom");
        assertEquals("v2", Files.readString(custom.resolve("cirno.ysm")));
    }
}
