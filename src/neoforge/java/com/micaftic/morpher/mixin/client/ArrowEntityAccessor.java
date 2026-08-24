package com.micaftic.morpher.mixin.client;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashSet;
import java.util.Set;

@Mixin(AbstractArrow.class)
public abstract class ArrowEntityAccessor {

    @Shadow
    protected abstract ItemStack getPickupItem();

    @Unique
    public Set<MobEffectInstance> getEffects() {
        ItemStack pickup = getPickupItem();
        PotionContents contents = pickup.get(DataComponents.POTION_CONTENTS);
        if (contents != null) {
            Set<MobEffectInstance> set = new HashSet<>();
            contents.forEachEffect(e -> set.add(e), 1.0f);
            return set;
        }
        return Set.of();
    }
}
