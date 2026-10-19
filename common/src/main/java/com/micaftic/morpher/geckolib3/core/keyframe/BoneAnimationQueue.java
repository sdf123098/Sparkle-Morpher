package com.micaftic.morpher.geckolib3.core.keyframe;

import com.micaftic.morpher.geckolib3.core.controller.PredicateBasedController;
import com.micaftic.morpher.geckolib3.core.keyframe.bone.BoneKeyFrame;
import com.micaftic.morpher.geckolib3.core.snapshot.BoneSnapshot;
import com.micaftic.morpher.geckolib3.core.snapshot.BoneTopLevelSnapshot;
import com.micaftic.morpher.geckolib3.util.InterpolationLookup;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class BoneAnimationQueue {

    public final BoneTopLevelSnapshot topLevelSnapshot;

    public final BoneSnapshot controllerSnapshot;

    @Nullable
    public InterpolationLookup<BoneKeyFrame> rotationTimeline;

    @Nullable
    public InterpolationLookup<BoneKeyFrame> positionTimeline;

    @Nullable
    public InterpolationLookup<BoneKeyFrame> scaleTimeline;

    private boolean animationActive = false;

    private float blendWeight = 1.0f;
    private final com.micaftic.morpher.client.animation.molang.PoseValueCache[] validPose = {
            new com.micaftic.morpher.client.animation.molang.PoseValueCache(new Vector3f()),
            new com.micaftic.morpher.client.animation.molang.PoseValueCache(new Vector3f()),
            new com.micaftic.morpher.client.animation.molang.PoseValueCache(new Vector3f(1))};
    private boolean reportedInvalidPose;
    public String sourceAnimation;

    public Vector3f samplePose(AnimationPoint point, com.micaftic.morpher.molang.runtime.ExpressionEvaluator<com.micaftic.morpher.geckolib3.core.molang.context.AnimationContext<?>> evaluator, int channel, boolean rawTransition) {
        Vector3f value=validPose[channel].sample(()->rawTransition && point instanceof TransitionPoint transition
                ? transition.evaluateRaw(evaluator) : point.getLerpPoint(evaluator));
        if(validPose[channel].rejected()) {
            if(point.cachedValue==null) point.cachedValue=new Vector3f(value); else point.cachedValue.set(value);
            (channel==0 ? controllerSnapshot.rotation : channel==1 ? controllerSnapshot.position : controllerSnapshot.scale).set(value);
            if(!reportedInvalidPose) {
                reportedInvalidPose=true;
                com.micaftic.morpher.YesSteveModel.LOGGER.warn("[SM-PHYSICS] Invalid animation source held at last valid pose: entity={} animation={} bone={} channel={}",
                        com.micaftic.morpher.client.animation.molang.EntityLogIdentity.describe(()->evaluator.entity().geoInstance().entity.getId()),sourceAnimation,topLevelSnapshot.boneId,channel);
            }
        }
        return value;
    }

    public Vector3f positionOutput;

    public Vector3f rotationOutput;

    public Vector3f scaleOutput;

    public boolean overrideMode;

    public AnimationPoint rotationQueue;

    public AnimationPoint positionQueue;

    public AnimationPoint scaleQueue;

    public final PredicateBasedController.TransformProviderRecord transformProviderRecord;

    public BoneAnimationQueue(BoneTopLevelSnapshot snapshot) {
        this.topLevelSnapshot = snapshot;
        this.controllerSnapshot = new BoneSnapshot(snapshot.bone);
        this.transformProviderRecord = new PredicateBasedController.TransformProviderRecord(this);
    }

    public void applyAnimation(BoneAnimation animation, boolean z) {
        if (!animation.rotationKeyFrames.isEmpty()) {
            this.rotationTimeline = new InterpolationLookup<>(animation.rotationKeyFrames, 0.0f, BoneKeyFrame::getEndTick);
        } else {
            this.rotationTimeline = null;
        }
        if (!animation.positionKeyFrames.isEmpty()) {
            this.positionTimeline = new InterpolationLookup<>(animation.positionKeyFrames, 0.0f, BoneKeyFrame::getEndTick);
        } else {
            this.positionTimeline = null;
        }
        if (!animation.scaleKeyFrames.isEmpty()) {
            this.scaleTimeline = new InterpolationLookup<>(animation.scaleKeyFrames, 0.0f, BoneKeyFrame::getEndTick);
        } else {
            this.scaleTimeline = null;
        }
        this.controllerSnapshot.applyTransform(this.topLevelSnapshot.bone);
        this.animationActive = true;
        this.overrideMode = z;
        resetQueues();
    }

    public BoneSnapshot snapshot() {
        return this.controllerSnapshot;
    }

    public AnimationPoint rotationQueue() {
        return this.rotationQueue;
    }

    public AnimationPoint positionQueue() {
        return this.positionQueue;
    }

    public AnimationPoint scaleQueue() {
        return this.scaleQueue;
    }

    public boolean isActive() {
        return this.animationActive;
    }

    public void clear() {
        this.rotationTimeline = null;
        this.positionTimeline = null;
        this.scaleTimeline = null;
        this.positionOutput = null;
        this.rotationOutput = null;
        this.scaleOutput = null;
        this.animationActive = false;
        resetQueues();
    }

    public float getBlendWeight() {
        return this.blendWeight;
    }

    public void setBlendWeight(float blendWeight) {
        if (!Float.isFinite(blendWeight)) { com.micaftic.morpher.client.animation.molang.EvaluationHealth.invalid(); return; }
        this.blendWeight = blendWeight > 0.0f ? blendWeight : 0.0f;
    }

    public void resetQueues() {
        this.rotationQueue = null;
        this.positionQueue = null;
        this.scaleQueue = null;
    }
}
