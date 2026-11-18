package com.micaftic.morpher.client.animation;

import org.apache.commons.lang3.StringUtils;

public class AnimationTracker {

    private String currentAnimation = "";

    private String previousAnimation = "";

    private String queuedAnimation = "";

    public String getCurrentAnimation() {
        return this.currentAnimation;
    }

    public void setCurrentAnimation(String animationName) {
        this.currentAnimation = animationName;
    }

    public boolean hasAnimation() {
        return StringUtils.isNoneBlank(this.currentAnimation);
    }

    public boolean isCurrentAnimation(String animationName) {
        return hasAnimation() && animationName.equals(this.currentAnimation);
    }

    public String getPreviousAnimation() {
        return this.previousAnimation;
    }

    public String getQueuedAnimation() {
        return this.queuedAnimation;
    }

    public void setPreviousAnimation(String animationName) {
        this.previousAnimation = animationName;
    }

    public void setQueuedAnimation(String animationName) {
        this.queuedAnimation = animationName;
    }
}
