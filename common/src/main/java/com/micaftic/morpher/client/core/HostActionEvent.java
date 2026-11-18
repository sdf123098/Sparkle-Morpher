package com.micaftic.morpher.client.core;

import org.jetbrains.annotations.Nullable;

/**
 * Per-frame animation evaluation event passed to predicates and bindings, replacing {@code AnimationEvent}.
 */
public class HostActionEvent<T extends HostAnimatable<?>> {

    private final T animatable;
    private final float limbSwing;
    private final float limbSwingAmount;
    private final int tickCount;
    private final float partialTick;
    private final float frameTime;
    private final boolean moving;
    private final boolean isFirstPerson;
    private final HostEntityModelData modelData;
    public float currentTick;
    private @Nullable HostControllerSlot<T> controller;

    public HostActionEvent(
            T animatable,
            float limbSwing,
            float limbSwingAmount,
            int tickCount,
            float partialTick,
            float frameTime,
            boolean moving,
            boolean isFirstPerson,
            HostEntityModelData modelData) {
        this.animatable = animatable;
        this.limbSwing = limbSwing;
        this.limbSwingAmount = limbSwingAmount;
        this.tickCount = tickCount;
        this.partialTick = partialTick;
        this.frameTime = frameTime;
        this.moving = moving;
        this.isFirstPerson = isFirstPerson;
        this.modelData = modelData;
        this.currentTick = tickCount + partialTick;
    }

    public T getAnimatable() {
        return this.animatable;
    }

    public float getLimbSwing() {
        return this.limbSwing;
    }

    public float getLimbSwingAmount() {
        return this.limbSwingAmount;
    }

    public int getTickCount() {
        return this.tickCount;
    }

    public float getPartialTick() {
        return this.partialTick;
    }

    public float getFrameTime() {
        return this.frameTime;
    }

    public boolean isMoving() {
        return this.moving;
    }

    public boolean isFirstPerson() {
        return this.isFirstPerson;
    }

    public float getCurrentTick() {
        return this.currentTick;
    }

    public HostEntityModelData getModelData() {
        return this.modelData;
    }

    public @Nullable HostControllerSlot<T> getController() {
        return this.controller;
    }

    @SuppressWarnings("unchecked")
    public void setController(@Nullable HostControllerSlot<?> controller) {
        this.controller = (HostControllerSlot<T>) controller;
    }
}
