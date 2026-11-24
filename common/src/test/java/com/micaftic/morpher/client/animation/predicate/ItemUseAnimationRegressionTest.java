package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.architecture.RepoSourceLookup;
import com.micaftic.morpher.client.core.HostAnimationAssets;
import com.micaftic.morpher.client.core.HostAnimationAssets.ClipSpec;
import com.micaftic.morpher.client.core.HostMolangScript;
import com.micaftic.morpher.client.render.BuiltModel;
import com.micaftic.morpher.client.render.ModelBone;
import com.micaftic.morpher.resource.YsmAnimationParsing;
import com.micaftic.morpher.resource.models.GeometryDescription;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import dev.spm.core.api.BonePose;
import dev.spm.core.api.CancellationToken;
import dev.spm.core.api.Skeleton;
import dev.spm.core.scene.CodedAnimationCollection;
import dev.spm.core.scene.CodedControllerCommand;
import dev.spm.core.scene.CodedControllerDefinition;
import dev.spm.core.scene.CodedLoopMode;
import dev.spm.core.scene.MolangBindings;
import dev.spm.core.scene.MolangHooks;
import dev.spm.core.scene.PoseAnimationLibrary;
import dev.spm.core.scene.SceneInputs;
import dev.spm.core.scene.ScriptBudget;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ItemUseAnimationRegressionTest {

    private static PoseAnimationLibrary compileSingleClip(String name, String json) {
        RawYsmModel.RawAnimationFile parsed = YsmAnimationParsing.parseAnimationFile(json.getBytes(StandardCharsets.UTF_8));
        RawYsmModel.RawAnimation raw = parsed.animations.get(name);
        assertNotNull(raw, "Missing clip " + name);
        ClipSpec spec = new ClipSpec(name, raw, List.<HostMolangScript>of(), "main", true, false);
        BuiltModel.BakedBone rightArm = new BuiltModel.BakedBone();
        rightArm.name = "RightArm";
        rightArm.parentIdx = -1;
        BuiltModel model = new BuiltModel(
                new ModelBone[0],
                new String[37][0],
                new boolean[3],
                new GeometryDescription("geometry.test", 64, 64, 1, 1, new double[]{0, 0, 0}),
                new boolean[1]
        );
        model.bakedBones = List.of(rightArm);
        model.bakedBoneOrder = new int[]{0};
        Skeleton skeleton = HostAnimationAssets.buildSkeleton(model);
        BonePose bind = HostAnimationAssets.buildBindPose(model);
        return HostAnimationAssets.compileLibrary("test:use", List.of(spec), skeleton, bind);
    }

    @Test
    void playOnceUseMainhandKeepsAnimatingBeyondDurationWhenDrivenInLoopMode() {
        String json = """
                {
                  "animations": {
                    "use_mainhand": {
                      "animation_length": 0.375,
                      "bones": {
                        "RightArm": {
                          "rotation": {
                            "0.0": [0, 0, 0],
                            "0.1667": [25, 0, 0],
                            "0.375": [0, 0, 0]
                          }
                        }
                      }
                    }
                  }
                }
                """;
        PoseAnimationLibrary library = compileSingleClip("use_mainhand", json);
        CodedAnimationCollection loopCollection = new CodedAnimationCollection(
                library,
                BonePose.identity(library.boneCount()),
                List.of(CodedControllerDefinition.hostDriven("player.use", 0.0, false)),
                12,
                1L,
                Map.of(),
                MolangHooks.empty()
        );
        CodedAnimationCollection defaultCollection = new CodedAnimationCollection(
                library,
                BonePose.identity(library.boneCount()),
                List.of(CodedControllerDefinition.hostDriven("player.use", 0.0, false)),
                12,
                1L,
                Map.of(),
                MolangHooks.empty()
        );

        CodedControllerCommand loopCmd = CodedControllerCommand.play("use_mainhand", CodedLoopMode.LOOP, 0.0);
        CodedControllerCommand defaultCmd = CodedControllerCommand.play("use_mainhand", CodedLoopMode.CLIP_DEFAULT, 0.0);
        SceneInputs loopInputs = new SceneInputs(
                Map.of(),
                new MolangBindings(Map.of(), Map.of()),
                false,
                SceneInputs.SyncMode.HOST,
                Map.of(),
                Map.of("player.use", loopCmd)
        );
        SceneInputs defaultInputs = new SceneInputs(
                Map.of(),
                new MolangBindings(Map.of(), Map.of()),
                false,
                SceneInputs.SyncMode.HOST,
                Map.of(),
                Map.of("player.use", defaultCmd)
        );
        loopCollection.evaluate(1L, 0L, loopInputs, new ScriptBudget(100_000, 32, new CancellationToken()));
        defaultCollection.evaluate(1L, 0L, defaultInputs, new ScriptBudget(100_000, 32, new CancellationToken()));
        for (int step = 1; step <= 45; step++) {
            long nanos = step * 16_666_667L;
            loopCollection.evaluate(1L + step, nanos, loopInputs, new ScriptBudget(100_000, 32, new CancellationToken()));
            defaultCollection.evaluate(1L + step, nanos, defaultInputs, new ScriptBudget(100_000, 32, new CancellationToken()));
        }
        var loopAfterOneSecond = loopCollection.evaluate(
                47L, 46L * 16_666_667L, loopInputs, new ScriptBudget(100_000, 32, new CancellationToken()));
        var defaultAfterOneSecond = defaultCollection.evaluate(
                47L, 46L * 16_666_667L, defaultInputs, new ScriptBudget(100_000, 32, new CancellationToken()));
        assertFalse(defaultAfterOneSecond.controllers().get(0).contributing(),
                "CLIP_DEFAULT on a play_once (0.375s) use_mainhand stops after one cycle");
        assertTrue(loopAfterOneSecond.controllers().get(0).contributing(),
                "Continuous use_mainhand with CodedLoopMode.LOOP must keep animating beyond 0.375s");
    }

    @Test
    void useAndHoldPredicatesUseInputStateKeyAndContinuousLooping() throws Exception {
        String usePred = RepoSourceLookup.readSource(
                "com/micaftic/morpher/client/animation/predicate/InteractionHandAnimationPredicate.java");
        assertTrue(usePred.contains("InputStateKey.isUsingItem"),
                "1.21.1 InteractionHandAnimationPredicate must check InputStateKey.isUsingItem");
        assertFalse(usePred.contains("playAnimationWithValid"),
                "Continuous item-use must preserve hold_on_last_frame or force LOOP instead of CLIP_DEFAULT play_once");

        String mainHold = RepoSourceLookup.readSource(
                "com/micaftic/morpher/client/animation/predicate/MainHandHoldPredicate.java");
        assertTrue(mainHold.contains("InputStateKey.isUsingItem"),
                "MainHandHoldPredicate must yield on InputStateKey.isUsingItem");
        assertFalse(mainHold.contains("playAnimationWithValid"),
                "MainHandHoldPredicate must use playContinuousAnimation rather than CLIP_DEFAULT play_once");

        String offHold = RepoSourceLookup.readSource(
                "com/micaftic/morpher/client/animation/predicate/OffHandHoldPredicate.java");
        assertFalse(offHold.contains("playAnimationWithValid"),
                "OffHandHoldPredicate must use playContinuousAnimation rather than CLIP_DEFAULT play_once");

        String fpWeapon = RepoSourceLookup.readSource(
                "com/micaftic/morpher/client/animation/predicate/FirstPersonWeaponAnimationPredicate.java");
        assertTrue(fpWeapon.contains("playContinuousAnimation"),
                "FirstPersonWeaponAnimationPredicate must use playContinuousAnimation");
    }
}
