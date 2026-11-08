package com.micaftic.morpher.client.core;

import dev.spm.core.scene.MolangBindings;
import dev.spm.core.scene.MolangValue;
import dev.spm.core.scene.PoseFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.util.List;

/** Actual opt-in consumer of independent Core; no link to the old scene/runtime path. */
public final class MinecraftCoreSceneHost {
    private static final Logger LOGGER = LoggerFactory.getLogger("SPM/CoreHost");
    private static final LaneState WORLD = new LaneState(CoreSceneOwner.Lane.WORLD);
    private static final LaneState PREVIEW = new LaneState(CoreSceneOwner.Lane.PREVIEW);
    private static Object levelIdentity;
    private static java.util.UUID entityIdentity;
    private static String lastPath = "";
    private MinecraftCoreSceneHost() { }

    /** Both client tick and pre-frame invoke this driver; CoreSceneOwner caches one logical slot. */
    public static synchronized void drive() {
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()) throw new IllegalStateException("Core Host driver requires client thread");
        String path = System.getProperty("spm.core.scene.model", "").strip();
        var entity = client.player;
        var identity = entity == null ? null : entity.getUUID();
        if (levelIdentity != client.level || !java.util.Objects.equals(entityIdentity, identity) || !lastPath.equals(path)) {
            invalidate("world/entity/model change");
            levelIdentity = client.level; entityIdentity = identity; lastPath = path;
        }
        if (path.isEmpty() || client.level == null || entity == null) return;
        long now = System.nanoTime();
        advance(WORLD, path, client, entity, now, client.isPaused());
        if (Boolean.getBoolean("spm.core.scene.preview") && client.screen != null) {
            advance(PREVIEW, path, client, entity, now, false);
        } else PREVIEW.retire();
    }

    private static void advance(LaneState state, String path, Minecraft client, LivingEntity entity, long now, boolean paused) {
        if (state.failure != null) return;
        try {
            if (state.owner == null) {
                state.owner = CoreSceneOwner.load(Path.of(path), state.lane);
                state.dispatcher = new MinecraftHostEffectDispatcher();
                LOGGER.info("Core scene loaded lane={} model={} clip={}", state.lane, path, state.owner.clip());
            }
            var owner = state.owner;
            var requests = new MinecraftHostEffectDispatcher.Requests[1];
            var frame = owner.advance(now, paused,
                    () -> MinecraftHostSnapshotSampler.sample(client, entity, state.lane == CoreSceneOwner.Lane.PREVIEW,
                            state.motion, now, paused),
                    (call, frozen) -> {
                        if (requests[0] == null) requests[0] = state.dispatcher.requests();
                        return resolve(call, frozen, requests[0], entity);
                    });
            var ticket = owner.ticket();
            state.dispatcher.validateBatch(frame.effects());
            for (var effect : owner.claim(ticket)) {
                long before = state.dispatcher.diagnostics().committedOccurrences();
                state.dispatcher.commit(client, entity, effect, () -> state.owner == owner && owner.current(ticket, effect)
                        && client.level == entity.level() && client.player == entity);
                var diagnostics = state.dispatcher.diagnostics();
                if (Boolean.getBoolean("spm.core.scene.diagnostics") && diagnostics.committedOccurrences() > before) {
                    LOGGER.info("Core effect lane={} owner={} generation={} frame={} tick={} sequence={} resource={} soundQueues={} particlesCreated={}",
                            state.lane, effect.instanceId(), effect.generation(), frame.frameId(), effect.step(), effect.sequence(),
                            effect.resource(), diagnostics.soundQueues(), diagnostics.particlesCreated());
                }
            }
        } catch (Exception failure) {
            state.failure = failure.getClass().getSimpleName() + ": " + failure.getMessage();
            state.retireOwner();
            LOGGER.error("Core scene rejected lane={} model={} (no fallback): {}", state.lane, path, state.failure, failure);
        }
    }

    private static MolangValue resolve(MolangBindings.Call call, dev.spm.core.scene.SceneInputs snapshot,
                                       MinecraftHostEffectDispatcher.Requests requests, LivingEntity entity) {
        var args = call.arguments();
        return switch (call.name()) {
            case "ysm.play_sound", "ysm.stop_sound", "ysm.stop_all_sounds", "ysm.particle", "ysm.abs_particle" -> requests.sample(call);
            case "ysm.relative_block_name" -> {
                if (args.size() != 3) throw new IllegalArgumentException("relative_block_name expects x,y,z");
                var position = entity.blockPosition().offset((int) args.get(0).number(), (int) args.get(1).number(), (int) args.get(2).number());
                yield MolangValue.text(BuiltInRegistries.BLOCK.getKey(entity.level().getBlockState(position).getBlock()).toString());
            }
            case "query.get_equipped_item_name" -> {
                if (args.size() > 1) throw new IllegalArgumentException("get_equipped_item_name expects at most one hand index");
                String hand = args.isEmpty() || args.get(0).number() == 0 ? "mainhand" : "offhand";
                var value = snapshot.bindings().values().get("host.equipment." + hand);
                if (!(value instanceof MolangValue.TextValue text)) throw new IllegalStateException("Missing sampled hand item");
                String name = text.value();
                yield MolangValue.text(name.startsWith("minecraft:") ? name.substring(10) : name);
            }
            default -> throw new IllegalArgumentException("Unregistered Core Host binding: " + call.name() + " arguments=" + args);
        };
    }

    /** Reload/disconnect invalidates tickets synchronously, before any deferred commit can run. */
    public static synchronized void invalidate(String reason) {
        WORLD.retire(); PREVIEW.retire();
        LOGGER.debug("Core scene generations retired: {}", reason);
    }

    /** Read-only presentation: no simulation, queries or game effects are evaluated here. */
    public static synchronized void renderDiagnostics(GuiGraphics graphics) {
        if (!Boolean.getBoolean("spm.core.scene.diagnostics")) return;
        var client = Minecraft.getInstance();
        graphics.drawString(client.font, diagnostic(WORLD), 6, 6, WORLD.failure == null ? 0xFF88FF88 : 0xFFFF7777);
        if (Boolean.getBoolean("spm.core.scene.preview")) graphics.drawString(client.font, diagnostic(PREVIEW), 6, 18, 0xFF88DDFF);
    }
    private static String diagnostic(LaneState state) {
        if (state.failure != null) return "Core " + state.lane + " REJECTED " + state.failure;
        PoseFrame frame = state.owner == null ? null : state.owner.snapshot();
        var stats = state.dispatcher == null ? null : state.dispatcher.diagnostics();
        String effects = stats == null ? "" : " effects=" + stats.committedOccurrences()
                + " soundQueue=" + stats.soundQueues() + " particles=" + stats.particlesCreated();
        return frame == null ? "Core " + state.lane + " awaiting explicit model/world"
                : "Core " + state.lane + " clip=" + state.owner.clip() + " frame=" + frame.frameId()
                + " 60HzStep=" + frame.step() + " generation=" + frame.generation() + " bones=" + frame.pose().boneCount() + effects;
    }
    public static synchronized PoseFrame worldSnapshot() { return WORLD.owner == null ? null : WORLD.owner.snapshot(); }
    public static synchronized PoseFrame previewSnapshot() { return PREVIEW.owner == null ? null : PREVIEW.owner.snapshot(); }

    private static final class LaneState {
        final CoreSceneOwner.Lane lane;
        CoreSceneOwner owner;
        MinecraftHostEffectDispatcher dispatcher;
        CoreMotionSnapshot motion = new CoreMotionSnapshot();
        String failure;
        LaneState(CoreSceneOwner.Lane lane) { this.lane = lane; }
        void retireOwner() {
            motion = new CoreMotionSnapshot();
            if (owner != null) { owner.close(); owner = null; }
            var retired = dispatcher; dispatcher = null;
            if (retired != null) {
                var client = Minecraft.getInstance();
                if (client.isSameThread()) retired.close(); else client.execute(retired::close);
            }
        }
        void retire() { retireOwner(); failure = null; }
    }
}
