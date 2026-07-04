package com.micaftic.morpher.core.api.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class KeyMappingFactory {

    private static final Map<String, KeyMapping.Category> CATEGORY_CACHE = new ConcurrentHashMap<>();

    private KeyMappingFactory() {
    }

    private static KeyMapping.Category getOrCreateCategory(String categoryKey) {
        return CATEGORY_CACHE.computeIfAbsent(categoryKey, k -> {
            // MC 26.x: Category uses Identifier; label() generates "key.category.<ns>.<path>"
            return KeyMapping.Category.register(Identifier.fromNamespaceAndPath("sparkle_morpher", "keys"));
        });
    }

    public static KeyMapping createInGameAlt(String name, InputConstants.Type type, int keyCode, String category) {
        return new KeyMapping(name, type, keyCode, getOrCreateCategory(category));
    }

    public static KeyMapping createInGameNone(String name, InputConstants.Type type, int keyCode, String category) {
        return new KeyMapping(name, type, keyCode, getOrCreateCategory(category));
    }

    public static boolean isActiveAndMatches(KeyMapping keyMapping, int keyCode, int scanCode) {
        return isActiveAndMatches(keyMapping, keyCode, scanCode, 0);
    }

    public static boolean isActiveAndMatches(KeyMapping keyMapping, int keyCode, int scanCode, int modifiers) {
        return keyMapping.matches(new KeyEvent(keyCode, scanCode, modifiers));
    }

    public static boolean isMouseActiveAndMatches(KeyMapping keyMapping, int button) {
        return isMouseActiveAndMatches(keyMapping, button, 0);
    }

    public static boolean isMouseActiveAndMatches(KeyMapping keyMapping, int button, int modifiers) {
        return keyMapping.matchesMouse(new MouseButtonEvent(0.0d, 0.0d, new MouseButtonInfo(button, modifiers)));
    }
}
