package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression contract for classic HUD animation input direction. */
class ClassicHudInputDirectionContractTest {

    @Test
    void classicHudPreviewDoesNotReplaceEntityViewYawBeforeAnimationEvaluation() throws IOException {
        Path source = Path.of("common/src/main/java/com/micaftic/morpher/client/renderer/ModelPreviewRenderer.java");
        if (!Files.isRegularFile(source)) {
            source = Path.of("../common/src/main/java/com/micaftic/morpher/client/renderer/ModelPreviewRenderer.java");
        }
        if (!Files.isRegularFile(source)) {
            source = Path.of("src/main/java/com/micaftic/morpher/client/renderer/ModelPreviewRenderer.java");
        }
        if (!Files.isRegularFile(source)) {
            source = Path.of("../src/main/java/com/micaftic/morpher/client/renderer/ModelPreviewRenderer.java");
        }

        String text = Files.readString(source, StandardCharsets.UTF_8);
        int methodStart = text.indexOf("private static void renderLivingGuiPreview(");
        int methodEnd = text.indexOf("private static void renderFreeGuiPreview(", methodStart);
        assertTrue(methodStart >= 0 && methodEnd > methodStart,
                "classic HUD preview method must remain present");

        String method = text.substring(methodStart, methodEnd);
        assertTrue(method.contains("livingEntity.yBodyRot = displayYaw"),
                "classic HUD must keep its presentation body rotation");
        assertTrue(method.contains("renderer.renderEntity(animatable"),
                "classic HUD must render the animated entity");
        assertFalse(method.contains("livingEntity.setYRot(displayYaw)"),
                "classic HUD must not replace the entity view yaw used by input_vertical");
        assertFalse(method.contains("livingEntity.yRotO = displayYaw"),
                "classic HUD must not replace the previous entity view yaw used by input_vertical");
    }
}
