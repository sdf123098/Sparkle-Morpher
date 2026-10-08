package com.micaftic.morpher.core.importing;

import com.micaftic.morpher.resource.pojo.RawYsmModel;
import com.micaftic.morpher.resource.gltf.GltfLoadResult;

import java.util.List;
import java.util.Objects;

/** Typed parser output; each format keeps its native runtime payload. */
public record ParsedImport(ImportSource source, Payload payload, List<Diagnostic> diagnostics) {
    public ParsedImport {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(payload, "payload");
        diagnostics = List.copyOf(diagnostics);
        if (source.isGltf() != (payload instanceof GltfPayload)) {
            throw new IllegalArgumentException("Import format and parsed payload do not match");
        }
    }

    public sealed interface Payload permits LegacyRawPayload, GltfPayload {}

    public record LegacyRawPayload(RawYsmModel model) implements Payload {
        public LegacyRawPayload { Objects.requireNonNull(model, "model"); }
    }

    public record GltfPayload(GltfLoadResult result) implements Payload {
        public GltfPayload { Objects.requireNonNull(result, "result"); }
    }

    public record Diagnostic(Severity severity, String code, String message) {
        public Diagnostic {
            Objects.requireNonNull(severity, "severity");
            if (code == null || code.isBlank()) throw new IllegalArgumentException("diagnostic code is required");
            if (message == null || message.isBlank()) throw new IllegalArgumentException("diagnostic message is required");
        }
    }

    public enum Severity { INFO, WARNING }
}
