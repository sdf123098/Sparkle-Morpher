package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.architecture.RepoSourceLookup;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostAnimatable;
import com.micaftic.morpher.client.core.HostAnimationAssets;
import com.micaftic.morpher.client.core.HostAnimationProcessor;
import com.micaftic.morpher.client.core.HostControllerSlot;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.LivingEntityFrameState;
import com.micaftic.morpher.client.render.BuiltModel;
import com.micaftic.morpher.client.render.ModelBone;
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
import dev.spm.core.scene.PoseFrame;
import dev.spm.core.scene.SceneInputs;
import dev.spm.core.scene.ScriptBudget;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SwingAndJsonLayerRegressionTest {

    private static BuiltModel simpleArmModel() {
        BuiltModel.BakedBone root = new BuiltModel.BakedBone();
        root.name = "root";
        root.parentIdx = -1;
        BuiltModel.BakedBone rightArm = new BuiltModel.BakedBone();
        rightArm.name = "RightArm";
        rightArm.parentIdx = 0;

        BuiltModel model = new BuiltModel(
                new ModelBone[0],
                new String[37][0],
                new boolean[3],
                new GeometryDescription("geometry.test", 64, 64, 1, 1, new double[]{0, 0, 0}),
                new boolean[1]
        );
        model.bakedBones = List.of(root, rightArm);
        model.bakedBoneOrder = new int[]{0, 1};
        return model;
    }

    private static HostAnimationAssets.ClipSpec clip(String name, float lengthSeconds, int loopMode, float rotXDeg) {
        RawYsmModel.RawAnimation raw = new RawYsmModel.RawAnimation();
        raw.name = name;
        raw.length = lengthSeconds;
        raw.loopMode = loopMode;
        RawYsmModel.RawBoneAnimation boneAnim = new RawYsmModel.RawBoneAnimation();
        boneAnim.boneName = "RightArm";
        RawYsmModel.RawKeyframe kf = new RawYsmModel.RawKeyframe();
        kf.timestamp = 0.0f;
        kf.postData = new Object[]{rotXDeg, 0, 0};
        boneAnim.rotation.add(kf);
        raw.boneAnimations.add(boneAnim);
        return new HostAnimationAssets.ClipSpec(name, raw, List.of(), "main", false, false);
    }

    @Test
    void reloadFlagStaysLatchedAcrossZeroStepFramesUntilAcknowledged() {
        AtomicBoolean requestReload = new AtomicBoolean(true);
        HostControllerSlot<HostAnimatable<LivingEntity>> slot = new HostControllerSlot<>(
                null,
                "player.swing",
                0.0f,
                (com.micaftic.morpher.client.animation.IAnimationPredicate<HostAnimatable<LivingEntity>>) event -> {
                    if (requestReload.compareAndSet(true, false)) {
                        event.getController().stopTransition();
                        event.getController().setAnimation("swing:sword", CodedLoopMode.PLAY_ONCE);
                    }
                    return HostPlayState.Decision.CONTINUE;
                });

        HostActionEvent<HostAnimatable<LivingEntity>> event = new HostActionEvent<>(null, 0.0f, 0.0f, 0, 0.0f, 0.0f, false, false, null);
        CodedControllerCommand frame0 = slot.evaluateCommand(event, false);
        assertTrue(frame0.reload(), "First frame requesting reload must emit reload=true");

        CodedControllerCommand frame1 = slot.evaluateCommand(event, false);
        assertTrue(frame1.reload(), "Reload must stay latched across zero-step frames until a simulation step consumes it");

        slot.acknowledgeCommandDelivery();
        CodedControllerCommand frame2 = slot.evaluateCommand(event, false);
        assertFalse(frame2.reload(), "Reload must clear after delivery is acknowledged");
    }

    @Test
    void swordSwingRestartsCleanlyEvenWhenTriggeredOnZeroStepFrameAt120Hz() {
        BuiltModel model = simpleArmModel();
        Skeleton sk = HostAnimationAssets.buildSkeleton(model);
        BonePose bind = HostAnimationAssets.buildBindPose(model);
        List<HostAnimationAssets.ClipSpec> clips = List.of(
                clip("idle", 1.0f, 1, 0.0f),
                clip("hold_mainhand:sword", 1.0f, 1, -20.0f),
                clip("swing:sword", 0.5f, 0, -90.0f)
        );
        PoseAnimationLibrary lib = HostAnimationAssets.compileLibrary("test:swing-120hz", clips, sk, bind);
        List<CodedControllerDefinition> defs = List.of(
                CodedControllerDefinition.hostDriven("player.main", 2.0, false),
                CodedControllerDefinition.hostDriven("player.hold_mainhand", 2.0, false),
                CodedControllerDefinition.hostDriven("player.swing", 0.0, false)
        );
        CodedAnimationCollection col = new CodedAnimationCollection(lib, bind, defs, 12, 1L, Map.of(), MolangHooks.empty());
        MolangBindings bindings = new MolangBindings(Map.of(), Map.of());
        var idleCmd = CodedControllerCommand.play("idle", CodedLoopMode.LOOP, 2.0);
        var holdCmd = CodedControllerCommand.play("hold_mainhand:sword", CodedLoopMode.LOOP, 2.0);
        var pauseCmd = new CodedControllerCommand(dev.spm.core.scene.CodedPlayDecision.PAUSE, "hold_mainhand:sword", CodedLoopMode.LOOP, 2.0, 1.0, false, false);

        AtomicBoolean triggerSwing = new AtomicBoolean(false);
        HostControllerSlot<HostAnimatable<LivingEntity>> swingSlot = new HostControllerSlot<>(
                null,
                "player.swing",
                0.0f,
                (com.micaftic.morpher.client.animation.IAnimationPredicate<HostAnimatable<LivingEntity>>) event -> {
                    if (triggerSwing.compareAndSet(true, false)) {
                        event.getController().stopTransition();
                        event.getController().setAnimation("swing:sword", CodedLoopMode.PLAY_ONCE);
                    }
                    return HostPlayState.Decision.CONTINUE;
                });
        HostActionEvent<HostAnimatable<LivingEntity>> event = new HostActionEvent<>(null, 0.0f, 0.0f, 0, 0.0f, 0.0f, false, false, null);

        long nanos = 0L;
        long frameId = 1L;
        long lastStep = -1L;

        triggerSwing.set(true);
        for (int i = 0; i < 240; i++) {
            CodedControllerCommand swingCmd = swingSlot.evaluateCommand(event, false);
            var frame = col.evaluate(frameId++, nanos,
                    new SceneInputs(Map.of("is_jumping", 0.0), bindings, false, SceneInputs.SyncMode.HOST, Map.of(),
                            Map.of("player.main", idleCmd, "player.hold_mainhand", holdCmd, "player.swing", swingCmd)),
                    new ScriptBudget(1_000_000, 64, new CancellationToken()));
            if (frame.step() != lastStep) {
                swingSlot.acknowledgeCommandDelivery();
                lastStep = frame.step();
            }
            nanos += 8_333_333L;
        }

        while (true) {
            CodedControllerCommand swingCmd = swingSlot.evaluateCommand(event, false);
            var frame = col.evaluate(frameId++, nanos,
                    new SceneInputs(Map.of("is_jumping", 0.0), bindings, false, SceneInputs.SyncMode.HOST, Map.of(),
                            Map.of("player.main", idleCmd, "player.hold_mainhand", holdCmd, "player.swing", swingCmd)),
                    new ScriptBudget(1_000_000, 64, new CancellationToken()));
            boolean advanced = frame.step() != lastStep;
            if (advanced) {
                swingSlot.acknowledgeCommandDelivery();
                lastStep = frame.step();
                nanos += 8_333_333L;
                break;
            }
            nanos += 8_333_333L;
        }

        triggerSwing.set(true);
        boolean swingContributed = false;
        for (int i = 0; i < 6; i++) {
            CodedControllerCommand swingCmd = swingSlot.evaluateCommand(event, false);
            var frame = col.evaluate(frameId++, nanos,
                    new SceneInputs(Map.of("is_jumping", 0.0), bindings, false, SceneInputs.SyncMode.HOST, Map.of(),
                            Map.of("player.main", idleCmd, "player.hold_mainhand", pauseCmd, "player.swing", swingCmd)),
                    new ScriptBudget(1_000_000, 64, new CancellationToken()));
            if (frame.step() != lastStep) {
                swingSlot.acknowledgeCommandDelivery();
                lastStep = frame.step();
            }
            if (frame.controllers().get(2).contributing()) {
                swingContributed = true;
            }
            nanos += 8_333_333L;
        }
        assertTrue(swingContributed, "2nd swing requested on a zero-step 120Hz frame must start on the following fixed step");
    }

    @Test
    void continuousMiningVanillaSwingWrapTriggersRestartEvenDuringLocalPulse() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        LivingEntityFrameState<LivingEntity> tracker = new LivingEntityFrameState<>(null);

        assertFalse(tracker.consumeVanillaSwingRestart(true, 0.0f, true));
        assertFalse(tracker.consumeVanillaSwingRestart(true, 0.17f, true));
        assertFalse(tracker.consumeVanillaSwingRestart(true, 0.33f, false));
        assertFalse(tracker.consumeVanillaSwingRestart(true, 0.50f, false));
        assertFalse(tracker.consumeVanillaSwingRestart(true, 0.67f, false));
        assertFalse(tracker.consumeVanillaSwingRestart(true, 1.0f, false));
        assertTrue(tracker.consumeVanillaSwingRestart(true, 0.17f, false),
                "Vanilla mining swing wrap must trigger a restart even while local swing pulse is active");
        assertFalse(tracker.consumeVanillaSwingRestart(true, 0.33f, false));

        assertFalse(tracker.consumeVanillaSwingRestart(false, 0.0f, false));
        assertTrue(tracker.consumeVanillaSwingRestart(true, 0.0f, false));
    }

    @Test
    void preLayerJsonControllersUnderlayWhilePostLayerJsonControllersOverlayPerBoneGroup() {
        assertTrue(HostAnimationProcessor.isPreLayerJsonController("player.pre_parallel_4"));
        assertTrue(HostAnimationProcessor.isPreLayerJsonController("player.pre_hold"));
        assertFalse(HostAnimationProcessor.isPreLayerJsonController("player.post_hold"));
        assertFalse(HostAnimationProcessor.isPreLayerJsonController("player.parallel_6"));

        BonePose bind = new BonePose(
                new float[]{0f, 0f, 0.3f, 0f, 0f, -0.3f},
                new float[]{0f, 0f, 0f, 0f, 0f, 0f},
                new float[]{1f, 1f, 1f, 1f, 1f, 1f},
                new boolean[]{false, false},
                new boolean[]{false, false},
                new int[]{255, 255});

        BonePose coded = new BonePose(
                new float[]{-0.5f, -0.15f, 0.47f, 0f, 0f, -0.3f},
                new float[]{0f, 0f, 0f, 0f, 0f, 0f},
                new float[]{1f, 1f, 1f, 1f, 1f, 1f},
                new boolean[]{false, false},
                new boolean[]{false, false},
                new int[]{255, 255});

        BonePose prePose = new BonePose(
                new float[]{0f, 0f, 0.31f, 0f, -1.57f, -0.82f},
                new float[]{0f, 0f, 0f, 0f, 0f, 0f},
                new float[]{1f, 1f, 1f, 1f, 1f, 1f},
                new boolean[]{false, false},
                new boolean[]{false, false},
                new int[]{255, 255});
        PoseFrame preFrame = new PoseFrame(1L, 0L, 1L, 1L, 0.016, 0.0, 0, "默认姿态", 0.016, false, prePose, Map.of(), List.of());

        BonePose composited = HostAnimationProcessor.compositeJsonPoses(coded, bind, List.of(preFrame), List.of());
        assertEquals(-0.5f, composited.rotations()[0], 1e-6f);
        assertEquals(-0.15f, composited.rotations()[1], 1e-6f);
        assertEquals(0.47f, composited.rotations()[2], 1e-6f);
        assertEquals(0f, composited.rotations()[3], 1e-6f);
        assertEquals(-1.57f, composited.rotations()[4], 1e-6f);
        assertEquals(-0.82f, composited.rotations()[5], 1e-6f);
    }

    @Test
    void firstPersonLanceHoldRespectsHoldOnLastFrame() throws Exception {
        String fpLance = RepoSourceLookup.readSource(
                "com/micaftic/morpher/client/animation/predicate/FirstPersonLanceAnimationPredicate.java");
        assertFalse(fpLance.contains("playIfPresent(event, animation, CodedLoopMode.LOOP)"),
                "FirstPersonLanceAnimationPredicate must respect HOLD_ON_LAST_FRAME on lance hold clips");
    }
}
