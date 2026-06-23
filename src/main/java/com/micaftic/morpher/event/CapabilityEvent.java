package com.micaftic.morpher.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.*;
import com.micaftic.morpher.config.ServerConfig;
import com.micaftic.morpher.model.ServerModelManager;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.*;
import com.micaftic.morpher.util.*;
import com.micaftic.morpher.core.api.capability.CapabilityLifecycle;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class CapabilityEvent {
    private static final ConcurrentMap<UUID, ConcurrentMap<UUID, String>> SYNCED_PLAYER_MODEL_STATES = new ConcurrentHashMap<>();
    private CapabilityEvent() {}
    public static void register() {
        NeoForge.EVENT_BUS.addListener(CapabilityEvent::onPlayerCloned);
        NeoForge.EVENT_BUS.addListener(CapabilityEvent::onPlayerQuit);
        NeoForge.EVENT_BUS.addListener(CapabilityEvent::onEntityAdd);
        NeoForge.EVENT_BUS.addListener(CapabilityEvent::onServerTick);
    }
    private static void onPlayerCloned(PlayerEvent.Clone event) {
        if (!YesSteveModel.isAvailable()) return;
        ServerPlayer old = (ServerPlayer) event.getOriginal();
        ServerPlayer neo = (ServerPlayer) event.getEntity();
        CapabilityLifecycle.revive(old);
        getModelInfoCap(old).ifPresent(oc -> getModelInfoCap(neo).ifPresent(nc -> nc.copyFrom(oc)));
        getAuthModelsCap(old).ifPresent(oc -> getAuthModelsCap(neo).ifPresent(nc -> nc.copyFrom(oc)));
        getStarModelsCap(old).ifPresent(oc -> getStarModelsCap(neo).ifPresent(nc -> nc.copyFrom(oc)));
        CapabilityLifecycle.invalidate(old);
    }
    private static void onPlayerQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        SYNCED_PLAYER_MODEL_STATES.remove(p.getUUID());
        SYNCED_PLAYER_MODEL_STATES.values().forEach(s -> s.remove(p.getUUID()));
    }
    public static void forgetSyncedModelStates(ServerPlayer r) { SYNCED_PLAYER_MODEL_STATES.remove(r.getUUID()); }
    private static void onEntityAdd(EntityJoinLevelEvent event) {
        Entity e = event.getEntity(); Level l = event.getLevel();
        if (!YesSteveModel.isAvailable()) return;
        if (!l.isClientSide() && e instanceof Projectile proj && proj.getOwner() instanceof ServerPlayer owner) syncProjectileModel(proj, owner);
        if (e instanceof ServerPlayer p) {
            getAuthModelsCap(p).ifPresent(c -> { for (String m : ServerModelManager.getAuthModels()) c.addModel(m); NetworkHandler.sendToClientPlayer(new S2CSyncAuthModelsPacket(c.getAuthModels()), p); });
            PlayerModelSelectionStore.restore(p);
            ServerModelManager.validatePlayerModel(p);
            syncPlayerModelToSelf(p); syncPlayerModelToTracking(p, false);
            getStarModelsCap(p).ifPresent(c -> NetworkHandler.sendToClientPlayer(new S2CSyncStarModelsPacket(c.getStarModels()), p));
        }
    }
    public static void syncPlayerModelToSelf(ServerPlayer p) {
        getModelInfoCap(p).ifPresent(c -> { if (!canSyncModel(p, c)) { c.markDirty(); return; } c.stopAnimation(p); c.createSyncMessage(p, false).ifPresentOrElse(m -> NetworkHandler.sendToClientPlayer(m, p), c::markDirty); });
    }
    public static void syncPlayerModelToTracking(ServerPlayer p, boolean reset) {
        getModelInfoCap(p).ifPresent(c -> { if (!canSyncModel(p, c)) { c.markDirty(); return; } c.createSyncMessage(p, reset).ifPresentOrElse(m -> { NetworkHandler.sendToTrackingEntityAndSelf(m, p); rememberTrackedState(p, c); }, c::markDirty); });
    }
    public static void syncVisiblePlayerModelsTo(ServerPlayer r) {
        MinecraftServer s = r.serverLevel().getServer(); if (s == null) return;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) getModelInfoCap(p).ifPresent(c -> { if (canSyncModel(p, c)) c.createSyncMessage(p, false).ifPresent(m -> { NetworkHandler.sendToClientPlayer(m, r); }); });
    }
    private static boolean canSyncModel(ServerPlayer p, ModelInfoCapability c) { return NetworkHandler.isPlayerConnected(p) || c.isMandatory(); }
    private static void onServerTick(ServerTickEvent.Post event) {
        if (!YesSteveModel.isAvailable()) return;
        MinecraftServer s = event.getServer(); if (s == null) return;
        Boolean low = ServerConfig.LOW_BANDWIDTH_USAGE.get();
        for (ServerPlayer sp : s.getPlayerList().getPlayers()) {
            getModelInfoCap(sp).ifPresent(c -> {
                if (!NetworkHandler.isPlayerConnected(sp) && !c.isMandatory()) { if (sp.tickCount == 200 || sp.tickCount == 600 || sp.tickCount == 1800) NetworkHandler.sendToClientPlayer(new S2CVersionCheckPacket(), sp); return; }
                if (c.isDirty()) { c.getAnimSync().updateAndSync(sp, false, low); c.createSyncMessage(sp, true).ifPresent(m -> { c.clearDirty(); NetworkHandler.sendToTrackingEntityAndSelf(m, sp); rememberTrackedState(sp, c); if (sp.getVehicle() != null && sp.getVehicle().getFirstPassenger() == sp) syncVehicleModel(sp.getVehicle(), sp); }); }
                else c.getAnimSync().updateAndSync(sp, true, low);
            });
        }
    }
    private static void rememberTrackedState(ServerPlayer src, ModelInfoCapability cap) {
        MinecraftServer s = src.serverLevel().getServer(); if (s == null) return;
        for (ServerPlayer r : s.getPlayerList().getPlayers()) if (r != src) SYNCED_PLAYER_MODEL_STATES.computeIfAbsent(r.getUUID(), u -> new ConcurrentHashMap<>()).put(src.getUUID(), cap.getModelId() + "\0" + cap.getSelectTexture() + "\0" + cap.isDisabled());
    }
    public static void syncProjectileModel(Projectile proj, ServerPlayer sp) {
        ModelInfoCapability.get(sp).ifPresent(c -> { if (!NetworkHandler.isPlayerConnected(sp) && !c.isMandatory()) return; ProjectileModelCapability.get(proj).ifPresent(pc -> c.withMolangVars(v -> { pc.setModel(c.getModelId(), v); S2CSyncProjectileModelPacket pkt = new S2CSyncProjectileModelPacket(proj.getId(), pc); NetworkHandler.sendToClientPlayer(pkt, sp); NetworkHandler.sendToTrackingEntity(pkt, proj); })); });
    }
    public static void syncVehicleModel(Entity e, ServerPlayer sp) {
        ModelInfoCapability.get(sp).ifPresent(c -> { if (!NetworkHandler.isPlayerConnected(sp) && !c.isMandatory()) return; VehicleModelCapability.get(e).ifPresent(vc -> c.getMolangVars().ifPresent(v -> { vc.setModel(c.getModelId(), v); NetworkHandler.sendToTrackingEntity(new S2CSyncVehicleModelPacket(e.getId(), vc), e); })); });
    }
    public static Optional<ModelInfoCapability> getModelInfoCap(Player p) { return ModelInfoCapability.get(p); }
    public static Optional<AuthModelsCapability> getAuthModelsCap(Player p) { return AuthModelsCapability.get(p); }
    public static Optional<StarModelsCapability> getStarModelsCap(Player p) { return StarModelsCapability.get(p); }
}