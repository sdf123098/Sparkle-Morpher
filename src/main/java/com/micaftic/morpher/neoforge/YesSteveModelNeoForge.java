package com.micaftic.morpher.neoforge;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.gui.ModernPlayerModelScreen;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.core.architectury.event.events.common.LifecycleEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import com.micaftic.morpher.core.api.config.ConfigRegistration;

@Mod(YesSteveModel.MOD_ID)
public final class YesSteveModelNeoForge {
    public YesSteveModelNeoForge(IEventBus modBus, ModContainer container) {
        ConfigRegistration.setContainer(container);
        NeoForgeCapabilityTypes.register(modBus);
        NeoForgeEventBridge.register(modBus);
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            container.registerExtensionPoint(IConfigScreenFactory.class, (modContainer, parent) -> ModernPlayerModelScreen.settings(parent));
            NeoForgeClientEventBridge.register(modBus);
        }
        YesSteveModel.init();
        NetworkHandler.init();
        LifecycleEvent.fireSetup();
    }
}
