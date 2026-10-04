package com.micaftic.morpher.fabric;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.event.CapabilityEvent;
import com.micaftic.morpher.network.NetworkHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.minecraft.server.level.ServerPlayer;

public final class YesSteveModelFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        YesSteveModel.init();
    }
}
