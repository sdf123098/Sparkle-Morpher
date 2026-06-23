package com.micaftic.morpher.mixin.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface EntityRidingAccessor {

    @Invoker("getPassengerAttachmentPoint")
    Vec3 invokeGetPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick);
}
