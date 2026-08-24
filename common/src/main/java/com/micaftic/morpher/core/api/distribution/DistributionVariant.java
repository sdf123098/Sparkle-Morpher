package com.micaftic.morpher.core.api.distribution;

public enum DistributionVariant { NATIVE, MODRINTH, CURSEFORGE; public static DistributionVariant parse(String value) { return value == null || value.isBlank() ? NATIVE : valueOf(value.trim().toUpperCase(java.util.Locale.ROOT)); } }
