package com.micaftic.morpher.resource;

import com.micaftic.morpher.resource.pojo.RawYsmModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R0.2 corpus：YSM folder 模型解析（合成最小样本，无第三方版权资产）。
 *
 * 样本结构：ysm.json 声明 player 的 main geometry + texture + 一个 idle 动画；
 * geometry 用最小 bedrock-style 格式（空 bones），动画用最小 controller。
 * 该样本用于锁定 folder 模型解析行为（R4 迁移到 ModelResourceContainer 后必须保持）。
 */
class YsmFolderCorpusTest {

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

    @TempDir
    Path tempDir;

    private Path writeMinimalFolder() throws Exception {
        Path root = tempDir.resolve("model");
        Files.createDirectories(root);
        Files.writeString(root.resolve("ysm.json"), YSM_JSON, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("model.geo.json"), MINIMAL_GEO_JSON, StandardCharsets.UTF_8);
        Files.write(root.resolve("tex.png"), PNG_1X1);
        return root;
    }

    @Test
    void minimalSyntheticFolder_parsesGeometryAndTexture() throws Exception {
        Path root = writeMinimalFolder();

        try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(root)) {
            RawYsmModel model = deserializer.deserialize();
            assertNotNull(model.mainEntity.mainModel, "player 主几何体必须被解析");
            assertNotNull(model.mainEntity.mainModel.identifier, "几何体必须有 identifier");
            assertTrue(model.mainEntity.textures.containsKey("tex"), "模型内纹理必须被解析");
        }
    }

    @Test
    void folderParse_isDeterministic() throws Exception {
        Path root = writeMinimalFolder();

        String hashA;
        String hashB;
        try (YSMFolderDeserializer a = new YSMFolderDeserializer(root)) {
            a.deserialize();
            hashA = a.getFolderHash();
        }
        try (YSMFolderDeserializer b = new YSMFolderDeserializer(root)) {
            b.deserialize();
            hashB = b.getFolderHash();
        }
        assertTrue(hashA.equals(hashB) && !hashA.isEmpty(), "同目录两次解析的 folder hash 必须一致");
    }
}
