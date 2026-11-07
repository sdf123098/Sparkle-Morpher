package com.micaftic.morpher.neoforge;

import com.micaftic.morpher.client.core.MinecraftCoreSceneHost;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** Independent client lifecycle bridge registered by the client-only mod initializer. */
public final class CoreSceneNeoForgeLifecycle {
    private static boolean registered;
    private CoreSceneNeoForgeLifecycle() { }
    public static synchronized void register(IEventBus modBus) {
        if (registered) return;
        registered = true;
        com.micaftic.morpher.client.core.MinecraftHostSnapshotSampler.registerParCoolPresence(
                () -> net.neoforged.fml.ModList.get().isLoaded("parcool"));
        modBus.addListener(CoreSceneNeoForgeLifecycle::reloadListeners);
        NeoForge.EVENT_BUS.addListener(CoreSceneNeoForgeLifecycle::tick);
        NeoForge.EVENT_BUS.addListener(CoreSceneNeoForgeLifecycle::frame);
        NeoForge.EVENT_BUS.addListener(CoreSceneNeoForgeLifecycle::hud);
        NeoForge.EVENT_BUS.addListener(CoreSceneNeoForgeLifecycle::join);
        NeoForge.EVENT_BUS.addListener(CoreSceneNeoForgeLifecycle::disconnect);
        NeoForge.EVENT_BUS.addListener(CoreSceneNeoForgeLifecycle::stop);
    }
    private static void tick(ClientTickEvent.Pre event) { MinecraftCoreSceneHost.drive(); }
    private static void frame(RenderFrameEvent.Pre event) { MinecraftCoreSceneHost.drive(); }
    private static void hud(RenderGuiEvent.Post event) { MinecraftCoreSceneHost.renderDiagnostics(event.getGuiGraphics()); }
    private static void join(ClientPlayerNetworkEvent.LoggingIn event) { MinecraftCoreSceneHost.invalidate("NeoForge join"); }
    private static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) { MinecraftCoreSceneHost.invalidate("NeoForge disconnect"); }
    private static void stop(GameShuttingDownEvent event) { MinecraftCoreSceneHost.invalidate("NeoForge stopping"); }
    private static void reloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> MinecraftCoreSceneHost.invalidate("NeoForge resource reload"));
    }
}
