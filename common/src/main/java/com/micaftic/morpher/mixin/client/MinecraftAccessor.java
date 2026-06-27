package com.micaftic.morpher.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Invoker("isLocalServer") boolean ysm$isLocalServer();
    @Invoker("getTextureManager") TextureManager ysm$getTextureManager();
    @Invoker("getEntityRenderDispatcher") EntityRenderDispatcher ysm$getEntityRenderDispatcher();
    @Invoker("getSoundManager") SoundManager ysm$getSoundManager();
    @Invoker("getResourceManager") ResourceManager ysm$getResourceManager();
    @Invoker("getConnection") ClientPacketListener ysm$getConnection();
    @Invoker("getDeltaTracker") DeltaTracker ysm$getDeltaTracker();
}
