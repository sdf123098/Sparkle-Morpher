package com.micaftic.morpher.core.compat.touhoulittlemaid;

import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoBone;
import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;

public final class TouhouMaidBoneProcessor {

    private TouhouMaidBoneProcessor() {
    }

    public static Object createLocationBone(AnimatedGeoBone bone) {
        return com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouMaidBoneProcessorImpl.createLocationBone(bone);
    }

    public static Object createLocationModel(AnimatedGeoModel model) {
        return com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouMaidBoneProcessorImpl.createLocationModel(model);
    }
}
