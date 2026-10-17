package com.micaftic.morpher.peer;

import com.micaftic.morpher.core.model.*;
import com.micaftic.morpher.core.model.catalog.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PeerLocalLibraryTest {
    @TempDir Path temporary;
    @Test void typedCatalogRescanPreservesPeerAndCloudAndRejectsRenamedRestrictedBytes() throws Exception {
        Path custom = Files.createDirectory(temporary.resolve("custom")), cloud = Files.createDirectory(temporary.resolve("cloud"));
        Path local = Files.writeString(custom.resolve("local.bbmodel"), "{}"), remote = Files.writeString(cloud.resolve("remote.bbmodel"), "{\"cloud\":true}");
        var library = new LocalModelLibrary(temporary.resolve("origins.sha256"));
        var cloudId = new CloudAssetIdentity("official", "tenant", "asset", "1", "a".repeat(64)).runtimeModelId();
        library.imported(cloudId, remote, ModelSourceType.LOCAL); library.imported("local", local, ModelSourceType.LOCAL);
        Path copied = Files.copy(remote, custom.resolve("renamed.bbmodel")); library.imported("renamed", copied, ModelSourceType.LOCAL);
        assertFalse(library.canShare(library.source(cloudId), custom, true)); assertFalse(library.canShare(library.source("renamed"), custom, true));
        assertFalse(library.canShare(library.source("local"), custom, false)); assertTrue(library.canShare(library.source("local"), custom, true));
        var selected = LocalShareSourceResolver.resolve(library, "local", custom, true);
        try(var snapshot = LocalShareSourceResolver.freeze(selected, temporary.resolve("outgoing"), () -> true)) {
            var store = new PeerAssetStore(temporary.resolve("peer")); var asset = store.copy(snapshot, () -> true);
            var identity = new PeerModelIdentity(asset.manifest().contentRoot(), "bbmodel-v1"); library.peer(identity, asset);
            var peer = new LocalModelCatalog.Entry(asset.entrypoint(), null, false, false, 0, null, null, ModelSourceType.PEER);
            assertTrue(LocalModelCatalog.diff(Map.of(identity.runtimeModelId(), peer), Map.of()).staleIds().isEmpty());
            library.refreshLocal(Map.of()); assertNotNull(library.source(cloudId)); assertTrue(library.isPeer(identity.runtimeModelId())); assertNull(library.source("local"));
            library.removePeer(); assertNull(library.source(identity.runtimeModelId())); assertNotNull(library.source(cloudId));
        }
        var restarted = new LocalModelLibrary(temporary.resolve("origins.sha256")); restarted.imported("renamed", copied, ModelSourceType.LOCAL);
        assertFalse(restarted.canShare(restarted.source("renamed"), custom, true));
    }
    @Test void directoryClosureIncludesExplicitGeometryAndExcludesUnrelatedPrivateFiles() throws Exception {
        Path custom = Files.createDirectory(temporary.resolve("custom")), model = Files.createDirectory(custom.resolve("model"));
        Files.writeString(model.resolve("ysm.json"), "{\"files\":{\"player\":{\"model\":\"custom-main.json\"}}}");
        Files.writeString(model.resolve("custom-main.json"), "{}"); Files.writeString(model.resolve("passwords.json"), "{\"secret\":true}");
        var library = new LocalModelLibrary(temporary.resolve("origins")); library.imported("model", model, ModelSourceType.LOCAL);
        var selected = LocalShareSourceResolver.resolve(library, "model", custom, true);
        assertEquals(Set.of("ysm.json", "custom-main.json"), selected.resources().keySet());
        Files.writeString(model.resolve("ysm.json"), "{\"files\":{\"model\":\"../escape.json\"}}");
        assertThrows(IllegalArgumentException.class, () -> LocalShareSourceResolver.resolve(library, "model", custom, true));
    }
    @Test void strictDecodeScopeIsThreadLocalAndLeavesLegacyScalarsUntouched() throws Exception {
        PeerDecodeScope.scalar(Integer.MAX_VALUE);
        try(var scope = new PeerDecodeScope()) {
            assertThrows(IllegalArgumentException.class, () -> PeerDecodeScope.scalar(Integer.MAX_VALUE));
            assertThrows(IllegalArgumentException.class, () -> PeerDecodeScope.expanded(1024L * 1024 * 1024));
            assertFalse(java.util.concurrent.CompletableFuture.supplyAsync(PeerDecodeScope::active).get());
        }
        assertFalse(PeerDecodeScope.active()); PeerDecodeScope.expanded(Long.MAX_VALUE);
        String deep = "[".repeat(65) + "0" + "]".repeat(65);
        assertThrows(IOException.class, () -> PeerModelPreflight.json(new ByteArrayInputStream(deep.getBytes())));
    }
    @Test void unreadableProvenanceBlocksSharingWithoutDiscardingImportedSource() throws Exception {
        Path custom = Files.createDirectory(temporary.resolve("custom")); Path file = Files.writeString(custom.resolve("local.bbmodel"), "{}");
        Path origins = Files.writeString(temporary.resolve("origins"), "invalid-hash");
        var library = new LocalModelLibrary(origins); library.imported("local", file, ModelSourceType.LOCAL);
        assertThrows(IOException.class, () -> library.canShare(library.source("local"), custom, true));
        assertNotNull(library.source("local")); assertFalse(library.canShare(library.source("local"), custom, true));
    }
}
