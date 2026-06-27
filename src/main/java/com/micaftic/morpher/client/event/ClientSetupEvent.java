package com.micaftic.morpher.client.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.animation.AnimationRegister;
import com.micaftic.morpher.client.input.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.opengl.*;
import com.micaftic.morpher.core.api.PlatformAPI;

@EventBusSubscriber(modid = YesSteveModel.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientSetupEvent {
    private ClientSetupEvent() {}
    @SubscribeEvent public static void onSetup(FMLClientSetupEvent event) { if (YesSteveModel.isAvailable()) AnimationRegister.registerAnimationState(); }
    @SubscribeEvent public static void onKeys(RegisterKeyMappingsEvent event) { registerKeyMappings(event); }
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(PlayerModelToggleKey.KEY_MAPPING);
        event.register(AnimationRouletteKey.KEY_ROULETTE); event.register(AnimationRouletteKey.KEY_LOCK);
        event.register(DebugAnimationKey.KEY_MAPPING); event.register(ExtraPlayerRenderKey.KEY_MAPPING);
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
