package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.PrivacyMode;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.core.model.CloudAssetIdentity;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.geckolib3.resource.GeckoLibCache;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Main-thread player publication and immutable render-worker snapshots. */
public final class CloudPlayerMotionSync {
    private static final CloudPlayerMotionState OWN = new CloudPlayerMotionState();
    private static final CloudPlayerMotionLedger PEERS = new CloudPlayerMotionLedger();
    private static final Map<UUID, String> MODELS = new ConcurrentHashMap<>();
    private static final Map<UUID, Applied> APPLIED = new HashMap<>();
    private static String ownModel;
    private static final class Applied {
        final PlayerCapability cap;
        final String model;
        String action;
        // null means no full snapshot has been applied to this model/capability yet.
        Map<String, Float> roaming;
        final Set<String> expressions = new LinkedHashSet<>();
        Applied(PlayerCapability cap) { this.cap = cap; this.model = cap.getModelId(); }
    }
    private CloudPlayerMotionSync() {}
    private static boolean local(AnimatableEntity<?> entity) {
        return entity instanceof CustomPlayerEntity custom && custom.isLocalPlayerModel()
                && entity.getEntity() == Minecraft.getInstance().player;
    }
    private static synchronized void ensureModel(String model) {
        if (!Objects.equals(ownModel, model)) { ownModel = model; OWN.clear(); }
    }
    public static CloudPlayerMotion snapshot(String model) { ensureModel(model); return OWN.snapshot(); }
    private static void changeOwn(Runnable change) {
        try { change.run(); }
        catch (IllegalArgumentException unsupported) { YesSteveModel.LOGGER.debug("[SM][CloudMotion] value exceeds protocol limits: {}", unsupported.getMessage()); }
    }
    public static void play(AnimatableEntity<?> entity, String key) {
        if (!(entity instanceof CustomPlayerEntity custom)) return;
        custom.requestModelSwitch(key);
        if (local(entity)) { ensureModel(custom.getModelId()); changeOwn(() -> OWN.play(custom.isModelSwitching() ? custom.getSelectedModelId() : "", System.currentTimeMillis())); }
    }
    public static void stop(AnimatableEntity<?> entity) {
        if (!(entity instanceof CustomPlayerEntity custom)) return;
        custom.clearModelSwitch();
        if (local(entity)) { ensureModel(custom.getModelId()); changeOwn(() -> OWN.stop(System.currentTimeMillis())); }
    }
    public static void roaming(PlayerCapability cap, Map<String, Float> changes) {
        if (!local(cap)) return; ensureModel(cap.getModelId()); changeOwn(() -> OWN.roaming(changes));
    }
    public static void expression(AnimatableEntity<?> entity, String expression, List<Float> values) {
        if (!local(entity)) return;
        ensureModel(((CustomPlayerEntity) entity).getModelId()); changeOwn(() -> OWN.expression(expression, values, System.currentTimeMillis()));
    }
    public static void controller(AnimatableEntity<?> entity, String name, String state, long started, Map<String, Float> variables) {
        if (!local(entity) || !CloudAssetIdentity.isRuntimeModelId(((CustomPlayerEntity) entity).getModelId())) return;
        ensureModel(((CustomPlayerEntity) entity).getModelId());
        try { OWN.controller(name, state, started, variables); }
        catch (IllegalArgumentException tooLarge) { YesSteveModel.LOGGER.debug("[SM][CloudMotion] controller exceeds protocol limits: {}", name); }
    }
    /** Advance the owner's body even when the first-person camera never renders it. */
    public static void tickOwner() {
        Minecraft client = Minecraft.getInstance();
        var runtime = CloudClientRuntime.state();
        if (PrivacyMode.isActive() || client.player == null || runtime == null || runtime.instanceInfo() == null
                || !runtime.instanceInfo().playerMotionSupported() || !client.options.getCameraType().isFirstPerson()) return;
        PlayerCapability.get(client.player).ifPresent(cap -> {
            if (!cap.isModelReady() || cap.isForceDisabled() || !CloudAssetIdentity.isRuntimeModelId(cap.getModelId())) return;
            cap.advanceCloudMotion();
        });
    }
    public static CloudPlayerMotion motion(AnimatableEntity<?> entity) {
        if (PrivacyMode.isActive() || !(entity instanceof CustomPlayerEntity custom)) return null;
        if (local(entity)) return snapshot(custom.getModelId());
        UUID uuid = entity.getEntity().getUUID();
        return custom.getModelId().equals(MODELS.get(uuid)) ? PEERS.get(uuid) : null;
    }
    public static boolean isOwner(AnimatableEntity<?> entity) { return local(entity); }
    public static boolean receive(Player player, CloudPlayerSelection selection) {
        UUID uuid = player.getUUID(); String model = selection.runtimeModelId();
        if (selection.appearanceRevision() > 0 && !PEERS.receive(uuid, selection.appearanceRevision(), selection.motion())) return false;
        if (selection.appearanceRevision() <= 0) PEERS.remove(uuid);
        if (!model.equals(MODELS.get(uuid))) APPLIED.remove(uuid);
        MODELS.put(uuid, model);
        return true;
    }
    public static void apply(Player player) {
        if (PrivacyMode.isActive()) return;
        PlayerCapability.get(player).ifPresent(cap -> {
            if (cap.isForceDisabled() || !cap.isModelReady() || !cap.getModelId().equals(MODELS.get(player.getUUID()))) return;
            cap.awaitAsyncResult();
            CloudPlayerMotion motion = PEERS.get(player.getUUID());
            Applied applied = APPLIED.get(player.getUUID());
            if (applied == null || applied.cap != cap || !applied.model.equals(cap.getModelId())) {
                applied = new Applied(cap); APPLIED.put(player.getUUID(), applied);
            }
            if (motion == null) {
                if (applied.action != null) cap.clearModelSwitch();
                if (applied.roaming == null || !applied.roaming.isEmpty()) cap.updateMolangVars(cap.getModelAssembly().getModelData().getHashId(), new Int2FloatOpenHashMap());
                applied.action = null; applied.roaming = Map.of(); return;
            }
            if (!motion.roaming().equals(applied.roaming)) {
                Int2FloatOpenHashMap vars = new Int2FloatOpenHashMap();
                motion.roaming().forEach((name, value) -> vars.put(StringPool.computeIfAbsent(name), value.floatValue()));
                cap.updateMolangVars(cap.getModelAssembly().getModelData().getHashId(), vars);
                applied.roaming = motion.roaming();
            }
            if (!motion.eventId().equals(applied.action)) {
                if (motion.animationKey().isEmpty()) cap.clearModelSwitch(); else cap.requestModelSwitch(motion.animationKey());
                applied.action = motion.eventId();
            }
            long now = System.currentTimeMillis();
            for (CloudPlayerMotion.Expression event : motion.expressions()) {
                if (!applied.expressions.add(event.eventId())) continue;
                if (now - event.startedAtUnixMs() > 30000 || event.startedAtUnixMs() - now > 60000) continue;
                try {
                    if (event.expression().isEmpty()) cap.executeAnimationExpression(new it.unimi.dsi.fastutil.floats.FloatArrayList(event.values()));
                    else cap.executeExpression(GeckoLibCache.parseSimpleExpression(event.expression()), true, false, null);
                } catch (Exception failure) { YesSteveModel.LOGGER.warn("[SM][CloudMotion] expression ignored: {}", failure.toString()); }
            }
            while (applied.expressions.size() > 128) applied.expressions.remove(applied.expressions.iterator().next());
        });
    }
    public static void remove(UUID uuid) { PEERS.remove(uuid); MODELS.remove(uuid); APPLIED.remove(uuid); }
    public static void clearPeers() { PEERS.clear(); MODELS.clear(); APPLIED.clear(); }
    public static synchronized void reset() { clearPeers(); OWN.clear(); ownModel = null; }
    public static float elapsedTicks(long started) { return Math.max(0, (System.currentTimeMillis() - started) / 50f); }
}
