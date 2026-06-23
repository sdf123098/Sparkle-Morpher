package com.micaftic.morpher.core.compat.oculus;

import net.minecraft.resources.Identifier;

public enum ShadersTextureType {
    NORMAL("_n"),
    SPECULAR("_s");

    public static final ShadersTextureType[] VALUES = values();

    private final String suffix;

    ShadersTextureType(String str) {
        this.suffix = str;
    }

    public Identifier appendSuffix(Identifier Identifier) {
        return Identifier.fromNamespaceAndPath(Identifier.getNamespace(), Identifier.getPath() + this.suffix);
    }
}
