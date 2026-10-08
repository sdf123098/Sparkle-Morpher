package com.micaftic.morpher.neoforge;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.config.GeneralConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(value = YesSteveModel.MOD_ID, dist = Dist.CLIENT)
public final class YesSteveModelNeoForge {

    public YesSteveModelNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        // Register configs in constructor so they're available before any events fire
        modContainer.registerConfig(ModConfig.Type.CLIENT, GeneralConfig.buildSpec());

        YesSteveModel.registerModBusEvents(modEventBus);

        modEventBus.addListener(YesSteveModelNeoForge::onCommonSetup);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(YesSteveModel::init);
    }
}
