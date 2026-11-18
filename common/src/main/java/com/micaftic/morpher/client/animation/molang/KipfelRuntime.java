package com.micaftic.morpher.client.animation.molang;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostAnimatable;
import com.micaftic.morpher.client.render.ModelBoneView;
import com.micaftic.morpher.client.render.ModelScenePose;
import com.micaftic.morpher.resource.bundle.KipfelCompatibility;
import dev.spm.core.api.BonePose;
import dev.spm.core.api.CancellationToken;
import dev.spm.core.api.Skeleton;
import dev.spm.core.scene.MolangContext;
import dev.spm.core.scene.MolangValue;
import dev.spm.core.scene.PoseAnimationLibrary;
import dev.spm.core.scene.ScriptBudget;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.stb.STBPerlin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Single writer owned by {@code HostAnimationProcessor}. Smooths Kipfel attachment physics onto {@link ModelScenePose}. */
public final class KipfelRuntime {
    private record Key(String animation, String bone) { }
    private record Rotation(float x, float y, float z, float w) {
        Rotation(Quaternionf q) { this(q.x, q.y, q.z, q.w); }
        Quaternionf quaternion() { return new Quaternionf(x, y, z, w); }
    }

    private final KipfelSimulation simulation;
    private Map<Key, Rotation> previous = Map.of(), current = Map.of(), recoveryFrom = Map.of();
    private double recoveryTime = Double.NEGATIVE_INFINITY;
    private double renderTime;
    private net.minecraft.world.phys.Vec3 lastPosition;
    private final HostAnimatable<?> owner;
    private final PoseAnimationLibrary library;
    private final Skeleton skeleton;
    private final BonePose bindPose;
    private final Map<String, Integer> boneIndexByName;
    private final Map<String, Double> lastPublishedVars = new HashMap<>();

    public KipfelRuntime(HostAnimatable<?> owner, PoseAnimationLibrary library, Skeleton skeleton, BonePose bindPose) {
        this.owner = owner;
        this.library = library;
        this.skeleton = skeleton;
        this.bindPose = bindPose;
        this.simulation = new KipfelSimulation(
                (seed, x, y) -> STBPerlin.stb_perlin_noise3_seed(x, y, 0, 0, 0, 0, seed),
                owner.entity.getUUID().hashCode());
        List<String> names = skeleton.names();
        Map<String, Integer> map = new HashMap<>(names.size() * 2);
        for (int i = 0; i < names.size(); i++) {
            map.put(names.get(i), i);
        }
        this.boneIndexByName = Map.copyOf(map);
    }

    public void update(HostActionEvent<?> event, ModelScenePose scenePose, MolangContext context) {
        Entity entity = owner.entity;
        double dx = entity.getX() - entity.xo, dz = entity.getZ() - entity.zo, dy = entity.getY() - entity.yo;
        double ground = 20 * Math.hypot(dx, dz);
        double yawRadians = Math.toRadians(entity.getYRot());
        float forward = ground > 1e-4 ? (float) ((-Math.sin(yawRadians) * dx + Math.cos(yawRadians) * dz) / Math.hypot(dx, dz)) : 1;
        MolangValue bvVal = context != null ? context.typedVariables().get("bv") : null;
        float bv = bvVal != null && Double.isFinite(bvVal.number()) ? (float) bvVal.number() : 0.0f;
        var input = new KipfelSimulation.Input(
                (float) ground,
                (float) (20 * dy),
                forward,
                event.getModelData().headPitch,
                20 * Mth.wrapDegrees(entity.getYRot() - entity.yRotO),
                bv);
        renderTime = Math.max(renderTime, ((double) event.getTickCount() + event.getPartialTick()) / 20.0);
        net.minecraft.world.phys.Vec3 position = entity.position();
        boolean teleport = lastPosition != null && position.distanceToSqr(lastPosition) > 64;
        lastPosition = position;
        if (teleport) {
            simulation.recoverOnNextAdvance();
        }
        simulation.advance(renderTime, t -> input, o -> capture(o, context));
        if (simulation.output() != null) {
            publishVariables(simulation.output(), this.lastPublishedVars);
            applyToScene(scenePose);
        }
    }

