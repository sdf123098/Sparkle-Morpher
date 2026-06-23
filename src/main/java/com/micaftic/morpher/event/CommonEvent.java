package com.micaftic.morpher.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.core.compat.touhoulittlemaid.TouhouMaidCompat;
import com.micaftic.morpher.model.ServerModelManager;
import com.micaftic.morpher.network.NetworkHandler;
import java.io.IOException;

public final class CommonEvent {
    private CommonEvent() {}
    public static void register() {}
    public static void init() {
        if (!YesSteveModel.isAvailable()) { YesSteveModel.LOGGER.error(YesSteveModel.getErrorMessage()); return; }
        NetworkHandler.init(); TouhouMaidCompat.init();
        try { ServerModelManager.reloadPacks(); } catch (IOException e) { throw new RuntimeException(e); }
    }
}