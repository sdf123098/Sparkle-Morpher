package com.micaftic.morpher.mixin.compat;

import com.micaftic.morpher.core.compat.touhoulittlemaid.MaidModelSync;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid")
public abstract class TouhouMaidEntityMixin {
    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true, require = 0)
    private void sparkleMorpher$setMaidModel(Player player, InteractionHand hand,
                                             CallbackInfoReturnable<InteractionResult> cir) {
        if (MaidModelSync.handleInteraction((Entity) (Object) this, player, hand)) {
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void sparkleMorpher$syncMaidModel(CallbackInfo ci) {
        MaidModelSync.periodicSync((Entity) (Object) this);
    }

    @Inject(method = "setModelId", at = @At("TAIL"), require = 0, remap = false)
    private void sparkleMorpher$clearMaidModel(String modelId, CallbackInfo ci) {
        MaidModelSync.handleBaseModelChanged((Entity) (Object) this);
    }
}
