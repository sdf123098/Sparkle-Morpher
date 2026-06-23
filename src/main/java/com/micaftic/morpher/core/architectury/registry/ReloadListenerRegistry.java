package com.micaftic.morpher.core.architectury.registry;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import java.util.ArrayList;
import java.util.List;

/**
 * NeoForge-compatible replacement for Architectury's reload listener registry.
 */
public class ReloadListenerRegistry {
    private static final List<Entry> ENTRIES = new ArrayList<>();

    public static void register(PackType packType, ResourceManagerReloadListener listener, Identifier id) {
        ENTRIES.add(new Entry(packType, listener, id));
    }

    public static List<Entry> entries() {
        return List.copyOf(ENTRIES);
    }

    public record Entry(PackType packType, ResourceManagerReloadListener listener, Identifier id) {
    }
}
