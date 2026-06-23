package com.micaftic.morpher.fabric.mixin;

import com.micaftic.morpher.core.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(Options.class)
public class OptionsMixin {

    @Shadow
    @Final
    @Mutable
    private KeyMapping[] keyMappings;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ysm$injectCustomKeyMappings(CallbackInfo ci) {
        List<KeyMapping> all = new ArrayList<>(java.util.Arrays.asList(this.keyMappings));
        for (KeyMapping km : KeyMappingRegistry.getCustomKeyMappings()) {
            all.add(km);
        }
        this.keyMappings = all.toArray(new KeyMapping[0]);
    }
}
