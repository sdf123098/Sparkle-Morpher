package com.micaftic.morpher.client;

import com.micaftic.morpher.core.model.CloudAssetIdentity;
import com.micaftic.morpher.core.model.ModelSourceType;
import com.micaftic.morpher.core.model.catalog.LocalModelCatalog;
import com.micaftic.morpher.core.model.catalog.LocalModelLibrary;
import com.micaftic.morpher.util.DigestUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudMemoryModelLifetimeTest {
    private static final String CLOUD_ID = new CloudAssetIdentity("official", "tenant", "asset", "1",
            "a".repeat(64)).runtimeModelId();

    @TempDir
    Path temporary;

    @Test
    void memoryEntriesKeepTheirSourceBytesWithoutAPath() {
        byte[] bytes = "cloud-model".getBytes(StandardCharsets.UTF_8);
        String sha = DigestUtil.sha256Hex(bytes);
        LocalModelCatalog.Entry entry = LocalModelCatalog.Entry.memory(bytes, sha, "model.ysm", false,
                LocalModelCatalog.fingerprint(bytes), null, null, ModelSourceType.CLOUD);

        assertTrue(entry.isMemory());
        assertNull(entry.path);
        assertSame(bytes, entry.memoryBytes);
        assertEquals(sha, entry.contentSha256);
        assertEquals("model.ysm", entry.memoryFormat);
        assertEquals(ModelSourceType.CLOUD, entry.sourceType);
        assertEquals(LocalModelCatalog.fingerprint(sha), entry.fingerprint);
    }

    @Test
    void localRescansNeverMarkCloudMemoryEntriesStale() throws IOException {
        Path local = Files.writeString(temporary.resolve("local.bbmodel"), "{}");
        LocalModelCatalog.Entry localEntry = new LocalModelCatalog.Entry(local, null, false, false,
                LocalModelCatalog.fingerprint(local), null, null);
        byte[] bytes = "cloud-model".getBytes(StandardCharsets.UTF_8);
        String sha = DigestUtil.sha256Hex(bytes);
        LocalModelCatalog.Entry memory = LocalModelCatalog.Entry.memory(bytes, sha, "model.ysm", false,
                LocalModelCatalog.fingerprint(bytes), null, null, ModelSourceType.CLOUD);
        Map<String, LocalModelCatalog.Entry> previous = new HashMap<>();
        previous.put(CLOUD_ID, memory);
        previous.put("local", localEntry);

        LocalModelCatalog.Diff diff = LocalModelCatalog.diff(previous, Map.of("local", localEntry));

        assertFalse(diff.isStale(CLOUD_ID));
        assertFalse(diff.isStale("local"));
    }

    @Test
    void memorySourceIsRestrictedAndSurvivesLocalCatalogRefresh() throws Exception {
        byte[] bytes = "cloud-model".getBytes(StandardCharsets.UTF_8);
        String sha = DigestUtil.sha256Hex(bytes);
        Path origins = temporary.resolve("origins.sha256");
        LocalModelLibrary library = new LocalModelLibrary(origins);

        library.importedMemory(CLOUD_ID, sha);
        LocalModelLibrary.Source source = library.source(CLOUD_ID);

        assertNotNull(source);
        assertTrue(source.isMemory());
        assertEquals(ModelSourceType.CLOUD, source.type());
        assertTrue(source.restricted());
        assertEquals(sha, source.contentSha256());
        assertFalse(library.canShare(source, temporary, true));
        assertTrue(Files.readString(origins).contains(sha));

        library.refreshLocal(Map.of());
        assertNotNull(library.source(CLOUD_ID));
    }

    @Test
    void copyingCloudBytesIntoTheCustomFolderStaysUnshareableAfterRestart() throws Exception {
        byte[] bytes = "cloud-model".getBytes(StandardCharsets.UTF_8);
        String sha = DigestUtil.sha256Hex(bytes);
        Path origins = temporary.resolve("origins.sha256");
        new LocalModelLibrary(origins).importedMemory(CLOUD_ID, sha);
        Path custom = Files.createDirectory(temporary.resolve("custom"));
        Path copy = Files.write(custom.resolve("renamed.ysm"), bytes);

        LocalModelLibrary restarted = new LocalModelLibrary(origins);
        restarted.imported("renamed", copy, ModelSourceType.LOCAL);

        assertFalse(restarted.canShare(restarted.source("renamed"), custom, true));
    }

    @Test
    void invalidDigestsCannotRegisterAMemorySource() {
        LocalModelLibrary library = new LocalModelLibrary(temporary.resolve("origins.sha256"));
        assertThrows(IOException.class, () -> library.importedMemory(CLOUD_ID, "not-a-digest"));
        assertNull(library.source(CLOUD_ID));
    }

    @Test
    void cloudMemoryModelStaysProtectedUntilEveryDisplayReleases() {
        ClientModelResidency residency = new ClientModelResidency();
        RuntimeModelLease player = residency.acquire(CLOUD_ID);
        RuntimeModelLease preview = residency.acquire(CLOUD_ID);
        AtomicInteger retired = new AtomicInteger();

        assertTrue(residency.deferWhileConsumed(CLOUD_ID, retired::incrementAndGet));
        assertTrue(residency.consumedModelIds().contains(CLOUD_ID));

        player.close();
        assertEquals(1, residency.consumerCount(CLOUD_ID));
        assertTrue(residency.consumedModelIds().contains(CLOUD_ID));
        assertEquals(0, retired.get());

        preview.close();
        assertEquals(0, residency.consumerCount(CLOUD_ID));
        assertFalse(residency.consumedModelIds().contains(CLOUD_ID));
        assertEquals(1, retired.get());
    }
}
