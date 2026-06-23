package com.micaftic.morpher.core.api.client.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class KeyMappingFactoryImpl {

    private static final Map<String, KeyMapping.Category> CATEGORY_CACHE = new ConcurrentHashMap<>();

    private KeyMappingFactoryImpl() {
    }

    private static KeyMapping.Category getOrCreateCategory(String categoryKey) {
        return CATEGORY_CACHE.computeIfAbsent(categoryKey, k ->
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("sparkle_morpher", "keys")));
    }

    public static KeyMapping createInGameAlt(String name, InputConstants.Type type, int keyCode, String category) {
        return new KeyMapping(name, type, keyCode, getOrCreateCategory(category));
    }

    public static KeyMapping createInGameNone(String name, InputConstants.Type type, int keyCode, String category) {
        return new KeyMapping(name, type, keyCode, getOrCreateCategory(category));
    }

    public static boolean isActiveAndMatches(KeyMapping keyMapping, int keyCode, int scanCode) {
        return keyMapping.matches(new KeyEvent(keyCode, scanCode, 0));
    }
}
