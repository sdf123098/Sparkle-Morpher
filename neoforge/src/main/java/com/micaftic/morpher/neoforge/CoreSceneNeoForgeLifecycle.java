package com.micaftic.morpher.neoforge;

import com.micaftic.morpher.client.core.MinecraftCoreSceneHost;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Independent client lifecycle bridge registered by the client-only mod initializer. */
public final class CoreSceneNeoForgeLifecycle {
    private static boolean registered;

    private CoreSceneNeoForgeLifecycle() {
    }

    public static synchronized void register(IEventBus modBus) {
        if (registered) {
            return;
        }
        registered = true;
        com.micaftic.morpher.client.core.MinecraftHostSnapshotSampler.registerCompatibilityPresence(
                id -> net.neoforged.fml.ModList.get().isLoaded(id));
        com.micaftic.morpher.client.core.CoreCompatibilityProviders.registerAll();
        com.micaftic.morpher.client.core.MinecraftHostSnapshotSampler.registerEquipmentProvider((entity, slot) -> {
            net.minecraft.world.entity.EquipmentSlot mcSlot = net.minecraft.world.entity.EquipmentSlot.valueOf(slot.name());
            net.minecraft.world.item.ItemStack stack = switch (mcSlot) {
                case HEAD, CHEST, LEGS, FEET -> com.micaftic.morpher.core.compat.cosmeticarmorreworked.CosmeticArmorHelper.getArmorItem(entity, mcSlot);
                default -> entity.getItemBySlot(mcSlot);
            };
            return new com.micaftic.morpher.client.core.CoreQueryFunctions.Equipment(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                    stack.isEmpty(),
                    stack.getMaxDamage(),
                    stack.getDamageValue(),
                    stack.typeHolder().tags().map(tag -> tag.location().toString())
                            .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        });
        modBus.addListener(CoreSceneNeoForgeLifecycle::reloadListeners);
        NeoForge.EVENT_BUS.addListener(CoreSceneNeoForgeLifecycle::tick);
        NeoForge.EVENT_BUS.addListener(CoreSceneNeoForgeLifecycle::hud);
        NeoForge.EVENT_BUS.addListener(CoreSceneNeoForgeLifecycle::join);
        NeoForge.EVENT_BUS.addListener(CoreSceneNeoForgeLifecycle::disconnect);
        NeoForge.EVENT_BUS.addListener(CoreSceneNeoForgeLifecycle::stop);
    }

    private static void tick(ClientTickEvent.Pre event) {
        MinecraftCoreSceneHost.drive();
    }

    private static void hud(RenderGuiEvent.Post event) {
        MinecraftCoreSceneHost.drive();
        MinecraftCoreSceneHost.renderDiagnostics(event.getGuiGraphics());
    }

    private static void join(ClientPlayerNetworkEvent.LoggingIn event) {
        MinecraftCoreSceneHost.invalidate("NeoForge join");
    }

    private static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        MinecraftCoreSceneHost.invalidate("NeoForge disconnect");
    }

    private static void stop(ClientStoppingEvent event) {
        MinecraftCoreSceneHost.invalidate("NeoForge stopping");
    }

    private static void reloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(
                Identifier.fromNamespaceAndPath("sparkle_morpher", "independent_core_scene"),
                (ResourceManagerReloadListener) resources -> MinecraftCoreSceneHost.invalidate("NeoForge resource reload"));
    }
}
