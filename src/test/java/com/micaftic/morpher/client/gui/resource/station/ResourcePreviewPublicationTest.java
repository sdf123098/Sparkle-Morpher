package com.micaftic.morpher.client.gui.resource.station;

import com.micaftic.morpher.client.CloudMemoryPublication;
import com.micaftic.morpher.core.model.ModelSourceType;
import com.micaftic.morpher.core.model.catalog.LocalModelLibrary;
import com.micaftic.morpher.util.DigestUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ResourcePreviewPublicationTest {
    @TempDir Path root;
    private final byte[] bytes = {1,2,3,4};

    @Test void previewingAPublicRepositoryModelDoesNotBlockItsLaterLocalSharing() throws Exception {
        var library = new LocalModelLibrary(root.resolve("origins.sha256"));
        var pending = new ArrayList<Object>();
        Object assembly = new Object();
        CloudMemoryPublication.publish("cloud_"+"a".repeat(64), assembly, bytes, "model.zip", DigestUtil.sha256Hex(bytes),
                library, (id, source) -> fail("A card must not register a lazy Cloud source"), pending::add, error -> { throw new AssertionError(error); }, CloudMemoryPublication.Origin.RESOURCE_PREVIEW);
        assertEquals(java.util.List.of(assembly), pending);
        assertFalse(Files.exists(root.resolve("origins.sha256")), "Browsing a public repository must not write a restricted Cloud origin");
        assertTrue(library.snapshot().isEmpty(), "A temporary card must not join the persisted model library");
        Path model = Files.write(root.resolve("downloaded.zip"), bytes);
        library.imported("downloaded", model, ModelSourceType.LOCAL);
        assertTrue(library.canShare(library.source("downloaded"), root, true));
    }

    @Test void cloudDisplayStillBlocksSharingOfTheSameBytes() throws Exception {
        var library = new LocalModelLibrary(root.resolve("origins.sha256"));
        CloudMemoryPublication.publish("cloud_"+"b".repeat(64), new Object(), bytes, "model.zip", DigestUtil.sha256Hex(bytes),
                library, (id, source) -> {}, assembly -> {}, error -> { throw new AssertionError(error); });
        Path copied = Files.write(root.resolve("copied.zip"), bytes);
        library.imported("copied", copied, ModelSourceType.LOCAL);
        assertFalse(library.canShare(library.source("copied"), root, true));
        assertTrue(Files.exists(root.resolve("origins.sha256")));
    }
}
