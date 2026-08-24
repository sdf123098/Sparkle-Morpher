package com.micaftic.morpher.mixin.client;

import com.micaftic.morpher.core.architectury.event.events.client.ClientPlayerEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Unique
    private LocalPlayer ysm$playerBeforeRespawn;

    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void ysm$capturePlayerBeforeRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        ysm$playerBeforeRespawn = Minecraft.getInstance().player;
    }

    @Inject(method = "handleRespawn", at = @At("TAIL"))
    private void ysm$fireClientRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        LocalPlayer oldPlayer = ysm$playerBeforeRespawn;
        LocalPlayer newPlayer = Minecraft.getInstance().player;
        ysm$playerBeforeRespawn = null;
        if (oldPlayer != null && newPlayer != null && oldPlayer != newPlayer) {
            ClientPlayerEvent.CLIENT_PLAYER_RESPAWN.fire(handler -> handler.accept(oldPlayer, newPlayer));
        }
    }
}
