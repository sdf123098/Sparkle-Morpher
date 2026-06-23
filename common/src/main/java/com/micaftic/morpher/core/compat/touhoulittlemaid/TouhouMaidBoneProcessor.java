package com.micaftic.morpher.core.compat.touhoulittlemaid;

import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoBone;
import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import dev.architectury.injectables.annotations.ExpectPlatform;

public final class TouhouMaidBoneProcessor {

    private TouhouMaidBoneProcessor() {
    }

    @ExpectPlatform
    public static Object createLocationBone(AnimatedGeoBone bone) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static Object createLocationModel(AnimatedGeoModel model) {
        throw new AssertionError();
    }
}
