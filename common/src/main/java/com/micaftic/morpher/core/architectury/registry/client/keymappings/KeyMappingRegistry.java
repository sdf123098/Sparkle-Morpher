package com.micaftic.morpher.core.architectury.registry.client.keymappings;

import net.minecraft.client.KeyMapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MC 26.x: Stores custom keybindings for injection into Options.keyMappings via mixin.
 */
public class KeyMappingRegistry {

    private static final List<KeyMapping> CUSTOM_KEY_MAPPINGS = new ArrayList<>();

    public static void register(KeyMapping keyMapping) {
        CUSTOM_KEY_MAPPINGS.add(keyMapping);
    }

    public static List<KeyMapping> getCustomKeyMappings() {
        return Collections.unmodifiableList(CUSTOM_KEY_MAPPINGS);
    }
}