    private void capture(KipfelSimulation.Output output, @Nullable MolangContext baseContext) {
        Map<String, Double> vars = baseContext != null ? new HashMap<>(baseContext.variables()) : new HashMap<>();
        publishVariables(output, vars);
        publishVariables(output, this.lastPublishedVars);
        MolangContext evalCtx = new MolangContext(
                Map.of("life_time", renderTime, "anim_time", 0.0),
                vars,
                Map.of(),
                new ScriptBudget(500_000, 64, new CancellationToken()));

        Map<Key, Rotation> next = new HashMap<>();
        float[] bindRot = bindPose.rotations();
        for (String name : KipfelCompatibility.OUTPUTS) {
            if (!library.hasClip(name)) continue;
            var spec = owner.getAnimation(name);
            if (spec == null || !spec.isStableKipfelAttachments()) continue;
            PoseAnimationLibrary.ClipSummary summary = library.clipSummary(name);
            if (summary.rotationBones().isEmpty()) continue;
            BonePose sampled = library.sample(name, 0.0, evalCtx, bindPose);
            float[] rot = sampled.rotations();
            for (String boneName : summary.rotationBones()) {
                Integer idxObj = boneIndexByName.get(boneName);
                if (idxObj == null) continue;
                int base = idxObj * 3;
                float vx = rot[base] - bindRot[base];
                float vy = rot[base + 1] - bindRot[base + 1];
                float vz = rot[base + 2] - bindRot[base + 2];
                Key key = new Key(name, boneName);
                Rotation old = current.get(key);
                if (!Float.isFinite(vx) || !Float.isFinite(vy) || !Float.isFinite(vz)) {
                    if (old != null) next.put(key, old);
                    continue;
                }
                Quaternionf q = new Quaternionf().rotationZYX(vz + bindRot[base + 2], vy + bindRot[base + 1], vx + bindRot[base]);
                if (old != null && !output.recovery()) {
                    Quaternionf from = old.quaternion();
                    double angle = 2 * Math.acos(Math.min(1, Math.abs(from.dot(q))));
                    double budget = Math.toRadians(KipfelSimulation.MAX_ANGULAR_SPEED) / 60;
                    if (angle > budget) from.slerp(q, (float) (budget / angle), q);
                }
                next.put(key, new Rotation(q));
            }
        }
        if (output.recovery() && !current.isEmpty()) {
            recoveryFrom = current;
            recoveryTime = renderTime;
        }
        previous = current.isEmpty() || output.recovery() ? Map.copyOf(next) : current;
        current = Map.copyOf(next);
        if (Boolean.getBoolean("sparkle_morpher.physicsTrace") && (output.recovery() || output.stepId() % 60 == 0)) {
            YesSteveModel.LOGGER.info(
                    "[SM-PHYSICS] entity={} profile=kipfel-stable-v1 step={} t={} dt={} recovery={} target={} hair={} excitation={} delta={}",
                    owner.entity.getId(), output.stepId(), output.time(), output.recovery() ? 0 : SimulationClock.STEP,
                    output.recovery(), output.target(), output.hair(), output.deltaHair(), output.delta());
        }
    }

    private void applyToScene(ModelScenePose scenePose) {
        for (String name : KipfelCompatibility.OUTPUTS) {
            if (!library.hasClip(name)) continue;
            var spec = owner.getAnimation(name);
            if (spec == null || !spec.isStableKipfelAttachments()) continue;
            for (String boneName : library.clipSummary(name).rotationBones()) {
                ModelBoneView bone = scenePose.getBone(boneName);
                if (bone == null) continue;
                Vector3f initial = bone.getInitialRotation();
                Vector3f delta = sample(name, boneName, initial);
                if (delta != null) {
                    bone.setRotation(initial.x + delta.x, initial.y + delta.y, initial.z + delta.z);
                }
            }
        }
    }

    public @Nullable Vector3f sample(String animation, String bone, Vector3f initial) {
        Key key = new Key(animation, bone);
        Rotation now = current.get(key);
        if (now == null) return null;
        Rotation before = previous.getOrDefault(key, now);
        Quaternionf q = before.quaternion().slerp(now.quaternion(), simulation.interpolation());
        Rotation recovery = recoveryFrom.get(key);
        float blend = (float) Math.max(0, Math.min(1, (renderTime - recoveryTime) / .1));
        if (recovery != null && blend < 1) {
            recovery.quaternion().slerp(q, blend, q);
        }
        return q.getEulerAnglesZYX(new Vector3f()).sub(initial);
    }

    private static void publishVariables(KipfelSimulation.Output o, Map<String, Double> target) {
        target.put("hairso", (double) o.hair());
        target.put("hairso_", (double) o.hair());
        target.put("hairyawso", (double) o.yaw());
        target.put("hairpitchso", (double) o.pitch());
        target.put("deltahair", (double) o.deltaHair());
        target.put("deltahairso", (double) o.delta());
        target.put("fronthairnoise", (double) o.frontNoise());
        target.put("fronthairrotationamplitude", 5.0);
        target.put("tailnoiseresponse", (double) o.tailResponse());
        target.put("tailangle", (double) o.tailAngle());
        target.put("tailbaseamplitude", 20.0);
        target.put("tailphi", -20.0);
        target.put("lastframetime", o.time());
    }
}
