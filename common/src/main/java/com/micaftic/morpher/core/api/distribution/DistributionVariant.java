package com.micaftic.morpher.core.api.distribution;

public enum DistributionVariant {
    NATIVE,
    MODRINTH,
    CURSEFORGE;

    public static DistributionVariant parse(String value) {
        if (value == null || value.isBlank()) {
            return NATIVE;
        }
        return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
