package com.micaftic.morpher.client.renderer.modernhud;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.client.model.HandLocatorProfile;
import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import com.micaftic.morpher.geckolib3.geo.render.built.GeoBone;
import com.micaftic.morpher.resource.models.GeometryDescription;
import net.minecraft.world.entity.HumanoidArm;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ModernHudHandItemLayoutTest {
    @Test
    void authoredHiddenLocatorSuppressesHudItemInsteadOfUsingFallback() {
        AnimatedGeoModel model = model(true);
        var right = model.rightHandBones().getLast();
        right.setScaleX(0); right.setScaleY(0); right.setScaleZ(0);
        assertNull(locate(model, HumanoidArm.RIGHT));
        assertNotNull(locate(model, HumanoidArm.LEFT));
        right.setScaleX(1); right.setScaleY(1); right.setScaleZ(1);
        assertNotNull(locate(model, HumanoidArm.RIGHT));
    }

    @Test
    void missingLocatorKeepsFallbackAndMirroredLocatorRemainsVisible() {
        assertNotNull(locate(model(false), HumanoidArm.RIGHT));
        AnimatedGeoModel model = model(true);
        model.rightHandBones().getLast().setScaleX(-1);
        assertNotNull(locate(model, HumanoidArm.RIGHT));
    }

    private static org.joml.Vector3f locate(AnimatedGeoModel model, HumanoidArm arm) {
        return ModernHudHandItemLayout.locate(model, HandLocatorProfile.YSM_AUTHORED, arm, 10, 20, 10, 0);
    }

    private static AnimatedGeoModel model(boolean locators) {
        String[][] paths = new String[37][];
        Arrays.setAll(paths, i -> new String[0]);
        GeoBone[] bones = locators ? new GeoBone[] {
                new GeoBone("LeftHandLocator", false, false, false, 0, 0, 0, 0, 0, 0),
                new GeoBone("RightHandLocator", false, false, false, 0, 0, 0, 0, 0, 0)} : new GeoBone[0];
        if (locators) {
            paths[0] = new String[] {"LeftHandLocator"};
            paths[1] = new String[] {"RightHandLocator"};
        }
        return new AnimatedGeoModel(new GeoModel(bones, paths, new boolean[3],
                new GeometryDescription("qa", 16, 16, 1, 1, new double[3]), new boolean[1]));
    }
}
