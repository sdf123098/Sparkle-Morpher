package com.micaftic.morpher.core.storage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class IndexedBuiltinResourcesTest {
    @TempDir Path directory;
    private static IndexedBuiltinResources.Source resources(Map<String, String> values) {
        return path -> values.containsKey(path) ? new ByteArrayInputStream(values.get(path).getBytes(StandardCharsets.UTF_8)) : null;
    }
    @Test void readsPackagedIndexAtomicallyAndPreservesOtherDirectories() throws Exception {
        Files.writeString(directory.resolve("custom-model.ysm"), "user file");
        var source = resources(Map.of("/index", "# comment\ndefault/ysm.json\n", "/root/default/ysm.json", "model"));
        Path built = directory.resolve("builtin");
        assertEquals(1, IndexedBuiltinResources.extract(built, "/root/", "/index", source));
        assertEquals("model", Files.readString(built.resolve("default/ysm.json")));
        assertEquals("user file", Files.readString(directory.resolve("custom-model.ysm")));
        assertEquals(1, IndexedBuiltinResources.extract(built, "/root/", "/index", source));
    }
    @Test void missingResourcesAndEscapingPathsFailWithoutTouchingTheEscapedTarget() throws Exception {
        for (String path : new String[]{"../outside", "D:/outside", "/absolute", "a\\outside"})
            assertThrows(IOException.class, () -> IndexedBuiltinResources.extract(directory.resolve("builtin"), "/root/", "/index", resources(Map.of("/index", path))));
        assertFalse(Files.exists(directory.resolve("outside")));
        assertThrows(IOException.class, () -> IndexedBuiltinResources.extract(directory.resolve("builtin"), "/root/", "/index", resources(Map.of("/index", "missing"))));
    }
}
