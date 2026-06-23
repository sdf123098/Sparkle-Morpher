package com.micaftic.morpher.client.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.input.AnimationRouletteKey;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.C2SPlayAnimationPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

@EventBusSubscriber(modid = YesSteveModel.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class AnimationLockEvent {
    private static boolean locked = false;
    private AnimationLockEvent() {}
    public static void register() {}
    @SubscribeEvent public static void onKey(InputEvent.Key event) { if (YesSteveModel.isAvailable() && event.getAction() == 1 && AnimationRouletteKey.KEY_LOCK.matches(event.getKey(), event.getScanCode())) locked = !locked; }
    @SubscribeEvent public static void onTick(net.neoforged.neoforge.client.event.ClientTickEvent.Pre event) {
        LocalPlayer p; if (YesSteveModel.isAvailable() && !locked && (p = Minecraft.getInstance().player) != null && isMoving(p)) PlayerCapability.get(p).ifPresent(c -> { if (c.isModelSwitching()) { c.clearModelSwitch(); if (NetworkHandler.isClientConnected()) NetworkHandler.sendToServer(C2SPlayAnimationPacket.createDefault()); } });
    }
    public static boolean isMoving(LocalPlayer p) { Input i = p.input; return i != null && (Math.abs(i.leftImpulse) > 1e-5f || Math.abs(i.forwardImpulse) > 1e-5f || i.jumping || i.shiftKeyDown); }
    public static void toggleLock() { locked = !locked; } public static boolean isLocked() { return locked; }
}