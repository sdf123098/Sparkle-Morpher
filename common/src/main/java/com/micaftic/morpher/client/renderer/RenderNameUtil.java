package com.micaftic.morpher.client.renderer;

import net.minecraft.client.Minecraft;

public final class RenderNameUtil {
    private RenderNameUtil() {
    }

    public static boolean shouldRenderNames() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null || !minecraft.options.hideMatchedNames().get();
    }
}
