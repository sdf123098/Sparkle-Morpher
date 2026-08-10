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
    void zipBomb_oversizedEntrySkipped() throws Exception {
        // 构造单个超大条目（超过 MAX_PER_FILE_BYTES）——用大量重复数据压缩后很小。
        // R4.2 起限额统一为 warn-skip：超限条目被跳过，正常条目仍可读（不抛异常）。
        Path zip = tempDir.resolve("bomb.zip");
        byte[] big = new byte[(int) ModelResourceContainer.MAX_PER_FILE_BYTES + 1024];
        java.util.Arrays.fill(big, (byte) 'A');
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("big.bin"));
            out.write(big);
            out.closeEntry();
            out.putNextEntry(new ZipEntry("ysm.json"));
            out.write("{}".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        try (ModelResourceContainer container = ModelResourceContainer.zip(zip)) {
            assertNull(container.read("big.bin"), "超单文件限额条目应被跳过");
            assertArrayEquals("{}".getBytes(StandardCharsets.UTF_8), container.read("ysm.json"),
                    "正常条目不受超限条目影响");
        }
    }

    @Test
    void caseInsensitive_readFindsDifferentCase() throws Exception {
        // YSM 模型包在 Windows 解压后大小写常不一致：read 先精确匹配，未命中按大小写不敏感回退
        try (ModelResourceContainer container = ModelResourceContainer.memory(
                Map.of("Tex.PNG", "PNG".getBytes(StandardCharsets.UTF_8)))) {
            assertArrayEquals("PNG".getBytes(StandardCharsets.UTF_8), container.read("Tex.PNG"), "精确匹配优先");
            assertArrayEquals("PNG".getBytes(StandardCharsets.UTF_8), container.read("tex.png"), "大小写不敏感回退");
            assertArrayEquals("PNG".getBytes(StandardCharsets.UTF_8), container.read("TEX.PNG"));
            assertTrue(container.exists("tex.png"));
        }
    }

    @Test
    void memory_duplicateCaseCollisionRejected() {
        // 大小写归一后冲突（Tex.png 与 tex.png 并存）是调用方数据错误
        assertThrows(IllegalArgumentException.class, () -> ModelResourceContainer.memory(
                java.util.Map.of("Tex.png", new byte[]{1}, "tex.png", new byte[]{2})));
    }

    @Test
    void zip_rootPrefixStripped() throws Exception {
        // zip 内嵌唯一模型根目录（ModelFolder/ 下含 ysm.json）→ 剥离前缀，read("ysm.json") 命中
        Path zip = tempDir.resolve("nested.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("ModelFolder/ysm.json"));
            out.write("{\"files\":{}}".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("ModelFolder/tex.png"));
            out.write("PNG".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        try (ModelResourceContainer container = ModelResourceContainer.zip(zip)) {
            assertArrayEquals("{\"files\":{}}".getBytes(StandardCharsets.UTF_8), container.read("ysm.json"));
            assertArrayEquals("PNG".getBytes(StandardCharsets.UTF_8), container.read("tex.png"));
            assertEquals(2, container.fileCount());
        }
    }

    @Test
    void folderSource_isLazyAndCaseInsensitive() throws Exception {
        // R4.2：folder 源懒加载——构造不预读，读取时实时读磁盘文件
        Path root = tempDir.resolve("lazy");
        Files.createDirectories(root);
        Files.writeString(root.resolve("YSM.JSON"), "{}");
        try (ModelResourceContainer container = ModelResourceContainer.folder(root)) {
            assertEquals(1, container.fileCount(), "构造时已收集条目清单");
            assertArrayEquals("{}".getBytes(StandardCharsets.UTF_8), container.read("ysm.json"),
                    "大小写不敏感回退命中磁盘原 case 文件");
            assertArrayEquals("{}".getBytes(StandardCharsets.UTF_8), container.read("YSM.JSON"));
            // 懒加载：读的是实时磁盘内容
            Files.writeString(root.resolve("YSM.JSON"), "{\"changed\":true}");
            assertArrayEquals("{\"changed\":true}".getBytes(StandardCharsets.UTF_8), container.read("ysm.json"));
        }
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
