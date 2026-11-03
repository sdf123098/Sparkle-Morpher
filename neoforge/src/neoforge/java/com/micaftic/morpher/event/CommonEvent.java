package com.micaftic.morpher.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.core.compat.touhoulittlemaid.TouhouMaidCompat;
import com.micaftic.morpher.client.LocalModelBootstrap;
public final class CommonEvent {
    private CommonEvent() {}
    public static void register() {}
    public static void init() {
        if (!YesSteveModel.isAvailable()) { YesSteveModel.LOGGER.error(YesSteveModel.getErrorMessage()); return; }
        TouhouMaidCompat.init();
        LocalModelBootstrap.initialize();
    }
}
