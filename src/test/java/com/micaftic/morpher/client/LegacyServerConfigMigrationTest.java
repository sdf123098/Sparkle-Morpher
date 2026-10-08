package com.micaftic.morpher.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LegacyServerConfigMigrationTest {
    @TempDir Path config;

    @Test
    void readsOnlyLocalPreferencesAndLeavesLegacyTomlUntouched() throws IOException {
        Path legacy = config.resolve("sparkle_morpher-server.toml");
        String toml = "DefaultModelId = \"legacy:model\"\n"
                + "DefaultModelTexture = \"legacy:texture\"\n"
                + "ClientNotDisplayModels = [\"hidden:a\", \"hidden:b\"]\n"
                + "ALLOW_MODEL_UPLOAD = false\nCAN_SWITCH_MODEL = false\nTHREAD_COUNT = 900\n"
                + "[server_scheduler]\nAcceptSoundFX = 2\n";
        Files.writeString(legacy, toml);

        var migrated = LegacyServerConfigMigration.read(config);

        assertEquals("legacy:model", migrated.defaults().modelId());
        assertEquals("legacy:texture", migrated.defaults().textureId());
        assertEquals(Set.of("hidden:a", "hidden:b"), migrated.display().hiddenModels());
        assertEquals(2, migrated.display().soundMode());
        assertEquals(toml, Files.readString(legacy));
    }

    @Test
    void missingLegacyFileUsesCurrentStoreDefaults() throws IOException {
        var migrated = LegacyServerConfigMigration.read(config);

        assertEquals("default", migrated.defaults().modelId());
        assertTrue(migrated.display().hiddenModels().isEmpty());
        assertEquals(0, migrated.display().soundMode());
    }

    @Test
    void damagedLegacyFileIsRetainedAndReported() throws IOException {
        Path legacy = config.resolve("sparkle_morpher-server.toml");
        String invalidToml = "DefaultModelId = [\n";
        Files.writeString(legacy, invalidToml);

        IOException failure = assertThrows(IOException.class, () -> LegacyServerConfigMigration.read(config));

        assertTrue(failure.getMessage().contains("retained"));
        assertEquals(invalidToml, Files.readString(legacy));
    }

    @Test
    void skipsLegacyReadOnceBothNewStoresExist() throws IOException {
        Path legacy = config.resolve("sparkle_morpher-server.toml");
        Files.writeString(legacy, "broken = [\n");
        Path localData = config.resolve("local");
        Files.createDirectories(localData);
        Files.createFile(localData.resolve("local-model-defaults.json"));
        Files.createFile(localData.resolve("local-display-preferences.json"));

        assertDoesNotThrow(() -> LegacyServerConfigMigration.readIfNeeded(config, localData));
        assertEquals("broken = [\n", Files.readString(legacy));
    }
}
