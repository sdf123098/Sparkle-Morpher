package com.micaftic.morpher.fabric;

import com.micaftic.morpher.YesSteveModel;
import net.fabricmc.api.ModInitializer;

public final class YesSteveModelFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        YesSteveModel.init();
    }
}
