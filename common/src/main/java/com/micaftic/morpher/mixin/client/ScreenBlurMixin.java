package com.micaftic.morpher.mixin.client;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * YSM screens draw their panel content (search box, text, model preview, etc.) directly and then
 * call {@code super.render(...)} at the end of their own render method. The vanilla
 * {@link Screen#render} calls {@code renderBackground -> renderBlurredBackground ->
 * GameRenderer.processBlurEffect}, which applies a gaussian blur to the whole framebuffer - which
 * by that point already contains the YSM panel content. That made the panel contents look blurred.
 *
 * This mixin cancels the blur, but only for screens belonging to this mod, so vanilla and other
 * mods' screens keep their normal menu-background blur.
 */
@Mixin(Screen.class)
public class ScreenBlurMixin {

    @Inject(method = "renderBlurredBackground", at = @At("HEAD"), cancellable = true)
    private void yesSteveModel$disableBlurForOwnScreens(float partialTick, CallbackInfo ci) {
        String screenClassName = this.getClass().getName();
        if (screenClassName.startsWith("com.micaftic.morpher.client.gui.")
                || screenClassName.startsWith("com.micaftic.morpher.core.gui.")) {
            ci.cancel();
        }
    }
}
