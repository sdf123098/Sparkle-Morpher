package com.micaftic.morpher.event;

import com.micaftic.morpher.client.event.AnimationLockEvent;
import com.micaftic.morpher.client.event.ClientPlayerCloneEvent;
import com.micaftic.morpher.client.event.ClientPlayerJoinNotification;
import com.micaftic.morpher.client.event.ClientResourceLifecycleEvent;
import com.micaftic.morpher.client.event.ClientSetupEvent;
import com.micaftic.morpher.client.event.ClientTickEvent;
import com.micaftic.morpher.client.event.PlayerSkinTextureManager;
import com.micaftic.morpher.client.input.AnimationRouletteKey;
import com.micaftic.morpher.client.input.DebugAnimationKey;
import com.micaftic.morpher.client.input.ExtraAnimationKey;
import com.micaftic.morpher.client.input.InputStateKey;
import com.micaftic.morpher.client.input.PlayerModelToggleKey;
import com.micaftic.morpher.client.renderer.RendererManager;
import com.micaftic.morpher.core.architectury.event.events.common.LifecycleEvent;
import com.micaftic.morpher.core.api.PlatformAPI;
import com.micaftic.morpher.core.compat.api.CompatServices;
import com.micaftic.morpher.model.ServerModelManagerService;
import com.micaftic.morpher.network.NetworkHandlerService;

public final class YsmEventBootstrap {

    private YsmEventBootstrap() {
    }

    public static void register() {
        // R11.2：兼容层服务注册——核心只定义 hook，adapter 在此注入。
        CompatServices.registerMaidModelService(ServerModelManagerService.INSTANCE);
        CompatServices.registerMaidNetworkService(NetworkHandlerService.INSTANCE);

        ServerStartupEvent.register();
        EnterServerEvent.register();
        PlayerLogoutEvent.register();
        CommonEvent.register();
        CommandRegistry.register();

        CapabilityEvent.register();

        if (!PlatformAPI.isServer()) {
            EntityJoinCallbackEvent.register();

            ClientSetupEvent.register();
            ClientResourceLifecycleEvent.register();
            ClientTickEvent.register();
            ClientPlayerJoinNotification.register();
            ClientPlayerCloneEvent.register();
            AnimationLockEvent.register();
            PlayerSkinTextureManager.register();
            RendererManager.register();
            PlayerModelToggleKey.register();
            AnimationRouletteKey.register();
            DebugAnimationKey.register();
            ExtraAnimationKey.register();
            InputStateKey.register();
        }

        LifecycleEvent.fireSetup();
    }
}
