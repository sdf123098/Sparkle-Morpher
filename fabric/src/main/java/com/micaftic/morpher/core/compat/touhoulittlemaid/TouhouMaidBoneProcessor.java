package com.micaftic.morpher.core.compat.touhoulittlemaid;

import com.micaftic.morpher.client.render.ModelBoneView;
import com.micaftic.morpher.client.render.ModelScenePose;

public final class TouhouMaidBoneProcessor {

    private TouhouMaidBoneProcessor() {
    }

    public static Object createLocationBone(ModelBoneView bone) {
        return com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouMaidBoneProcessorImpl.createLocationBone(bone);
    }

    public static Object createLocationModel(ModelScenePose model) {
        return com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouMaidBoneProcessorImpl.createLocationModel(model);
    }
}
