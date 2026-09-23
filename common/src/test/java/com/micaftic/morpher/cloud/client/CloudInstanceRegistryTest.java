package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.cloud.CloudInstanceConfig;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudInstanceRegistryTest {
    @Test
    void persistsOnlyInstanceMetadataAndSelection() throws Exception {
        var directory = Files.createTempDirectory("spm-cloud-registry");
        var file = directory.resolve("instances.json");
        var registry = new CloudInstanceRegistry(file);
        registry.addOrReplace(new CloudInstanceRegistry.CloudInstanceProfile(
                CloudInstanceConfig.v1("official", URI.create("https://cloud.example.org")), "Official Cloud"));
        registry.save();

        String json = Files.readString(file);
        assertFalse(json.contains("access_token"));
        assertFalse(json.contains("refresh_token"));

        var restored = new CloudInstanceRegistry(file);
        restored.load();
        assertEquals("official", restored.selected().orElseThrow().instanceId());
        assertEquals("Official Cloud", restored.selected().orElseThrow().name());
    }

    @Test
    void replacingAndRemovingKeepsSelectionConsistent() throws Exception {
        var registry = new CloudInstanceRegistry(Files.createTempDirectory("spm-cloud-registry").resolve("instances.json"));
        registry.addOrReplace(new CloudInstanceRegistry.CloudInstanceProfile(
                CloudInstanceConfig.v1("one", URI.create("https://one.example.org")), "One"));
        registry.addOrReplace(new CloudInstanceRegistry.CloudInstanceProfile(
                CloudInstanceConfig.v1("two", URI.create("https://two.example.org")), "Two"));
        registry.select("two");
        assertTrue(registry.remove("two"));
        assertEquals("one", registry.selected().orElseThrow().instanceId());
    }
}
