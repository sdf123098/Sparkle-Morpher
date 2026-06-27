package com.micaftic.morpher.event;

import com.micaftic.morpher.client.event.*;
import com.micaftic.morpher.client.renderer.RendererManager;
import com.micaftic.morpher.core.api.PlatformAPI;

public final class YsmEventBootstrap {
    private YsmEventBootstrap() {}
    public static void register() {
        ServerStartupEvent.register(); EnterServerEvent.register(); PlayerLogoutEvent.register();
        CommandRegistry.register(); CapabilityEvent.register(); LivingEventBridge.register();
        if (!PlatformAPI.isServer()) { EntityJoinCallbackEvent.register(); ClientResourceLifecycleEvent.register(); PlayerSkinTextureManager.register(); RendererManager.register(); }
    }
}
