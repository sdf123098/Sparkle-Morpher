package com.micaftic.morpher.client.core;

import com.micaftic.morpher.architecture.RepoSourceLookup;
import com.micaftic.morpher.client.core.HostAnimationAssets.ClipSpec;
import com.micaftic.morpher.client.render.BuiltModel;
import com.micaftic.morpher.client.render.ModelBone;
import com.micaftic.morpher.client.render.ModelScenePose;
import com.micaftic.morpher.resource.YSMFolderDeserializer;
import com.micaftic.morpher.resource.YsmAnimationParsing;
import com.micaftic.morpher.resource.bundle.ClientModelBundleAssembler;
import com.micaftic.morpher.resource.models.GeometryDescription;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import dev.spm.core.api.BonePose;
import dev.spm.core.api.CancellationToken;
import dev.spm.core.api.ModelDocument;
import dev.spm.core.api.Skeleton;
import dev.spm.core.scene.CodedAnimationCollection;
import dev.spm.core.scene.CodedControllerCommand;
import dev.spm.core.scene.CodedControllerDefinition;
import dev.spm.core.scene.CodedLoopMode;
import dev.spm.core.scene.EffectCommand;
import dev.spm.core.scene.MolangBindings;
import dev.spm.core.scene.MolangHooks;
import dev.spm.core.scene.PoseAnimationController;
import dev.spm.core.scene.PoseAnimationLibrary;
import dev.spm.core.scene.PoseControllerDefinition;
import dev.spm.core.scene.PoseFrame;
import dev.spm.core.scene.SceneInputs;
import dev.spm.core.scene.ScriptBudget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end regression tests ensuring hard-won old-kernel spear & animation fixes stay intact on spm-core.
 */
class OldKernelExperienceRegressionTest {

    private static BuiltModel twoBoneModel() {
        ModelBone rightArm = new ModelBone("RightArm", false, false, false, 0f, 0f, 0f, 0f, 0f, 0.3f);
        ModelBone leftArm = new ModelBone("LeftArm", false, false, false, 0f, 0f, 0f, 0f, 0f, -0.3f);
        return new BuiltModel(
                new ModelBone[]{rightArm, leftArm},
                new String[37][0],
                new boolean[3],
                new GeometryDescription("geometry.test", 64, 64, 1, 1, new double[]{0, 0, 0}),
                new boolean[1]
        );
    }

