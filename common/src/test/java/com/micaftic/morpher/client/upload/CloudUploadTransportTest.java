package com.micaftic.morpher.client.upload;

import com.micaftic.morpher.core.api.network.upload.ModelUploadTransport;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloudUploadTransportTest {

    @Test
    void uploadMetadataRejectsInvalidLengthAndHeaderValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new ModelUploadTransport.UploadMetadata("asset", "model.ysm", "ysm", "hash", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ModelUploadTransport.UploadMetadata("asset\n", "model.ysm", "ysm", "hash", 1));
    }

    @Test
    void legacyTransportCannotPretendToBeCloudUpload() {
        ModelUploadTransport legacy = LegacyServerUploadTransport.INSTANCE;
        var future = legacy.upload(
                new ModelUploadTransport.UploadMetadata("asset", "model.ysm", "ysm", "hash", 1),
                Path.of("missing-model.ysm"),
                (sent, total) -> { },
                () -> false);
        CompletionException failure = assertThrows(CompletionException.class, future::join);
        assertEquals(UnsupportedOperationException.class, failure.getCause().getClass());
    }

    @Test
    void uploadResultKeepsCommittedRevisionIdentity() {
        var result = new ModelUploadTransport.UploadResult("asset", 3, "hash", 12);
        assertEquals("asset", result.assetId());
        assertEquals(3, result.revision());
        assertEquals(12, result.byteLength());
    }
}
