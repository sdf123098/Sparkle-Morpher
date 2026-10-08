package com.micaftic.morpher.mixin.client;

import com.micaftic.morpher.cloud.client.CloudProjectileModelSync;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Read-only capture around native input. All game use/packets remain vanilla. */
@Mixin(MultiPlayerGameMode.class)
public final class ProjectileFiringInputMixin {
    @Unique private CloudProjectileModelSync.Ticket spm$firing;
    @Inject(method="useItem",at=@At("HEAD"))
    private void spm$captureUse(Player player,InteractionHand hand,CallbackInfoReturnable<InteractionResult> ci){spm$firing=CloudProjectileModelSync.captureUse(player,hand);}
    @Inject(method="useItem",at=@At("RETURN"))
    private void spm$commitUse(Player player,InteractionHand hand,CallbackInfoReturnable<InteractionResult> ci){var ticket=spm$firing;spm$firing=null;CloudProjectileModelSync.commit(ticket,ci.getReturnValue().consumesAction());}
    @Inject(method="releaseUsingItem",at=@At("HEAD"))
    private void spm$captureRelease(Player player,CallbackInfo ci){CloudProjectileModelSync.captureRelease(player);}
}
