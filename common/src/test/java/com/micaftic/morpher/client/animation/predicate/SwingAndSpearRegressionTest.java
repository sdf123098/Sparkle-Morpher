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
import com.micaftic.morpher.resource.YSMFolderDeserializer;
import com.micaftic.morpher.resource.bundle.ClientModelBundleAssembler;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import net.minecraft.world.entity.LivingEntity;
import dev.spm.core.api.BonePose;
import dev.spm.core.api.CancellationToken;
import dev.spm.core.api.Skeleton;
import dev.spm.core.scene.CodedAnimationCollection;
import dev.spm.core.scene.CodedControllerCommand;
import dev.spm.core.scene.CodedControllerDefinition;
import dev.spm.core.scene.CodedLoopMode;
import dev.spm.core.scene.CodedPoseFrame;
import dev.spm.core.scene.MolangBindings;
import dev.spm.core.scene.MolangHooks;
import dev.spm.core.scene.PoseAnimationLibrary;
import dev.spm.core.scene.PoseFrame;
import dev.spm.core.scene.SceneInputs;
import dev.spm.core.scene.ScriptBudget;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SwingAndSpearRegressionTest {

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

        // Zero-step render frame: host has NOT acknowledged delivery yet, so reload must stay latched
        CodedControllerCommand frame1 = slot.evaluateCommand(event, false);
        assertTrue(frame1.reload(), "Reload must stay latched across zero-step frames until a simulation step consumes it");

        // Once a fixed step advances and host acknowledges delivery, reload clears
        slot.acknowledgeCommandDelivery();
        CodedControllerCommand frame2 = slot.evaluateCommand(event, false);
        assertFalse(frame2.reload(), "Reload must clear after delivery is acknowledged");
    }

    @Test
    void swordSwingRestartsCleanlyEvenWhenTriggeredOnZeroStepFrameAt120Hz() throws Exception {
        Path defaultDir = Path.of("../common/src/main/resources/assets/sparkle_morpher/builtin/default");
        RawYsmModel raw;
        try (var d = new YSMFolderDeserializer(defaultDir)) {
            raw = d.deserialize();
        }
        var info = ClientModelBundleAssembler.buildParsedBundle(raw, "default");
        BuiltModel model = info.getMainModelData().getModels().get(0);
        var clips = new LinkedHashMap<String, HostAnimationAssets.ClipSpec>();
        for (var e : info.getMainModelData().getAnimations().entrySet()) {
            if (!"fp_arm".equals(e.getKey())) {
                clips.putAll(e.getValue().getAnimations());
            }
        }
        Skeleton sk = HostAnimationAssets.buildSkeleton(model);
        BonePose bind = HostAnimationAssets.buildBindPose(model);
        PoseAnimationLibrary lib = HostAnimationAssets.compileLibrary("test:swing-120hz", clips.values(), sk, bind);
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

        // 1. Trigger 1st swing and let it finish completely
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

        // 2. Align right after a step advance so the very next 8.33ms frame executes 0 steps
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

        // 3. Trigger 2nd swing on that exact zero-step frame and verify it starts on the next step
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
        LivingEntityFrameState<LivingEntity> tracker = new LivingEntityFrameState<>(null);

        // Tick 0: LMB click starts local pulse + vanilla swing (initial start suppressed to avoid double trigger)
        assertFalse(tracker.consumeVanillaSwingRestart(true, 0.0f, true));
        // Ticks 1..4: progress monotonically increases
        assertFalse(tracker.consumeVanillaSwingRestart(true, 0.17f, true));
        assertFalse(tracker.consumeVanillaSwingRestart(true, 0.33f, false));
        assertFalse(tracker.consumeVanillaSwingRestart(true, 0.50f, false));
        assertFalse(tracker.consumeVanillaSwingRestart(true, 0.67f, false));
        // Tick 5/6: continuous mining restarts vanilla swing -> progress wraps 1.0 -> 0.17 (or 0.67 -> 0.0)
        assertFalse(tracker.consumeVanillaSwingRestart(true, 1.0f, false));
        assertTrue(tracker.consumeVanillaSwingRestart(true, 0.17f, false),
                "Vanilla mining swing wrap must trigger a restart even while local swing pulse is active");
        // Next tick continues smoothly without re-triggering
        assertFalse(tracker.consumeVanillaSwingRestart(true, 0.33f, false));

        // Release LMB -> resets cleanly; a subsequent vanilla-only swing starts cleanly
        assertFalse(tracker.consumeVanillaSwingRestart(false, 0.0f, false));
        assertTrue(tracker.consumeVanillaSwingRestart(true, 0.0f, false));
    }

    @Test
    void spearChargeTimingReachesExhaustedPhaseForAllMaterialTiers() {
        // Netherite: delay=8, dismount=60 -> 68, knockback=115 -> 123, damage=175 -> 183t (9.15s)
        // Wooden:    delay=15, dismount=100 -> 115, knockback=200 -> 215, damage=300 -> 315t (15.75s)
        double clipLen = 13.0;
        double netheriteAtExhaustStart = LanceAnimationTiming.computeTargetChargeSeconds(183.0, 8, 68, 123, 183, clipLen);
        double netheriteAtExhaustEnd = LanceAnimationTiming.computeTargetChargeSeconds(193.0, 8, 68, 123, 183, clipLen);
        assertEquals(12.0, netheriteAtExhaustStart, 1e-6, "Netherite spear must enter 12.0s exhausted drop right at 183t");
        assertEquals(13.0, netheriteAtExhaustEnd, 1e-6, "Netherite spear must finish exhausted drop at 13.0s");

        double woodAtNetheriteTime = LanceAnimationTiming.computeTargetChargeSeconds(183.0, 15, 115, 215, 315, clipLen);
        double woodAtExhaustStart = LanceAnimationTiming.computeTargetChargeSeconds(315.0, 15, 115, 215, 315, clipLen);
        double woodAtExhaustEnd = LanceAnimationTiming.computeTargetChargeSeconds(325.0, 15, 115, 215, 315, clipLen);
        assertTrue(woodAtNetheriteTime < 8.0, "Wooden spear must still be in earlier phase at 183t");
        assertEquals(12.0, woodAtExhaustStart, 1e-6, "Wooden spear must enter 12.0s exhausted drop right at 315t");
        assertEquals(13.0, woodAtExhaustEnd, 1e-6, "Wooden spear must finish exhausted drop at 13.0s");

        // Closed-loop simulation for Netherite spear reaches >= 12.9s by tick 195 (9.75s)
        double animTime = 0.0;
        for (int tick = 0; tick <= 195; tick++) {
            double speed = LanceAnimationTiming.computeStageSyncedSpeed(tick, 8, 68, 123, 183, clipLen, animTime);
            assertTrue(speed > 0.0 && Double.isFinite(speed));
            animTime = Math.min(clipLen, animTime + (1.0 / 20.0) * speed);
        }
        assertTrue(animTime >= 12.9, "Closed-loop speed pacing must carry Netherite spear into the 13.0s exhausted pose, got " + animTime);
    }

    @Test
    void preLayerJsonControllersUnderlayWhilePostLayerJsonControllersOverlayPerBoneGroup() {
        assertTrue(HostAnimationProcessor.isPreLayerJsonController("player.pre_parallel_4"));
        assertTrue(HostAnimationProcessor.isPreLayerJsonController("player.pre_hold"));
        assertFalse(HostAnimationProcessor.isPreLayerJsonController("player.post_hold"));
        assertFalse(HostAnimationProcessor.isPreLayerJsonController("player.parallel_6"));

        // 2 bones: bone 0 = RightArm (bind Z=0.3), bone 1 = LeftArm (bind Z=-0.3)
        BonePose bind = new BonePose(
                new float[]{0f, 0f, 0.3f, 0f, 0f, -0.3f},
                new float[]{0f, 0f, 0f, 0f, 0f, 0f},
                new float[]{1f, 1f, 1f, 1f, 1f, 1f},
                new boolean[]{false, false},
                new boolean[]{false, false},
                new int[]{255, 255});

        // Coded hold_mainhand:lance moves RightArm (bone 0) to (-0.5, -0.15, 0.47), leaves LeftArm (bone 1) at bind
        BonePose coded = new BonePose(
                new float[]{-0.5f, -0.15f, 0.47f, 0f, 0f, -0.3f},
                new float[]{0f, 0f, 0f, 0f, 0f, 0f},
                new float[]{1f, 1f, 1f, 1f, 1f, 1f},
                new boolean[]{false, false},
                new boolean[]{false, false},
                new int[]{255, 255});

        // Furina pre_parallel_4 ("默认姿态") moves BOTH RightArm Z (0, 0, 0.31) and LeftArm (0, -1.57, -0.82)
        BonePose prePose = new BonePose(
                new float[]{0f, 0f, 0.31f, 0f, -1.57f, -0.82f},
                new float[]{0f, 0f, 0f, 0f, 0f, 0f},
                new float[]{1f, 1f, 1f, 1f, 1f, 1f},
                new boolean[]{false, false},
                new boolean[]{false, false},
                new int[]{255, 255});
        PoseFrame preFrame = new PoseFrame(1L, 0L, 1L, 1L, 0.016, 0.0, 0, "默认姿态", 0.016, false, prePose, Map.of(), List.of());

        BonePose composited = HostAnimationProcessor.compositeJsonPoses(coded, bind, List.of(preFrame), List.of());
        // RightArm must keep ALL 3 axes from hold_mainhand:lance (NOT have Z overwritten by pre_parallel_4!)
        assertEquals(-0.5f, composited.rotations()[0], 1e-6f);
        assertEquals(-0.15f, composited.rotations()[1], 1e-6f);
        assertEquals(0.47f, composited.rotations()[2], 1e-6f);
        // LeftArm was untouched by hold_mainhand:lance, so pre_parallel_4 cleanly supplies its idle pose
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
