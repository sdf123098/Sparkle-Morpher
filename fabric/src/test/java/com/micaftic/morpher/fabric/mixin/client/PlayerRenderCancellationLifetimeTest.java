package com.micaftic.morpher.fabric.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression for Icy's Better Horses 2.0.0: its submit HEAD opens a rider pose
 * and render context, and its RETURN closes them. A cancellable HEAD must run
 * before that scope opens. The fixture covers Icy-first discovery at equal
 * priority; an exported LivingEntityRenderer verifies actual Mixin ordering.
 */
class PlayerRenderCancellationLifetimeTest {
    @Test
    void customMountedPlayerDoesNotLeakHorsePoseOrContext() throws Exception {
        Result result = submit(true, true, playerMixinPriority());
        assertTrue(result.poseStack.isEmpty(), "Pose stack not empty after custom rider cancellation");
        assertEquals(0, result.horseContextDepth, "Horse render context must not survive cancellation");
    }

    @Test
    void vanillaMountedPlayerStillRunsHorseScope() throws Exception {
        Result result = submit(false, true, playerMixinPriority());
        assertTrue(result.horsePoseApplied, "Vanilla riders must keep Icy's saddle transform");
        assertTrue(result.poseStack.isEmpty());
        assertEquals(0, result.horseContextDepth);
    }

    @Test
    void unmountedCustomPlayerDoesNotLeakHorseContext() throws Exception {
        Result result = submit(true, false, playerMixinPriority());
        assertTrue(result.poseStack.isEmpty());
        assertEquals(0, result.horseContextDepth);
    }

    @Test
    void equalPriorityFixtureReproducesReportedPoseLeak() {
        Result result = submit(true, true, 1000);
        assertFalse(result.poseStack.isEmpty());
        assertEquals(1, result.horseContextDepth);
    }

    private static Result submit(boolean custom, boolean mounted, int playerPriority) {
        Result result = new Result();
        // MixinInfo.compareTo sorts lower priorities first, then discovery order.
        ArrayList<Head> heads = new ArrayList<>();
        heads.add(new Head(1000, false));
        heads.add(new Head(playerPriority, true));
        heads.sort(Comparator.comparingInt(Head::priority));
        for (Head head : heads) {
            if (head.player) {
                if (custom) return result; // cancellable HEAD skips RETURN injections
            } else {
                result.horseContextDepth++;
                if (mounted) {
                    result.poseStack.pushPose();
                    result.horsePoseApplied = true;
                }
            }
        }
        if (mounted) result.poseStack.popPose();
        result.horseContextDepth--;
        return result;
    }

    private static int playerMixinPriority() throws Exception {
        String relative = "fabric/src/main/java/com/micaftic/morpher/fabric/mixin/client/PlayerRendererMixin.java";
        for (Path base = Path.of("").toAbsolutePath(); base != null; base = base.getParent()) {
            Path source = base.resolve(relative);
            if (!Files.isRegularFile(source)) continue;
            var annotation = Pattern.compile("@Mixin\\((.*?)\\)\\s*public abstract class PlayerRendererMixin", Pattern.DOTALL)
                    .matcher(Files.readString(source));
            assertTrue(annotation.find(), "Player renderer mixin annotation missing");
            var priority = Pattern.compile("priority\\s*=\\s*(\\d+)").matcher(annotation.group(1));
            return priority.find() ? Integer.parseInt(priority.group(1)) : 1000;
        }
        throw new IllegalStateException("Player renderer mixin source unavailable");
    }

    private record Head(int priority, boolean player) {}

    private static final class Result {
        final PoseStack poseStack = new PoseStack();
        int horseContextDepth;
        boolean horsePoseApplied;
    }
}
