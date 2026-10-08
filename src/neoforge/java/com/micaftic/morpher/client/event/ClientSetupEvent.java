package com.micaftic.morpher.client.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.animation.AnimationManager;
import com.micaftic.morpher.client.input.*;
import com.micaftic.morpher.core.api.client.RenderLivingBridge;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.opengl.*;
import com.micaftic.morpher.core.api.PlatformAPI;

@EventBusSubscriber(modid = YesSteveModel.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientSetupEvent {
    private ClientSetupEvent() {}
    @SubscribeEvent public static void onSetup(FMLClientSetupEvent event) {
        RenderLivingBridge.install(new RenderLivingBridge.Dispatcher() {
            @SuppressWarnings({"rawtypes", "unchecked"})
            @Override
            public boolean firePre(net.minecraft.world.entity.LivingEntity entity, LivingEntityRenderer<?, ?> renderer,
                                   float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack,
                                   net.minecraft.client.renderer.MultiBufferSource bufferSource, int packedLight) {
                RenderLivingEvent.Pre pre = new RenderLivingEvent.Pre(entity, (LivingEntityRenderer) renderer,
                        partialTick, poseStack, bufferSource, packedLight);
                NeoForge.EVENT_BUS.post(pre);
                return pre.isCanceled();
            }

            @SuppressWarnings({"rawtypes", "unchecked"})
            @Override
            public void firePost(net.minecraft.world.entity.LivingEntity entity, LivingEntityRenderer<?, ?> renderer,
                                 float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack,
                                 net.minecraft.client.renderer.MultiBufferSource bufferSource, int packedLight) {
                NeoForge.EVENT_BUS.post(new RenderLivingEvent.Post(entity, (LivingEntityRenderer) renderer,
                        partialTick, poseStack, bufferSource, packedLight));
            }
        });
        if (YesSteveModel.isAvailable()) AnimationManager.registerDefaultStates();
    }
    @SubscribeEvent public static void onKeys(RegisterKeyMappingsEvent event) { registerKeyMappings(event); }
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(PlayerModelToggleKey.KEY_MAPPING);
        event.register(TargetActionWheelKey.KEY_MAPPING);
        event.register(AnimationRouletteKey.KEY_ROULETTE); event.register(AnimationRouletteKey.KEY_LOCK);
        event.register(DebugAnimationKey.KEY_MAPPING);
        for (KeyMapping m : ExtraAnimationKey.getKeyMappings()) event.register(m);
    }
    public static Object nativeClientInit() {
        try {
            int max = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
            if (max <= 0) return Component.literal("YSM: OpenGL context not available");
            try { int s = GL20.glCreateShader(GL20.GL_VERTEX_SHADER); if (s != 0) GL20.glDeleteShader(s); } catch (Exception e) { return Component.literal("YSM: GL20 (shaders) not available"); }
            return null;
        } catch (Exception e) { return Component.literal("sparkle Client Init Failed: " + e.getMessage()); }
    }
}
