package com.micaftic.morpher.mixin.compat;

import com.micaftic.morpher.YesSteveModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.compat.ysm.YsmCompat", remap = false)
public abstract class TouhouLittleMaidYsmCompatMixin {
    @Inject(method = "isInstalled", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void sparkleMorpher$enableYsmHook(CallbackInfoReturnable<Boolean> cir) {
        if (YesSteveModel.isAvailable()) {
            cir.setReturnValue(true);
        }
    }
}
