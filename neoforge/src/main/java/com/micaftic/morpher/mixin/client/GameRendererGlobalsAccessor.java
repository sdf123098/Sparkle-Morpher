package com.micaftic.morpher.mixin.client;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererGlobalsAccessor {
    @Accessor("globalSettingsUniform")
    GlobalSettingsUniform sparkleMorpher$globalSettingsUniform();
}
