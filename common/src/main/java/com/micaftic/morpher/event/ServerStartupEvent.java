package com.micaftic.morpher.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.model.ServerModelManager;
import com.micaftic.morpher.core.architectury.event.events.common.LifecycleEvent;

public final class ServerStartupEvent {

    private ServerStartupEvent() {
    }

    public static void register() {
        LifecycleEvent.SERVER_BEFORE_START.register(server -> {
            if (!YesSteveModel.isAvailable()) {
                return;
            }
            ServerModelManager.loadModels(result -> {
                if (!result.isSuccess()) {
                    server.execute(() -> {
                        throw new RuntimeException("sparkle Loading Failed: " + result.getErrorMessage().getString(256));
                    });
                }
            }, null);
        });
    }
}