package com.micaftic.morpher.core.compat.oculus;

import net.minecraft.resources.ResourceLocation;

public enum ShadersTextureType {
    NORMAL("_n"),
    SPECULAR("_s");

    public static final ShadersTextureType[] VALUES = values();

    private final String suffix;

    ShadersTextureType(String str) {
        this.suffix = str;
    }

    public ResourceLocation appendSuffix(ResourceLocation resourceLocation) {
        return com.micaftic.morpher.core.api.resource.ResourceApi.nativeId(resourceLocation.getNamespace(), resourceLocation.getPath() + this.suffix);
    }
}