    private static RawYsmModel loadBuiltinDefaultRaw() throws Exception {
        Path defaultDir = Path.of("common/src/main/resources/assets/sparkle_morpher/builtin/default");
        if (!Files.isDirectory(defaultDir)) {
            defaultDir = Path.of("../common/src/main/resources/assets/sparkle_morpher/builtin/default");
        }
        assertTrue(Files.isDirectory(defaultDir), "builtin/default assets must exist");
        if (Files.isRegularFile(defaultDir.resolve("ysm.json"))) {
            try (var d = new YSMFolderDeserializer(defaultDir)) {
                return d.deserialize();
            }
        }
        Map<String, byte[]> merged = new LinkedHashMap<>();
        Path rootDir = defaultDir;
        try (var stream = Files.walk(rootDir)) {
            for (Path p : stream.filter(Files::isRegularFile).toList()) {
                merged.put(rootDir.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
            }
        }
        Path loaderYsm = Path.of("src/main/resources/assets/sparkle_morpher/builtin/default/ysm.json");
        assertTrue(Files.isRegularFile(loaderYsm), "loader builtin/default/ysm.json must exist");
        merged.put("ysm.json", Files.readAllBytes(loaderYsm));
        try (var d = new YSMFolderDeserializer(merged)) {
            return d.deserialize();
        }
    }

    @Test
    void mapKeyedCompileLibraryPreservesWeaponAliasesAndPlaysLanceCharge() throws Exception {
        RawYsmModel raw = loadBuiltinDefaultRaw();
        var info = ClientModelBundleAssembler.buildParsedBundle(raw, "default");
        BuiltModel model = info.getMainModelData().getModels().get(0);
        Map<String, ClipSpec> mainClips = new LinkedHashMap<>();
        for (var e : info.getMainModelData().getAnimations().entrySet()) {
            if (!"fp_arm".equals(e.getKey())) {
                mainClips.putAll(e.getValue().getAnimations());
            }
        }
        // Mirror ModelAssemblyFactory.addWeaponAnimationAliases: alias points to the same ClipSpec
        // whose internal clip.name() is still "use_mainhand:lance" / "hold_mainhand:lance" / "swing:lance".
        ClipSpec lanceUse = mainClips.get("use_mainhand:lance");
        ClipSpec lanceHold = mainClips.get("hold_mainhand:lance");
        ClipSpec lanceSwing = mainClips.get("swing:lance");
        assertNotNull(lanceUse, "builtin/default must contain use_mainhand:lance");
        assertNotNull(lanceHold, "builtin/default must contain hold_mainhand:lance");
        assertNotNull(lanceSwing, "builtin/default must contain swing:lance");
        mainClips.putIfAbsent("lance_charge", lanceUse);
        mainClips.putIfAbsent("lance_stand", lanceHold);
        mainClips.putIfAbsent("lance_jab", lanceSwing);
        mainClips.putIfAbsent("lance_lunge", lanceSwing);
        mainClips.putIfAbsent("lance_riding_idle", lanceHold);
        mainClips.putIfAbsent("lance_riding_charge", lanceUse);
        mainClips.putIfAbsent("lance_fall_flying_charge", lanceUse);

        Skeleton sk = HostAnimationAssets.buildSkeleton(model);
        BonePose bind = HostAnimationAssets.buildBindPose(model);
        PoseAnimationLibrary lib = HostAnimationAssets.compileLibrary("test:weapon-aliases", mainClips, sk, bind);

        assertTrue(lib.hasClip("lance_charge"), "PoseAnimationLibrary must compile 'lance_charge' alias");
        assertTrue(lib.hasClip("lance_stand"), "PoseAnimationLibrary must compile 'lance_stand' alias");
        assertTrue(lib.hasClip("lance_jab"), "PoseAnimationLibrary must compile 'lance_jab' alias");
        assertTrue(lib.hasClip("lance_lunge"), "PoseAnimationLibrary must compile 'lance_lunge' alias");
        assertTrue(lib.hasClip("lance_riding_charge"), "PoseAnimationLibrary must compile 'lance_riding_charge' alias");
        assertTrue(lib.hasClip("lance_fall_flying_charge"), "PoseAnimationLibrary must compile 'lance_fall_flying_charge' alias");

        // Verify CodedAnimationCollection actually plays "lance_charge" and moves RightArm!
        CodedAnimationCollection col = new CodedAnimationCollection(
                lib, bind,
                List.of(CodedControllerDefinition.hostDriven("player.use", 0.0, false)),
                12, 1L, Map.of(), MolangHooks.empty());
        var cmd = CodedControllerCommand.play("lance_charge", CodedLoopMode.HOLD_ON_LAST_FRAME, 0.0);
        var inputs = new SceneInputs(Map.of(), new MolangBindings(Map.of(), Map.of()), false,
                SceneInputs.SyncMode.HOST, Map.of(), Map.of("player.use", cmd));
        col.evaluate(1L, 0L, inputs, new ScriptBudget(1_000_000, 64, new CancellationToken()));
        var frame = col.evaluate(2L, 500_000_000L, inputs, new ScriptBudget(1_000_000, 64, new CancellationToken()));
        assertTrue(frame.controllers().get(0).contributing(), "lance_charge alias must actively contribute in CodedAnimationCollection");
        assertEquals("lance_charge", frame.controllers().get(0).selectedClip());
        assertNotEquals(bind, frame.pose(), "lance_charge alias must produce a non-bind pose at 0.5s");
    }

    @Test
    void orderedJsonCompositingRespectsExactSlotPriorityAndBindPoseKeyframes() {
        // 2 bones: 0 = RightArm (bind Z=0.3), 1 = LeftArm (bind Z=-0.3)
        BonePose bind = new BonePose(
                new float[]{0f, 0f, 0.3f, 0f, 0f, -0.3f},
                new float[]{0f, 0f, 0f, 0f, 0f, 0f},
                new float[]{1f, 1f, 1f, 1f, 1f, 1f},
                new boolean[]{false, false},
                new boolean[]{false, false},
                new int[]{255, 255});

        // Case A: Coded player.use (slot 15) explicitly keyframes RightArm back to bind pose (0,0,0.3)
        // at t=12.5-13.0s (exhausted spear drop). Lower JSON player.pre_parallel_4 (slot 4) MUST NOT punch through!
        // Meanwhile JSON player.main (slot 8) MUST NOT overwrite coded player.use (slot 15) either,
        // while JSON player.parallel_1 (slot 20) MAY override LeftArm (not driven by slot 15).
        BonePose codedPoseAtBind = bind;
        BonePose preParallelPose = new BonePose(
                new float[]{0.5f, 0.5f, 0.9f, 0f, -1.57f, -0.82f},
                new float[]{0f, 0f, 0f, 0f, 0f, 0f},
                new float[]{1f, 1f, 1f, 1f, 1f, 1f},
                new boolean[]{false, false},
                new boolean[]{false, false},
                new int[]{255, 255});
        BonePose jsonMainPose = new BonePose(
                new float[]{1.1f, 0.2f, 0.3f, 0.2f, 0.2f, 0.2f},
                new float[]{0f, 0f, 0f, 0f, 0f, 0f},
                new float[]{1f, 1f, 1f, 1f, 1f, 1f},
                new boolean[]{false, false},
                new boolean[]{false, false},
                new int[]{255, 255});
        BonePose jsonPostUsePose = new BonePose(
                new float[]{0f, 0f, 0.3f, 0.7f, 0.8f, 0.9f},
                new float[]{0f, 0f, 0f, 0f, 0f, 0f},
                new float[]{1f, 1f, 1f, 1f, 1f, 1f},
                new boolean[]{false, false},
                new boolean[]{false, false},
                new int[]{255, 255});

        var preLayer = new HostAnimationProcessor.OrderedJsonLayer(4, "player.pre_parallel_4",
                new PoseFrame(1L, 0L, 1L, 1L, 0.016, 0.0, 0, "default", 0.016, false, preParallelPose, Map.of(), List.of()));
        var mainLayer = new HostAnimationProcessor.OrderedJsonLayer(8, "player.main",
                new PoseFrame(1L, 0L, 1L, 1L, 0.016, 0.0, 0, "idle", 0.016, false, jsonMainPose, Map.of(), List.of()));
        var postLayer = new HostAnimationProcessor.OrderedJsonLayer(18, "player.parallel_1",
                new PoseFrame(1L, 0L, 1L, 1L, 0.016, 0.0, 0, "overlay", 0.016, false, jsonPostUsePose, Map.of(), List.of()));

        // RightArm driven by coded slot 15 (even though value == bind!); LeftArm not driven by coded (-1).
        int[] maxRot = new int[]{15, -1};
        int[] maxPos = new int[]{-1, -1};
        int[] maxScale = new int[]{-1, -1};

        BonePose out = HostAnimationProcessor.compositeOrderedJsonPoses(
                codedPoseAtBind, bind, List.of(preLayer, mainLayer, postLayer),
                maxRot, maxPos, maxScale, 15);

        // RightArm MUST retain coded slot 15's bind pose (neither pre_parallel_4 nor json player.main punched through!)
        assertEquals(0.0f, out.rotations()[0], 1e-6f);
        assertEquals(0.0f, out.rotations()[1], 1e-6f);
        assertEquals(0.3f, out.rotations()[2], 1e-6f);
        // LeftArm was not driven by coded slot 15, so highest JSON layer (slot 18) wins cleanly
        assertEquals(0.7f, out.rotations()[3], 1e-6f);
        assertEquals(0.8f, out.rotations()[4], 1e-6f);
        assertEquals(0.9f, out.rotations()[5], 1e-6f);
    }

    @Test
    void blankAnimationEntriesInJsonControllerAreSkippedNotMappedToEmpty() {
        BuiltModel model = twoBoneModel();
        Skeleton sk = HostAnimationAssets.buildSkeleton(model);
        BonePose bind = HostAnimationAssets.buildBindPose(model);

        String animJson = """
                {
                  "animations": {
                    "idle": {
                      "animation_length": 0.2,
                      "loop": false,
                      "bones": {
                        "RightArm": { "rotation": { "0.0": [45, 0, 0] } }
                      }
                    },
                    "empty": {
                      "animation_length": 1.0,
                      "loop": true,
                      "bones": {
                        "RightArm": { "rotation": { "0.0": [0, 0, 0] } }
                      }
                    }
                  }
                }
                """;
        var parsed = YsmAnimationParsing.parseAnimationFile(animJson.getBytes(StandardCharsets.UTF_8));
        Map<String, ClipSpec> clips = new LinkedHashMap<>();
        parsed.animations.forEach((k, v) -> clips.put(k, new ClipSpec(k, v, List.of(), "main", true, false)));
        PoseAnimationLibrary lib = HostAnimationAssets.compileLibrary("test:sparkle-blank", clips, sk, bind);

        RawYsmModel.RawAnimationController rac = new RawYsmModel.RawAnimationController();
        rac.animationName = "controller.animation.player.main";
        rac.initialState = "default";
        RawYsmModel.RawControllerState state = new RawYsmModel.RawControllerState();
        state.name = "default";
        state.animations.add(Map.entry("idle", ""));
        state.animations.add(Map.entry("", ""));
        state.animations.add(Map.entry("   ", ""));
        state.transitions.add(Map.entry("done", "query.all_animations_finished"));
        rac.states.add(state);
        RawYsmModel.RawControllerState doneState = new RawYsmModel.RawControllerState();
        doneState.name = "done";
        rac.states.add(doneState);

        var spec = new HostAnimationAssets.ControllerSpec(rac.animationName, rac.initialState, Map.of(), rac, false);
        PoseControllerDefinition def = spec.compile(lib);
        assertNotNull(def);
        PoseAnimationController ctrl = new PoseAnimationController(lib, def, bind, 12, 1L, Map.of(), MolangHooks.empty());
        var inputs = new SceneInputs(Map.of(), new MolangBindings(Map.of(), Map.of()), false,
                SceneInputs.SyncMode.HOST, Map.of(), Map.of());
        ctrl.evaluate(1L, 0L, inputs, new ScriptBudget(100_000, 32, new CancellationToken()));
        PoseFrame midFrame = ctrl.evaluate(2L, 100_000_000L, inputs, new ScriptBudget(100_000, 32, new CancellationToken()));
        // Authored "empty" clip MUST NOT have stomped "idle"'s RightArm rotation while "idle" is playing!
        assertNotEquals(0.0f, midFrame.pose().rotations()[0], 1e-4f,
                "Blank entries in ['idle', '', ''] must be skipped so authored 'empty' never stomps 'idle'");
        PoseFrame endFrame = ctrl.evaluate(3L, 300_000_000L, inputs, new ScriptBudget(100_000, 32, new CancellationToken()));
        assertEquals("done", endFrame.animation(),
                "Skipping blank entries must allow query.all_animations_finished to fire once 'idle' ends");
    }

    @Test
    void folderDeserializerParsesBlendViaShortestPathAndControllerToCoreJsonPreservesMapBlendTransition(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("ysm.json"), """
                {
                  "spec": 2,
                  "metadata": { "name": "test" },
                  "files": {
                    "player": {
                      "model": { "main": "main.json" },
                      "animation_controllers": ["ctrl.json"]
                    }
                  }
                }
                """);
        Files.writeString(tempDir.resolve("main.json"), """
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": { "identifier": "geometry.test", "texture_width": 64, "texture_height": 64 },
                    "bones": [{ "name": "RightArm", "pivot": [0, 0, 0] }]
                  }]
                }
                """);
        Files.writeString(tempDir.resolve("ctrl.json"), """
                {
                  "format_version": "1.10.0",
                  "animation_controllers": {
                    "controller.animation.player.post_swing": {
                      "initial_state": "default",
                      "states": {
                        "default": {
                          "blend_transition": { "0.0": 0.0, "0.25": 1.0 },
                          "blend_via_shortest_path": true
                        },
                        "other": {
                          "blend_transition": 0.0
                        }
                      }
                    }
                  }
                }
                """);

        RawYsmModel raw;
        try (var deserializer = new YSMFolderDeserializer(tempDir)) {
            raw = deserializer.deserialize();
        }
        var acFile = raw.mainEntity.animationControllerFiles.get(0);
        var rac = acFile.controllers.get("controller.animation.player.post_swing");
        assertNotNull(rac);
        RawYsmModel.RawControllerState defState = rac.states.stream().filter(s -> "default".equals(s.name)).findFirst().orElseThrow();
        RawYsmModel.RawControllerState otherState = rac.states.stream().filter(s -> "other".equals(s.name)).findFirst().orElseThrow();
        assertTrue(defState.blendViaShortestPath, "YSMFolderDeserializer must parse blend_via_shortest_path");
        assertFalse(otherState.blendViaShortestPath);

        ModelDocument.JsonObject coreJson = HostAnimationAssets.controllerToCoreJson(rac, false, false);
        var statesObj = ((ModelDocument.JsonObject) ((ModelDocument.JsonObject) ((ModelDocument.JsonObject)
                coreJson.values().get("animation_controllers")).values().get("controller.animation.player.post_swing")).values().get("states")).values();
        var defJson = ((ModelDocument.JsonObject) statesObj.get("default")).values();
        var otherJson = ((ModelDocument.JsonObject) statesObj.get("other")).values();
        assertEquals(0.25, ((ModelDocument.JsonNumber) defJson.get("blend_transition")).value(), 1e-6,
                "Map blend_transition must preserve non-zero transition duration");
        assertTrue(defJson.containsKey("blend_via_shortest_path"));
        assertFalse(otherJson.containsKey("blend_via_shortest_path"),
                "Unset blend_via_shortest_path must not emit false unconditionally");
    }

