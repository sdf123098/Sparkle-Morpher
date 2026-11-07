package com.micaftic.morpher.fabric.client;

import com.micaftic.morpher.client.core.MinecraftCoreSceneHost;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

/** Independent lifecycle bridge; no calls to the old animation/runtime implementation. */
public final class CoreSceneFabricLifecycle {
    private static boolean registered;
    private CoreSceneFabricLifecycle() { }
    @SuppressWarnings("deprecation") // 1.21.1 Fabric HUD callback retained by this loader version.
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        com.micaftic.morpher.client.core.MinecraftHostSnapshotSampler.registerParCoolPresence(
                () -> net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("parcool"));
        ClientTickEvents.START_CLIENT_TICK.register(client -> MinecraftCoreSceneHost.drive());
        WorldRenderEvents.START.register(context -> MinecraftCoreSceneHost.drive());
        HudRenderCallback.EVENT.register((graphics, delta) -> {
            MinecraftCoreSceneHost.drive(); // Frame driver; the presentation below only consumes snapshots.
            MinecraftCoreSceneHost.renderDiagnostics(graphics);
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> MinecraftCoreSceneHost.invalidate("Fabric join"));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> MinecraftCoreSceneHost.invalidate("Fabric disconnect"));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> MinecraftCoreSceneHost.invalidate("Fabric stopping"));
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public ResourceLocation getFabricId() { return ResourceLocation.fromNamespaceAndPath("sparkle_morpher", "independent_core_scene"); }
            @Override public void onResourceManagerReload(ResourceManager resources) { MinecraftCoreSceneHost.invalidate("Fabric resource reload"); }
        });
    }
}
