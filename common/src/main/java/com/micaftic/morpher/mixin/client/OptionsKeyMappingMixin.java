package com.micaftic.morpher.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Options.class)
public abstract class OptionsKeyMappingMixin {
    @Shadow @Final public KeyMapping[] keyMappings;

    @Inject(method = "load", at = @At("TAIL"))
    private void sparkle_morpher$repairUnboundExtraAnimationKeys(CallbackInfo ci) {
        for (KeyMapping mapping : keyMappings) {
            if (mapping.getName().startsWith("key.sparkle_morpher.extra_animation.")
                    && "key.keyboard.-1".equals(mapping.saveString())) {
                mapping.setKey(InputConstants.UNKNOWN);
            }
        }
    }
}