    @Test
    void animationParsingSupportsCaseInsensitiveLerpModeVectorAndPostLinearDefault() {
        String json = """
                {
                  "animations": {
                    "compat": {
                      "animation_length": 2.0,
                      "bones": {
                        "RightArm": {
                          "rotation": {
                            "0.0": { "vector": [10, 20, 30], "easing": "CatmullRom" },
                            "0.5": { "post": [40, 50, 60] },
                            "1.0": { "post": [70, 80, 90], "lerp_mode": "catmull_rom" },
                            "1.5": { "post": [0, 0, 0], "lerp_mode": "STEP" }
                          }
                        }
                      }
                    }
                  }
                }
                """;
        var raf = YsmAnimationParsing.parseAnimationFile(json.getBytes(StandardCharsets.UTF_8));
        var frames = raf.animations.get("compat").boneAnimations.get(0).rotation;
        assertEquals(4, frames.size());
        assertEquals(RawYsmModel.RawKeyframe.INTERPOLATION_CATMULLROM, frames.get(0).interpolationMode);
        assertEquals(10.0f, ((Number) frames.get(0).postData[0]).floatValue(), 1e-4f);
        assertEquals(RawYsmModel.RawKeyframe.INTERPOLATION_LINEAR, frames.get(1).interpolationMode,
                "Keyframe object with post only and no lerp_mode must default to LINEAR, not STEP");
        assertEquals(RawYsmModel.RawKeyframe.INTERPOLATION_CATMULLROM, frames.get(2).interpolationMode);
        assertEquals(RawYsmModel.RawKeyframe.INTERPOLATION_STEP, frames.get(3).interpolationMode);
    }

