package com.micaftic.morpher.client.entity;

import com.micaftic.morpher.client.core.HostEntityFrameState;
import com.micaftic.morpher.core.compat.immersivemelodies.ImmersiveMelodiesCompat;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class LivingEntityFrameState<T extends LivingEntity> extends HostEntityFrameState<T> {

    private final ImmersiveMelodiesCompat.ImmersiveMelodiesData imData;

    private ItemStack mainHandItem;

    private ItemStack offHandItem;

    private boolean fishing;

    private int processedSwingPulseSequence;

    public LivingEntityFrameState(T t) {
        super(t);
        this.imData = new ImmersiveMelodiesCompat.ImmersiveMelodiesData();
        this.mainHandItem = ItemStack.EMPTY;
        this.offHandItem = ItemStack.EMPTY;
        this.fishing = false;
        this.processedSwingPulseSequence = 0;
    }

    @Override
    public void reset() {
        this.mainHandItem = ItemStack.EMPTY;
        this.offHandItem = ItemStack.EMPTY;
        this.fishing = false;
        this.processedSwingPulseSequence = 0;
        super.reset();
    }

    @Override
    public void onTimeUpdate(float currentTick, float deltaTick, float partialTick) {
        super.onTimeUpdate(currentTick, deltaTick, partialTick);
        // 更新沉浸式奏乐数据
        ImmersiveMelodiesCompat.updateMelodyProgress(this.entity, this.imData);
    }

    public ItemStack getHandItemsForAnimation(InteractionHand interactionHand) {
        if (interactionHand == InteractionHand.MAIN_HAND) {
            return this.mainHandItem;
        }
        return this.offHandItem;
    }

    public void setHandItemsForAnimation(ItemStack itemStack, InteractionHand interactionHand) {
        if (interactionHand == InteractionHand.MAIN_HAND) {
            this.mainHandItem = itemStack;
        } else {
            this.offHandItem = itemStack;
        }
    }

    public boolean updateFishingForAnimation(boolean fishing) {
        boolean changed = this.fishing != fishing;
        this.fishing = fishing;
        return changed;
    }

    private float lastVanillaSwingProgress = -1.0f;
    private boolean lastVanillaSwinging;

    public boolean markSwingPulseProcessed(int sequence) {
        if (sequence <= 0 || this.processedSwingPulseSequence == sequence) {
            return false;
        }
        this.processedSwingPulseSequence = sequence;
        return true;
    }

    public boolean consumeVanillaSwingRestart(boolean vanillaSwinging, float vanillaSwingProgress, boolean suppressInitialStart) {
        if (!vanillaSwinging) {
            this.lastVanillaSwinging = false;
            this.lastVanillaSwingProgress = -1.0f;
            return false;
        }
        float clamped = Float.isFinite(vanillaSwingProgress)
                ? Math.max(0.0f, Math.min(1.0f, vanillaSwingProgress))
                : 0.0f;
        boolean started = !this.lastVanillaSwinging;
        boolean wrapped = this.lastVanillaSwinging
                && this.lastVanillaSwingProgress >= 0.0f
                && clamped + 0.25f < this.lastVanillaSwingProgress;
        this.lastVanillaSwinging = true;
        this.lastVanillaSwingProgress = clamped;
        if (wrapped) {
            return true;
        }
        return started && !suppressInitialStart;
    }

    public void resetVanillaSwingTracking() {
        this.lastVanillaSwinging = false;
        this.lastVanillaSwingProgress = -1.0f;
    }

    public ImmersiveMelodiesCompat.ImmersiveMelodiesData getImmersiveMelodiesData() {
        return this.imData;
    }
}
