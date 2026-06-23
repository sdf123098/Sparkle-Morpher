package com.micaftic.morpher.client.renderer;

import com.micaftic.morpher.YesSteveModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import com.micaftic.morpher.core.compat.sbackpack.SBackpackCompat;

@EventBusSubscriber(modid = YesSteveModel.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class RendererManager {
    private static CustomPlayerRenderer pr; private static ProjectileRenderer pjr; private static HandItemRenderer hr; private static VehicleRenderer vr;
    private RendererManager() {}
    public static void register() {}
    @SubscribeEvent public static void onReload(AddReloadListenerEvent event) {
        event.addListener(new net.minecraft.server.packs.resources.ResourceManagerReloadListener() {
            
            @Override public void onResourceManagerReload(ResourceManager rm) { pr = null; pjr = null; hr = null; vr = null; }
        });
    }
    private static void init(ResourceManager rm) {
        if (!YesSteveModel.isAvailable()) return;
        EntityRenderDispatcher d = Minecraft.getInstance().getEntityRenderDispatcher();
        EntityRendererProvider.Context ctx = new EntityRendererProvider.Context(d, Minecraft.getInstance().getItemRenderer(), Minecraft.getInstance().getBlockRenderer(), d.getItemInHandRenderer(), rm, Minecraft.getInstance().getEntityModels(), Minecraft.getInstance().font);
        pr = new CustomPlayerRenderer(ctx); pjr = new ProjectileRenderer(ctx); hr = new HandItemRenderer(); vr = new VehicleRenderer(ctx); SBackpackCompat.setupRenderLayers();
    }
    public static CustomPlayerRenderer getPlayerRenderer() { if (pr == null) init(Minecraft.getInstance().getResourceManager()); return pr; }
    public static ProjectileRenderer getProjectileRenderer() { if (pjr == null) init(Minecraft.getInstance().getResourceManager()); return pjr; }
    public static HandItemRenderer getHandRenderer() { if (hr == null) init(Minecraft.getInstance().getResourceManager()); return hr; }
    public static VehicleRenderer getVehicleRenderer() { if (vr == null) init(Minecraft.getInstance().getResourceManager()); return vr; }
}