    @Test
    void animationParticleEffectsPreserveLocatorThroughCore130() {
        String json = """
                {
                  "animations": {
                    "spark": {
                      "animation_length": 0.5,
                      "particle_effects": {
                        "0.0": {
                          "effect": "minecraft:flame",
                          "locator": "RightHandLocator"
                        }
                      }
                    }
                  }
                }
                """;
        BuiltModel model = twoBoneModel();
        Skeleton sk = HostAnimationAssets.buildSkeleton(model);
        BonePose bind = HostAnimationAssets.buildBindPose(model);
        var parsed = YsmAnimationParsing.parseAnimationFile(json.getBytes(StandardCharsets.UTF_8));
        ClipSpec spec = new ClipSpec("spark", parsed.animations.get("spark"), List.of(), "main", true, false);
        PoseAnimationLibrary lib = HostAnimationAssets.compileLibrary("test:particle", Map.of("spark", spec), sk, bind);

        CodedAnimationCollection col = new CodedAnimationCollection(
                lib, bind,
                List.of(CodedControllerDefinition.hostDriven("player.main", 0.0, false)),
                12, 1L, Map.of(), MolangHooks.empty());
        var inputs = new SceneInputs(Map.of(), new MolangBindings(Map.of(), Map.of()), true,
                SceneInputs.SyncMode.HOST, Map.of(),
                Map.of("player.main", CodedControllerCommand.play("spark", CodedLoopMode.PLAY_ONCE, 0.0)));
        var frame = col.evaluate(1L, 0L, inputs, new ScriptBudget(100_000, 32, new CancellationToken()));
        EffectCommand particleCmd = frame.effects().stream()
                .filter(e -> "particle".equals(e.kind()))
                .findFirst()
                .orElse(null);
        assertNotNull(particleCmd, "particle_effects cue must fire at t=0.0");
        String rawRes = particleCmd.resource();
        int sep = rawRes.indexOf(HostAnimationAssets.PARTICLE_LOCATOR_SEPARATOR);
        assertTrue(sep > 0, "Locator must be preserved via separator on spm-core 1.3.0, got: " + rawRes);
        assertEquals("minecraft:flame", rawRes.substring(0, sep));
        assertEquals("RightHandLocator", rawRes.substring(sep + 1));
    }

