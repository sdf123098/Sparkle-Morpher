package com.micaftic.morpher.mixin;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.util.accessors.ProjectileStateAccessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({AbstractArrow.class})
public class AbstractArrowEntityMixin implements ProjectileStateAccessor {

    @Unique
    private volatile String ownerMainHandItem = StringPool.EMPTY;

    @Shadow
    public boolean inGround;

    @Shadow
    public int inGroundTime;

    @Override
    @Unique
    public boolean isInGround() {
        return this.inGround;
    }

    @Override
    @Unique
    public int getInGroundTime() {
        return this.inGroundTime;
    }

    @Override
    @Unique
    public String getOwnerItemId() {
        return this.ownerMainHandItem;
    }

    @Override
    @Unique
    public void ysm$setDisplayFiringItem(String itemId) {
        this.ownerMainHandItem = itemId == null ? StringPool.EMPTY : itemId;
    }
}
