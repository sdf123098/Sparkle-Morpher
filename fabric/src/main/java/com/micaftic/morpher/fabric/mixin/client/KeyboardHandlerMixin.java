package com.micaftic.morpher.fabric.mixin.client;

import com.micaftic.morpher.core.architectury.event.EventResult;
import com.micaftic.morpher.core.architectury.event.events.client.ClientRawInputEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void ysm$fireKeyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        EventResult result = ClientRawInputEvent.KEY_PRESSED.fireEventResult(handler ->
            handler.keyPressed(client, event.key(), event.scancode(), action, event.modifiers()));
        if (result.isFalse()) {
            ci.cancel();
        }
    }
}