    @Test
    void negativeKeyframeDoesNotEvictExplicitZeroKeyframe() {
        String json = """
                {
                  "animations": {
                    "neg_test": {
                      "animation_length": 1.0,
                      "bones": {
                        "RightArm": {
                          "rotation": {
                            "-0.5": [90, 0, 0],
                            "0.0": [30, 0, 0],
                            "1.0": [30, 0, 0]
                          }
                        }
                      }
                    }
                  }
                }
                """;
        BuiltModel model = twoBoneModel();
        Skeleton sk = HostAnimationAssets.buildSkeleton(model);
        BonePose bind = HostAnimationAssets.buildBindPose(model);
        var parsed = YsmAnimationParsing.parseAnimationFile(json.getBytes(StandardCharsets.UTF_8));
        ClipSpec spec = new ClipSpec("neg_test", parsed.animations.get("neg_test"), List.of(), "main", true, false);
        PoseAnimationLibrary lib = HostAnimationAssets.compileLibrary("test:neg", Map.of("neg_test", spec), sk, bind);

        CodedAnimationCollection col = new CodedAnimationCollection(
                lib, bind,
                List.of(CodedControllerDefinition.hostDriven("player.main", 0.0, false)),
                12, 1L, Map.of(), MolangHooks.empty());
        var inputs = new SceneInputs(Map.of(), new MolangBindings(Map.of(), Map.of()), false,
                SceneInputs.SyncMode.HOST, Map.of(),
                Map.of("player.main", CodedControllerCommand.play("neg_test", CodedLoopMode.HOLD_ON_LAST_FRAME, 0.0)));
        var frame = col.evaluate(1L, 0L, inputs, new ScriptBudget(100_000, 32, new CancellationToken()));
        float rotXDeg = (float) Math.toDegrees(frame.pose().rotations()[0]);
        assertEquals(30.0f, Math.abs(rotXDeg), 1.0f, "Explicit t=0 keyframe (30 deg) must not be evicted by negative timestamp (-0.5 -> 90 deg)");
    }

