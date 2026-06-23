package com.micaftic.morpher.neoforge;

import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.event.ReplacePlayerRenderEvent;
import com.micaftic.morpher.client.event.ReplacePlayerHandRenderEvent;
import com.micaftic.morpher.client.renderer.AnimationDebugOverlay;
import com.micaftic.morpher.client.renderer.ExtraPlayerOverlay;
import com.micaftic.morpher.client.renderer.ModelSyncStateOverlay;
import com.micaftic.morpher.core.architectury.event.EventResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import com.micaftic.morpher.core.architectury.event.events.client.ClientCommandRegistrationEvent;
import com.micaftic.morpher.core.architectury.event.events.client.ClientLifecycleEvent;
import com.micaftic.morpher.core.architectury.event.events.client.ClientPlayerEvent;
import com.micaftic.morpher.core.architectury.event.events.client.ClientRawInputEvent;
import com.micaftic.morpher.core.architectury.event.events.client.ClientTickEvent;
import com.micaftic.morpher.core.architectury.registry.ReloadListenerRegistry;
import com.micaftic.morpher.core.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStartedEvent;
import net.neoforged.neoforge.common.NeoForge;
import com.micaftic.morpher.core.api.client.HudOverlay;
import com.micaftic.morpher.core.api.network.YSMChannel;

import java.util.List;

public final class NeoForgeClientEventBridge {
    private static final List<HudOverlay> HUD_OVERLAYS = List.of(
            new ModelSyncStateOverlay(),
            new ExtraPlayerOverlay(),
            AnimationDebugOverlay.createOverlay()
    );

    private NeoForgeClientEventBridge() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(YSMChannel::registerClientPayloadHandlers);
        modBus.addListener(NeoForgeClientEventBridge::onRegisterKeyMappings);
        modBus.addListener(NeoForgeClientEventBridge::onAddClientReloadListeners);
        NeoForge.EVENT_BUS.register(NeoForgeClientEventBridge.class);
    }

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        for (var mapping : KeyMappingRegistry.getCustomKeyMappings()) {
            event.register(mapping);
        }
    }

    public static void onAddClientReloadListeners(AddClientReloadListenersEvent event) {
        for (ReloadListenerRegistry.Entry entry : ReloadListenerRegistry.entries()) {
            if (entry.packType() == PackType.CLIENT_RESOURCES) {
                event.addListener(entry.id(), entry.listener());
            }
        }
    }

    @SubscribeEvent
    public static void onClientStarted(ClientStartedEvent event) {
        ClientLifecycleEvent.fireClientStarted(event.getClient());
    }

    @SubscribeEvent
    public static void onClientTickPre(net.neoforged.neoforge.client.event.ClientTickEvent.Pre event) {
        ClientTickEvent.fireClientPre(net.minecraft.client.Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void onClientTickPost(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        ClientTickEvent.fireClientPost(net.minecraft.client.Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void onClientCommands(RegisterClientCommandsEvent event) {
        ClientCommandRegistrationEvent.fire(event.getDispatcher(), event.getBuildContext());
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        ClientRawInputEvent.KEY_PRESSED.fireEventResult(handler -> handler.keyPressed(
                Minecraft.getInstance(),
                event.getKey(),
                event.getScanCode(),
                event.getAction(),
                event.getModifiers()
        ));
    }

    @SubscribeEvent
    public static void onMouseButtonPre(InputEvent.MouseButton.Pre event) {
        EventResult result = ClientRawInputEvent.MOUSE_CLICKED_PRE.fireEventResult(handler -> handler.mouseClickedPre(
                Minecraft.getInstance(),
                event.getButton(),
                event.getAction(),
                event.getModifiers()
        ));
        if (result.isFalse()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        for (HudOverlay overlay : HUD_OVERLAYS) {
            overlay.render(event.getGuiGraphics(), minecraft.font, partialTick, width, height);
        }
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre<?> event) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        Entity entity = Minecraft.getInstance().level.getEntity(event.getRenderState().id);
        if (!(entity instanceof Player player)) {
            return;
        }
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        PlayerCapability capability = PlayerCapability.get(player).orElse(null);
        if (capability != null) {
            capability.beginRenderState(event.getRenderState());
        }
        boolean replaced;
        try {
            replaced = ReplacePlayerRenderEvent.onRenderPlayerPre(
                    player,
                    event.getRenderState().yRot,
                    event.getPartialTick(),
                    event.getPoseStack(),
                    bufferSource,
                    event.getSubmitNodeCollector(),
                    event.getRenderState().lightCoords
            );
        } finally {
            if (capability != null) {
                capability.endRenderState();
            }
        }
        if (replaced) {
            bufferSource.endBatch();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent event) {
        if (ReplacePlayerHandRenderEvent.onRenderArm(
                event.getPlayer(),
                event.getArm(),
                event.getPoseStack(),
                event.getSubmitNodeCollector(),
                event.getPackedLight()
        )) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientPlayerJoin(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
        ClientPlayerEvent.fireJoin(event.getPlayer());
    }

    @SubscribeEvent
    public static void onClientPlayerQuit(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPlayerEvent.fireQuit(event.getPlayer());
    }

    @SubscribeEvent
    public static void onClientPlayerClone(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.Clone event) {
        ClientPlayerEvent.fireRespawn(event.getOldPlayer(), event.getNewPlayer());
    }
}
