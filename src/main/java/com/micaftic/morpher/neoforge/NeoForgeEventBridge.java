package com.micaftic.morpher.neoforge;

import com.micaftic.morpher.client.event.MobEffectEvent;
import com.micaftic.morpher.client.event.ShieldBlockCooldownEvent;
import com.micaftic.morpher.core.architectury.event.EventResult;
import com.micaftic.morpher.core.architectury.event.events.common.CommandRegistrationEvent;
import com.micaftic.morpher.core.architectury.event.events.common.EntityEvent;
import com.micaftic.morpher.core.architectury.event.events.common.LifecycleEvent;
import com.micaftic.morpher.core.architectury.event.events.common.PlayerEvent;
import com.micaftic.morpher.core.architectury.event.events.common.TickEvent;
import com.micaftic.morpher.core.architectury.registry.ReloadListenerRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import com.micaftic.morpher.core.api.network.YSMChannel;

public final class NeoForgeEventBridge {
    private NeoForgeEventBridge() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(YSMChannel::registerPayloadHandlers);
        NeoForge.EVENT_BUS.register(NeoForgeEventBridge.class);
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        LifecycleEvent.fireServerBeforeStart(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LifecycleEvent.fireServerStopped();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandRegistrationEvent.fire(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection());
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerEvent.fireJoin(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerEvent.fireQuit(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer oldPlayer && event.getEntity() instanceof ServerPlayer newPlayer) {
            PlayerEvent.fireClone(oldPlayer, newPlayer, event.isWasDeath());
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        EventResult result = EntityEvent.fireAdd(event.getEntity(), event.getLevel());
        if (result.isFalse()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMobEffectAdded(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Added event) {
        if (event.getEffectInstance() != null) {
            MobEffectEvent.onEffectAdded(event.getEntity(), event.getEffectInstance().getEffect().value(), event.getEffectInstance().getAmplifier());
        }
    }

    @SubscribeEvent
    public static void onMobEffectRemoved(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Remove event) {
        if (event.getEffect() != null) {
            MobEffectEvent.onEffectRemoved(event.getEntity(), event.getEffect().value());
        }
    }

    @SubscribeEvent
    public static void onLivingShieldBlock(LivingShieldBlockEvent event) {
        if (event.getBlocked()) {
            ShieldBlockCooldownEvent.onShieldBlock(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onEntityTickPost(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity livingEntity) {
            ShieldBlockCooldownEvent.onLivingTick(livingEntity);
        }
    }

    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        TickEvent.fireServerPost(event.getServer());
    }

    @SubscribeEvent
    public static void onAddServerReloadListeners(AddServerReloadListenersEvent event) {
        for (ReloadListenerRegistry.Entry entry : ReloadListenerRegistry.entries()) {
            if (entry.packType() == PackType.SERVER_DATA) {
                event.addListener(entry.id(), entry.listener());
            }
        }
    }

    @SubscribeEvent
    public static void onStartTracking(net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking event) {
        if (!com.micaftic.morpher.YesSteveModel.isAvailable()) return;
        net.minecraft.world.entity.Entity target = event.getTarget();
        if (!(event.getEntity() instanceof ServerPlayer tracker)) return;
        if (target instanceof ServerPlayer tracked) {
            com.micaftic.morpher.event.CapabilityEvent.getModelInfoCap(tracked).ifPresent(c -> {
                if (com.micaftic.morpher.network.NetworkHandler.isPlayerConnected(tracked) || c.isMandatory()) {
                    c.createSyncMessage(tracked, false).ifPresent(m -> {
                        com.micaftic.morpher.network.NetworkHandler.sendToClientPlayer(m, tracker);
                    });
                }
            });
        }
    }
}
