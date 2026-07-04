package com.micaftic.morpher.neoforge;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.core.architectury.event.events.common.LifecycleEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import com.micaftic.morpher.core.api.config.ConfigRegistration;

@Mod(YesSteveModel.MOD_ID)
public final class YesSteveModelNeoForge {
    public YesSteveModelNeoForge(IEventBus modBus, ModContainer container) {
        ConfigRegistration.setContainer(container);
        NeoForgeCapabilityTypes.register(modBus);
        NeoForgeEventBridge.register(modBus);
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            initClient(modBus, container);
        }
        YesSteveModel.init();
        NetworkHandler.init();
        LifecycleEvent.fireSetup();
    }

    private static void initClient(IEventBus modBus, ModContainer container) {
        try {
            Class.forName("com.micaftic.morpher.neoforge.YesSteveModelNeoForgeClient")
                    .getMethod("init", IEventBus.class, ModContainer.class)
                    .invoke(null, modBus, container);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to initialize Sparkle's Morpher client hooks", e);
        }
    }
}
