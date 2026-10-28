package com.micaftic.morpher.core.architectury.registry;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * Fabric-backed replacement for Architectury's reload listener registry.
 */
public class ReloadListenerRegistry {
    public static void register(PackType packType, ResourceManagerReloadListener listener, Identifier id) {
        ResourceLoader.get(packType).registerReloadListener(id, listener);
    }
}
