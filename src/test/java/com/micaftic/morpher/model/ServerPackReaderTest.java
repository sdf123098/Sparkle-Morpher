package com.micaftic.morpher.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * R8-4 ServerPackReader 测试：ysm-pack.json/ysm-pack.png 元数据解析（从 ServerModelManager 抽取）。
 */
class ServerPackReaderTest {

    @TempDir
    Path tempDir;

    private static byte[] minimalPng(int width, int height) {
        byte[] data = new byte[24];
        // PNG 签名
        byte[] signature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(signature, 0, data, 0, 8);
        // IHDR 宽度/高度（大端，位于 offset 16/20）
        data[16] = (byte) (width >>> 24);
        data[17] = (byte) (width >>> 16);
        data[18] = (byte) (width >>> 8);
        data[19] = (byte) width;
        data[20] = (byte) (height >>> 24);
        data[21] = (byte) (height >>> 16);
        data[22] = (byte) (height >>> 8);
        data[23] = (byte) height;
        return data;
    }

    @Test
    void pngDimensions_parsesValidPngHeader() {
        assertArrayEquals(new int[]{64, 32}, ServerPackReader.pngDimensions(minimalPng(64, 32)));
    }

    @Test
    void pngDimensions_returnsZeroForInvalidOrShortData() {
        assertArrayEquals(new int[]{0, 0}, ServerPackReader.pngDimensions(new byte[10]));
        assertArrayEquals(new int[]{0, 0}, ServerPackReader.pngDimensions(new byte[]{1, 2, 3}));
        assertArrayEquals(new int[]{0, 0}, ServerPackReader.pngDimensions(null));
        assertArrayEquals(new int[]{0, 0}, ServerPackReader.pngDimensions("not-a-png".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void read_parsesNameDescriptionLangAndIcon() throws IOException {
        Path packDir = tempDir.resolve("packs").resolve("demo");
        Files.createDirectories(packDir);
        Files.writeString(packDir.resolve("ysm-pack.json"), """
                {"name":"Demo Pack","description":"A demo",
                 "lang":{"zh_cn":{"key":"value"}}}""", StandardCharsets.UTF_8);
        Files.write(packDir.resolve("ysm-pack.png"), minimalPng(16, 8));
        Path baseDir = tempDir.resolve("packs");

        ServerPackData data = ServerPackReader.read(baseDir, packDir);

        assertEquals("demo/", data.folderPath, "folderPath 为 baseDir 相对路径（URI relativize 带尾斜杠，原实现行为）");
        assertEquals("\"Demo Pack\"", data.name, "原实现 name 取 json.get(name).toString()（含引号）");
        assertEquals("\"A demo\"", data.description);
        assertEquals("\"value\"", data.lang.get("zh_cn").get("key"), "lang 值取 toString()（带引号，原实现行为）");
        assertEquals(16, data.iconWidth);
        assertEquals(8, data.iconHeight);
        assertEquals(2, data.iconFormat, "iconFormat=2 表示 PNG");
        assertArrayEquals(minimalPng(16, 8), data.iconData);
    }

    @Test
    void read_withoutPng_leavesIconNull() throws IOException {
        Path packDir = tempDir.resolve("packs").resolve("noicon");
        Files.createDirectories(packDir);
        Files.writeString(packDir.resolve("ysm-pack.json"), "{\"name\":\"X\"}", StandardCharsets.UTF_8);
        Path baseDir = tempDir.resolve("packs");

        ServerPackData data = ServerPackReader.read(baseDir, packDir);

        assertNull(data.iconData);
        assertEquals(0, data.iconWidth);
        assertEquals(0, data.iconHeight);
        assertEquals(0, data.iconFormat);
        assertEquals("\"X\"", data.name);
        assertNull(data.lang, "无 lang 字段时保持 null");
    }

    @Test
    void read_withoutPackJson_throws() throws IOException {
        Path packDir = tempDir.resolve("packs").resolve("bad");
        Files.createDirectories(packDir);
        Path baseDir = tempDir.resolve("packs");

        assertThrows(IOException.class, () -> ServerPackReader.read(baseDir, packDir));
    }

    @Test
    void read_invalidJson_throws() throws IOException {
        Path packDir = tempDir.resolve("packs").resolve("badjson");
        Files.createDirectories(packDir);
        Files.writeString(packDir.resolve("ysm-pack.json"), "not-json", StandardCharsets.UTF_8);
        Path baseDir = tempDir.resolve("packs");

        assertThrows(RuntimeException.class, () -> ServerPackReader.read(baseDir, packDir));
    }
}
