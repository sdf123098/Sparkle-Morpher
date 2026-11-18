package com.micaftic.morpher.client.animation.particle;

import com.micaftic.morpher.client.core.HostAnimationAssets;
import com.micaftic.morpher.client.core.HostAnimationAssets.ClipSpec;
import com.micaftic.morpher.client.render.ModelBone;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import dev.spm.core.api.BonePose;
import dev.spm.core.api.CancellationToken;
import dev.spm.core.api.ModelDocument;
import dev.spm.core.api.Skeleton;
import dev.spm.core.scene.MolangBindings;
import dev.spm.core.scene.PoseAnimationController;
import dev.spm.core.scene.PoseAnimationLibrary;
import dev.spm.core.scene.PoseControllerDefinition;
import dev.spm.core.scene.SceneInputs;
import dev.spm.core.scene.ScriptBudget;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AnimationParticleRuntimeTest {
    @Test
    void inactiveEventsDoNotEvaluateOrEmitEffectsWhenDisallowed() {
        var json = new com.google.gson.JsonObject();
        var particleEffects = new com.google.gson.JsonObject();
        var effectObj = new com.google.gson.JsonObject();
        effectObj.addProperty("effect", "minecraft:smoke");
        effectObj.addProperty("locator", "hand");
        effectObj.addProperty("pre_effect_script", "v.started=1;");
        particleEffects.add("0", effectObj);
        json.add("particle_effects", particleEffects);

        var raw = new RawYsmModel.RawAnimation();
        raw.name = "particle_clip";
        raw.length = 1.0f;
        raw.loopMode = 0;
        raw.timelineEvents.addAll(com.micaftic.morpher.resource.AnimationParticleEvents.read(json));

        Skeleton skeleton = new Skeleton(List.of("hand"), new int[]{-1}, new float[]{0f, 0f, 0f});
        BonePose bindPose = BonePose.identity(1);
        PoseAnimationLibrary library = HostAnimationAssets.compileLibrary(
                "test:particle",
                List.of(new ClipSpec("particle_clip", raw, List.of(), null, false, false)),
                skeleton,
                bindPose);
        var activeState = new ModelDocument.JsonObject(Map.of(
                "animations", new ModelDocument.JsonArray(List.of(new ModelDocument.JsonString("particle_clip")))));
        var idleState = new ModelDocument.JsonObject(Map.of(
                "transitions", new ModelDocument.JsonArray(List.of(
                        new ModelDocument.JsonObject(Map.of("active", new ModelDocument.JsonString("0")))))));
        var inactiveCtrlBody = new ModelDocument.JsonObject(Map.of(
                "initial_state", new ModelDocument.JsonString("idle"),
                "states", new ModelDocument.JsonObject(Map.of("idle", idleState, "active", activeState))));
        var inactiveCtrlDef = PoseControllerDefinition.decode(
                new ModelDocument.JsonObject(Map.of(
                        "animation_controllers", new ModelDocument.JsonObject(Map.of("main", inactiveCtrlBody)))),
                "main", library, new ScriptBudget(100_000, 32, new CancellationToken()));
        var inactiveCtrl = new PoseAnimationController(library, inactiveCtrlDef, bindPose, 4);
        SceneInputs allowed = new SceneInputs(Map.of(), MolangBindings.empty(), true, SceneInputs.SyncMode.NONE);
        var inactiveFrame = inactiveCtrl.evaluate(1, 0L, allowed, new ScriptBudget(100_000, 32, new CancellationToken()));
        assertTrue(inactiveFrame.effects().isEmpty());
        assertFalse(inactiveFrame.variables().containsKey("started"));

        var activeCtrlBody = new ModelDocument.JsonObject(Map.of(
                "initial_state", new ModelDocument.JsonString("default"),
                "states", new ModelDocument.JsonObject(Map.of("default", activeState))));
        var activeCtrlDef = PoseControllerDefinition.decode(
                new ModelDocument.JsonObject(Map.of(
                        "animation_controllers", new ModelDocument.JsonObject(Map.of("main", activeCtrlBody)))),
                "main", library, new ScriptBudget(100_000, 32, new CancellationToken()));
        var activeCtrl = new PoseAnimationController(library, activeCtrlDef, bindPose, 4);
        SceneInputs disallowed = new SceneInputs(Map.of(), MolangBindings.empty(), false, SceneInputs.SyncMode.NONE);
        var disallowedFrame = activeCtrl.evaluate(1, 0L, disallowed, new ScriptBudget(100_000, 32, new CancellationToken()));
        assertTrue(disallowedFrame.effects().isEmpty());
    }

    @Test
    void repeatedPassesCannotEmitTheSameSampleTwice() {
        var queue = new AnimationParticleQueue();
        var count = new AtomicInteger();
        long key = AnimationParticleQueue.sampleKey(10, .5f);
        assertTrue(queue.enqueue(key, "hand", offset -> count.incrementAndGet()));
        queue.drain(key, name -> new Vector3f());
        queue.drain(key, name -> new Vector3f());
        assertFalse(queue.enqueue(key, "hand", offset -> count.incrementAndGet()));
        assertEquals(1, count.get());
    }

    @Test
    void staleSamplesAreDiscardedAndPendingEffectsAreBounded() {
        var queue = new AnimationParticleQueue();
        var count = new AtomicInteger();
        for (int i = 0; i < 64; i++) assertTrue(queue.enqueue(1, "hand", offset -> count.incrementAndGet()));
        assertFalse(queue.enqueue(1, "hand", offset -> count.incrementAndGet()));
        queue.drain(2, name -> new Vector3f());
        assertEquals(0, count.get());
        assertTrue(queue.enqueue(2, "hand", offset -> count.incrementAndGet()));
        queue.drain(2, name -> new Vector3f());
        assertEquals(1, count.get());
    }

    @Test
    void locatorUsesTheCurrentParentPoseAndTheActualRenderRoot() {
        var root = new ModelBone("root", false, false, false, 0, 0, 0, 0, 0, 0);
        var hand = new ModelBone("hand", false, false, false, 16, 0, 0, 0, 0, 0);
        hand.parentIdx = 0;
        var parameters = new float[24];
        for (int index : new int[]{6, 7, 8, 18, 19, 20}) parameters[index] = 1;
        parameters[2] = (float) Math.PI / 2;
        parameters[3] = -16;
        parameters[6] = parameters[7] = parameters[8] = 2;
        var renderRoot = new Matrix4f().translation(3, 4, 5).scale(2, 3, 4);
        var position = AnimationParticleLocator.resolve(List.of(root, hand), parameters, "hand", renderRoot);
        assertNotNull(position);
        assertEquals(5f, position.x, 1e-5f);
        assertEquals(10f, position.y, 1e-5f);
        assertEquals(5f, position.z, 1e-5f);
        assertNull(AnimationParticleLocator.resolve(List.of(root, hand), parameters, "absent", renderRoot));
    }
}
