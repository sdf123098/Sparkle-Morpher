package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1.2.6 §22.3/§23 Paper Doll 隔离契约（源码扫描，六分支通用）。
 *
 * <p>HUD 小人（Paper Doll）与 GUI 预览是纯渲染路径：不得产生 gameplay / 网络副作用，
 * 且对 live 实体朝向/姿态的改动必须在 {@code finally} 中恢复（否则会污染世界渲染）。
 * 本测试把这两条约束固化为回归门禁。</p>
 */
class PaperDollIsolationTest {

    @Test
    void paperDollPathHasNoGameplayOrNetworkSideEffects() throws IOException {
        String source = readPaperDollOrGuiSource();

        // 只读渲染：不得发包 / 上传 / 直接改 gameplay 状态。
        for (String forbidden : new String[]{
                "sendToServer", "sendPacket", "ClientPlayNetworking", "ServerPlayNetworking",
                "PacketDistributor", "ModelUploadSession", "startUpload", "submitUpload",
                ".setHealth(", ".setPos(", "dropItem", "addItem", "setItemInHand",
                "broadcastEntityEvent", "setDeltaMovement", "remove(", "discard(",
        }) {
            assertFalse(source.contains(forbidden),
                    "Paper Doll path must not perform gameplay/network side effect: " + forbidden);
        }
    }

    @Test
    void previewEntityPoseMutationsAreRestored() throws IOException {
        String source = readGuiRendererSource();

        // 被改写的实体状态必须逐项保存与恢复（否则泄漏到世界渲染）。
        // 迁移前 renderLivingGuiPreview/renderFreeGuiPreview 即遵循此模式。
        assertTrue(source.contains("float oldBodyRot = livingEntity.yBodyRot"),
                "preview must snapshot the entity body rotation before mutating it");
        assertTrue(source.contains("livingEntity.yBodyRot = oldBodyRot"),
                "preview must restore the entity body rotation");
        assertTrue(source.contains("livingEntity.yHeadRot = oldHeadRot"),
                "preview must restore the entity head rotation");
        assertTrue(source.contains("livingEntity.setXRot(oldXRot)"),
                "preview must restore the entity pitch");
        assertTrue(source.contains("livingEntity.setPose(oldPose)"),
                "free preview must restore the entity pose");
        // 恢复必须发生在 finally 块中。
        assertTrue(source.contains("finally"),
                "preview state restoration must run in a finally block");
    }

    private static String readPaperDollOrGuiSource() throws IOException {
        Path source = firstExisting(
                Path.of("common/src/main/java/com/micaftic/morpher/client/renderer/preview/PaperDollRenderer.java"),
                Path.of("../common/src/main/java/com/micaftic/morpher/client/renderer/preview/PaperDollRenderer.java"),
                Path.of("src/main/java/com/micaftic/morpher/client/renderer/preview/PaperDollRenderer.java"));
        return Files.readString(source, StandardCharsets.UTF_8);
    }

    private static String readGuiRendererSource() throws IOException {
        Path source = firstExisting(
                Path.of("common/src/main/java/com/micaftic/morpher/client/renderer/preview/GuiModelRenderer.java"),
                Path.of("../common/src/main/java/com/micaftic/morpher/client/renderer/preview/GuiModelRenderer.java"),
                Path.of("src/main/java/com/micaftic/morpher/client/renderer/preview/GuiModelRenderer.java"),
                // 未收到拆分的分支：回退到旧的单一渲染器
                Path.of("common/src/main/java/com/micaftic/morpher/client/renderer/ModelPreviewRenderer.java"),
                Path.of("../common/src/main/java/com/micaftic/morpher/client/renderer/ModelPreviewRenderer.java"),
                Path.of("src/main/java/com/micaftic/morpher/client/renderer/ModelPreviewRenderer.java"));
        return Files.readString(source, StandardCharsets.UTF_8);
    }

    private static Path firstExisting(Path... candidates) throws IOException {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            for (Path candidate : candidates) {
                Path resolved = directory.resolve(candidate);
                if (Files.isRegularFile(resolved)) {
                    return resolved;
                }
            }
            directory = directory.getParent();
        }
        throw new IOException("Paper Doll / preview source not found");
    }
}
