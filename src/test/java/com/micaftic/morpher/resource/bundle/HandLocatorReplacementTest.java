package com.micaftic.morpher.resource.bundle;

import com.micaftic.morpher.client.model.SpecialHandLocatorProfile;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HandLocatorReplacementTest {
    private static final Map<String, String> PARENTS = Map.of(
            "LeftHandLocator", "LeftHand", "RightHandLocator", "RightHand");

    @Test
    void weaponReplacementMustNotEnableCarryOnSwordScaleBypass() {
        RawYsmModel raw = model("main", 1, "hold_mainhand:sword");
        assertEquals(SpecialHandLocatorProfile.NONE,
                ClientModelBundleAssembler.detectSpecialHandLocatorProfile(raw, PARENTS));
    }

    @Test
    void carryOnAndAuthoredWeaponStillRespectWeaponHiding() {
        RawYsmModel raw = model("carryon", 6, "carryon:block");
        raw.mainEntity.animationFiles.putAll(model("main", 1, "swing:sword").mainEntity.animationFiles);
        assertEquals(SpecialHandLocatorProfile.NONE,
                ClientModelBundleAssembler.detectSpecialHandLocatorProfile(raw, PARENTS));
    }

    @Test
    void toolReplacementAlsoKeepsAuthoredScale() {
        RawYsmModel raw = model("main", 1, "hold_offhand:pickaxe");
        assertEquals(SpecialHandLocatorProfile.NONE,
                ClientModelBundleAssembler.detectSpecialHandLocatorProfile(raw, PARENTS));
    }

    @Test
    void carryOnOnlyCompatibilityRemainsAvailable() {
        RawYsmModel raw = model("carryon", 6, "carryon:block");
        assertEquals(SpecialHandLocatorProfile.HAND_LOCATOR_HIDDEN_BY_CARRYON,
                ClientModelBundleAssembler.detectSpecialHandLocatorProfile(raw, PARENTS));
    }

    @Test
    void unrelatedAnimationInCarryOnFileDoesNotEnableBypass() {
        RawYsmModel raw = model("carryon", 6, "hold_mainhand:sword");
        assertEquals(SpecialHandLocatorProfile.NONE,
                ClientModelBundleAssembler.detectSpecialHandLocatorProfile(raw, PARENTS));
    }

    private static RawYsmModel model(String file, int type, String animationName) {
        RawYsmModel raw = new RawYsmModel();
        RawYsmModel.RawAnimationFile animations = new RawYsmModel.RawAnimationFile();
        animations.animType = type;
        RawYsmModel.RawAnimation animation = new RawYsmModel.RawAnimation();
        animation.name = animationName;
        RawYsmModel.RawBoneAnimation bone = new RawYsmModel.RawBoneAnimation();
        bone.boneName = "RightHandLocator";
        RawYsmModel.RawKeyframe keyframe = new RawYsmModel.RawKeyframe();
        keyframe.postData = new Object[] {0f, 0f, 0f};
        bone.scale.add(keyframe);
        animation.boneAnimations.add(bone);
        animations.animations.put(animationName, animation);
        raw.mainEntity.animationFiles.put(file, animations);
        return raw;
    }
}
