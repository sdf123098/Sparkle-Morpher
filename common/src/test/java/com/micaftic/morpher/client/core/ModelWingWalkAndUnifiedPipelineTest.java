package com.micaftic.morpher.client.core;

import com.micaftic.morpher.client.render.BuiltModel;
import com.micaftic.morpher.client.render.ModelBone;
import com.micaftic.morpher.resource.YSMBinaryDeserializer;
import com.micaftic.morpher.resource.YSMFolderDeserializer;
import com.micaftic.morpher.resource.bundle.ClientModelBundleAssembler;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import dev.spm.core.api.BonePose;
import dev.spm.core.api.CancellationToken;
import dev.spm.core.api.Skeleton;
import dev.spm.core.scene.CodedAnimationCollection;
import dev.spm.core.scene.CodedControllerCommand;
import dev.spm.core.scene.CodedControllerDefinition;
import dev.spm.core.scene.CodedControllerSnapshot;
import dev.spm.core.scene.CodedLoopMode;
import dev.spm.core.scene.CodedPoseFrame;
import dev.spm.core.scene.MolangBindings;
import dev.spm.core.scene.MolangException;
import dev.spm.core.scene.MolangValue;
import dev.spm.core.scene.PoseAnimationLibrary;
import dev.spm.core.scene.PoseControllerDefinition;
import dev.spm.core.scene.PoseFrame;
import dev.spm.core.scene.SceneInputs;
import dev.spm.core.scene.ScriptBudget;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression suite covering universal model/animation bugs recovered from old-kernel experience:
 * <ol>
 *   <li>{@link com.micaftic.morpher.resource.bundle.GeometryBaker} backfilling {@code ModelBone.parentIdx},
 *       {@code parentName}, and topological {@code partMask} onto {@link BuiltModel#topLevelBones()};</li>
 *   <li>{@link HostEntityFrameState} guarding against 1-frame tick-boundary micro-delta dips that previously
 *       flipped {@code ctrl.walk -> ctrl.idle} mid-stride and caused walking twitches;</li>
 *   <li>{@link HostAnimationProcessor#compositeOrderedJsonPoses} preserving bind-equal driven channels
 *       ({@code Wing.scale = [1,1,1]}, {@code LeftLeg.pos = [0,0,0]}) without dropping them;</li>
 *   <li>Real-model end-to-end simulation on Furina ({@code vn_furina_v3.13_cn_voice.ysm}), Mingfeng, and Rossi
 *       when running on {@code spm-core 1.3.1+} native hybrid controllers (creative flight wings, elytra flight wings,
 *       forward/backward walking stability, shared {@code v.*} variables, and multi-bone {@code ysm.second_order}).</li>
 * </ol>
 */
class ModelWingWalkAndUnifiedPipelineTest {

    @AfterAll
    static void reclaimRealCorpusHeap() {
        // Real .ysm archives allocate tens of MB of transient ASTs; trigger GC so subsequent tests
        // checking Runtime free-heap budgets (e.g. TextureDecoder) observe accurate headroom.
        System.gc();
    }

    private static Path resolveTestModel(String fileName) {
        String envDir = System.getenv("SM_CORPUS_DIR");
        if (envDir != null && !envDir.isBlank()) {
            Path p = Path.of(envDir, fileName);
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return Path.of("D:/SparkleMorpher/TestModel", fileName);
    }

    private static RawYsmModel loadYsm(Path path) throws Exception {
        byte[] data = Files.readAllBytes(path);
        RawYsmModel raw;
        int version = com.micaftic.morpher.legacy.compat.LegacyCompatModelFormat.detectCryptoVersion(data);
        if (version == 1 || version == 2) {
            try (YSMFolderDeserializer d = new YSMFolderDeserializer(
                    com.micaftic.morpher.legacy.compat.LegacyCompatModelFormat.read(data))) {
                raw = d.deserialize();
            }
        } else {
            byte[] decrypted = com.micaftic.morpher.core.security.YsmCrypt.decryptYsmFile(data);
            try (YSMBinaryDeserializer d = new YSMBinaryDeserializer(decrypted)) {
                raw = d.deserializeKeepOpen();
            }
        }
        // Skeleton & animation tests do not need texture pixel decoding inside the Gradle test worker heap.
        raw.mainEntity.textures.clear();
        raw.vehicles.values().forEach(v -> v.textures.clear());
        raw.projectiles.values().forEach(p -> p.textures.clear());
        raw.soundFiles.clear();
        return raw;
    }

    private static ScriptBudget budget() {
        return new ScriptBudget(4_000_000, 128, new CancellationToken());
    }

    private static CodedControllerDefinition hybridDef(
            String name, double transitionTicks, boolean additive, PoseControllerDefinition json) {
        return CodedControllerDefinition.hostDriven(name, transitionTicks, additive, json);
    }

    @Test
    void groundSpeedGuardPreventsMidStrideWalkDropWhilePreservingInstantStopAndRemoteSuppression() {
        // Mid-stride 1-frame lerp micro-delta (0.01 blocks/s) while player physically moved 0.22 blocks/tick (4.4 b/s):
        // must NOT drop below WALK_DROP_GUARD_SPEED (0.05) and flip ctrl.walk -> ctrl.idle!
        float guardedByTick = HostEntityFrameState.resolveGroundSpeed(0.01f, 4.4f, 0.0f, 0.0f, 0.0f, false);
        assertTrue(guardedByTick >= 4.0f, "Expected tick speed guard to hold walking speed, got " + guardedByTick);

        float guardedByVel = HostEntityFrameState.resolveGroundSpeed(0.005f, 0.0f, 3.8f, 0.0f, 0.0f, false);
        assertTrue(guardedByVel >= 3.5f, "Expected velocity speed guard to hold walking speed, got " + guardedByVel);

        // When player truly stops (tracker, tick, velocity, limb all 0), ground speed immediately drops to 0.
        float stopped = HostEntityFrameState.resolveGroundSpeed(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false);
        assertEquals(0.0f, stopped, 1e-6f);

        // Remote player suppression must never fabricate synthetic walk speed from stale tick/velocity/limb values.
        float remoteIdle = HostEntityFrameState.resolveGroundSpeed(0.0f, 4.4f, 3.8f, 0.9f, 0.9f, true);
        assertEquals(0.0f, remoteIdle, 1e-6f);
        float remoteMoving = HostEntityFrameState.resolveGroundSpeed(2.5f, 0.0f, 0.0f, 0.0f, 0.0f, true);
        assertEquals(2.5f, remoteMoving, 1e-6f);

        // Tiny sub-millimeter tracker jitter (< 0.001 blocks) while physically advancing forward (> 0.01 blocks)
        // must fall back to tickDelta so ysm.input_vertical does not briefly invert sign.
        Vec3 resolved = HostEntityFrameState.resolvePositionDelta(
                new Vec3(0.0, 0.0, -0.0002),
                new Vec3(0.0, 0.0, 0.21),
                Vec3.ZERO,
                0.5f,
                false);
        assertEquals(0.21, resolved.z, 1e-6);
    }

    @Test
    void compositeOrderedJsonPosesPreservesBindEqualDrivenChannelsAndRespectsHigherCodedSlots() {
        // Bone 0 = Wing (pre_parallel2 coded slot 0 sets scale=[0,0,0]; armor_chest JSON slot 5 sets scale=[1,1,1] == bind!)
        // Bone 1 = LeftLeg (pre_parallel coded slot 0 sets pos=[0,1,0]; walk JSON slot 2 sets pos=[0,0,0] == bind!)
        // Bone 2 = RightArm (coded hold slot 4 sets rot=[1.0,0,0]; walk JSON slot 2 must NOT overwrite slot 4!)
        BonePose bind = BonePose.identity(3);
        BonePose coded = new BonePose(
                new float[]{0f, 0f, 0f, 0f, 0f, 0f, 1.0f, 0f, 0f},
                new float[]{0f, 0f, 0f, 0f, 1.0f, 0f, 0f, 0f, 0f},
                new float[]{0f, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f},
                bind.ownHidden(), bind.childrenHidden(), bind.glow(), bind.colorsRgba());

        // Walk JSON layer (slot 2) explicitly drives LeftLeg pos=[0,0,0] (equals bind!) and RightArm rot=[0.2,0,0].
        BonePose walkPose = new BonePose(
                new float[]{0f, 0f, 0f, 0f, 0f, 0f, 0.2f, 0f, 0f},
                new float[]{0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f},
                new float[]{1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f},
                bind.ownHidden(), bind.childrenHidden(), bind.glow(), bind.colorsRgba());
        // Armor chest JSON layer (slot 5) explicitly drives Wing scale=[1,1,1] (equals bind!).
        BonePose armorPose = BonePose.identity(3);

        PoseFrame walkFrame = new PoseFrame(1, 0, 1, 1, 0.016, 0.0, 0, "walk_fwd", 0.016, false, walkPose, Map.of(), List.of());
        PoseFrame armorFrame = new PoseFrame(2, 0, 1, 1, 0.016, 0.0, 0, "unfold", 0.016, false, armorPose, Map.of(), List.of());

        List<HostAnimationProcessor.OrderedJsonLayer> layers = List.of(
                new HostAnimationProcessor.OrderedJsonLayer(2, "player.pre_parallel_4", walkFrame, new int[]{2}, new int[]{1}, new int[0]),
                new HostAnimationProcessor.OrderedJsonLayer(5, "player.armor_chest", armorFrame, new int[0], new int[0], new int[]{0}));

        int[] maxRot = new int[]{-1, -1, 4};
        int[] maxPos = new int[]{-1, 0, -1};
        int[] maxScale = new int[]{0, -1, -1};

        BonePose out = HostAnimationProcessor.compositeOrderedJsonPoses(coded, bind, layers, maxRot, maxPos, maxScale, 4);
        // Wing scale=[1,1,1] from slot 5 MUST override coded slot 0's [0,0,0], NOT be dropped by bind-equality!
        assertEquals(1.0f, out.scales()[0], 1e-6f);
        assertEquals(1.0f, out.scales()[1], 1e-6f);
        assertEquals(1.0f, out.scales()[2], 1e-6f);
        // LeftLeg pos=[0,0,0] from slot 2 MUST override coded slot 0's [0,1,0]!
        assertEquals(0.0f, out.translations()[4], 1e-6f);
        // RightArm rot from coded slot 4 MUST NOT be overwritten by lower JSON slot 2!
        assertEquals(1.0f, out.rotations()[6], 1e-6f);
    }

    @Test
    void geometryBakerPopulatesParentIndicesAndPartMasksOnRealFurinaModel() throws Exception {
        Path furinaPath = resolveTestModel("vn_furina_v3.13_cn_voice.ysm");
        Assumptions.assumeTrue(Files.isRegularFile(furinaPath), "TestModel corpus not present");

        RawYsmModel raw = loadYsm(furinaPath);
        var bundle = ClientModelBundleAssembler.buildParsedBundle(raw, furinaPath.getFileName().toString());
        BuiltModel built = bundle.getMainModelData().getModels().getFirst();
        assertNotNull(built);

        Map<String, Integer> idxByName = new HashMap<>();
        List<ModelBone> bones = built.topLevelBones();
        int nonRootCount = 0;
        for (int i = 0; i < bones.size(); i++) {
            ModelBone b = bones.get(i);
            idxByName.put(b.getName(), i);
            if (b.parentIdx >= 0) {
                nonRootCount++;
            }
        }
        assertTrue(nonRootCount > bones.size() / 2,
                "Expected majority of bones to have valid parentIdx >= 0, got " + nonRootCount + "/" + bones.size());

        // Verify Furina's Wing hierarchy is intact on ModelBone AND Skeleton!
        Integer wingIdx = idxByName.get("Wing");
        Integer leftWingIdx = idxByName.get("LeftWing");
        Integer rightWingIdx = idxByName.get("RightWing");
        assertNotNull(wingIdx);
        assertNotNull(leftWingIdx);
        assertNotNull(rightWingIdx);
        assertTrue(bones.get(wingIdx).parentIdx >= 0, "Wing must have a parent bone");
        assertEquals(wingIdx.intValue(), bones.get(leftWingIdx).parentIdx, "LeftWing parent must be Wing");
        assertEquals(wingIdx.intValue(), bones.get(rightWingIdx).parentIdx, "RightWing parent must be Wing");

        Skeleton skel = HostAnimationAssets.buildSkeleton(built);
        assertEquals(wingIdx.intValue(), skel.parents()[leftWingIdx]);
        assertEquals(wingIdx.intValue(), skel.parents()[rightWingIdx]);
    }

    @Test
    void realFurinaWingsUnfoldInCreativeAndElytraFlightAndWalkingIsStable() throws Throwable {
        assertTrue(HostAnimationProcessor.supportsNativeHybridControllers(),
                "spm-core 1.3.1+ native hybrid CodedAnimationCollection must be active");
        Path furinaPath = resolveTestModel("vn_furina_v3.13_cn_voice.ysm");
        Assumptions.assumeTrue(Files.isRegularFile(furinaPath), "TestModel corpus not present");

        RawYsmModel raw = loadYsm(furinaPath);
        var bundle = ClientModelBundleAssembler.buildParsedBundle(raw, furinaPath.getFileName().toString());
        var main = bundle.getMainModelData();
        BuiltModel built = main.getModels().getFirst();
        Skeleton skel = HostAnimationAssets.buildSkeleton(built);
        BonePose bind = HostAnimationAssets.buildBindPose(built);

        Map<String, HostAnimationAssets.ClipSpec> allClips = new LinkedHashMap<>();
        for (var ae : main.getAnimations().entrySet()) {
            for (var ce : ae.getValue().getAnimations().entrySet()) {
                allClips.putIfAbsent(ce.getKey(), ce.getValue());
            }
        }
        PoseAnimationLibrary lib = HostAnimationAssets.compileLibrary("furina", allClips, skel, bind);

        Map<String, HostAnimationAssets.ControllerSpec> ctrlSpecs = new LinkedHashMap<>();
        for (var acSet : main.getAnimationControllers()) {
            ctrlSpecs.putAll(acSet.getAnimationControllers());
        }
        PoseControllerDefinition walkJson = ctrlSpecs.get("player.pre_parallel_4").compile(lib);
        PoseControllerDefinition armorJson = ctrlSpecs.get("player.armor_chest").compile(lib);
        assertNotNull(walkJson);
        assertNotNull(armorJson);

        List<CodedControllerDefinition> defs = List.of(
                CodedControllerDefinition.hostDriven("pre_parallel_0", 0, false),
                CodedControllerDefinition.hostDriven("pre_parallel_2", 0, false),
                hybridDef("player.pre_parallel_4", 0, false, walkJson),
                CodedControllerDefinition.hostDriven("player.main", 0, false),
                CodedControllerDefinition.hostDriven("parallel_0", 0, true),
                CodedControllerDefinition.hostDriven("parallel_1", 0, true),
                hybridDef("player.armor_chest", 0, false, armorJson));

        int wingIdx = skel.names().indexOf("Wing");
        int leftWingIdx = skel.names().indexOf("LeftWing");
        int rightWingIdx = skel.names().indexOf("RightWing");
        int leftLegIdx = skel.names().indexOf("LeftLeg");
        assertTrue(wingIdx >= 0 && leftWingIdx >= 0 && rightWingIdx >= 0 && leftLegIdx >= 0);

        Map<String, CodedControllerCommand> baseCmds = Map.of(
                "pre_parallel_0", CodedControllerCommand.play("pre_parallel0", CodedLoopMode.LOOP, 0, 1.0),
                "pre_parallel_2", CodedControllerCommand.play("pre_parallel2", CodedLoopMode.LOOP, 0, 1.0),
                "parallel_0", CodedControllerCommand.play("parallel0", CodedLoopMode.LOOP, 0, 1.0),
                "parallel_1", CodedControllerCommand.play("parallel1", CodedLoopMode.LOOP, 0, 1.0));

        // --- Scenario A: Creative Flight WITH Elytra worn (ctrl.fly = 1, ysm.has_elytra = 1, elytra_rot_z = -45) ---
        {
            CodedAnimationCollection collection = new CodedAnimationCollection(lib, bind, defs, 12, 101L);
            Map<String, CodedControllerCommand> flyCmds = new LinkedHashMap<>(baseCmds);
            flyCmds.put("player.main", CodedControllerCommand.play("fly", CodedLoopMode.LOOP, 0, 1.0));
            Map<MolangBindings.Call, MolangValue> calls = new HashMap<>();
            CodedPoseFrame last = null;
            for (int step = 0; step < 240; step++) { // 4.0 seconds (past 3.0s 鞘翅穿戴 into 鞘翅idle!)
                Map<String, MolangValue> bindings = buildFurinaBindings(true, true, false, false, -45.0, 0.0, 0.0);
                last = evaluateWithCallRetry(collection, step, Math.round(step * (1_000_000_000.0 / 60.0)),
                        Map.of("ground_speed", 8.0, "vertical_speed", 0.0, "yaw_speed", 0.0, "is_sneaking", 0.0, "is_on_ground", 0.0),
                        bindings, calls, flyCmds);
                assertAllFinite(last.pose());
                // After 鞘翅穿戴 pops open (t=0.625s -> step 38, Catmull-Rom overshoots ~1.06 before settling at t=1.125s/step 68),
                // Wing MUST remain unfolded (>0.9 during pop-in, ==1.0 after settle and throughout 鞘翅idle)!
                if (step >= 45 && step < 70) {
                    assertTrue(last.pose().scales()[wingIdx * 3] > 0.9f,
                            "Creative flight Wing.scale.x collapsed during unfold at step " + step);
                } else if (step >= 70) {
                    assertEquals(1.0f, last.pose().scales()[wingIdx * 3], 0.02f,
                            "Creative flight Wing.scale.x collapsed at step " + step);
                    assertEquals(1.0f, last.pose().scales()[wingIdx * 3 + 1], 0.02f,
                            "Creative flight Wing.scale.y collapsed at step " + step);
                    assertEquals(1.0f, last.pose().scales()[wingIdx * 3 + 2], 0.02f,
                            "Creative flight Wing.scale.z collapsed at step " + step);
                }
            }
            assertNotNull(last);
            // Confirm player.armor_chest completed 鞘翅穿戴 -> 鞘翅idle and parallel1 keeps Wing unfolded!
            assertEquals("鞘翅idle", last.controllers().get(6).selectedClip());
            // At elytra_rot_z = -45: LeftWing/RightWing scale = 0.7 + (-45)*(-0.6/90) = 1.0, rotZ = 80 + (-45)*80/90 = +40 deg!
            assertEquals(1.0f, last.pose().scales()[leftWingIdx * 3], 0.05f);
            assertEquals((float) Math.toRadians(40.0), last.pose().rotations()[leftWingIdx * 3 + 2], 0.05f);
            assertEquals((float) Math.toRadians(-40.0), last.pose().rotations()[rightWingIdx * 3 + 2], 0.05f);
        }

        // --- Scenario B: Elytra Flight WITH Elytra (ctrl.elytra_fly = 1, ysm.has_elytra = 1, elytra_rot_z = -90) ---
        {
            CodedAnimationCollection collection = new CodedAnimationCollection(lib, bind, defs, 12, 202L);
            Map<String, CodedControllerCommand> elytraCmds = new LinkedHashMap<>(baseCmds);
            elytraCmds.put("player.main", CodedControllerCommand.play("elytra_fly", CodedLoopMode.LOOP, 0, 1.0));
            Map<MolangBindings.Call, MolangValue> calls = new HashMap<>();
            CodedPoseFrame last = null;
            for (int step = 0; step < 240; step++) {
                Map<String, MolangValue> bindings = buildFurinaBindings(true, false, true, false, -90.0, 0.0, 0.0);
                last = evaluateWithCallRetry(collection, step, Math.round(step * (1_000_000_000.0 / 60.0)),
                        Map.of("ground_speed", 20.0, "vertical_speed", -1.0, "yaw_speed", 0.0, "is_sneaking", 0.0, "is_on_ground", 0.0),
                        bindings, calls, elytraCmds);
                assertAllFinite(last.pose());
                if (step >= 45 && step < 70) {
                    assertTrue(last.pose().scales()[wingIdx * 3] > 0.9f,
                            "Elytra flight Wing.scale.x collapsed during unfold at step " + step);
                } else if (step >= 70) {
                    assertEquals(1.0f, last.pose().scales()[wingIdx * 3], 0.02f,
                            "Elytra flight Wing.scale.x collapsed at step " + step);
                }
            }
            assertNotNull(last);
            // At elytra_rot_z = -90: LeftWing/RightWing scale = 0.7 + (-90)*(-0.6/90) = 1.3, rotZ = 80 + (-90)*80/90 = 0!
            assertEquals(1.3f, last.pose().scales()[leftWingIdx * 3], 0.05f, "LeftWing should expand to 1.3x in elytra flight");
            assertEquals(0.0f, last.pose().rotations()[leftWingIdx * 3 + 2], 0.1f, "LeftWing rotZ should be unfolded (~0 rad)");
            assertEquals(0.0f, last.pose().rotations()[rightWingIdx * 3 + 2], 0.1f, "RightWing rotZ should be unfolded (~0 rad)");
        }

        // --- Scenario C: Walking Forward then Backward (shared v.input_vertical + zero drops/snaps) ---
        {
            CodedAnimationCollection collection = new CodedAnimationCollection(lib, bind, defs, 12, 303L);
            Map<String, CodedControllerCommand> walkCmds = new LinkedHashMap<>(baseCmds);
            walkCmds.put("player.main", CodedControllerCommand.play("walk", CodedLoopMode.LOOP, 0, 1.0));
            Map<MolangBindings.Call, MolangValue> calls = new HashMap<>();

            int fwdStateCount = 0;
            float prevLegY = Float.NaN;
            float maxLegStepDelta = 0.0f;
            for (int step = 0; step < 120; step++) {
                Map<String, MolangValue> bindings = buildFurinaBindings(false, false, false, true, 0.0, 1.0, 0.0);
                CodedPoseFrame frame = evaluateWithCallRetry(collection, step, Math.round(step * (1_000_000_000.0 / 60.0)),
                        Map.of("ground_speed", 4.3, "vertical_speed", 0.0, "yaw_speed", 0.0, "is_sneaking", 0.0, "is_on_ground", 1.0),
                        bindings, calls, walkCmds);
                assertAllFinite(frame.pose());
                CodedControllerSnapshot walkSnap = frame.controllers().get(2);
                if ("走路前进".equals(walkSnap.selectedClip())) {
                    fwdStateCount++;
                }
                float legY = frame.pose().translations()[leftLegIdx * 3 + 1];
                if (step >= 30 && Float.isFinite(prevLegY)) {
                    maxLegStepDelta = Math.max(maxLegStepDelta, Math.abs(legY - prevLegY));
                }
                prevLegY = legY;
            }
            assertTrue(fwdStateCount >= 110, "Expected stable 走路前进 during forward walk, got " + fwdStateCount + "/120");
            assertTrue(maxLegStepDelta < 0.05f, "LeftLeg Y snapped mid-stride! maxDelta=" + maxLegStepDelta);

            // Now reverse direction (backward walking: ysm.input_vertical = -1.0)
            int bwdStateCount = 0;
            CodedPoseFrame lastBwd = null;
            for (int step = 120; step < 240; step++) {
                Map<String, MolangValue> bindings = buildFurinaBindings(false, false, false, true, 0.0, -1.0, 0.0);
                lastBwd = evaluateWithCallRetry(collection, step, Math.round(step * (1_000_000_000.0 / 60.0)),
                        Map.of("ground_speed", 4.3, "vertical_speed", 0.0, "yaw_speed", 0.0, "is_sneaking", 0.0, "is_on_ground", 1.0),
                        bindings, calls, walkCmds);
                assertAllFinite(lastBwd.pose());
                if ("走路倒退".equals(lastBwd.controllers().get(2).selectedClip())) {
                    bwdStateCount++;
                }
            }
            assertNotNull(lastBwd);
            assertTrue(lastBwd.variables().getOrDefault("input_vertical", 0.0) < -0.5,
                    "Expected shared v.input_vertical < -0.5 during backward walk");
            assertTrue(bwdStateCount >= 60,
                    "Expected player.pre_parallel_4 to transition to 走路倒退 using shared v.input_vertical, got " + bwdStateCount);
        }
    }

    @Test
    void realMingfengAndRossiScaleOverlapAndMultiBonePhysicsStayFiniteAndConsistent() throws Throwable {
        assertTrue(HostAnimationProcessor.supportsNativeHybridControllers(),
                "spm-core 1.3.1+ native hybrid CodedAnimationCollection must be active");

        // 1. Mingfeng: verify pre_parallel2 + parallel2 scale overlap does not zero-lock clothing bones!
        Path mingfengPath = resolveTestModel("mingfeng.ysm");
        if (Files.isRegularFile(mingfengPath)) {
            RawYsmModel raw = loadYsm(mingfengPath);
            var main = ClientModelBundleAssembler.buildParsedBundle(raw, mingfengPath.getFileName().toString()).getMainModelData();
            BuiltModel built = main.getModels().getFirst();
            Skeleton skel = HostAnimationAssets.buildSkeleton(built);
            BonePose bind = HostAnimationAssets.buildBindPose(built);
            Map<String, HostAnimationAssets.ClipSpec> clips = new LinkedHashMap<>();
            main.getAnimations().values().forEach(set -> set.getAnimations().forEach(clips::putIfAbsent));
            PoseAnimationLibrary lib = HostAnimationAssets.compileLibrary("mingfeng", clips, skel, bind);
            assertNotNull(lib);

            List<CodedControllerDefinition> defs = new ArrayList<>();
            Map<String, CodedControllerCommand> cmds = new LinkedHashMap<>();
            if (lib.hasClip("pre_parallel7")) {
                defs.add(CodedControllerDefinition.hostDriven("pre_parallel_7", 0, false));
                cmds.put("pre_parallel_7", CodedControllerCommand.play("pre_parallel7", CodedLoopMode.LOOP, 0, 1.0));
            }
            if (lib.hasClip("idle")) {
                defs.add(CodedControllerDefinition.hostDriven("player.main", 0, false));
                cmds.put("player.main", CodedControllerCommand.play("idle", CodedLoopMode.LOOP, 0, 1.0));
            }
            CodedAnimationCollection coll = new CodedAnimationCollection(lib, bind, defs, 12, 404L);
            Map<MolangBindings.Call, MolangValue> calls = new HashMap<>();
            for (int step = 0; step < 30; step++) {
                Map<String, MolangValue> bindings = buildFurinaBindings(false, false, false, false, 0.0, 0.0, 0.0);
                CodedPoseFrame frame = evaluateWithCallRetry(coll, step, Math.round(step * (1_000_000_000.0 / 60.0)),
                        Map.of("ground_speed", 0.0, "vertical_speed", 0.0, "yaw_speed", 0.0, "is_on_ground", 1.0, "is_sneaking", 0.0),
                        bindings, calls, cmds);
                assertAllFinite(frame.pose());
            }
        }

        // 2. Rossi: verify multi-bone second_order('D1z增量') and short looping parallel2/parallel5 evaluate cleanly!
        Path rossiPath = resolveTestModel("Rossi(Promoted)V1.1.ysm");
        if (Files.isRegularFile(rossiPath)) {
            RawYsmModel raw = loadYsm(rossiPath);
            var main = ClientModelBundleAssembler.buildParsedBundle(raw, rossiPath.getFileName().toString()).getMainModelData();
            BuiltModel built = main.getModels().getFirst();
            Skeleton skel = HostAnimationAssets.buildSkeleton(built);
            BonePose bind = HostAnimationAssets.buildBindPose(built);
            Map<String, HostAnimationAssets.ClipSpec> clips = new LinkedHashMap<>();
            main.getAnimations().values().forEach(set -> set.getAnimations().forEach(clips::putIfAbsent));
            PoseAnimationLibrary lib = HostAnimationAssets.compileLibrary("rossi", clips, skel, bind);
            assertNotNull(lib);

            List<CodedControllerDefinition> defs = List.of(
                    CodedControllerDefinition.hostDriven("parallel_1", 0, true),
                    CodedControllerDefinition.hostDriven("parallel_2", 0, true));
            Map<String, CodedControllerCommand> cmds = Map.of(
                    "parallel_1", CodedControllerCommand.play("parallel1", CodedLoopMode.LOOP, 0, 1.0),
                    "parallel_2", CodedControllerCommand.play("parallel2", CodedLoopMode.LOOP, 0, 1.0));
            CodedAnimationCollection coll = new CodedAnimationCollection(lib, bind, defs, 12, 505L);
            Map<MolangBindings.Call, MolangValue> calls = new HashMap<>();
            for (int step = 0; step < 30; step++) {
                Map<String, MolangValue> bindings = buildFurinaBindings(false, false, false, true, 0.0, 1.0, 0.0);
                CodedPoseFrame frame = evaluateWithCallRetry(coll, step, Math.round(step * (1_000_000_000.0 / 60.0)),
                        Map.of("ground_speed", 4.0, "vertical_speed", 0.0, "yaw_speed", 10.0, "is_on_ground", 1.0, "is_sneaking", 0.0),
                        bindings, calls, cmds);
                assertAllFinite(frame.pose());
            }
        }
    }

    private static CodedPoseFrame evaluateWithCallRetry(
            CodedAnimationCollection collection,
            long frameId,
            long nowNanos,
            Map<String, Double> queries,
            Map<String, MolangValue> bindings,
            Map<MolangBindings.Call, MolangValue> calls,
            Map<String, CodedControllerCommand> cmds) {
        MolangValue zeroVec = new MolangValue.StructValue(Map.of(
                "x", MolangValue.number(0.0),
                "y", MolangValue.number(0.0),
                "z", MolangValue.number(0.0)));
        for (int attempt = 0; attempt < 128; attempt++) {
            SceneInputs inputs = new SceneInputs(
                    queries, new MolangBindings(bindings, calls), true, SceneInputs.SyncMode.HOST, Map.of(), cmds);
            try {
                return collection.evaluate(frameId, nowNanos, inputs, budget());
            } catch (MolangException missing) {
                var req = missing.bindingRequest().orElseThrow(() -> missing);
                if (calls.containsKey(req)) {
                    throw missing;
                }
                if (req.name().equals("ysm.bone_rot") || req.name().equals("ysm.bone_pos")) {
                    calls.put(req, zeroVec);
                } else {
                    calls.put(req, MolangValue.number(0.0));
                }
            }
        }
        throw new IllegalStateException("Exceeded Molang call retry limit in test");
    }

    private static Map<String, MolangValue> buildFurinaBindings(
            boolean hasElytra,
            boolean ctrlFly,
            boolean ctrlElytraFly,
            boolean ctrlWalk,
            double elytraRotZ,
            double inputVertical,
            double inputHorizontal) {
        Map<String, MolangValue> map = new LinkedHashMap<>();
        map.put("ysm.has_elytra", MolangValue.bool(hasElytra));
        map.put("ysm.elytra_rot_x", MolangValue.number(0.0));
        map.put("ysm.elytra_rot_y", MolangValue.number(0.0));
        map.put("ysm.elytra_rot_z", MolangValue.number(elytraRotZ));
        map.put("ysm.input_vertical", MolangValue.number(inputVertical));
        map.put("ysm.input_horizontal", MolangValue.number(inputHorizontal));
        map.put("ysm.head_pitch", MolangValue.number(0.0));
        map.put("ysm.head_yaw", MolangValue.number(0.0));
        map.put("ysm.is_sneak", MolangValue.bool(false));
        map.put("ysm.is_passenger", MolangValue.bool(false));
        map.put("ysm.is_sleep", MolangValue.bool(false));
        map.put("ysm.on_ladder", MolangValue.bool(false));
        map.put("ysm.swinging", MolangValue.bool(false));
        map.put("ysm.rendering_in_inventory", MolangValue.bool(false));
        map.put("ysm.rendering_in_paperdoll", MolangValue.bool(false));
        map.put("ysm.first_person_mod_hide", MolangValue.bool(false));
        map.put("ysm.person_view", MolangValue.number(1.0));
        map.put("ysm.ground_speed2", MolangValue.number(ctrlWalk ? 4.3 : 0.0));
        map.put("ysm.delta_movement_length", MolangValue.number(ctrlWalk ? 0.21 : 0.0));
        map.put("ctrl.fly", MolangValue.bool(ctrlFly));
        map.put("ctrl.elytra_fly", MolangValue.bool(ctrlElytraFly));
        map.put("ctrl.walk", MolangValue.bool(ctrlWalk));
        map.put("ctrl.idle", MolangValue.bool(!ctrlWalk && !ctrlFly && !ctrlElytraFly));
        map.put("ctrl.run", MolangValue.bool(false));
        map.put("ctrl.sneak", MolangValue.bool(false));
        map.put("ctrl.sneaking", MolangValue.bool(false));
        map.put("ctrl.swim", MolangValue.bool(false));
        map.put("ctrl.swim_stand", MolangValue.bool(false));
        map.put("ctrl.jump", MolangValue.bool(false));
        map.put("ctrl.climb", MolangValue.bool(false));
        map.put("ctrl.climbing", MolangValue.bool(false));
        map.put("ctrl.sleep", MolangValue.bool(false));
        map.put("ctrl.ride", MolangValue.bool(false));
        map.put("ctrl.sit", MolangValue.bool(false));
        map.put("ctrl.playing_extra_animation", MolangValue.bool(false));
        map.put("query.position_delta", new MolangValue.StructValue(Map.of(
                "0", MolangValue.number(0.0),
                "1", MolangValue.number(0.0),
                "2", MolangValue.number(0.0))));
        return map;
    }

    private static void assertAllFinite(BonePose pose) {
        for (float v : pose.rotations()) {
            assertFalse(Float.isNaN(v) || Float.isInfinite(v), "Non-finite rotation in pose: " + v);
        }
        for (float v : pose.translations()) {
            assertFalse(Float.isNaN(v) || Float.isInfinite(v), "Non-finite translation in pose: " + v);
        }
        for (float v : pose.scales()) {
            assertFalse(Float.isNaN(v) || Float.isInfinite(v), "Non-finite scale in pose: " + v);
        }
    }
}
