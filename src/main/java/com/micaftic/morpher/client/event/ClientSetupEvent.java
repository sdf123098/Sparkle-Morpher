package com.micaftic.morpher.client.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.animation.AnimationRegister;
import com.micaftic.morpher.client.input.AnimationRouletteKey;
import com.micaftic.morpher.client.input.DebugAnimationKey;
import com.micaftic.morpher.client.input.ExtraAnimationKey;
import com.micaftic.morpher.client.input.ExtraPlayerRenderKey;
import com.micaftic.morpher.client.input.PlayerModelToggleKey;
import com.micaftic.morpher.core.architectury.event.events.client.ClientLifecycleEvent;
import com.micaftic.morpher.core.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.network.chat.Component;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import com.micaftic.morpher.core.api.PlatformAPI;
import net.minecraft.client.KeyMapping;

public final class ClientSetupEvent {

    private ClientSetupEvent() {
    }

    public static void register() {
        registerKeyMappings();
        if (YesSteveModel.isAvailable()) {
            AnimationRegister.registerAnimationState();
        }
        ClientLifecycleEvent.CLIENT_STARTED.register(client -> {
            if (!YesSteveModel.isAvailable()) {
                return;
            }
            checkNativeInitialization();
            ClientModelManager.loadDefaultModel();
            ClientModelManager.reloadLocalModels(null, false);
        });
    }

    private static void registerKeyMappings() {
        KeyMappingRegistry.register(PlayerModelToggleKey.KEY_MAPPING);
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        KeyMappingRegistry.register(AnimationRouletteKey.KEY_ROULETTE);
        KeyMappingRegistry.register(AnimationRouletteKey.KEY_LOCK);
        KeyMappingRegistry.register(DebugAnimationKey.KEY_MAPPING);
        KeyMappingRegistry.register(ExtraPlayerRenderKey.KEY_MAPPING);
        for (KeyMapping mapping : ExtraAnimationKey.getKeyMappings()) {
            KeyMappingRegistry.register(mapping);
        }
    }

    public static Object nativeClientInit() {
        try {
            int maxTexSize = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
            if (maxTexSize <= 0) {
                return Component.literal("YSM: OpenGL context not available");
            }
            // 原始C++碼檢查了GL20（著色器）和 GL30（VAO）的可用性
            try {
                int testShader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
                if (testShader != 0) {
                    GL20.glDeleteShader(testShader);
                }
            } catch (Exception e) {
                return Component.literal("YSM: GL20 (shaders) not available");
            }

            // 预載入default模型，延遲至第一次渲染tick
            // 不能在FMLClientSetupEvent中同步執行ModelAssembler，會導致StackOverflow
            //ClientModelManager.schedulePreloadDefaultModel();
            return null; // 成功
        } catch (Exception e) {
            return Component.literal("sparkle Client Init Failed: " + e.getMessage());
        }
    }

    private static void checkNativeInitialization() {
        Component component = (Component) nativeClientInit();
        if (component != null) {
            throw new RuntimeException("sparkle Client Initialization Failed: " + component.getString(256));
        }
    }

    // 這裡本來有一個native方法，可能是運行時會初始化載入模型
}