    @Test
    void hostControllerSlotClampsPlaybackSpeedToCoreRangeAndScenePoseWritesValidPose() {
        BuiltModel model = twoBoneModel();
        ModelScenePose scenePose = new ModelScenePose(model);
        BonePose validPose = new BonePose(
                new float[]{0.25f, 0f, 0.3f, 0.1f, 0.2f, 0.3f},
                new float[]{0.5f, 0f, 0f, 1f, 2f, 3f},
                new float[]{1.5f, 1f, 1f, 2f, 2f, 2f},
                new boolean[]{false, false},
                new boolean[]{false, false},
                new int[]{255, 255});
        scenePose.writeBonePose(validPose);
        var rightArm = scenePose.getBone("RightArm");
        assertNotNull(rightArm);
        assertEquals(0.25f, rightArm.getRotationX(), 1e-6f);
        assertEquals(-8.0f, rightArm.getPositionX(), 1e-5f);
        assertEquals(1.5f, rightArm.getScaleX(), 1e-6f);

        HostControllerSlot<HostAnimatable<net.minecraft.world.entity.LivingEntity>> slot = new HostControllerSlot<>(
                null, "player.use", 0.0f,
                (com.micaftic.morpher.client.animation.IAnimationPredicate<HostAnimatable<net.minecraft.world.entity.LivingEntity>>) e -> {
                    e.getController().setPlaybackSpeed(999.0);
                    e.getController().setAnimation("use_mainhand:lance", CodedLoopMode.HOLD_ON_LAST_FRAME);
                    return HostPlayState.Decision.CONTINUE;
                });
        var cmd = slot.evaluateCommand(new HostActionEvent<>(null, 0f, 0f, 0, 0f, 0f, false, false, null), false);
        assertEquals(64.0, cmd.speed(), 1e-9, "Playback speed must clamp to spm-core's max 64.0 instead of throwing");
    }

