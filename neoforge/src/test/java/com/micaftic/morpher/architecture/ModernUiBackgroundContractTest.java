package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression contract for the 26.x GuiRenderState one-blur-per-frame rule. */
class ModernUiBackgroundContractTest {

    @Test
    void modelScreenDoesNotSubmitTransparentBackgroundFromRenderState() throws IOException {
        String source = RepoSourceLookup.readSource("com/micaftic/morpher/client/gui/ModernPlayerModelScreen.java");
        int methodStart = source.indexOf("void extractRenderState(");
        int methodEnd = source.indexOf("\n    private ", methodStart);

        assertTrue(methodStart >= 0 && methodEnd > methodStart,
                "ModernPlayerModelScreen.extractRenderState must remain present");
        assertFalse(source.substring(methodStart, methodEnd).contains("extractTransparentBackground("),
                "the inherited screen background stage already submits this blur-sensitive operation");
    }
}
