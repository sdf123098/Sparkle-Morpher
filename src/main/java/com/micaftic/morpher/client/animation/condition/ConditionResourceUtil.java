package com.micaftic.morpher.client.animation.condition;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

final class ConditionResourceUtil {

    private ConditionResourceUtil() {
    }

    static Identifier parseIdentifier(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return Identifier.tryParse(value);
    }

    static <T> TagKey<T> parseTag(ResourceKey<? extends Registry<T>> registry, String value) {
        Identifier id = parseIdentifier(value);
        return id == null ? null : TagKey.create(registry, id);
    }
}
