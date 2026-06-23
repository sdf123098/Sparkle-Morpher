package com.micaftic.morpher.client.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.input.AnimationRouletteKey;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.C2SPlayAnimationPacket;
import com.micaftic.morpher.util.InputUtil;
import com.micaftic.morpher.core.architectury.event.EventResult;
import com.micaftic.morpher.core.architectury.event.events.client.ClientRawInputEvent;
import com.micaftic.morpher.core.architectury.event.events.client.ClientTickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;

public class AnimationLockEvent {

    private static boolean animationLocked = false;

    private AnimationLockEvent() {
    }

    public static void register() {
        ClientRawInputEvent.KEY_PRESSED.register((client, keyCode, scanCode, action, modifiers) -> {
            if (YesSteveModel.isAvailable() && InputUtil.isPlayerReady() && action == 1 && InputUtil.isKeyPressed(keyCode, scanCode, AnimationRouletteKey.KEY_LOCK)) {
                animationLocked = !animationLocked;
            }
            return EventResult.pass();
        });
        ClientTickEvent.CLIENT_POST.register(AnimationLockEvent::onClientTick);
    }

    private static void onClientTick(Minecraft client) {
        LocalPlayer localPlayer;
        if (YesSteveModel.isAvailable() && !animationLocked && (localPlayer = client.player) != null && isPlayerMoving(localPlayer)) {
            PlayerCapability.get(localPlayer).ifPresent(cap -> {
                if (cap.isModelSwitching()) {
                    cap.clearModelSwitch();
                    if (NetworkHandler.isClientConnected()) {
                        NetworkHandler.sendToServer(C2SPlayAnimationPacket.createDefault());
                    }
                }
            });
        }
    }

    public static boolean isPlayerMoving(LocalPlayer localPlayer) {
        ClientInput input = localPlayer.input;
        return input != null && (isSignificantImpulse(input.getMoveVector().x) || isSignificantImpulse(input.getMoveVector().y) || input.keyPresses.jump() || input.keyPresses.shift());
    }

    private static boolean isSignificantImpulse(float impulse) {
        return Math.abs(impulse) > 1.0E-5f;
    }

    public static void toggleLock() {
        animationLocked = !animationLocked;
    }

    public static boolean isLocked() {
        return animationLocked;
    }
}
