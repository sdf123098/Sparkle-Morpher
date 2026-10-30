package com.micaftic.morpher.core.gpu;

import com.micaftic.morpher.core.render.SmGraphicsBackend;
import com.micaftic.morpher.core.render.SmGraphicsBackendDetector;
import com.micaftic.morpher.mixin.client.GameRendererGlobalsAccessor;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Supplies the engine-owned Globals UBO to the 26.2 atlas upload/animation passes. */
public final class VulkanAtlasUniforms {
    private VulkanAtlasUniforms() {}

    public static void bind(RenderPass pass) {
        if (SmGraphicsBackendDetector.currentBackend() != SmGraphicsBackend.VULKAN) return;
        // Resource uploads may happen before the first frame initializes Globals.
        // The normal GameRenderer update takes over on its first frame.
        if (RenderSystem.getGlobalSettingsUniform() == null) {
            var mc = Minecraft.getInstance();
            ((GameRendererGlobalsAccessor) mc.gameRenderer).sparkleMorpher$globalSettingsUniform().update(
                    mc.getWindow().getWidth(), mc.getWindow().getHeight(), mc.options.glintStrength().get(),
                    0L, mc.getDeltaTracker(), 0, Vec3.ZERO, false);
        }
        pass.setUniform("Globals", RenderSystem.getGlobalSettingsUniform());
    }
}
