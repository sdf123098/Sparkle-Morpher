package com.micaftic.morpher.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.core.api.PlatformAPI;
import com.micaftic.morpher.core.compat.api.CompatServices;
import com.micaftic.morpher.model.ServerModelManagerService;
import com.micaftic.morpher.network.NetworkHandlerService;

import java.lang.reflect.InvocationTargetException;

public final class YsmEventBootstrap {
    private YsmEventBootstrap() {}
    public static void register() {
        // R11.2：兼容层服务注册——核心只定义 hook，adapter 在此注入。
        CompatServices.registerMaidModelService(ServerModelManagerService.INSTANCE);
        CompatServices.registerMaidNetworkService(NetworkHandlerService.INSTANCE);

        ServerStartupEvent.register(); EnterServerEvent.register(); PlayerLogoutEvent.register();
        CommandRegistry.register(); CapabilityEvent.register(); LivingEventBridge.register();
        if (!PlatformAPI.isServer()) {
            registerClient("com.micaftic.morpher.event.EntityJoinCallbackEvent");
            registerClient("com.micaftic.morpher.client.event.ClientResourceLifecycleEvent");
            registerClient("com.micaftic.morpher.client.event.PlayerSkinTextureManager");
            registerClient("com.micaftic.morpher.client.renderer.RendererManager");
        }
    }

    private static void registerClient(String className) {
        try {
            Class.forName(className).getMethod("register").invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to register client hook " + className, e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            YesSteveModel.LOGGER.error("Client hook {} failed", className, cause);
            throw new RuntimeException(cause);
        }
    }
}
