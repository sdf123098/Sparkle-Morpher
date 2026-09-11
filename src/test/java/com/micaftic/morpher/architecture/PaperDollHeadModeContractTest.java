package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1.2.6 §22.2 经典 HUD 小人 head mode 契约（源码扫描，六分支通用）。
 *
 * <p>默认 {@code STRAIGHT} 必须与历史行为完全等价：头部偏移为 0，小人姿态不随玩家视线变化。
 * 只有显式 {@code FOLLOW} 才注入玩家头部偏移。本测试防止「默认值被改成 FOLLOW」这类
 * 静默行为变更。</p>
 */
class PaperDollHeadModeContractTest {

    @Test
    void defaultHeadModeKeepsStraightPresentation() throws IOException {
        String source = readSource();
        // 默认（STRAIGHT）头部偏移为 0，只在显式 head mode 开启时注入。
        assertTrue(source.contains("float headYawOffset = 0.0f;"),
                "classic HUD doll must default to a zero head yaw offset (STRAIGHT)");
        assertTrue(source.contains("float headYawOffsetO = 0.0f;"),
                "classic HUD doll must default to a zero previous head yaw offset (STRAIGHT)");
        // 偏移只在显式 head mode 开启时注入。
        assertTrue(source.contains("followPlayerHead()"),
                "head offset must be gated behind the head-mode config");
        assertTrue(source.contains("if (followPlayerHead()) {"),
                "head offset must only be applied under the follow mode branch");
        // GUI 预览选项必须保持零偏移、不对齐载具（历史行为）。
        String gui = readGuiSource();
        assertTrue(gui.contains("new DollOptions(0.0f, 0.0f, false)"),
                "GUI preview DollOptions must keep zero head offset and no vehicle alignment");
        // 默认配置值为 STRAIGHT（等价历史行为）。
        String config = readConfigSource();
        assertTrue(config.contains("defineEnum(\"ClassicHudHeadMode\", HeadMode.STRAIGHT)"),
                "ClassicHudHeadMode default must remain STRAIGHT");
        assertFalse(config.contains("defineEnum(\"ClassicHudHeadMode\", HeadMode.FOLLOW)"),
                "ClassicHudHeadMode default must not be FOLLOW");
    }

    @Test
    void newDollFeaturesDefaultToLegacyBehaviour() throws IOException {
        String config = readConfigSource();
        // 所有新增功能项默认值必须等价历史行为（不改变渲染）。
        assertTrue(config.contains("defineEnum(\"ClassicHudAnchor\", PaperDollLayout.Anchor.TOP_LEFT)"),
                "anchor default must be TOP_LEFT (legacy absolute position)");
        assertTrue(config.contains("define(\"ClassicHudHideInThirdPerson\", false)"),
                "F5 hide default must be false");
        assertTrue(config.contains("defineInRange(\"ClassicHudAutoHideIdleSeconds\", 0, 0, 3600)"),
                "auto-hide default must be 0 (disabled)");
        assertTrue(config.contains("define(\"ClassicHudShowOnActionOnly\", false)"),
                "action-only default must be false");
        assertTrue(config.contains("define(\"ClassicHudAlignWithVehicle\", false)"),
                "vehicle alignment default must be false");
    }

    private static String readGuiSource() throws IOException {
        Path source = firstExisting(
                Path.of("common/src/main/java/com/micaftic/morpher/client/renderer/preview/GuiModelRenderer.java"),
                Path.of("../common/src/main/java/com/micaftic/morpher/client/renderer/preview/GuiModelRenderer.java"),
                Path.of("src/main/java/com/micaftic/morpher/client/renderer/preview/GuiModelRenderer.java"));
        return Files.readString(source, StandardCharsets.UTF_8);
    }

    private static String readSource() throws IOException {
        Path source = firstExisting(
                Path.of("common/src/main/java/com/micaftic/morpher/client/renderer/preview/PaperDollRenderer.java"),
                Path.of("../common/src/main/java/com/micaftic/morpher/client/renderer/preview/PaperDollRenderer.java"),
                Path.of("src/main/java/com/micaftic/morpher/client/renderer/preview/PaperDollRenderer.java"));
        return Files.readString(source, StandardCharsets.UTF_8);
    }

    private static String readConfigSource() throws IOException {
        // Config lives in the loader source set: fabric/... (Fabric) or src/neoforge/java/... (NeoForge).
        Path source = firstExisting(
                Path.of("fabric/src/main/java/com/micaftic/morpher/config/ExtraPlayerRenderConfig.java"),
                Path.of("src/neoforge/java/com/micaftic/morpher/config/ExtraPlayerRenderConfig.java"),
                Path.of("src/main/java/com/micaftic/morpher/config/ExtraPlayerRenderConfig.java"),
                Path.of("../fabric/src/main/java/com/micaftic/morpher/config/ExtraPlayerRenderConfig.java"),
                Path.of("../src/neoforge/java/com/micaftic/morpher/config/ExtraPlayerRenderConfig.java"));
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
        throw new IOException("Paper Doll head-mode source not found");
    }
}
