package com.micaftic.morpher.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalModelSelectionStoreTest {
    @TempDir Path directory;

    @Test
    void readsAndWritesExistingLocalAndCloudRuntimeIdsWithoutChangingJsonFields() throws IOException {
        Path file = directory.resolve("selection.json");
        LocalModelSelectionStore.save(file, "cloud:instance:asset:revision:hash", "texture:cloud");

        var selection = LocalModelSelectionStore.load(file);

        assertEquals("cloud:instance:asset:revision:hash", selection.getLeft());
        assertEquals("texture:cloud", selection.getRight());
        assertTrue(Files.readString(file).contains("\"model_id\""));
        assertTrue(Files.readString(file).contains("\"texture_id\""));
    }

    @Test
    void readsLegacySelectionWithMissingTextureAsDefault() throws IOException {
        Path file = directory.resolve("selection.json");
        Files.writeString(file, "{\"model_id\":\"local:old\"}");

        var selection = LocalModelSelectionStore.load(file);

        assertEquals("local:old", selection.getLeft());
        assertEquals("default", selection.getRight());
    }

    @Test
    void damagedAndDuplicateJsonRemainUntouchedAndCannotBeOverwritten() throws IOException {
        for (String json : new String[]{"{broken", "{\"model_id\":\"a\",\"model_id\":\"b\"}"}) {
            Path file = directory.resolve("selection-" + Math.abs(json.hashCode()) + ".json");
            Files.writeString(file, json);

            assertThrows(IOException.class, () -> LocalModelSelectionStore.load(file));
            assertThrows(IOException.class, () -> LocalModelSelectionStore.save(file, "local:new", "default"));
            assertEquals(json, Files.readString(file));
        }
    }

    @Test
    void malformedUtf8AndOversizedInputArePreserved() throws IOException {
        Path invalidUtf8 = directory.resolve("invalid-utf8.json");
        Files.write(invalidUtf8, new byte[]{(byte) 0xC3, (byte) 0x28});
        byte[] original = Files.readAllBytes(invalidUtf8);
        assertThrows(IOException.class, () -> LocalModelSelectionStore.load(invalidUtf8));
        assertThrows(IOException.class, () -> LocalModelSelectionStore.save(invalidUtf8, "local:new", "default"));
        assertArrayEquals(original, Files.readAllBytes(invalidUtf8));

        Path oversized = directory.resolve("oversized.json");
        String large = "{" + " ".repeat(4 * 1024 * 1024) + "}";
        Files.writeString(oversized, large);
        assertThrows(IOException.class, () -> LocalModelSelectionStore.load(oversized));
        assertThrows(IOException.class, () -> LocalModelSelectionStore.save(oversized, "local:new", "default"));
        assertEquals(large.length(), Files.size(oversized));
    }

    @Test
    void explicitDefaultSelectionClearsSavedChoice() throws IOException {
        Path file = directory.resolve("selection.json");
        LocalModelSelectionStore.save(file, "local:chosen", "default");

        LocalModelSelectionStore.save(file, "default", "default");

        assertFalse(Files.exists(file));
    }

    @Test
    void failedWritePreservesExistingSelection() throws IOException {
        Path blockedParent = directory.resolve("not-a-directory");
        Files.writeString(blockedParent, "block");
        Path file = blockedParent.resolve("selection.json");

        assertThrows(IOException.class, () -> LocalModelSelectionStore.save(file, "local:chosen", "default"));
        assertEquals("block", Files.readString(blockedParent));
    }
}
