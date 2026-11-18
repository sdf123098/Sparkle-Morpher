package com.micaftic.morpher.client.animation;

import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostAnimationAssets.ClipSpec;

public class AnimationFormatValidator {
    public static boolean validate(HostActionEvent<?> event, String animationName, int version) {
        if (version >= 19) {
            return true;
        }
        ClipSpec animation = event.getAnimatable().getAnimation(animationName);
        if (animation == null) {
            return false;
        }
        return animation.isFromPrimaryAssembly();
    }
}
