package com.micaftic.morpher.client.event;

import com.micaftic.morpher.cloud.client.CloudPlayerMotionSync;
import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.core.architectury.event.events.client.ClientTickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;

public class AnimationLockEvent {

    private static boolean animationLocked = false;

    private AnimationLockEvent() {
    }

    public static void register() {
        ClientTickEvent.CLIENT_POST.register(AnimationLockEvent::onClientTick);
    }

    private static void onClientTick(Minecraft client) {
        LocalPlayer localPlayer;
        if (YesSteveModel.isAvailable() && !animationLocked && (localPlayer = client.player) != null && isPlayerMoving(localPlayer)) {
            PlayerCapability.get(localPlayer).ifPresent(cap -> {
                if (cap.isModelSwitching()) {
                    CloudPlayerMotionSync.stop(cap);
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
