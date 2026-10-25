package com.micaftic.morpher.resource;

import com.micaftic.morpher.resource.gltf.GltfLoadResult;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M2：按文件名/内容把内存字节分发到 YSM、bbmodel、Bedrock、自包含 glTF 解析。 */
class MemoryModelParserTest {

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
                  "bones": [
                    { "name": "LeftArm", "pivot": [0, 0, 0] }
                  ]
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

    private static final String BBMODEL_JSON = """
            {
              "meta": { "format_version": "4.5", "model_format": "free" },
              "resolution": { "width": 16, "height": 16 },
              "name": "Memory Model",
              "elements": [],
              "outliner": [],
              "textures": [],
              "animations": []
            }
            """;

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static Map<String, byte[]> ysmModelZip(String prefix) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(prefix + "ysm.json", YSM_JSON.getBytes(StandardCharsets.UTF_8));
        files.put(prefix + "model.geo.json", MINIMAL_GEO_JSON.getBytes(StandardCharsets.UTF_8));
        files.put(prefix + "tex.png", PNG_1X1);
        return files;
    }

    @Test
    void folderZip_parsesWithoutTempFiles() throws Exception {
        RawYsmModel model = MemoryModelParser.parseLegacy(zip(ysmModelZip("MyModel/")), "model.zip");
        assertNotNull(model.mainEntity.mainModel);
        assertTrue(model.mainEntity.textures.containsKey("tex"));
    }

    @Test
    void figuraZip_parsesBbModelAndSideTextures() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("avatar.json", "{\"name\":\"MemoryAvatar\"}".getBytes(StandardCharsets.UTF_8));
        files.put("avatar.bbmodel", BBMODEL_JSON.getBytes(StandardCharsets.UTF_8));
        files.put("tex.png", PNG_1X1);
        RawYsmModel model = MemoryModelParser.parseLegacy(zip(files), "avatar.zip");
        assertNotNull(model.mainEntity.mainModel);
        assertNotNull(model.properties.sha256);
    }

    @Test
    void standaloneBbModel_parses() throws Exception {
        RawYsmModel model = MemoryModelParser.parseLegacy(
                BBMODEL_JSON.getBytes(StandardCharsets.UTF_8), "model.bbmodel");
        assertNotNull(model.mainEntity.mainModel);
    }

    @Test
    void bedrockGeometry_normalizesBoneCase() throws Exception {
        RawYsmModel model = MemoryModelParser.parseLegacy(
                MINIMAL_GEO_JSON.getBytes(StandardCharsets.UTF_8), "dragon.geo.json");
        assertEquals("leftarm", model.mainEntity.mainModel.bones.get(0).name);
        assertEquals("geometry.model", model.modelId);
    }

    @Test
    void selfContainedGltf_parsesInMemory() throws Exception {
        String json = """
                {"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[]}],"nodes":[],
                 "meshes":[],"buffers":[],"images":[]}
                """;
        GltfLoadResult result = MemoryModelParser.parseGltf(json.getBytes(StandardCharsets.UTF_8), "model.gltf");
        assertNotNull(result);
        assertNotNull(result.model());
    }

    @Test
    void externalGltfBuffer_isRejected() {
        String json = """
                {"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[]}],"nodes":[],
                 "meshes":[],"buffers":[{"uri":"buffer.bin","byteLength":4}]}
                """;
        IOException failure = assertThrows(IOException.class, () -> MemoryModelParser.parseGltf(
                json.getBytes(StandardCharsets.UTF_8), "model.gltf"));
        assertTrue(failure.getMessage().contains("base directory"), failure.getMessage());
    }

    @Test
    void gltfThroughLegacy_throws() {
        assertThrows(IllegalArgumentException.class, () -> MemoryModelParser.parseLegacy(
                "{}".getBytes(StandardCharsets.UTF_8), "model.gltf"));
    }

    @Test
    void malformedZip_fails() {
        assertThrows(IOException.class, () -> MemoryModelParser.parseLegacy(
                "PK-not-a-real-zip".getBytes(StandardCharsets.UTF_8), "broken.zip"));
    }
}
