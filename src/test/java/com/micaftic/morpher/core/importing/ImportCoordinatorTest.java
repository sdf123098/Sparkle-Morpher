package com.micaftic.morpher.core.importing;

import com.micaftic.morpher.resource.pojo.RawYsmModel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportCoordinatorTest {
    @Test
    void gltfBytesBecomeTypedPayloadWithNoInventedResourceRoot() throws Exception {
        byte[] source = "{\"asset\":{\"version\":\"2.0\"}}".getBytes(StandardCharsets.UTF_8);
        AtomicBoolean legacyParserCalled = new AtomicBoolean();

        ParsedImport parsed = ImportCoordinator.parsePickedBytes("picked.gltf", source, () -> {
            legacyParserCalled.set(true);
            return new RawYsmModel();
        });

        ParsedImport.GltfPayload payload = assertInstanceOf(ParsedImport.GltfPayload.class, parsed.payload());
        assertEquals(ImportSource.Kind.PICKED_BYTES, parsed.source().kind());
        assertEquals(ImportSource.Format.GLTF, parsed.source().format());
        assertTrue(payload.result().dependencies().entries().isEmpty());
        assertFalse(legacyParserCalled.get());
    }

    @Test
    void legacyBytesUseLegacyParserAndRemainTypedAsRawYsm() throws Exception {
        RawYsmModel expected = new RawYsmModel();
        AtomicBoolean legacyParserCalled = new AtomicBoolean();

        ParsedImport parsed = ImportCoordinator.parsePickedBytes("picked.bbmodel", new byte[]{1}, () -> {
            legacyParserCalled.set(true);
            return expected;
        });

        ParsedImport.LegacyRawPayload payload = assertInstanceOf(ParsedImport.LegacyRawPayload.class, parsed.payload());
        assertEquals(ImportSource.Format.BBMODEL, parsed.source().format());
        assertTrue(legacyParserCalled.get());
        assertEquals(expected, payload.model());
    }

    @Test
    void unsupportedFormatFailsBeforeCallingFormatParser() {
        AtomicBoolean legacyParserCalled = new AtomicBoolean();
        assertThrows(IllegalArgumentException.class,
                () -> ImportCoordinator.parsePickedBytes("picked.obj", new byte[]{1}, () -> {
                    legacyParserCalled.set(true);
                    return new RawYsmModel();
                }));
        assertFalse(legacyParserCalled.get());
    }
}
