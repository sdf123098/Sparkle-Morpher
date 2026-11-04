package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression contract for avoiding two active blur engines on legacy GUI rendering. */
class ModernUiLegacyBlurContractTest {

    @Test
    void blurStackChecksModernUiBackgroundBlurBeforeCapturingTheScreen() throws IOException {
        String source = RepoSourceLookup.readSource("com/micaftic/morpher/core/gpu/BlurStack.java");

        int flushStart = source.indexOf("void flush(GuiGraphics graphics)");
        int flushEnd = source.indexOf("\n    /**", flushStart);
        assertTrue(flushStart >= 0 && flushEnd > flushStart, "BlurStack.flush must remain present");
        String flush = source.substring(flushStart, flushEnd);
        assertTrue(flush.contains("BlurHandler"),
                "legacy BlurStack must detect ModernUI's optional background blur");
        assertTrue(flush.contains("sBlurEffect"),
                "legacy BlurStack must only skip its blur when ModernUI blur is enabled");
        assertTrue(flush.contains("regions.clear()"),
                "skipping the local blur must still clear queued regions");
    }
}
