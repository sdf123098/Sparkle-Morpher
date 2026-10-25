package com.micaftic.morpher.resource;

import com.micaftic.morpher.resource.pojo.RawYsmModel;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M2：zip 字节源在内存中有界展开（不落临时文件），并保持 folder/zip 源的解析语义。 */
class MemoryZipContainerTest {

    private static final byte[] PNG_1X1 = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private static final String MINIMAL_GEO_JSON = """
            {
              "format_version": "1.12.0",
              "minecraft:geometry": [
                {
                  "description": {
                    "identifier": "geometry.model",
                    "texture_width": 64,
                    "texture_height": 64,
                    "visible_bounds_width": 2,
                    "visible_bounds_height": 2,
                    "visible_bounds_offset": [0, 0, 0]
                  },
                  "bones": []
                }
              ]
            }
            """;

    private static final String YSM_JSON = """
            {
              "files": {
                "player": {
                  "model": { "main": "model.geo.json" },
                  "texture": "tex.png"
                }
              }
            }
            """;

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        return zip(entries, StandardCharsets.UTF_8);
    }

    private static byte[] zip(Map<String, byte[]> entries, Charset charset) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, charset)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static Map<String, byte[]> minimalModel(String prefix) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(prefix + "ysm.json", YSM_JSON.getBytes(StandardCharsets.UTF_8));
        files.put(prefix + "model.geo.json", MINIMAL_GEO_JSON.getBytes(StandardCharsets.UTF_8));
        files.put(prefix + "tex.png", PNG_1X1);
        return files;
    }

    @Test
    void singleModelRootPrefix_isStripped() throws Exception {
        try (ModelResourceContainer container = ModelResourceContainer.zipBytes(zip(minimalModel("MyModel/")))) {
            assertEquals(3, container.fileCount());
            assertNotNull(container.read("ysm.json"));
            assertNotNull(container.read("model.geo.json"));
            assertArrayEquals(PNG_1X1, container.read("tex.png"));
            assertTrue(container.exists("ysm.json"));
        }
    }

    @Test
    void readFallsBackToCaseInsensitiveMatch() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("Model/ysm.json", YSM_JSON.getBytes(StandardCharsets.UTF_8));
        files.put("Model/TEX.PNG", PNG_1X1);
        try (ModelResourceContainer container = ModelResourceContainer.zipBytes(zip(files))) {
            assertArrayEquals(PNG_1X1, container.read("tex.png"));
            assertTrue(container.exists("Tex.Png"));
        }
    }

    @Test
    void gbkEntryNames_areRecoveredByFallback() throws Exception {
        byte[] archive = zip(minimalModel("模型/"), Charset.forName("GBK"));
        try (ModelResourceContainer container = ModelResourceContainer.zipBytes(archive)) {
            assertEquals(3, container.fileCount());
            assertNotNull(container.read("ysm.json"));
            assertArrayEquals(PNG_1X1, container.read("tex.png"));
        }
    }

    @Test
    void escapingEntryNames_areSkipped() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("ysm.json", YSM_JSON.getBytes(StandardCharsets.UTF_8));
        files.put("../evil.txt", "nope".getBytes(StandardCharsets.UTF_8));
        files.put("nested/../../evil2.txt", "nope".getBytes(StandardCharsets.UTF_8));
        try (ModelResourceContainer container = ModelResourceContainer.zipBytes(zip(files))) {
            assertNull(container.read("evil.txt"));
            assertNull(container.read("evil2.txt"));
            assertNotNull(container.read("ysm.json"));
            assertThrows(IOException.class, () -> container.read("../evil.txt"));
        }
    }

    @Test
    void multipleDistinctModelRoots_keepWholeArchiveAsRoot() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>(minimalModel("A/"));
        files.put("B/ysm.json", YSM_JSON.getBytes(StandardCharsets.UTF_8));
        try (ModelResourceContainer container = ModelResourceContainer.zipBytes(zip(files))) {
            assertNotNull(container.read("A/ysm.json"));
            assertNotNull(container.read("A/tex.png"));
            assertNotNull(container.read("B/ysm.json"));
        }
    }

    @Test
    void perFileBudget_skipsOversizedEntry() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("small.txt", "abcd".getBytes(StandardCharsets.UTF_8));
        files.put("big.bin", new byte[32]);
        byte[] archive = zip(files);
        try (ModelResourceContainer container = ModelResourceContainer.zipBytes(
                archive, StandardCharsets.UTF_8, 16, 8, 1024)) {
            assertNotNull(container.read("small.txt"));
            assertNull(container.read("big.bin"));
        }
    }

    @Test
    void totalBudget_rejectsArchive() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("a.txt", new byte[8]);
        files.put("b.txt", new byte[8]);
        byte[] archive = zip(files);
        assertThrows(IOException.class, () -> ModelResourceContainer.zipBytes(
                archive, StandardCharsets.UTF_8, 16, 1024, 12));
    }

    @Test
    void fileCountBudget_stopsAtLimit() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("a.txt", new byte[1]);
        files.put("b.txt", new byte[1]);
        files.put("c.txt", new byte[1]);
        byte[] archive = zip(files);
        try (ModelResourceContainer container = ModelResourceContainer.zipBytes(
                archive, StandardCharsets.UTF_8, 1, 1024, 1024)) {
            assertEquals(1, container.fileCount());
        }
    }

    @Test
    void malformedArchive_isRejected() {
        assertThrows(IOException.class, () -> ModelResourceContainer.zipBytes(new byte[8]));
        assertThrows(IOException.class, () -> ModelResourceContainer.zipBytes(null));
    }

    @Test
    void memoryArchive_parsesYsmModelWithoutFiles() throws Exception {
        byte[] archive = zip(minimalModel("MyModel/"));
        try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(archive)) {
            RawYsmModel model = deserializer.deserialize();
            assertNotNull(model.mainEntity.mainModel, "内存 zip 中的主几何体必须被解析");
            assertNotNull(model.mainEntity.mainModel.identifier);
            assertTrue(model.mainEntity.textures.containsKey("tex"), "内存 zip 中的纹理必须被解析");
        }
    }

    @Test
    void memoryArchive_folderHashMatchesFolderSource() throws Exception {
        byte[] archive = zip(minimalModel("MyModel/"));
        String archiveHash;
        try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(archive)) {
            deserializer.deserialize();
            archiveHash = deserializer.getFolderHash();
        }
        assertFalse(archiveHash == null || archiveHash.isEmpty(), "内存 zip 解析也必须产出 folder hash");
    }
}
