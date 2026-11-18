package com.micaftic.morpher.fabric.client;

import com.micaftic.morpher.client.core.MinecraftCoreSceneHost;
import com.micaftic.morpher.core.architectury.registry.ReloadListenerRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

/** Independent lifecycle bridge; no calls to the old animation/runtime implementation. */
public final class CoreSceneFabricLifecycle {
    private static boolean registered;

    private CoreSceneFabricLifecycle() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        com.micaftic.morpher.client.core.MinecraftHostSnapshotSampler.registerCompatibilityPresence(
                id -> net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(id));
        ClientTickEvents.START_CLIENT_TICK.register(client -> MinecraftCoreSceneHost.drive());
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.BOSS_BAR,
                Identifier.fromNamespaceAndPath("sparkle_morpher", "independent_core_scene_hud"),
                (graphics, tickDelta) -> {
                    MinecraftCoreSceneHost.drive(); // Frame driver; the presentation below only consumes snapshots.
                    MinecraftCoreSceneHost.renderDiagnostics(graphics);
                });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> MinecraftCoreSceneHost.invalidate("Fabric join"));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> MinecraftCoreSceneHost.invalidate("Fabric disconnect"));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> MinecraftCoreSceneHost.invalidate("Fabric stopping"));
        ReloadListenerRegistry.register(
                PackType.CLIENT_RESOURCES,
                resources -> MinecraftCoreSceneHost.invalidate("Fabric resource reload"),
                Identifier.fromNamespaceAndPath("sparkle_morpher", "independent_core_scene"));
    }
}
