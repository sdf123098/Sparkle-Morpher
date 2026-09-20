package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression contract for the 26.x GuiRenderState one-blur-per-frame rule. */
class ModernUiBackgroundContractTest {

    @Test
    void modelScreenDoesNotSubmitTransparentBackgroundFromRenderState() throws IOException {
        String source = readScreenSource();
        int methodStart = source.indexOf("void extractRenderState(");
        int methodEnd = source.indexOf("\n    private ", methodStart);

        assertTrue(methodStart >= 0 && methodEnd > methodStart,
                "ModernPlayerModelScreen.extractRenderState must remain present");
        assertFalse(source.substring(methodStart, methodEnd).contains("extractTransparentBackground("),
                "the inherited screen background stage already submits this blur-sensitive operation");
    }

    private static String readScreenSource() throws IOException {
        Path[] candidates = {
                Path.of("fabric/src/main/java/com/micaftic/morpher/client/gui/ModernPlayerModelScreen.java"),
                Path.of("src/neoforge/java/com/micaftic/morpher/client/gui/ModernPlayerModelScreen.java"),
                Path.of("../fabric/src/main/java/com/micaftic/morpher/client/gui/ModernPlayerModelScreen.java"),
                Path.of("../src/neoforge/java/com/micaftic/morpher/client/gui/ModernPlayerModelScreen.java")
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate, StandardCharsets.UTF_8);
            }
        }
        throw new IOException("ModernPlayerModelScreen source not found");
    }
}
