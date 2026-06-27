package com.micaftic.morpher.neoforge;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.event.ClientSetupEvent;
import com.micaftic.morpher.client.gui.ModernPlayerModelScreen;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.config.ServerConfig;
import com.micaftic.morpher.network.NetworkHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import com.micaftic.morpher.core.api.network.neoforge.YSMChannelImpl;

@Mod(YesSteveModel.MOD_ID)
public final class YesSteveModelNeoForge {

    public YesSteveModelNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        // Register configs in constructor so they're available before any events fire
        modContainer.registerConfig(ModConfig.Type.CLIENT, GeneralConfig.buildSpec());
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.buildSpec());

        if (FMLEnvironment.dist == Dist.CLIENT) {
            // Register config screen so the Settings button in the Mods menu works
            modContainer.registerExtensionPoint(IConfigScreenFactory.class, (mc, parentScreen) -> ModernPlayerModelScreen.settings(parentScreen));
        }

        // Init network channel and register packets early (before any player join events)
        YSMChannelImpl.init(NetworkHandler.CHANNEL_ID, NetworkHandler.VERSION);
        NetworkHandler.init();

        YesSteveModel.registerModBusEvents(modEventBus);
        modEventBus.addListener(YSMChannelImpl::registerPayloadHandlers);
        modEventBus.addListener(YesSteveModelNeoForge::onCommonSetup);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(YesSteveModel::init);
    }
}
