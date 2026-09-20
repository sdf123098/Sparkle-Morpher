package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression contract for avoiding two active blur engines on legacy GUI rendering. */
class ModernUiLegacyBlurContractTest {

    @Test
    void blurStackChecksModernUiBackgroundBlurBeforeCapturingTheScreen() throws IOException {
        String source = Files.readString(findSourceFile(
                "common/src/main/java/com/micaftic/morpher/core/gpu/BlurStack.java"), StandardCharsets.UTF_8);

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

    private static Path findSourceFile(String relativePath) {
        Path[] roots = {Path.of("").toAbsolutePath(), codeLocation()};
        for (Path root : roots) {
            for (Path directory = root; directory != null; directory = directory.getParent()) {
                Path candidate = directory.resolve(relativePath);
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("Source file not found: " + relativePath);
    }

    private static Path codeLocation() {
        try {
            URI location = ModernUiLegacyBlurContractTest.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI();
            return Path.of(location).toAbsolutePath();
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Unable to locate test classes", exception);
        }
    }
}
