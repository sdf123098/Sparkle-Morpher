package com.micaftic.morpher.client.renderer;

import com.micaftic.morpher.YesSteveModel;
import dev.architectury.registry.ReloadListenerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import com.micaftic.morpher.core.api.PlatformAPI;
import com.micaftic.morpher.core.compat.sbackpack.SBackpackCompat;

public class RendererManager {

    private static CustomPlayerRenderer playerRenderer;

    private static ProjectileRenderer projectileRenderer;

    private static HandItemRenderer handRenderer;

    private static VehicleRenderer vehicleRenderer;

    private RendererManager() {
    }

    public static void register() {
        if (PlatformAPI.isServer()) {
            return;
        }
        ResourceManagerReloadListener listener = resourceManager -> resetRenderers();
        ReloadListenerRegistry.register(PackType.CLIENT_RESOURCES, listener, ResourceLocation.fromNamespaceAndPath(YesSteveModel.MOD_ID, "renderer_manager"));
    }

    private static void resetRenderers() {
        playerRenderer = null;
        projectileRenderer = null;
        handRenderer = null;
        vehicleRenderer = null;
    }

    private static void initRenderers(ResourceManager resourceManager) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        EntityRenderDispatcher entityRenderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        EntityRendererProvider.Context context = new EntityRendererProvider.Context(entityRenderDispatcher, Minecraft.getInstance().getItemRenderer(), Minecraft.getInstance().getBlockRenderer(), entityRenderDispatcher.getItemInHandRenderer(), resourceManager, Minecraft.getInstance().getEntityModels(), Minecraft.getInstance().font);
        playerRenderer = new CustomPlayerRenderer(context);
        projectileRenderer = new ProjectileRenderer(context);
        handRenderer = new HandItemRenderer();
        vehicleRenderer = new VehicleRenderer(context);
        SBackpackCompat.setupRenderLayers();
    }

    public static CustomPlayerRenderer getPlayerRenderer() {
        if (playerRenderer == null) {
            initRenderers(Minecraft.getInstance().getResourceManager());
        }
        return playerRenderer;
    }

    public static ProjectileRenderer getProjectileRenderer() {
        if (projectileRenderer == null) {
            initRenderers(Minecraft.getInstance().getResourceManager());
        }
        return projectileRenderer;
    }

    public static HandItemRenderer getHandRenderer() {
        if (handRenderer == null) {
            initRenderers(Minecraft.getInstance().getResourceManager());
        }
        return handRenderer;
    }

    public static VehicleRenderer getVehicleRenderer() {
        if (vehicleRenderer == null) {
            initRenderers(Minecraft.getInstance().getResourceManager());
        }
        return vehicleRenderer;
    }
}