    @Test
    void hostBindingsAndFirstPersonArmControllerContractsAreEnforced() throws Exception {
        String bindings = RepoSourceLookup.readSource("com/micaftic/morpher/client/core/HostMolangBindings.java");
        assertFalse(bindings.contains("MinecraftHostSnapshotSampler.queryResolver"),
                "HostMolangBindings must use thread-safe CoreQueryFunctions.Source instead of main-thread-only MinecraftCoreQueryResolver");
        assertTrue(bindings.contains("case \"ysm.get_equipped_item_name\""),
                "HostMolangBindings must restore legacy ysm.get_equipped_item_name");
        assertTrue(bindings.contains("Mth.wrapDegrees(entity.getYRot() - entity.yRotO)"),
                "HostMolangBindings.getYawSpeed must wrap yaw delta across +/-180 deg");

        String fpArm = RepoSourceLookup.readSource(
                "com/micaftic/morpher/client/animation/controller/FirstPersonArmAnimationController.java");
        assertFalse(fpArm.contains("registerWeaponProcessors()"),
                "FirstPersonArmAnimationController must not mutate static processorRegistry inside buildControllers");

        String renderMath = RepoSourceLookup.readSource("com/micaftic/morpher/client/renderer/SpmRenderMath.java");
        assertTrue(renderMath.contains("bone.getScaleX() == 0.0f || bone.getScaleY() == 0.0f || bone.getScaleZ() == 0.0f"),
                "SpmRenderMath.isHiddenScale must treat any zero axis as hidden, matching SpmCoreBoneMatrixWriter");

        String spmEntity = RepoSourceLookup.readSource("com/micaftic/morpher/client/entity/SpmEntity.java");
        int processIdx = spmEntity.indexOf("public HostActionEvent<?> processAnimationImpl(");
        assertTrue(processIdx >= 0, "SpmEntity.processAnimationImpl must exist");
        int awaitIdx = spmEntity.indexOf("public HostActionEvent<?> awaitAsyncResult()", processIdx);
        assertTrue(awaitIdx > processIdx);
        String processBody = spmEntity.substring(processIdx, awaitIdx);
        assertFalse(processBody.contains("submitAsyncUpdate("),
                "SpmEntity.processAnimationImpl must not submitAsyncUpdate only to immediately block on Future.get()");

        String processor = RepoSourceLookup.readSource("com/micaftic/morpher/client/core/HostAnimationProcessor.java");
        assertTrue(processor.contains("HostMolangBindings.createCallCache(event)"),
                "HostAnimationProcessor must reuse a single per-frame FrameCallCache across knownCallRequests");
        assertTrue(processor.contains("resolveClipDrivenCache("),
                "HostAnimationProcessor must cache per-clip driven bone indices instead of rebuilding summaries per frame");
    }

    @Test
    void builtModelCachesGlowBonesFlattensQuadsAndNormalizesQuadNormals() {
        BuiltModel model = twoBoneModel();
        BuiltModel.BakedBone boneA = new BuiltModel.BakedBone();
        boneA.name = "boneA";
        boneA.glow = false;
        BuiltModel.BakedCube cube1 = new BuiltModel.BakedCube();
        BuiltModel.BakedQuad q1 = new BuiltModel.BakedQuad();
        q1.setNormal(0.0f, 3.0f, 4.0f);
        assertEquals(0.0f, q1.normalX, 1e-6f);
        assertEquals(0.6f, q1.normalY, 1e-5f);
        assertEquals(0.8f, q1.normalZ, 1e-5f);
        cube1.quads.add(q1);
        BuiltModel.BakedCube cube2 = new BuiltModel.BakedCube();
        BuiltModel.BakedQuad q2 = new BuiltModel.BakedQuad();
        q2.setNormal(1.0f, 0.0f, 0.0f);
        cube2.quads.add(q2);
        boneA.cubes.add(cube1);
        boneA.cubes.add(cube2);

        BuiltModel.BakedBone boneB = new BuiltModel.BakedBone();
        boneB.name = "boneB";
        boneB.glow = true;

        model.bakedBones = List.of(boneA, boneB);
        assertTrue(model.hasGlowBones(), "BuiltModel.hasGlowBones must detect glow bone");
        assertTrue(model.hasGlowBones(), "BuiltModel.hasGlowBones must return cached true on repeat calls");

        BuiltModel.BakedQuad[] flat = boneA.getOrBuildFlatQuads();
        assertEquals(2, flat.length);
        assertSame(q1, flat[0]);
        assertSame(q2, flat[1]);
        assertSame(flat, boneA.getOrBuildFlatQuads(), "BakedBone.getOrBuildFlatQuads must return cached array");
    }
}
