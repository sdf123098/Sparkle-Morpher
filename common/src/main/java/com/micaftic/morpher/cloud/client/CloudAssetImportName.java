package com.micaftic.morpher.cloud.client;

import java.util.Locale;

/** Chooses the parser extension from Cloud metadata, independently of the display name. */
public final class CloudAssetImportName {
    private static final String[] EXTENSIONS = {".ysm", ".zip", ".bbmodel", ".gltf", ".glb"};

    private CloudAssetImportName() {}

    public static String fileName(CloudAssetSummary summary) {
        String name = summary.name().isBlank() ? summary.ref().assetId() : summary.name();
        String lowerName = name.toLowerCase(Locale.ROOT);
        String format = summary.format().trim().toLowerCase(Locale.ROOT);
        String extension = switch (format) {
            case "ysm", "zip", "bbmodel", "gltf", "glb" -> "." + format;
            default -> "";
        };
        for (String known : EXTENSIONS) {
            if (lowerName.endsWith(known)) {
                if (extension.isEmpty()) return name;
                name = name.substring(0, name.length() - known.length());
                break;
            }
        }
        return name + (extension.isEmpty() ? ".ysm" : extension);
    }
}
