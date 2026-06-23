package com.micaftic.morpher.fabric.mixin.client;

import com.micaftic.morpher.core.architectury.event.events.client.ClientRawInputEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "onButton", at = @At("HEAD"))
    private void ysm$fireMouseClickedPre(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        ClientRawInputEvent.MOUSE_CLICKED_PRE.fire(handler ->
            handler.mouseClickedPre(client, info.button(), action, info.modifiers()));
    }
}
