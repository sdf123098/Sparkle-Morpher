package com.micaftic.morpher.util;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

public final class ModelIdUtil {
    private static final Pattern INVALID_MODEL_ID_CHARS = Pattern.compile("[^\\p{L}\\p{M}\\p{N}_./-]+");
    private static final Pattern MODEL_ID_PATTERN = Pattern.compile("[\\p{L}\\p{M}\\p{N}_./-]+");
    private static final Pattern MODEL_ID_CONTENT_PATTERN = Pattern.compile(".*[\\p{L}\\p{N}].*");

    private ModelIdUtil() {
    }

    public static String normalizeImportModelId(@Nullable String modelId) {
        String normalized = modelId == null ? "" : modelId.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        normalized = INVALID_MODEL_ID_CHARS.matcher(normalized)
                .replaceAll("_")
                .replaceAll("/+", "/")
                .replaceAll("^/+", "")
                .replaceAll("/+$", "");
        while (normalized.contains("..")) {
            normalized = normalized.replace("..", ".");
        }
        return normalized;
    }

    public static boolean isValidModelId(@Nullable String modelId) {
        return modelId != null
                && !modelId.isBlank()
                && !modelId.contains("..")
                && MODEL_ID_PATTERN.matcher(modelId).matches();
    }

    public static boolean hasLetterOrNumber(@Nullable String modelId) {
        return modelId != null && MODEL_ID_CONTENT_PATTERN.matcher(modelId).matches();
    }
}
