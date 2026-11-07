package com.micaftic.morpher.client.core;

import dev.spm.core.api.BonePose;
import dev.spm.core.api.Skeleton;
import dev.spm.core.scene.EffectCommand;
import dev.spm.core.scene.MolangValue;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

final class CoreBoneEffectsTest {
    private static Skeleton skeleton() { return new Skeleton(List.of("root"), new int[]{-1}, new float[3]); }
    private static EffectCommand command(String function, String bone, double... values) {
        var args = new java.util.ArrayList<MolangValue>();
        args.add(MolangValue.text(bone));
        for (double value : values) args.add(MolangValue.number(value));
        return new EffectCommand(1, 0, 0, 0, function, function, Map.of(), args);
    }
    @Test void colorAlphaAndGlowUseFrozenCoreByteOrderAndSourceRounding() {
        var effect = new CoreBoneEffects();
        var pose = effect.apply(skeleton(), BonePose.identity(1), List.of(
                command("ysm.bone_color", "root", 300, 63.6, -2),
                command("ysm.bone_transparency", "root", 128), command("ysm.bone_glow", "root", -1)));
        assertEquals(0x800040FF, pose.colorsRgba()[0]);
        assertEquals(255, pose.glow()[0]);
        assertEquals(0xFFFFFFFF, BonePose.identity(1).colorsRgba()[0]);
    }
    @Test void stylesPersistWithinOneOwnerAndDoNotLeakToAnotherOwner() {
        var first = new CoreBoneEffects();
        first.apply(skeleton(), BonePose.identity(1), List.of(command("ysm.bone_glow", "root", 7)));
        assertEquals(7, first.apply(skeleton(), BonePose.identity(1), List.of()).glow()[0]);
        assertEquals(255, new CoreBoneEffects().apply(skeleton(), BonePose.identity(1), List.of()).glow()[0]);
    }
    @Test void unknownBonesAreNoopsAndInvalidBatchDoesNotPublishEarlierStyleChanges() {
        var effect = new CoreBoneEffects();
        assertEquals(255, effect.apply(skeleton(), BonePose.identity(1), List.of(command("ysm.bone_glow", "missing", 7))).glow()[0]);
        assertThrows(IllegalArgumentException.class, () -> effect.apply(skeleton(), BonePose.identity(1), List.of(
                command("ysm.bone_glow", "root", 7), command("ysm.bone_color", "root", 1))));
        assertEquals(255, effect.apply(skeleton(), BonePose.identity(1), List.of()).glow()[0]);
    }
}
