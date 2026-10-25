package com.micaftic.morpher.client;

import com.micaftic.morpher.core.model.ModelSourceType;
import com.micaftic.morpher.core.model.catalog.LocalModelCatalog;
import com.micaftic.morpher.core.model.catalog.LocalModelLibrary;
import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Shared publication seam: provenance metadata, lazy source registration and pending assembly. */
public final class CloudMemoryPublication {
    private CloudMemoryPublication() {}

    public enum Origin { CLOUD, RESOURCE_PREVIEW }

    public static <T> void publish(String id, T assembly, byte[] bytes, String fileName, String sha,
            LocalModelLibrary library, BiConsumer<String, LocalModelCatalog.Entry> register,
            Consumer<T> pending, Consumer<IOException> provenanceFailure) {
        publish(id, assembly, bytes, fileName, sha, library, register, pending, provenanceFailure, Origin.CLOUD);
    }

    public static <T> void publish(String id, T assembly, byte[] bytes, String fileName, String sha,
            LocalModelLibrary library, BiConsumer<String, LocalModelCatalog.Entry> register,
            Consumer<T> pending, Consumer<IOException> provenanceFailure, Origin origin) {
        if (origin == Origin.RESOURCE_PREVIEW) {
            // The screen owns the decoded assembly and releases it on leaving the viewport.
            // Public repository cards must never change persistent source provenance.
            pending.accept(assembly);
            return;
        }
        try { library.importedMemory(id, sha); }
        catch (IOException failure) { provenanceFailure.accept(failure); }
        LocalModelCatalog.Entry source = LocalModelCatalog.Entry.memory(bytes, sha, fileName, false,
                LocalModelCatalog.fingerprint(bytes), null, null, ModelSourceType.CLOUD);
        register.accept(id, source);
        pending.accept(assembly);
    }
}
