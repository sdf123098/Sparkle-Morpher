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
    void missingRegistrySeedsOfficialCloud() throws Exception {
        var registry = new CloudInstanceRegistry(Files.createTempDirectory("spm-cloud-registry").resolve("instances.json"));

        registry.load();

        var official = registry.selected().orElseThrow();
        assertEquals("official", official.instanceId());
        assertEquals("Official Cloud", official.name());
        assertEquals("https://micafic.xyz", official.instance().origin().toString());
        assertTrue(CloudInstanceRegistry.isBuiltinOfficial(official));
        assertFalse(CloudInstanceRegistry.isBuiltinOfficial(new CloudInstanceRegistry.CloudInstanceProfile(
                CloudInstanceConfig.v1("official", URI.create("https://cloud.example.org")), "Official Cloud")));
    }

    @Test
    void migratesSavedOfficialOriginAndPersistsItWithoutChangingCommunityClouds() throws Exception {
        var file = Files.createTempDirectory("spm-cloud-migration").resolve("instances.json");
        Files.writeString(file, """
                {"version":1,"selected_instance_id":"official","instances":[
                  {"instance_id":"official","origin":"https://spm-cloud-official.robinson171.workers.dev","name":"My official account"},
                  {"instance_id":"community","origin":"https://community.example.org","name":"Community"}]}
                """);
        var registry = new CloudInstanceRegistry(file);
        registry.load();
        assertEquals("https://micafic.xyz", registry.selected().orElseThrow().instance().origin().toString());
        assertEquals("My official account", registry.selected().orElseThrow().name());
        assertTrue(CloudInstanceRegistry.isBuiltinOfficial(registry.selected().orElseThrow()));
        assertEquals("https://community.example.org", registry.find("community").orElseThrow().instance().origin().toString());
        assertFalse(Files.readString(file).contains("workers.dev"));
        var restored = new CloudInstanceRegistry(file);
        restored.load();
        assertEquals(registry.selected(), restored.selected());
        assertEquals(registry.find("community"), restored.find("community"));
        assertEquals(registry.profiles().size(), restored.profiles().size());
    }

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
