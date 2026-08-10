package com.micaftic.morpher.resource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R4.1/R4.3 ModelResourceContainer 测试：三源读取 / sandbox 逃逸拒绝 / 限额（zip bomb） / hash 确定性。
 */
class ModelResourceContainerTest {

    @TempDir
    Path tempDir;

    private Path writeZip(Map<String, String> entries) throws IOException {
        Path zip = tempDir.resolve("model.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(e.getKey()));
                out.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return zip;
    }

    @Test
    void zipSource_readsEntries() throws Exception {
        Path zip = writeZip(Map.of("ysm.json", "{\"files\":{}}", "tex.png", "PNG"));
        try (ModelResourceContainer container = ModelResourceContainer.zip(zip)) {
            assertArrayEquals("{\"files\":{}}".getBytes(StandardCharsets.UTF_8), container.read("ysm.json"));
            assertArrayEquals("PNG".getBytes(StandardCharsets.UTF_8), container.read("tex.png"));
            assertEquals(2, container.fileCount());
            assertNull(container.read("absent.json"), "缺失资源返回 null");
        }
    }

    @Test
    void memorySource_readsEntries() throws Exception {
        try (ModelResourceContainer container = ModelResourceContainer.memory(
                Map.of("a.json", "A".getBytes(StandardCharsets.UTF_8)))) {
            assertArrayEquals("A".getBytes(StandardCharsets.UTF_8), container.read("a.json"));
        }
    }

    @Test
    void folderSource_readsEntries() throws Exception {
        Path root = tempDir.resolve("model");
        Files.createDirectories(root);
        Files.writeString(root.resolve("ysm.json"), "{}");
        Files.createDirectories(root.resolve("sub"));
        Files.writeString(root.resolve("sub/tex.png"), "PNG");
        try (ModelResourceContainer container = ModelResourceContainer.folder(root)) {
            assertArrayEquals("{}".getBytes(StandardCharsets.UTF_8), container.read("ysm.json"));
            assertArrayEquals("PNG".getBytes(StandardCharsets.UTF_8), container.read("sub/tex.png"));
        }
    }

    @Test
    void sandbox_rejectsTraversalAndAbsolute() throws Exception {
        try (ModelResourceContainer container = ModelResourceContainer.memory(Map.of("ok.json", new byte[]{1}))) {
            assertThrows(IOException.class, () -> container.read("../secret.png"));
            assertThrows(IOException.class, () -> container.read("/etc/passwd"));
            assertThrows(IOException.class, () -> container.read("C:/windows/system32/x"));
            assertThrows(IOException.class, () -> container.read("//server/share/x"));
            assertThrows(IOException.class, () -> container.read("..\\..\\escape"), "反斜杠逃逸同样拒绝");
        }
    }

    @Test
    void normalize_acceptsPlainRelative() {
        assertEquals("a/b.json", ModelResourceContainer.normalize("a/b.json"));
        assertEquals("a/b.json", ModelResourceContainer.normalize("a\\b.json"));
        assertEquals("a/b.json", ModelResourceContainer.normalize("./a/./b.json"));
        assertNull(ModelResourceContainer.normalize("a/../b.json"));
        assertNull(ModelResourceContainer.normalize("../a"));
        assertNull(ModelResourceContainer.normalize("/abs"));
        assertNull(ModelResourceContainer.normalize("D:/x"));
        assertNull(ModelResourceContainer.normalize(""));
    }

    @Test
    void zipBomb_perFileLimitRejected() throws Exception {
        // 构造单个超大条目（超过 MAX_PER_FILE_BYTES）——用大量重复数据压缩后很小
        Path zip = tempDir.resolve("bomb.zip");
        byte[] big = new byte[(int) ModelResourceContainer.MAX_PER_FILE_BYTES + 1024];
        java.util.Arrays.fill(big, (byte) 'A');
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("big.bin"));
            out.write(big);
            out.closeEntry();
        }
        assertThrows(IOException.class, () -> ModelResourceContainer.zip(zip), "超单文件限额应拒绝");
    }

    @Test
    void hash_isDeterministicAndContentSensitive() throws Exception {
        try (ModelResourceContainer a = ModelResourceContainer.memory(Map.of("x", "1".getBytes(StandardCharsets.UTF_8)));
             ModelResourceContainer b = ModelResourceContainer.memory(Map.of("x", "1".getBytes(StandardCharsets.UTF_8)));
             ModelResourceContainer c = ModelResourceContainer.memory(Map.of("x", "2".getBytes(StandardCharsets.UTF_8)))) {
            assertEquals(a.sha256(), b.sha256(), "同内容同条目 → 同 hash");
            assertTrue(!a.sha256().equals(c.sha256()), "内容变化 → hash 变化");
        }
    }
}
