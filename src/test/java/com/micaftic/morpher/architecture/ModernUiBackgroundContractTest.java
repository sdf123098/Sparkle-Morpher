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
        Path source = Path.of("src/neoforge/java/com/micaftic/morpher/client/gui/ModernPlayerModelScreen.java");
        if (Files.isRegularFile(source)) {
            return Files.readString(source, StandardCharsets.UTF_8);
        }
        throw new IOException("ModernPlayerModelScreen source not found");
    }
}
