package com.micaftic.morpher.core.importing;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/** Source identity and resource authority for one import attempt. */
public record ImportSource(String fileName, Format format, Kind kind, Path authorizedRoot) {
    public enum Kind { PICKED_BYTES, DOWNLOADED_BYTES, LOCAL_PATH, ARCHIVE_ENTRY }

    public enum Format { YSM, ZIP, BBMODEL, BEDROCK_GEOMETRY, GLTF, GLB }

    public ImportSource {
        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName is required");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(kind, "kind");
        authorizedRoot = authorizedRoot == null ? null : authorizedRoot.toAbsolutePath().normalize();
        if ((kind == Kind.LOCAL_PATH || kind == Kind.ARCHIVE_ENTRY) && authorizedRoot == null) {
            throw new IllegalArgumentException(kind + " requires an explicit resource root");
        }
        if ((kind == Kind.PICKED_BYTES || kind == Kind.DOWNLOADED_BYTES) && authorizedRoot != null) {
            throw new IllegalArgumentException(kind + " must not infer a resource root");
        }
    }

    public static ImportSource pickedBytes(String fileName) {
        return new ImportSource(fileName, formatFromName(fileName), Kind.PICKED_BYTES, null);
    }

    public static Format formatFromName(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".ysm")) return Format.YSM;
        if (lower.endsWith(".zip")) return Format.ZIP;
        if (lower.endsWith(".bbmodel")) return Format.BBMODEL;
        if (lower.endsWith(".geo.json") || lower.endsWith("geometry.json")) return Format.BEDROCK_GEOMETRY;
        if (lower.endsWith(".gltf")) return Format.GLTF;
        if (lower.endsWith(".glb")) return Format.GLB;
        throw new IllegalArgumentException("Unsupported model import type: " + fileName);
    }

    public boolean isGltf() {
        return format == Format.GLTF || format == Format.GLB;
    }
}
