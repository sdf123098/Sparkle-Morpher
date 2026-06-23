package com.micaftic.morpher.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// MC 26.x: shaderLightDirections removed, accessor disabled
// @Mixin(RenderSystem.class)
public interface RenderSystemAccessor {
    static Vector3f[] ysm$getShaderLightDirections() {
        return null;
    }
}
