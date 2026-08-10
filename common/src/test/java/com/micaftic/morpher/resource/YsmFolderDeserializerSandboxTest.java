package com.micaftic.morpher.resource;

import com.micaftic.morpher.resource.pojo.RawYsmModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R0 characterization：模型资源路径沙箱行为基线（审计文档 S0.1）。
 *
 * 目标行为：模型目录之外的文件（..、绝对路径、盘符等）必须被拒绝，
 * 模型内容不得拥有读取 Minecraft 目录其他文件的能力。
 *
 * 当前实现（YSMFolderDeserializer.resolveResourcePath）在路径逃逸时直接放行：
 *     if (!direct.startsWith(rootPath.normalize()) || Files.exists(direct)) return direct;
 * 因此 traversalOutsideModelRoot_isRejected 当前预期为 FAIL（RED），
 * 修复 S0.1 后应转绿。其余测试记录正常路径基线，必须保持 PASS。
 */
class YsmFolderDeserializerSandboxTest {

    /** 1x1 红色 PNG（合法最小图片，供纹理解析使用）。 */
    private static final byte[] PNG_1X1 = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private static final String YSM_JSON_TEMPLATE =
            "{\"files\":{\"player\":{\"texture\":\"%s\"}}}";

    @TempDir
    Path tempDir;

    private Path writeModel(String texturePath, Path outsideFile, byte[] outsideContent) throws Exception {
        Path modelRoot = tempDir.resolve("model");
        Files.createDirectories(modelRoot);
        Files.write(modelRoot.resolve("ysm.json"),
                String.format(YSM_JSON_TEMPLATE, texturePath).getBytes(StandardCharsets.UTF_8));
        if (outsideFile != null) {
            Files.createDirectories(outsideFile.getParent());
            Files.write(outsideFile, outsideContent);
        }
        return modelRoot;
    }

    /** 基线：模型目录内的正常纹理路径应被解析。 */
    @Test
    void textureInsideModelRoot_isRead() throws Exception {
        Path modelRoot = writeModel("tex.png", null, null);
        Files.write(modelRoot.resolve("tex.png"), PNG_1X1);

        try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(modelRoot)) {
            RawYsmModel model = deserializer.deserialize();
            assertTrue(model.mainEntity.textures.containsKey("tex"),
                    "模型根目录内的正常纹理应被解析进 textures");
        }
    }

    /** 基线：不存在/未声明的资源不应产生纹理条目。 */
    @Test
    void missingTexture_producesNoEntry() throws Exception {
        Path modelRoot = writeModel("no_such.png", null, null);

        try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(modelRoot)) {
            RawYsmModel model = deserializer.deserialize();
            assertTrue(model.mainEntity.textures.isEmpty(),
                    "缺失的纹理不应产生纹理条目");
        }
    }

    /**
     * RED（当前行为）：恶意模型通过 .. 逃逸模型根目录读取外部文件。
     * 外部 secret.png 位于模型目录上级（tempDir/，即 model/..），当前实现会将其读入 textures。
     * 修复 S0.1 后此测试必须转绿（断言拒绝越界读取）。
     */
    @Test
    void traversalOutsideModelRoot_isRejected() throws Exception {
        Path secret = tempDir.resolve("secret.png");
        Path modelRoot = writeModel("../secret.png", secret, PNG_1X1);

        try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(modelRoot)) {
            RawYsmModel model = deserializer.deserialize();
            assertFalse(model.mainEntity.textures.containsKey("secret"),
                    "模型资源不得读取模型根目录之外的文件（S0.1 路径逃逸）");
        }
    }

    /** RED（当前行为）：绝对路径同样不应被允许读取。 */
    @Test
    void absolutePathOutsideModelRoot_isRejected() throws Exception {
        Path secret = tempDir.resolve("secret.png");
        Files.write(secret, PNG_1X1);
        Path modelRoot = tempDir.resolve("model");
        Files.createDirectories(modelRoot);

        // 绝对路径需要序列化成 json string，windows 下路径含反斜杠会被 normalizeResourceKey 转为 '/'
        String absolute = secret.toAbsolutePath().toString().replace('\\', '/');
        Files.write(modelRoot.resolve("ysm.json"),
                String.format(YSM_JSON_TEMPLATE, absolute).getBytes(StandardCharsets.UTF_8));

        try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(modelRoot)) {
            RawYsmModel model = deserializer.deserialize();
            assertFalse(model.mainEntity.textures.containsKey("secret"),
                    "绝对路径不得被用于读取模型根目录之外的文件");
        }
    }
}
