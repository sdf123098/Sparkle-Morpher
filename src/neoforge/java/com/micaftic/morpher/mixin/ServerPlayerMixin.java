package com.micaftic.morpher.mixin;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.event.CapabilityEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ServerPlayer.class})
public abstract class ServerPlayerMixin {
    @Inject(method = {"startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z"}, at = @At("RETURN"))
    private void onStartRiding(Entity entity, boolean force, boolean forceNonLiving, CallbackInfoReturnable<Boolean> ci) {
        Entity entity2;
        if (YesSteveModel.isAvailable() && entity.getFirstPassenger() == (entity2 = (ServerPlayer) (Object) this)) {
            CapabilityEvent.syncVehicleModel(entity, (ServerPlayer) entity2);
        }
    }
}