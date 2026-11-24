package com.micaftic.morpher.client.core;

import com.micaftic.morpher.client.animation.molang.struct.RoamingStruct;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.model.processor.ParallelProcessor;
import com.micaftic.morpher.client.render.BuiltModel;
import com.micaftic.morpher.client.render.ModelBone;
import com.micaftic.morpher.client.render.ModelScenePose;
import com.micaftic.morpher.core.acceleration.AccelerationCapability;
import com.micaftic.morpher.core.gpu.SpmCoreBoneMatrixWriter;
import com.micaftic.morpher.resource.YSMFolderDeserializer;
import com.micaftic.morpher.resource.bundle.ClientModelBundleAssembler;
import com.micaftic.morpher.resource.models.GeometryDescription;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import dev.spm.core.api.BonePose;
import dev.spm.core.api.CancellationToken;
import dev.spm.core.api.Skeleton;
import dev.spm.core.scene.CodedAnimationCollection;
import dev.spm.core.scene.CodedControllerCommand;
import dev.spm.core.scene.CodedControllerDefinition;
import dev.spm.core.scene.CodedLoopMode;
import dev.spm.core.scene.CodedPlayDecision;
import dev.spm.core.scene.MolangBindings;
import dev.spm.core.scene.MolangHooks;
import dev.spm.core.scene.MolangValue;
import dev.spm.core.scene.PoseAnimationLibrary;
import dev.spm.core.scene.PoseControllerDefinition;
import dev.spm.core.scene.SceneInputs;
import dev.spm.core.scene.ScriptBudget;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class HostAnimationRegressionTest {

    private static BuiltModel simpleModel() {
        BuiltModel.BakedBone root = new BuiltModel.BakedBone();
        root.name = "root";
        root.parentIdx = -1;
        BuiltModel.BakedBone accessory = new BuiltModel.BakedBone();
        accessory.name = "accessory";
        accessory.parentIdx = 0;

        BuiltModel model = new BuiltModel(
                new ModelBone[0],
                new String[37][0],
                new boolean[3],
                new GeometryDescription("geometry.test", 64, 64, 1, 1, new double[]{0, 0, 0}),
                new boolean[1]
        );
        model.bakedBones = List.of(root, accessory);
        model.bakedBoneOrder = new int[]{0, 1};
        return model;
    }

    private static RawYsmModel.RawAnimation rawClip(String name, int loopMode) {
        RawYsmModel.RawAnimation anim = new RawYsmModel.RawAnimation();
        anim.name = name;
        anim.length = 1.0f;
        anim.loopMode = loopMode;
        return anim;
    }

    @Test
    void legacyMolangQuirksAndTimelineMarkersCompileAndIsolateUnrecoverableClips() {
        BuiltModel model = simpleModel();
        Skeleton skeleton = HostAnimationAssets.buildSkeleton(model);
        BonePose bind = HostAnimationAssets.buildBindPose(model);

        RawYsmModel.RawAnimation parallel = rawClip("parallel0", 1);
        RawYsmModel.RawTimelineEvent tl = new RawYsmModel.RawTimelineEvent();
        tl.timestamp = 0.0f;
        tl.events = List.of(
                "[;",
                "['group_name';",
                "'注释：ysm.bone_rot('M1').x内部引号注释';",
                "v.p=((1+2)/3;",
                "v.flag = ysm.head_yaw&lt0;",
                "];"
        );
        parallel.timelineEvents = List.of(tl);

        RawYsmModel.RawAnimation walk = rawClip("walk", 1);
        RawYsmModel.RawBoneAnimation walkBone = new RawYsmModel.RawBoneAnimation();
        walkBone.boneName = "root";
        RawYsmModel.RawKeyframe walkKf = new RawYsmModel.RawKeyframe();
        walkKf.timestamp = 0.0f;
        walkKf.postData = new Object[]{"({data3}) + NaN + 15", 0, 0};
        walkBone.rotation.add(walkKf);
        walk.boneAnimations.add(walkBone);

        RawYsmModel.RawAnimation preParallel4 = rawClip("pre_parallel4", 1);
        RawYsmModel.RawBoneAnimation pp4Bone = new RawYsmModel.RawBoneAnimation();
        pp4Bone.boneName = "accessory";
        RawYsmModel.RawKeyframe pp4Kf = new RawYsmModel.RawKeyframe();
        pp4Kf.timestamp = 0.0f;
        pp4Kf.postData = new Object[]{"v.t == 1", "v.t == 1", "v.t == 1"};
        pp4Bone.scale.add(pp4Kf);
        preParallel4.boneAnimations.add(pp4Bone);

        RawYsmModel.RawAnimation badTypo = rawClip("jump", 0);
        RawYsmModel.RawBoneAnimation badBone = new RawYsmModel.RawBoneAnimation();
        badBone.boneName = "root";
        RawYsmModel.RawKeyframe badKf = new RawYsmModel.RawKeyframe();
        badKf.timestamp = 0.0f;
        badKf.postData = new Object[]{"o", 0, 0};
        badBone.rotation.add(badKf);
        badTypo.boneAnimations.add(badBone);

        PoseAnimationLibrary lib = HostAnimationAssets.compileLibrary(
                "test:quirks",
                List.of(
                        new HostAnimationAssets.ClipSpec("parallel0", parallel, List.of(), "main", false, false),
                        new HostAnimationAssets.ClipSpec("walk", walk, List.of(), "main", false, false),
                        new HostAnimationAssets.ClipSpec("pre_parallel4", preParallel4, List.of(), "main", false, false),
                        new HostAnimationAssets.ClipSpec("jump", badTypo, List.of(), "main", false, false)
                ),
                skeleton,
                bind);

        assertTrue(lib.hasClip("parallel0"));
        assertTrue(lib.hasClip("walk"));
        assertTrue(lib.hasClip("pre_parallel4"));
        assertTrue(lib.hasClip("jump"));
        assertTrue(lib.hasClip("empty"));
    }

    @Test
    void controllerWithImplicitEmptyAndMissingClipRecoversCleanly() {
        BuiltModel model = simpleModel();
        Skeleton skeleton = HostAnimationAssets.buildSkeleton(model);
        BonePose bind = HostAnimationAssets.buildBindPose(model);

        PoseAnimationLibrary lib = HostAnimationAssets.compileLibrary(
                "test:ctrl",
                List.of(new HostAnimationAssets.ClipSpec("idle", rawClip("idle", 1), List.of(), "main", false, false)),
                skeleton,
                bind);

        RawYsmModel.RawAnimationController raw = new RawYsmModel.RawAnimationController();
        raw.animationName = "player.post_main";
        RawYsmModel.RawControllerState idleState = new RawYsmModel.RawControllerState();
        idleState.name = "default";
        idleState.animations.add(Map.entry("empty", ""));
        idleState.animations.add(Map.entry("nonexistent_clip", ""));
        idleState.transitions.add(Map.entry("idle_s", "ysm.head_yaw&lt0"));
        RawYsmModel.RawControllerState nextState = new RawYsmModel.RawControllerState();
        nextState.name = "idle_s";
        nextState.animations.add(Map.entry("idle", ""));
        raw.states.add(idleState);
        raw.states.add(nextState);

        var spec = new HostAnimationAssets.ControllerSpec(
                raw.animationName, "default", Map.of(), raw);
        PoseControllerDefinition compiled = spec.compile(lib, false);
        assertNotNull(compiled, "controller referencing 'empty' or missing clip must still compile");
    }

    @Test
    void builtinDefaultCompilesAllClipsAndCullsHiddenOutfitsByDefault() throws Exception {
        Path defaultDir = Path.of("common/src/main/resources/assets/sparkle_morpher/builtin/default");
        if (!java.nio.file.Files.isDirectory(defaultDir)) {
            defaultDir = Path.of("../common/src/main/resources/assets/sparkle_morpher/builtin/default");
        }
        assertTrue(java.nio.file.Files.isDirectory(defaultDir), "builtin/default assets must exist");

        RawYsmModel raw;
        if (java.nio.file.Files.isRegularFile(defaultDir.resolve("ysm.json"))) {
            try (YSMFolderDeserializer d = new YSMFolderDeserializer(defaultDir)) {
                raw = d.deserialize();
            }
        } else {
            Map<String, byte[]> merged = new LinkedHashMap<>();
            Path rootDir = defaultDir;
            try (var stream = java.nio.file.Files.walk(rootDir)) {
                for (Path p : stream.filter(java.nio.file.Files::isRegularFile).toList()) {
                    merged.put(rootDir.relativize(p).toString().replace('\\', '/'), java.nio.file.Files.readAllBytes(p));
                }
            }
            Path loaderYsm = Path.of("src/main/resources/assets/sparkle_morpher/builtin/default/ysm.json");
            assertTrue(java.nio.file.Files.isRegularFile(loaderYsm), "loader builtin/default/ysm.json must exist");
            merged.put("ysm.json", java.nio.file.Files.readAllBytes(loaderYsm));
            try (YSMFolderDeserializer d = new YSMFolderDeserializer(merged)) {
                raw = d.deserialize();
            }
        }
        var info = ClientModelBundleAssembler.buildParsedBundle(raw, "default");
        BuiltModel m = info.getMainModelData().getModels().get(0);
        Map<String, HostAnimationAssets.ClipSpec> mainClips = new LinkedHashMap<>();
        for (var entry : info.getMainModelData().getAnimations().entrySet()) {
            if (!"fp_arm".equals(entry.getKey())) {
                mainClips.putAll(entry.getValue().getAnimations());
            }
        }
        Skeleton skeleton = HostAnimationAssets.buildSkeleton(m);
        BonePose bind = HostAnimationAssets.buildBindPose(m);
        PoseAnimationLibrary lib = HostAnimationAssets.compileLibrary("test:builtin_default", mainClips.values(), skeleton, bind);

        assertTrue(lib.hasClip("idle"), "builtin/default idle clip must compile");
        assertTrue(lib.hasClip("walk"), "builtin/default walk clip must compile");
        assertTrue(lib.hasClip("parallel0"), "builtin/default parallel0 clip must compile");
        assertTrue(lib.hasClip("pre_parallel4"), "builtin/default pre_parallel4 clip must compile");

        List<CodedControllerDefinition> defs = new ArrayList<>();
        Map<String, CodedControllerCommand> cmds = new LinkedHashMap<>();
        for (int i = 0; i < 8; i++) {
            String slot = "player.pre_parallel_" + i;
            String clip = "pre_parallel" + i;
            defs.add(CodedControllerDefinition.hostDriven(slot, 0, false));
            if (lib.hasClip(clip)) {
                cmds.put(slot, new CodedControllerCommand(CodedPlayDecision.CONTINUE, clip, CodedLoopMode.LOOP, 0, 1.0, false, false));
            }
        }
        defs.add(CodedControllerDefinition.hostDriven("player.main", 2, false));
        cmds.put("player.main", new CodedControllerCommand(CodedPlayDecision.CONTINUE, "idle", CodedLoopMode.LOOP, 2, 1.0, false, false));
        CodedAnimationCollection col = new CodedAnimationCollection(lib, bind, defs, 12, 42L, Map.of(), MolangHooks.empty());
        var inputs = new SceneInputs(Map.of(), new MolangBindings(Map.of(), Map.of()), true, SceneInputs.SyncMode.HOST, Map.of(), cmds);
        var frame = col.evaluate(1, 0L, inputs, new ScriptBudget(1_000_000, 64, new CancellationToken()));

        var pose = new ModelScenePose(m);
        pose.writeBonePose(frame.pose());
        var mats = AccelerationCapability.computeProvider().evaluateBones(
                m.ensureComputeCache().skeleton,
                SpmCoreBoneMatrixWriter.toBonePose(m.bakedBones, pose.getMatrixData()));
        int visibleBones = 0;
        for (boolean v : mats.visible()) {
            if (v) visibleBones++;
        }
        assertTrue(visibleBones < 250, "pre_parallel0..7 must cull hidden outfits in builtin/default, saw " + visibleBones + "/" + m.bakedBones.size());
    }

    @Test
    void parallelProcessorRegistersTimelineOnlyParallelSlots() {
        var timelineOnly = new HostAnimationAssets.ClipSpec("parallel0", rawClip("parallel0", 1), List.of(), "main", false, false);
        assertFalse(timelineOnly.hasKeyframes());
        var rawTl = new RawYsmModel.RawTimelineEvent();
        rawTl.timestamp = 0.0f;
        rawTl.events = List.of("v.x = 1;");
        timelineOnly.raw().timelineEvents = List.of(rawTl);
        assertTrue(timelineOnly.hasTimelineEvents());
        assertFalse(timelineOnly.isEmpty());
    }
}
