package com.micaftic.morpher.network;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.core.api.network.PacketContext;
import net.minecraft.network.Connection;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.lang.reflect.InvocationTargetException;

public final class ClientNetworkBridge {
    private static final String HANDLER_CLASS = "com.micaftic.morpher.client.network.ClientPacketHandlers";

    private ClientNetworkBridge() {}

    public static boolean isPhysicalClient() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }

    public static boolean isClientConnected() {
        if (!isPhysicalClient()) return false;
        Object result = invoke("isClientConnected", new Class<?>[0]);
        return result instanceof Boolean value && value;
    }

    public static boolean isPrivacyModeActive() {
        if (!isPhysicalClient()) return false;
        Object result = invoke("isPrivacyModeActive", new Class<?>[0]);
        return result instanceof Boolean value && value;
    }

    public static boolean isLocalPlayer(Object player) {
        if (!isPhysicalClient()) return false;
        Object result = invoke("isLocalPlayer", new Class<?>[]{Object.class}, player);
        return result instanceof Boolean value && value;
    }

    public static void handle(PacketContext ctx, String methodName, Object message) {
        if (ctx.isClientSide()) {
            ctx.enqueueWork(() -> invoke(methodName, new Class<?>[]{Object.class}, message));
        }
    }

    public static void handle(PacketContext ctx, String methodName, Object message, Connection connection) {
        if (ctx.isClientSide()) {
            ctx.enqueueWork(() -> invoke(methodName, new Class<?>[]{Object.class, Connection.class}, message, connection));
        }
    }

    private static Object invoke(String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Class<?> handler = Class.forName(HANDLER_CLASS);
            return handler.getMethod(methodName, parameterTypes).invoke(null, args);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Client handler is unavailable on physical client", e);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to invoke client handler " + methodName, e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            if (cause instanceof Error error) throw error;
            YesSteveModel.LOGGER.error("Client handler {} failed", methodName, cause);
            throw new RuntimeException(cause);
        }
    }
}
