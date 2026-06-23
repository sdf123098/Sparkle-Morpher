package com.micaftic.morpher.core.architectury.registry;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * Fabric-backed replacement for Architectury's reload listener registry.
 */
public class ReloadListenerRegistry {
    public static void register(PackType packType, ResourceManagerReloadListener listener, Identifier id) {
        ResourceManagerHelper.get(packType).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return id;
            }

            @Override
            public void onResourceManagerReload(ResourceManager resourceManager) {
                listener.onResourceManagerReload(resourceManager);
            }
        });
    }
}
