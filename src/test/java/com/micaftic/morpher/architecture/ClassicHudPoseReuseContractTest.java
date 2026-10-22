package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 经典 HUD 动画复用契约：世界姿态优先复用，HUD 自评估按采样帧率节流，头部追踪每帧应用。
 */
class ClassicHudPoseReuseContractTest {

    @Test
    void hudReusesWorldPoseAndKeepsHeadTrackingSeparate() throws IOException {
        String animatable = read("common/src/main/java/com/micaftic/morpher/geckolib3/core/AnimatableEntity.java");
        assertTrue(animatable.contains("HudAnimationGate.decide(this, event)"),
                "HUD animation decisions must go through HudAnimationGate");
        assertTrue(animatable.contains("HudPoseCache.applyTo(this)"),
                "HUD must reuse the world pose matrices when the cache matches");
        assertTrue(animatable.contains("applyHudHeadTracking(event, HudPoseCache.headBaseX(), HudPoseCache.headBaseY())"),
                "reused poses must re-apply HUD head tracking from the cached head base");

        String living = read("common/src/main/java/com/micaftic/morpher/client/entity/LivingAnimatable.java");
        assertTrue(living.contains("public void applyHudHeadTracking"),
                "LivingAnimatable must provide HUD-specific head tracking");

        String playerRenderer = read("common/src/main/java/com/micaftic/morpher/client/renderer/CustomPlayerRenderer.java");
        assertTrue(playerRenderer.contains("HudPoseCache.publish(capability, partialTick)"),
                "world player render must publish the completed pose for the HUD");
    }

    @Test
    void samplingGateIsHudScopedAndDefaultsToSixtyFps() throws IOException {
        String gate = read("common/src/main/java/com/micaftic/morpher/client/renderer/hud/HudAnimationGate.java");
        assertTrue(gate.contains("!RenderContext.isOldHud()"),
                "sampling/reuse must only apply inside the classic HUD scope");
        assertTrue(gate.contains("Decision.HOLD_SAMPLED_POSE"),
                "unsampled HUD frames must hold the previous pose");
        assertTrue(gate.contains("System.getProperty(\"sparklemorpher.hud.reuseWorldPose\", \"true\")"),
                "world pose reuse must keep a system-property rollback switch");

        String config = readConfig();
        assertTrue(config.contains("defineInRange(\"ClassicHudAnimationSampleFps\", 60, 0, 480)"),
                "ClassicHudAnimationSampleFps must default to 60 and allow 0..480");
    }

    @Test
    void samplingOptionIsExposedInUiAndAllLocales() throws IOException {
        Path screen = firstExisting(
                "fabric/src/main/java/com/micaftic/morpher/client/gui/ModernPlayerModelScreen.java",
                "src/neoforge/java/com/micaftic/morpher/client/gui/ModernPlayerModelScreen.java",
                "common/src/main/java/com/micaftic/morpher/client/gui/ModernPlayerModelScreen.java");
        String screenSource = Files.readString(screen, StandardCharsets.UTF_8);
        assertTrue(screenSource.contains("gui.sparkle_morpher.model_panel.setting.classic_hud_animation_fps"),
                "the settings UI must expose the classic HUD sampling option");
        assertTrue(screenSource.contains("CLASSIC_HUD_FPS_PRESETS = { 0, 30, 60, 90, 120, 144, 165, 240, 480 }"),
                "the sampling option must cycle through the documented presets");
        assertTrue(screenSource.contains("gui.sparkle_morpher.settings.cloud.performance.unlimited"),
                "0 must be displayed as unlimited");

        String[] locales = { "en_us", "es_es", "fr_fr", "id_id", "ja_jp", "ko_kr", "lzh",
                "pt_br", "ru_ru", "tr_tr", "uk_ua", "vi_vn", "zh_cn", "zh_hk", "zh_tw" };
        for (String locale : locales) {
            Path lang = firstExisting(
                    "common/src/main/resources/assets/sparkle_morpher/lang/" + locale + ".json",
                    "src/main/resources/assets/sparkle_morpher/lang/" + locale + ".json");
            String json = Files.readString(lang, StandardCharsets.UTF_8);
            assertTrue(json.contains("gui.sparkle_morpher.model_panel.setting.classic_hud_animation_fps"),
                    locale + " must translate the classic HUD sampling option");
            assertFalse(json.contains("modern_hud"),
                    locale + " must not keep translations for the removed modern HUD");
        }
    }

    @Test
    void modernHudIsGone() throws IOException {
        String animatable = read("common/src/main/java/com/micaftic/morpher/geckolib3/core/AnimatableEntity.java");
        assertFalse(animatable.contains("modernhud"), "AnimatableEntity must not reference the removed modern HUD");

        String overlay = read("common/src/main/java/com/micaftic/morpher/client/renderer/ExtraPlayerOverlay.java");
        assertFalse(overlay.contains("ModernHudRenderer"), "ExtraPlayerOverlay must not reference the removed modern HUD");

        assertFalse(Files.exists(repoRoot().resolve("common/src/main/java/com/micaftic/morpher/client/renderer/modernhud")),
                "modern HUD package must be removed from the common tree");
    }

    private static String read(String relative) throws IOException {
        Path source = firstExisting(relative, relative.replace("common/src/", "src/"));
        return Files.readString(source, StandardCharsets.UTF_8);
    }

    private static String readConfig() throws IOException {
        Path source = firstExisting(
                "fabric/src/main/java/com/micaftic/morpher/config/ExtraPlayerRenderConfig.java",
                "src/neoforge/java/com/micaftic/morpher/config/ExtraPlayerRenderConfig.java",
                "common/src/main/java/com/micaftic/morpher/config/ExtraPlayerRenderConfig.java");
        return Files.readString(source, StandardCharsets.UTF_8);
    }

    private static Path firstExisting(String... candidates) throws IOException {
        Path root = repoRoot();
        for (String candidate : candidates) {
            Path resolved = root.resolve(candidate);
            if (Files.exists(resolved)) {
                return resolved;
            }
        }
        throw new IOException("source not found among candidates under " + root);
    }

    private static Path repoRoot() {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            if (Files.isDirectory(directory.resolve(".git"))) {
                return directory;
            }
            directory = directory.getParent();
        }
        return Path.of("").toAbsolutePath();
    }
}
