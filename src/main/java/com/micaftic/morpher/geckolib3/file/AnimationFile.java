package com.micaftic.morpher.geckolib3.file;

import com.micaftic.morpher.geckolib3.core.builder.Animation;
import it.unimi.dsi.fastutil.objects.Object2ReferenceLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMaps;

import java.util.Map;

public class AnimationFile {
    private final Map<String, Animation> animations;

    public AnimationFile(Map<String, Animation> map) {
        this.animations = Object2ReferenceMaps.unmodifiable(new Object2ReferenceLinkedOpenHashMap<>(map));
    }

    public Map<String, Animation> getAnimations() {
        return this.animations;
    }
}