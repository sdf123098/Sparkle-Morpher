package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression contract for the 26.1.2 Fabric modern HUD layout editor. */
class ModernHudLayoutContractTest {

    @Test
    void modernLayoutEditorUsesTheModernHudRenderer() throws IOException {
        Path source = Path.of("common/src/main/java/com/micaftic/morpher/client/gui/HudLayoutScreen.java");
        if (!Files.isRegularFile(source)) {
            source = Path.of("../common/src/main/java/com/micaftic/morpher/client/gui/HudLayoutScreen.java");
        }
        if (!Files.isRegularFile(source)) {
            source = Path.of("src/main/java/com/micaftic/morpher/client/gui/HudLayoutScreen.java");
        }
        if (!Files.isRegularFile(source)) {
            source = Path.of("../src/main/java/com/micaftic/morpher/client/gui/HudLayoutScreen.java");
        }
        String text = Files.readString(source, StandardCharsets.UTF_8);
        int methodStart = text.indexOf("public static HudPreview modernPreview()");
        int methodEnd = text.indexOf("\n    }", methodStart);

        assertTrue(methodStart >= 0 && methodEnd > methodStart, "modernPreview() must remain present");
        String method = text.substring(methodStart, methodEnd);
        assertTrue(method.contains("ModernHudRenderer::renderAt"),
                "modern HUD editor must use the same FBO renderer as the in-game HUD");
        assertFalse(method.contains("return classicPreview()"),
                "modern HUD editor must not fall back to the classic preview scale");
    }
}
