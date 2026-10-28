package com.micaftic.morpher.client.animation.particle;

import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import org.joml.Matrix4f;

public final class AnimationParticleRuntime {
    private AnimationParticleRuntime() {}

    public static void flush(AnimatableEntity<?> owner, AnimationEvent<?> event,
                             Matrix4f referencePose, Matrix4f renderedPose) {
        var model = owner.getCurrentModel();
        if (model == null || !model.animationParticles.hasPending()
                || (ModelPreviewRenderer.isPreview() && !ModelPreviewRenderer.isWorldRender())) return;
        // Cancel camera/dispatch transforms, keeping the renderer's actual pose and model scale.
        var root = new Matrix4f(referencePose).invert().mul(renderedPose);
        long key = AnimationParticleQueue.sampleKey(event.getTickCount(), event.getFrameTime());
        model.animationParticles.drain(key, locator -> AnimationParticleLocator.resolve(
                model.getGeoModel().topLevelBones(), model.getMatrixData(), locator, root));
    }
}
