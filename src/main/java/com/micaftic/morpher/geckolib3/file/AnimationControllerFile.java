package com.micaftic.morpher.geckolib3.file;

import com.micaftic.morpher.geckolib3.core.builder.AnimationController;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMaps;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;

import java.util.Map;

public class AnimationControllerFile {

    private final Map<String, AnimationController> animationControllers;

    public AnimationControllerFile(Map<String, AnimationController> animationControllers) {
        this.animationControllers = Object2ReferenceMaps.unmodifiable(new Object2ReferenceOpenHashMap<>(animationControllers));
    }

    public Map<String, AnimationController> getAnimationControllers() {
        return this.animationControllers;
    }
}