package com.micaftic.morpher.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.core.api.PlatformAPI;
import com.micaftic.morpher.core.compat.touhoulittlemaid.TouhouMaidCompat;
import com.micaftic.morpher.model.ServerModelManager;
import com.micaftic.morpher.network.NetworkHandler;
import dev.architectury.event.events.common.LifecycleEvent;

import java.io.IOException;

public final class CommonEvent {

    private CommonEvent() {
    }

    public static Object nativeInit() {
        try {
            ServerModelManager.reloadPacks();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public static void register() {
        LifecycleEvent.SETUP.register(() -> {
            if (!YesSteveModel.isAvailable()) {
                YesSteveModel.LOGGER.error(YesSteveModel.getErrorMessage());
                return;
            }
            NetworkHandler.init();
            TouhouMaidCompat.init();
            nativeInit();
        });
    }
}